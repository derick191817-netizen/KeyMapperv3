package com.example.keymapper

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class KeyMapperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Necessário para acessar IInputManager e InputEvent.setDisplayId via reflexão
        if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("L")
    }
}
