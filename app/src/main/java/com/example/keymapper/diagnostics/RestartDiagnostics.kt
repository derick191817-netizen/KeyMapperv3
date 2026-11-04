package com.example.keymapper.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small, bounded lifecycle journal used to distinguish app-process restarts from device reboots. */
object RestartDiagnostics {
    private const val PREFS = "keymapper_diagnostics"
    private const val KEY_LAST_LAUNCH_WALL = "restart_last_launch_wall"
    private const val KEY_LAST_LAUNCH_ELAPSED = "restart_last_launch_elapsed"
    private const val KEY_LAST_LAUNCH_BOOT = "restart_last_launch_boot"
    private const val KEY_PREVIOUS_LAUNCH_WALL = "restart_previous_launch_wall"
    private const val KEY_PREVIOUS_LAUNCH_ELAPSED = "restart_previous_launch_elapsed"
    private const val KEY_PREVIOUS_LAUNCH_BOOT = "restart_previous_launch_boot"
    private const val KEY_EVENTS = "restart_lifecycle_events"
    private const val KEY_LAST_BOOT_AUTOSTART = "restart_last_boot_autostart"
    private const val MAX_EVENTS_CHARS = 12_000

    @Volatile
    private var cachedBootCount: Int? = null

    fun currentBootCount(context: Context): Int {
        cachedBootCount?.takeIf { it >= 0 }?.let { return it }
        val count = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Settings.Global.getInt(context.applicationContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
            } else -1
        } catch (_: Throwable) {
            -1
        }
        cachedBootCount = count
        return count
    }

    /** Records the current app launch while preserving the previous launch marker. */
    @Synchronized
    fun recordAppLaunch(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val boot = currentBootCount(app)
        val previousWall = prefs.getLong(KEY_LAST_LAUNCH_WALL, 0L)
        val previousBoot = prefs.getInt(KEY_LAST_LAUNCH_BOOT, -1)
        val previousElapsed = prefs.getLong(KEY_LAST_LAUNCH_ELAPSED, -1L)
        prefs.edit()
            .putLong(KEY_PREVIOUS_LAUNCH_WALL, previousWall)
            .putLong(KEY_PREVIOUS_LAUNCH_ELAPSED, previousElapsed)
            .putInt(KEY_PREVIOUS_LAUNCH_BOOT, previousBoot)
            .putLong(KEY_LAST_LAUNCH_WALL, nowWall)
            .putLong(KEY_LAST_LAUNCH_ELAPSED, nowElapsed)
            .putInt(KEY_LAST_LAUNCH_BOOT, boot)
            .apply()
        recordEvent(app, "Application.onCreate bootCount=$boot elapsed=${nowElapsed}ms")
    }

    /** Claim boot auto-start at most once for the current Android boot. Must be called off main. */
    @Synchronized
    fun claimBootAutoStart(context: Context): Boolean {
        val boot = currentBootCount(context)
        if (boot < 0) {
            recordEvent(context, "BootReceiver: boot_count indisponível; deduplicação não aplicada")
            return true
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST_BOOT_AUTOSTART, Int.MIN_VALUE) == boot) {
            recordEvent(context, "BootReceiver: partida automática duplicada ignorada para bootCount=$boot")
            return false
        }
        val saved = prefs.edit().putInt(KEY_LAST_BOOT_AUTOSTART, boot).commit()
        if (!saved) {
            recordEvent(context, "BootReceiver: não foi possível persistir trava para bootCount=$boot")
            return false
        }
        recordEvent(context, "BootReceiver: tentativa única de partida automática para bootCount=$boot")
        return true
    }

    @Synchronized
    fun recordEvent(context: Context, event: String) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val clean = event.replace('\n', ' ').replace('\r', ' ').take(500)
            val entry = "$now pid=${Process.myPid()} $clean"
            val old = prefs.getString(KEY_EVENTS, "").orEmpty()
            val combined = (if (old.isBlank()) entry else "$old\n$entry").takeLast(MAX_EVENTS_CHARS)
            prefs.edit().putString(KEY_EVENTS, combined).apply()
        } catch (_: Throwable) {
            // Diagnostics must never prevent the service or the UI from starting.
        }
    }

    fun report(context: Context): String {
        return try {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val currentBoot = currentBootCount(app)
            val previousWall = prefs.getLong(KEY_PREVIOUS_LAUNCH_WALL, 0L)
            val previousBoot = prefs.getInt(KEY_PREVIOUS_LAUNCH_BOOT, -1)
            val previousElapsed = prefs.getLong(KEY_PREVIOUS_LAUNCH_ELAPSED, -1L)
            val lastWall = prefs.getLong(KEY_LAST_LAUNCH_WALL, 0L)
            val lastElapsed = prefs.getLong(KEY_LAST_LAUNCH_ELAPSED, -1L)
            val lastBoot = prefs.getInt(KEY_LAST_LAUNCH_BOOT, -1)
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            buildString {
                appendLine("PID atual: ${Process.myPid()}")
                appendLine("Boot count atual: $currentBoot")
                appendLine("Última abertura do app: ${if (lastWall > 0) formatter.format(Date(lastWall)) else "sem registro"}")
                appendLine("Boot count na última abertura: $lastBoot")
                appendLine("Uptime do telefone na última abertura: ${if (lastElapsed >= 0) "${lastElapsed / 1000}s" else "sem registro"}")
                appendLine("Abertura anterior: ${if (previousWall > 0) formatter.format(Date(previousWall)) else "sem registro"}")
                appendLine("Boot count na abertura anterior: $previousBoot")
                appendLine("Uptime na abertura anterior: ${if (previousElapsed >= 0) "${previousElapsed / 1000}s" else "sem registro"}")
                when {
                    previousBoot >= 0 && lastBoot >= 0 && previousBoot != lastBoot -> appendLine("Sinal: o contador de boot mudou entre as duas últimas aberturas do app; o telefone reiniciou nesse intervalo.")
                    previousBoot >= 0 && lastBoot >= 0 && previousBoot == lastBoot -> appendLine("Sinal: as duas últimas aberturas ocorreram no mesmo boot do telefone; não há evidência de reinicialização do aparelho nesse intervalo.")
                    else -> appendLine("Sinal: contador de boot indisponível; não é possível separar reinício do telefone por esse indicador.")
                }
                if (lastBoot >= 0 && currentBoot >= 0 && lastBoot != currentBoot) {
                    appendLine("ALERTA: BOOT_COUNT atual difere do salvo na abertura mais recente; houve mudança de boot após o último registro.")
                }
                appendLine()
                appendLine("EVENTOS RECENTES DO CICLO DE VIDA")
                append(prefs.getString(KEY_EVENTS, "Sem eventos registrados.").orEmpty())
            }
        } catch (t: Throwable) {
            "Não foi possível gerar o diagnóstico de reinicialização: ${t.javaClass.simpleName}: ${t.message}"
        }
    }
}
