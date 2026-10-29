package com.example.keymapper

import android.app.Application
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.diagnostics.CrashReporter

class KeyMapperApp : Application() {
    /** Manual application-scoped dependency container; avoids duplicate repositories across UI/service. */
    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        // O relatório de crash é auxiliar: uma falha nele não deve bloquear a Activity.
        runCatching { CrashReporter.install(this) }
        // Não inicializar bibliotecas de acesso a APIs ocultas durante a abertura do painel.
        // O recurso é preparado sob demanda pelo injetor, para que uma incompatibilidade
        // com o fabricante/Android não impeça a Activity principal de abrir.
    }
}
