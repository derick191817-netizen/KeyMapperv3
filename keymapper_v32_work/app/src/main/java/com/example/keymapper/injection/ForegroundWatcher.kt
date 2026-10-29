package com.example.keymapper.injection

import android.app.ActivityManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Descobre qual app está em primeiro plano, via Shizuku (IActivityTaskManager.getTasks).
 * Não precisa de permissão no Manifest. Se não for possível detectar (sem Shizuku,
 * ou Android que mudou a API), informa null e o serviço mantém o overlay ligado.
 */
class ForegroundWatcher(private val onChange: (String?) -> Unit) {
    private val thread = HandlerThread("fg-watch").also { it.start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false
    private var last: String? = "?"   // sentinela: o primeiro resultado sempre é informado
    private var atm: Any? = null

    @Volatile
    var supported = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val pkg = topPackage()
            if (pkg != last) {
                last = pkg
                main.post { onChange(pkg) }
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (running) return
        running = true
        last = "?"
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    fun shutdown() {
        stop()
        thread.quitSafely()
    }

    private fun service(): Any? {
        atm?.let { return it }
        return try {
            val b = SystemServiceHelper.getSystemService("activity_task") ?: return null
            Class.forName("android.app.IActivityTaskManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(b))
                .also { atm = it }
        } catch (e: Throwable) {
            null
        }
    }

    private fun call(o: Any, types: Array<Class<*>>, args: Array<Any>): List<*>? = try {
        o.javaClass.getMethod("getTasks", *types).invoke(o, *args) as? List<*>
    } catch (e: NoSuchMethodException) {
        null
    }

    private fun topPackage(): String? {
        val s = service() ?: return null
        return try {
            val i = Int::class.javaPrimitiveType!!
            val z = Boolean::class.javaPrimitiveType!!
            val tasks = call(s, arrayOf<Class<*>>(i, z, z, i), arrayOf<Any>(5, false, false, -1))
                ?: call(s, arrayOf<Class<*>>(i, z, z), arrayOf<Any>(5, false, false))
                ?: call(s, arrayOf<Class<*>>(i), arrayOf<Any>(5))
            supported = tasks != null
            val info = tasks?.firstOrNull() as? ActivityManager.RunningTaskInfo
            info?.topActivity?.packageName
        } catch (e: Throwable) {
            atm = null // binder pode ter morrido; tenta de novo no próximo ciclo
            supported = false
            null
        }
    }
}
