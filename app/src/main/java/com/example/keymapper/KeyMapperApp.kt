package com.example.keymapper

import android.app.Application
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.diagnostics.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KeyMapperApp : Application() {
    /** Manual application-scoped dependency container; avoids duplicate repositories across UI/service. */
    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(applicationContext)
    }

    /**
     * Operações de persistência solicitadas pelo overlay sobrevivem ao encerramento do serviço.
     * Main.immediate começa cada gravação pela ordem em que o usuário as solicita; DataStore
     * executa as transações em seu dispatcher interno. O processo continua sendo o limite final.
     */
    val persistenceScope: CoroutineScope by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
