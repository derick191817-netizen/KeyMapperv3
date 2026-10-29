package com.example.keymapper.presentation

import android.util.Log
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Presentation state holder for settings. Kept independent from AndroidX ViewModel/Hilt because
 * this project intentionally preserves its existing Gradle dependency set. The owner closes it
 * from Activity.onDestroy().
 */
class SettingsViewModel(private val repository: SettingsRepository) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableSettings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    init {
        scope.launch {
            repository.flow.collect { mutableSettings.value = it }
        }
    }

    fun save(value: AppSettings) {
        mutableSettings.value = value
        scope.launch(Dispatchers.IO) {
            try {
                repository.save(value)
            } catch (t: Throwable) {
                Log.e(TAG, "Não foi possível salvar as configurações", t)
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) = save(transform(mutableSettings.value))

    override fun close() { scope.cancel() }

    private companion object { const val TAG = "KeyMapperSettingsVM" }
}
