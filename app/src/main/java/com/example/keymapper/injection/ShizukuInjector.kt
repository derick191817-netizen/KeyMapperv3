package com.example.keymapper.injection

import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import android.view.InputEvent
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.atomic.AtomicBoolean

class ShizukuInjector : Injector {

    companion object { private const val TAG = "KeyMapperShizuku" }
    private val failureLogged = AtomicBoolean(false)

    override fun isReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        if (failureLogged.compareAndSet(false, true)) Log.w(TAG, "Não foi possível consultar o estado do Shizuku", t)
        false
    }

    private val inputManager: Any by lazy {
        // Prepara o acesso restrito somente quando a injeção é realmente solicitada.
        // Alguns firmwares não permitem esse bypass; falha será contida por inject().
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/view/InputEvent;",
                    "Landroid/hardware/input/IInputManager;",
                    "Landroid/app/IActivityTaskManager;"
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Bypass de APIs ocultas indisponível neste dispositivo", t)
            }
        }
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
        Class.forName("android.hardware.input.IInputManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    private val injectMethod by lazy {
        inputManager.javaClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
    }

    /** mode 0 = INJECT_INPUT_EVENT_MODE_ASYNC */
    override fun inject(event: InputEvent): Boolean = try {
        val result = injectMethod.invoke(inputManager, event, 0) as Boolean
        if (result) failureLogged.set(false)
        result
    } catch (t: Throwable) {
        // Registra apenas a primeira falha consecutiva; evita inundar o log a cada movimento do mouse.
        if (failureLogged.compareAndSet(false, true)) Log.w(TAG, "Falha ao injetar entrada via Shizuku", t)
        false
    }
}
