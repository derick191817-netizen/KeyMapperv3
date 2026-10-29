package com.example.keymapper.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

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
