package com.example.keymapper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.keymapper.diagnostics.RestartDiagnostics
import com.example.keymapper.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Reads the opt-in auto-start setting off the broadcast main thread. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val auto = SettingsRepository(appContext).flow.first().autoStart
                if (auto) {
                    if (RestartDiagnostics.claimBootAutoStart(appContext)) {
                        MappingService.requestStart(appContext)
                    }
                } else {
                    RestartDiagnostics.recordEvent(appContext, "BootReceiver: autoStart desativado")
                }
            } catch (e: Exception) {
                // Do not retry in a loop: Android may prohibit foreground starts in this state.
                RestartDiagnostics.recordEvent(appContext, "BootReceiver falhou: ${e.javaClass.simpleName}: ${e.message}")
                Log.e("KeyMapper", "Falha ao processar a inicialização automática", e)
            } finally {
                pending.finish()
            }
        }
    }
}
