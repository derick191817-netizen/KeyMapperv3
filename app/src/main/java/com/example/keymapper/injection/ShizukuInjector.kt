package com.example.keymapper.injection

import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.InputEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Encaminha InputEvent ao InputManager do sistema usando a identidade concedida pelo Shizuku.
 *
 * O Binder de `input` não é permanente: quando o Shizuku ou o serviço de sistema reinicia,
 * a referência refletida pode ficar obsoleta mesmo que uma consulta posterior a pingBinder()
 * volte a responder. Por isso, falhas de transporte invalidam o cache e a próxima sondagem
 * reconstrói o proxy. Um retorno `true` confirma somente a aceitação pela API de injeção.
 */
class ShizukuInjector : Injector, AutoCloseable {

    private data class RemoteInputApi(
        val manager: Any,
        val injectMethod: Method
    )

    companion object {
        private const val TAG = "KeyMapperShizuku"
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
    }

    private val failureLogged = AtomicBoolean(false)
    private val remoteApiCache = RebindableServiceCache<RemoteInputApi>()
    private val hiddenApiLock = Any()

    @Volatile
    private var hiddenApiBypassAttempted = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        invalidateRemoteApi()
        failureLogged.set(false)
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        invalidateRemoteApi()
    }

    init {
        // Sticky também cobre o caso em que Shizuku já estava ativo antes do serviço iniciar.
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
            .onFailure { Log.w(TAG, "Não foi possível registrar listener de reconexão do Shizuku", it) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
            .onFailure { Log.w(TAG, "Não foi possível registrar listener de queda do Shizuku", it) }
    }

    /**
     * Verifica somente se o app pode iniciar o UserService Shizuku. Não exige INJECT_EVENTS
     * nem acesso ao Binder InputManager; isso permite o backend nativo uinput independente.
     */
    fun canBindUserService(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
            Shizuku.getVersion() >= 10
    } catch (t: Throwable) {
        Log.w(TAG, "Não foi possível validar a sessão Shizuku para UserService", t)
        false
    }

    override fun isReady(): Boolean {
        val permissionGranted = try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                Shizuku.checkRemotePermission("android.permission.INJECT_EVENTS") == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            invalidateRemoteApi()
            logFailureOnce("Não foi possível verificar o Binder/permissão do Shizuku", t)
            false
        }

        if (!permissionGranted) {
            invalidateRemoteApi()
            logFailureOnce(
                "Sessão Shizuku sem autorização efetiva para INJECT_EVENTS; verifique UID shell/root e permissões da ROM",
                SecurityException("INJECT_EVENTS não concedida à sessão remota")
            )
            return false
        }

        return try {
            acquireRemoteApi()
            true
        } catch (t: Throwable) {
            invalidateRemoteApi()
            logFailureOnce("Shizuku está acessível, mas o serviço InputManager não pôde ser preparado", t)
            false
        }
    }

    /** Mostra a identidade real da sessão Shizuku sem inferir acesso a /dev/input ou uinput. */
    fun identityDescription(): String {
        return try {
            if (!Shizuku.pingBinder()) {
                "Shizuku desconectado"
            } else {
                when (val uid = Shizuku.getUid()) {
                    0 -> "Shizuku ativo — UID 0 (root)"
                    2000 -> "Shizuku ativo — UID 2000 (shell/ADB)"
                    else -> "Shizuku ativo — UID $uid (permissões dependem do firmware)"
                }
            }
        } catch (t: Throwable) {
            "Identidade Shizuku indisponível (${t.javaClass.simpleName})"
        }
    }

    private fun acquireRemoteApi(): RemoteInputApi = remoteApiCache.get {
        ensureHiddenApiAccess()

        val inputBinder = SystemServiceHelper.getSystemService("input")
            ?: throw IllegalStateException("Serviço Binder 'input' indisponível")
        val wrappedBinder = ShizukuBinderWrapper(inputBinder)
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        val asInterface = stub.getMethod("asInterface", IBinder::class.java)
        val manager = asInterface.invoke(null, wrappedBinder)
            ?: throw IllegalStateException("IInputManager.Stub.asInterface retornou null")
        val method = manager.javaClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType!!
        )

        RemoteInputApi(manager, method)
    }

    private fun ensureHiddenApiAccess() {
        if (hiddenApiBypassAttempted) return
        synchronized(hiddenApiLock) {
            if (hiddenApiBypassAttempted) return
            hiddenApiBypassAttempted = true
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    HiddenApiBypass.addHiddenApiExemptions(
                        "Landroid/view/InputEvent;",
                        "Landroid/hardware/input/IInputManager;",
                        "Landroid/app/IActivityTaskManager;"
                    )
                } catch (t: Throwable) {
                    // A chamada refletida abaixo continua sendo a validação definitiva.
                    Log.w(TAG, "Bypass de APIs ocultas indisponível; será tentado o acesso normal", t)
                }
            }
        }
    }

    override fun inject(event: InputEvent): Boolean {
        var activeApi: RemoteInputApi? = null
        return try {
            val current = acquireRemoteApi()
            activeApi = current
            val result = current.injectMethod.invoke(
                current.manager,
                event,
                INJECT_INPUT_EVENT_MODE_ASYNC
            ) as? Boolean ?: false
            if (result) failureLogged.set(false)
            result
        } catch (t: Throwable) {
            val cause = unwrapReflectionFailure(t)
            // Uma falha de transporte pode deixar o proxy antigo inutilizável. Não limpamos o
            // cache em erros comuns de política/validação do evento, somente em falha Binder.
            if (cause is RemoteException || !runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
                invalidateRemoteApi(activeApi)
            }
            logFailureOnce("Falha ao injetar entrada via Shizuku/InputManager", cause)
            false
        }
    }

    private fun invalidateRemoteApi(expected: RemoteInputApi? = null) {
        remoteApiCache.invalidate(expected)
    }

    private fun unwrapReflectionFailure(error: Throwable): Throwable {
        var current = error
        while (true) {
            val next = when (current) {
                is InvocationTargetException -> current.targetException
                else -> current.cause
            } ?: return current
            if (next === current) return current
            current = next
        }
    }

    /** Remove callbacks estáticos do Shizuku quando o serviço de mapeamento é encerrado. */
    override fun close() {
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
    }

    private fun logFailureOnce(message: String, error: Throwable) {
        if (failureLogged.compareAndSet(false, true)) Log.w(TAG, message, error)
    }
}
