package com.example.keymapper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.keymapper.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val auto = runBlocking { SettingsRepository(context).flow.first().autoStart }
        if (auto) {
            ContextCompat.startForegroundService(context, Intent(context, MappingService::class.java))
        }
    }
}
