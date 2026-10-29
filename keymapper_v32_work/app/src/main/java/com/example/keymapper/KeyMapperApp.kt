package com.example.keymapper

import android.app.Application
import android.os.Build
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.diagnostics.CrashReporter
import org.lsposed.hiddenapibypass.HiddenApiBypass

class KeyMapperApp : Application() {
    /** Manual application-scoped dependency container; avoids duplicate repositories across UI/service. */
    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // Necessário para acessar IInputManager e InputEvent.setDisplayId via reflexão
        if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("L")
    }
}
