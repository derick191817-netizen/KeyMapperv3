package com.example.keymapper.injection

import android.content.pm.PackageManager
import android.os.IBinder
import android.view.InputEvent
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class ShizukuInjector : Injector {

    override fun isReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    private val inputManager: Any by lazy {
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
        injectMethod.invoke(inputManager, event, 0) as Boolean
    } catch (e: Throwable) {
        false
    }
}
