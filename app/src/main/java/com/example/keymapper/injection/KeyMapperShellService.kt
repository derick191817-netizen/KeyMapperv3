package com.example.keymapper.injection

import android.os.Build
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.annotation.Keep
import android.os.RemoteException
import android.util.Log
import com.example.keymapper.input.NativeBatchCallbackForwarder
import java.io.File

/**
 * Executa no processo privilegiado do Shizuku (UID shell/ADB ou root).
 * Mantém um leitor evdev nativo por JNI durante a sessão; inspectRuntime continua sendo
 * apenas diagnóstico. Todos os dispositivos capturados são liberados no stop/destroy.
 */
@Keep
class KeyMapperShellService @Keep constructor() : IKeyMapperShellService.Stub() {

    @Volatile
    private var inputCallback: IKeyMapperInputCallback? = null

    @Volatile
    private var nativeStarted = false

    private fun loadNativeLibrary() {
        System.loadLibrary(NativeBackendLibrary.LOAD_NAME)
    }

    /** Starts the persistent native evdev reader in this privileged Shizuku process. */
    @Synchronized
    @Keep
    override fun startInputCapture(callback: IKeyMapperInputCallback, enabled: Boolean): Boolean {
        inputCallback = callback
        return try {
            loadNativeLibrary()
            if (nativeStarted && nativeIsCaptureRunning()) return true
            // Reinicia o leitor caso o worker nativo tenha terminado por erro de poll/JNI.
            nativeStarted = nativeStartCapture(enabled)
            nativeStarted
        } catch (t: Throwable) {
            Log.e(TAG, "Não foi possível iniciar o backend nativo evdev", t)
            inputCallback = null
            nativeStarted = false
            false
        }
    }

    @Keep
    fun onNativeInputBatch(records: LongArray) {
        // Não trate callback ausente como entrega bem-sucedida: o worker JNI precisa receber
        // uma exceção para marcar os dispositivos do lote e prefixar SYN_DROPPED na próxima
        // tentativa. Durante o stop normal, o worker é encerrado antes de inputCallback=null.
        val callback = inputCallback
            ?: throw IllegalStateException("Callback de entrada não está vinculado")
        NativeBatchCallbackForwarder.forward(
            records = records,
            callback = callback::onInputEvents,
            onFailure = { failure ->
                if (failure is RemoteException) {
                    Log.w(TAG, "Cliente do KeyMapper desconectado; a captura será pausada", failure)
                    runCatching { nativeSetCaptureEnabled(false) }
                } else {
                    Log.e(TAG, "Falha encaminhando o lote evdev; sinalizando perda à camada JNI", failure)
                }
            }
        )
    }

    @Keep
    fun onNativeCaptureState(mouseDevices: Int, keyboardDevices: Int, enabled: Boolean, detail: String) {
        try {
            inputCallback?.onCaptureState(mouseDevices, keyboardDevices, enabled, detail)
        } catch (e: RemoteException) {
            Log.w(TAG, "Cliente não recebeu o estado do backend evdev", e)
        }
    }

    @Synchronized
    override fun setInputCaptureEnabled(enabled: Boolean) {
        try {
            if (!nativeStarted || !nativeIsCaptureRunning()) {
                nativeStarted = nativeStartCapture(enabled)
                if (!nativeStarted) {
                    inputCallback?.onCaptureState(
                        0, 0, false,
                        "O leitor evdev terminou e não pôde ser reiniciado; use o backend alternativo"
                    )
                }
                return
            }
            nativeSetCaptureEnabled(enabled)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha alterando/recuperando o estado da captura evdev", t)
            nativeStarted = false
        }
    }

