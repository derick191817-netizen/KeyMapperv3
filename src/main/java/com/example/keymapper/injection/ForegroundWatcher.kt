package com.example.keymapper.injection

import android.app.ActivityManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.lang.reflect.InvocationTargetException
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Detects the foreground package through Shizuku. Polling is limited to one query per second
 * while enabled; callbacks from an older run are discarded after stop/restart.
 */
class ForegroundWatcher(private val onChange: (String?) -> Unit) {
    companion object { private const val TAG = "KeyMapperForeground" }

    private val thread = HandlerThread("fg-watch").also { it.start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    @Volatile private var generation = 0L
    @Volatile private var last: String? = "?"
    @Volatile private var atm: Any? = null
    @Volatile private var atmBinder: IBinder? = null
    private var lastBinderWarningAt = 0L

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        atm = null
        atmBinder = null
        supported = false
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        atm = null
        atmBinder = null
        supported = false
    }

    @Volatile
    var supported = false
        private set

    init {
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
            .onFailure { Log.w(TAG, "Não foi possível observar a reconexão do Shizuku", it) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
            .onFailure { Log.w(TAG, "Não foi possível observar a queda do Shizuku", it) }
    }

    fun start() {
        if (running) return
        running = true
        val token = ++generation
        last = "?"
        handler.post { tick(token) }
    }

    fun stop() {
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
        main.removeCallbacksAndMessages(null)
    }

    fun shutdown() {
        stop()
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        thread.quitSafely()
    }

    private fun tick(token: Long) {
        if (!running || token != generation) return
        val pkg = topPackage()
        if (pkg != last) {
            last = pkg
            main.post {
                if (running && token == generation) {
                    try {
                        onChange(pkg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Falha ao entregar mudança de aplicativo em primeiro plano", e)
                    }
                }
            }
        }
        if (running && token == generation) {
            handler.postDelayed({ tick(token) }, 1000L)
        }
    }

    private fun service(): Any? {
        val cached = atm
        val cachedBinder = atmBinder
        if (cached != null && cachedBinder?.isBinderAlive == true) return cached
        atm = null
        atmBinder = null
        return try {
            val binder = SystemServiceHelper.getSystemService("activity_task") ?: return null
            val proxy = Class.forName("android.app.IActivityTaskManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
                ?: return null
            atmBinder = binder
            atm = proxy
            proxy
        } catch (e: Exception) {
            Log.w(TAG, "A API de tarefas em primeiro plano não está disponível", e)
            null
        }
    }

    private fun call(service: Any, types: Array<Class<*>>, args: Array<Any>): List<*>? = try {
        service.javaClass.getMethod("getTasks", *types).invoke(service, *args) as? List<*>
    } catch (_: NoSuchMethodException) {
        // Firmware pode expor outra assinatura; topPackage() tenta as alternativas suportadas.
        null
    } catch (e: Exception) {
        // Binder morto/permissão revogada: não mantenha um proxy obsoleto para sempre.
        // A próxima amostra (1 s) tentará obter novamente o serviço activity_task.
        atm = null
        atmBinder = null
        supported = false
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBinderWarningAt >= 5_000L) {
            lastBinderWarningAt = now
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            Log.w(TAG, "Falha ao consultar tarefas; proxy será renovado na próxima amostra", cause)
        }
        null
    }

    private fun topPackage(): String? {
        val service = service() ?: run { supported = false; return null }
        return try {
            val intType = Int::class.javaPrimitiveType!!
            val boolType = Boolean::class.javaPrimitiveType!!
            val tasks = call(service, arrayOf(intType, boolType, boolType, intType), arrayOf(5, false, false, -1))
                ?: call(service, arrayOf(intType, boolType, boolType), arrayOf(5, false, false))
                ?: call(service, arrayOf(intType), arrayOf(5))
            supported = tasks != null
            val info = tasks?.firstOrNull() as? ActivityManager.RunningTaskInfo
            info?.topActivity?.packageName
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao interpretar o aplicativo em primeiro plano", e)
            atm = null // binder/API pode ter mudado; tenta restabelecer no próximo ciclo
            atmBinder = null
            supported = false
            null
        }
    }
}
