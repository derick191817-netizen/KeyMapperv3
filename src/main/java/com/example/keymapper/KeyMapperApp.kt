package com.example.keymapper

import android.app.Application
import android.os.Build
import android.util.Log
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
        // Libera somente as classes ocultas efetivamente usadas pelo injetor/monitor.
        // O prefixo global "L" amplia desnecessariamente o acesso a APIs internas do sistema.
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/view/InputEvent;",
                    "Landroid/hardware/input/IInputManager;",
                    "Landroid/app/IActivityTaskManager;"
                )
            } catch (e: Exception) {
                Log.e("KeyMapper", "Não foi possível habilitar o acesso às APIs internas necessárias", e)
            }
        }
    }
}