    @Synchronized
    override fun stopInputCapture() {
        // Encerrar a sessão de toque antes de liberar o processo privilegiado evita contatos
        // virtuais presos quando o cliente cai ou o serviço é destruído abruptamente.
        runCatching { nativeReleaseVirtualTouch() }
            .onFailure { Log.w(TAG, "Falha liberando contatos uinput no stop", it) }
        runCatching { nativeCloseVirtualTouch() }
            .onFailure { Log.w(TAG, "Falha fechando o dispositivo uinput", it) }
        if (nativeStarted) runCatching { nativeStopCapture() }
            .onFailure { Log.e(TAG, "Falha encerrando o backend evdev", it) }
        nativeStarted = false
        inputCallback = null
    }

    /** Cria um dispositivo touchscreen virtual independente da captura física evdev. */
    @Synchronized
    @Keep
    override fun openVirtualTouch(maxSlots: Int): Boolean = try {
        loadNativeLibrary()
        nativeOpenVirtualTouch(maxSlots.coerceIn(1, 32))
    } catch (t: Throwable) {
        Log.w(TAG, "uinput touchscreen indisponível; InputManager permanecerá como fallback", t)
        false
    }

    /** Entrega um quadro multitoque completo ao backend nativo. Chamado pela thread serial do injetor. */
    @Synchronized
    @Keep
    @Throws(RemoteException::class)
    override fun sendVirtualTouchFrame(
        actionMasked: Int,
        actionIndex: Int,
        pointerCount: Int,
        pointerIds: IntArray?,
        pointerXs: FloatArray?,
        pointerYs: FloatArray?
    ): Boolean {
        if (pointerIds == null || pointerXs == null || pointerYs == null) return false
        return try {
            nativeSendVirtualTouchFrame(actionMasked, actionIndex, pointerCount, pointerIds, pointerXs, pointerYs)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha enviando quadro pelo uinput", t)
            false
        }
    }

    @Synchronized
    @Keep
    override fun releaseVirtualTouch(): Boolean = runCatching { nativeReleaseVirtualTouch() }
        .getOrElse {
            Log.w(TAG, "Falha liberando touchscreen virtual", it)
            false
        }

    @Synchronized
    @Keep
    override fun closeVirtualTouch() {
        runCatching { nativeCloseVirtualTouch() }
            .onFailure { Log.w(TAG, "Falha fechando touchscreen virtual", it) }
    }

    @Synchronized
    @Keep
    override fun virtualTouchStatus(): String = runCatching {
        loadNativeLibrary()
        nativeVirtualTouchStatus()
    }.getOrElse { "uinput indisponível: ${it.javaClass.simpleName}: ${it.message}" }

    @Synchronized
    @Keep
    override fun isVirtualTouchOpen(): Boolean = runCatching {
        loadNativeLibrary()
        nativeIsVirtualTouchOpen()
    }.getOrDefault(false)

    /** Called by Shizuku when the bound UserService is explicitly destroyed. */
    @Keep
    override fun destroy() {
        stopInputCapture()
        Process.killProcess(Process.myPid())
    }

    /** Standard UserService exit transaction. */
    @Keep
    override fun exit() {
        destroy()
    }

