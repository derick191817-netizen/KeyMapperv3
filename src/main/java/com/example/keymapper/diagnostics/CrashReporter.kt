package com.example.keymapper.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stores only the most recent uncaught crash for an explicit user-initiated bug report. */
object CrashReporter {
    private const val TAG = "KeyMapperCrashReport"
    private const val PREFS = "keymapper_diagnostics"
    private const val LAST_CRASH = "last_crash"
    private const val MAX_CHARS = 16_000

    fun install(context: Context) {
        val app = context.applicationContext
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is RecordingHandler) return
        Thread.setDefaultUncaughtExceptionHandler(RecordingHandler(app, current))
    }

    fun lastCrash(context: Context): String = try {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_CRASH, "Nenhum crash não tratado registrado desde a última instalação do relatório.")
            .orEmpty()
    } catch (t: Throwable) {
        "Não foi possível ler o último crash: ${t.javaClass.simpleName}"
    }

    /** Android 11+ keeps process-exit records, including crashes, ANRs and low-memory kills. */
    fun processExitHistory(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Histórico do sistema indisponível antes do Android 11."
        }
        return try {
            val manager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 6)
            if (exits.isNullOrEmpty()) return "O Android não forneceu registros recentes de encerramento."
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            exits.joinToString("\n\n") { exit ->
                val reason = when (exit.reason) {
                    ApplicationExitInfo.REASON_ANR -> "ANR (aplicativo não respondeu)"
                    ApplicationExitInfo.REASON_CRASH -> "Crash Java/Kotlin"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "Crash nativo"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "Encerrado por falta de memória"
                    ApplicationExitInfo.REASON_EXIT_SELF -> "Encerramento solicitado pelo próprio app"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "Encerrado pelo usuário/sistema"
                    ApplicationExitInfo.REASON_USER_STOPPED -> "App parado pelo usuário"
                    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Falha de inicialização"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Uso excessivo de recursos"
                    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "Dependência do processo encerrou"
                    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "Permissões alteradas"
                    ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "Pacote atualizado"
                    ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "Estado do pacote alterado"
                    ApplicationExitInfo.REASON_SIGNALED -> "Encerrado por sinal do sistema"
                    else -> "Outro/desconhecido (${exit.reason})"
                }
                buildString {
                    appendLine("Horário: ${formatter.format(Date(exit.timestamp))}")
                    appendLine("Motivo: $reason")
                    appendLine("Processo: ${exit.processName} | PID: ${exit.pid}")
                    appendLine("Importância antes do encerramento: ${exit.importance}")
                    appendLine("Status: ${exit.status}")
                    appendLine("Memória PSS/RSS: ${exit.pss} / ${exit.rss} KB (última amostragem disponível)")
                    val description = exit.description?.takeIf { it.isNotBlank() }
                    if (description != null) appendLine("Descrição: ${description.take(1200)}")
                    val trace = readTrace(exit)
                    if (trace.isNotBlank()) append("Rastro do sistema: $trace")
                }.trim()
            }
        } catch (t: Throwable) {
            "Não foi possível consultar os encerramentos do Android: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    /** Reads a bounded system trace. ANR traces are generally text; native tombstones may be protobuf. */
    private fun readTrace(exit: ApplicationExitInfo): String {
        return try {
            val stream = exit.traceInputStream ?: return ""
            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                var total = 0
                while (total < 12_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 12_000 - total))
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
                output.toByteArray()
            }
            if (bytes.isEmpty()) return "stream de trace vazio"
            val text = String(bytes, Charsets.UTF_8)
            val printable = text.count { it == '\n' || it == '\r' || it == '\t' || it == ' ' || !it.isISOControl() }
            if (printable.toDouble() / text.length.coerceAtLeast(1) > 0.88) {
                "\n${text.take(9_000)}"
            } else {
                "conteúdo binário (possível tombstone protobuf), ${bytes.size} bytes; não convertido para texto"
            }
        } catch (t: Throwable) {
            "indisponível (${t.javaClass.simpleName}: ${t.message})"
        }
    }

    private class RecordingHandler(
        private val context: Context,
        private val delegate: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))
                val crash = buildString {
                    appendLine("Thread: ${thread.name}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Time: ${System.currentTimeMillis()}")
                    append(writer.toString().takeLast(MAX_CHARS))
                }.takeLast(MAX_CHARS)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(LAST_CRASH, crash).commit()
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao salvar o crash recente", t)
            } finally {
                delegate?.uncaughtException(thread, throwable)
            }
        }
    }
}
