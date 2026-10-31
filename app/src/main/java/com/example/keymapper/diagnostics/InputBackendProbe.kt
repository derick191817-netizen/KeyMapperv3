package com.example.keymapper.diagnostics

import android.content.Context
import android.os.Build
import com.example.keymapper.input.InputDeviceManager
import java.io.File

/**
 * Sondagem estritamente não invasiva do ambiente. Consulta apenas metadados de caminhos;
 * não abre /dev/input/event*, não executa ioctl, não solicita EVIOCGRAB e não cria uinput.
 */
object InputBackendProbe {
    private data class PathObservation(
        val visible: Boolean,
        val directory: Boolean,
        val readableByJava: Boolean,
        val writableByJava: Boolean,
        val matchingEntries: Int? = null
    )

    fun inspect(context: Context, shizukuAuthorized: Boolean): String {
        val inputDirectory = observeDirectory(File("/dev/input"), Regex("event\\d+"))
        val uinputNode = observePath(File("/dev/uinput"))
        val sysInputDirectory = observeDirectory(File("/sys/class/input"), Regex("event\\d+"))
        val nativeLibrary = File(context.applicationInfo.nativeLibraryDir, "libkeymapper_input.so")
        val devices = runCatching { InputDeviceManager.snapshot() }.getOrNull()
        val capture = BackendCapabilityPolicy.assess(
            nativeModulePresent = nativeLibrary.isFile,
            backendInitialized = false,
            endToEndVerified = false
        )
        // Não existe módulo nativo de saída separado nesta revisão.
        val virtualOutput = BackendCapabilityPolicy.assess(
            nativeModulePresent = false,
            backendInitialized = false,
            endToEndVerified = false
        )

        return buildString {
            appendLine("SONDAGEM NÃO INVASIVA DO BACKEND")
            appendLine("Android API=${Build.VERSION.SDK_INT}; ABI=${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Dispositivos reconhecidos pela API Android: ${devices?.devices?.size ?: "indisponível"}")
            appendLine("Shizuku autorizado para o app: $shizukuAuthorized")
            appendLine("Shizuku grant isolado concede /dev/input ou /dev/uinput: ${BackendCapabilityPolicy.shizukuGrantAloneConfirmsKernelNodeAccess()}")
            appendLine("/dev/input: ${describe(inputDirectory)}")
            appendLine("/sys/class/input: ${describe(sysInputDirectory)}")
            appendLine("/dev/uinput: ${describe(uinputNode)}")
            appendLine("Biblioteca libkeymapper_input.so presente: ${nativeLibrary.isFile}")
            appendLine("Captura global evdev: ${capture.level} — ${capture.description}")
            appendLine("Saída virtual uinput: ${virtualOutput.level} — ${virtualOutput.description}")
            appendLine("Limite: exists/canRead/canWrite/listFiles refletem apenas metadados vistos pelo processo; não provam abertura, ioctl, permissões SELinux, grab, escrita no dispositivo nem entrega ao app de destino.")
            appendLine("Nenhum nó de evento foi aberto ou capturado durante esta sondagem.")
        }
    }

    private fun observeDirectory(directory: File, match: Regex): PathObservation {
        val visible = runCatching { directory.exists() }.getOrDefault(false)
        val names = if (visible && runCatching { directory.isDirectory }.getOrDefault(false)) {
            runCatching { directory.list()?.filter { match.matches(it) } }.getOrNull()
        } else null
        return PathObservation(
            visible = visible,
            directory = visible && runCatching { directory.isDirectory }.getOrDefault(false),
            readableByJava = visible && runCatching { directory.canRead() }.getOrDefault(false),
            writableByJava = visible && runCatching { directory.canWrite() }.getOrDefault(false),
            matchingEntries = names?.size
        )
    }

    private fun observePath(path: File): PathObservation {
        val visible = runCatching { path.exists() }.getOrDefault(false)
        return PathObservation(
            visible = visible,
            directory = visible && runCatching { path.isDirectory }.getOrDefault(false),
            readableByJava = visible && runCatching { path.canRead() }.getOrDefault(false),
            writableByJava = visible && runCatching { path.canWrite() }.getOrDefault(false)
        )
    }

    private fun describe(observation: PathObservation): String = buildString {
        append("visível=${observation.visible}, diretório=${observation.directory}")
        append(", canRead=${observation.readableByJava}, canWrite=${observation.writableByJava}")
        observation.matchingEntries?.let { append(", event*=$it") }
    }
}