    override fun inspectRuntime(): String = buildString {
        val uid = Process.myUid()
        val identity = when (uid) {
            0 -> "root"
            2000 -> "shell/ADB"
            else -> "UID personalizado"
        }
        val selinux = runCatching {
            File("/proc/self/attr/current").readText().trim().ifBlank { "indisponível" }
        }.getOrDefault("indisponível")

        val inputDir = File("/dev/input")
        val eventFiles = runCatching {
            inputDir.listFiles()?.filter { it.name.matches(Regex("event\\d+")) }?.sortedBy { it.name }
        }.getOrNull()

        val uinputCandidates = listOf(File("/dev/uinput"), File("/dev/input/uinput"))
        val uinput = uinputCandidates.firstOrNull { it.exists() }
        val getevent = listOf(File("/system/bin/getevent"), File("/vendor/bin/getevent"))
            .firstOrNull { it.exists() && it.canExecute() }

        appendLine("SONDAGEM REAL DA SESSÃO SHIZUKU/ADB")
        appendLine("Android API=${Build.VERSION.SDK_INT}; fabricante=${Build.MANUFACTURER}; modelo=${Build.MODEL}")
        appendLine("UID do processo privilegiado=$uid ($identity)")
        appendLine("Contexto SELinux=$selinux")
        appendLine("/dev/input: existe=${inputDir.exists()}, diretório=${inputDir.isDirectory}, listagem=${inputDir.canRead()}")
        if (eventFiles == null) {
            appendLine("Nós event*: listagem indisponível")
        } else if (eventFiles.isEmpty()) {
            appendLine("Nós event*: nenhum visível para esta identidade")
        } else {
            appendLine("Nós event* visíveis=${eventFiles.size}")
            var openable = 0
            eventFiles.take(20).forEach { file ->
                val openResult = testOpen(file, OsConstants.O_RDONLY)
                if (openResult.startsWith("OK")) openable++
                appendLine("  ${file.name}: canRead=${file.canRead()}, abertura O_RDONLY=$openResult")
            }
            if (eventFiles.size > 20) appendLine("  ... ${eventFiles.size - 20} omitidos")
            appendLine("Nós event* que aceitaram open/close sem leitura=$openable/${minOf(eventFiles.size, 20)}")
        }
        val uinputOpen = uinput?.let { testOpen(it, OsConstants.O_WRONLY) } ?: "nó ausente"
        appendLine("uinput: caminho=${uinput?.absolutePath ?: "ausente"}, canWrite=${uinput?.canWrite() == true}, abertura O_WRONLY=$uinputOpen")
        appendLine("getevent: ${getevent?.absolutePath ?: "não encontrado"}")
        appendLine("A captura contínua, quando solicitada, é feita pelo backend JNI evdev e exige que open + EVIOCGRAB sejam permitidos nesta ROM.")
        appendLine("A sondagem acima continua sendo apenas open/close; ela não afirma que EVIOCGRAB foi autorizado nem que os eventos chegaram ao app de destino.")
        val touchStatus = runCatching { nativeVirtualTouchStatus() }
            .getOrDefault("Biblioteca nativa ainda não carregada; dispositivo virtual não solicitado")
        appendLine("Saída touchscreen virtual: $touchStatus")
        appendLine("uinput é opcional: quando a criação/abertura falha, o app mantém InputManager via Shizuku. O backend uinput é roteado somente para o display principal (ID 0); monitores DeX/externos permanecem no InputManager para evitar direcionamento incorreto.")
    }

    private external fun nativeStartCapture(enabled: Boolean): Boolean
    private external fun nativeIsCaptureRunning(): Boolean
    private external fun nativeSetCaptureEnabled(enabled: Boolean)
    private external fun nativeStopCapture()
    private external fun nativeOpenVirtualTouch(maxSlots: Int): Boolean
    private external fun nativeIsVirtualTouchOpen(): Boolean
    private external fun nativeSendVirtualTouchFrame(
        actionMasked: Int,
        actionIndex: Int,
        pointerCount: Int,
        pointerIds: IntArray,
        pointerXs: FloatArray,
        pointerYs: FloatArray
    ): Boolean
    private external fun nativeReleaseVirtualTouch(): Boolean
    private external fun nativeCloseVirtualTouch()
    private external fun nativeVirtualTouchStatus(): String

    /** Abre e fecha um nó sem ler ou escrever dados e sem executar ioctl. */
    private fun testOpen(file: File, accessMode: Int): String {
        var descriptor: java.io.FileDescriptor? = null
        return try {
            descriptor = Os.open(
                file.absolutePath,
                accessMode or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC,
                0
            )
            "OK (open/close permitido)"
        } catch (error: ErrnoException) {
            "NEGADO (errno=${error.errno}: ${error.message ?: error.toString()})"
        } catch (error: Throwable) {
            "ERRO (${error.javaClass.simpleName}: ${error.message})"
        } finally {
            descriptor?.let { runCatching { Os.close(it) } }
        }
    }
    companion object {
        private const val TAG = "KeyMapperShellService"
    }
}
