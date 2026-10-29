package com.example.keymapper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.store by preferencesDataStore(name = "keymapper")

class SettingsRepository(context: Context) {
    private val ctx = context.applicationContext
    private val key = stringPreferencesKey("settings_json")

    val flow: Flow<AppSettings> = ctx.store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p -> p[key]?.let { AppSettings.fromJson(it) } ?: AppSettings() }

    suspend fun save(s: AppSettings) {
        ctx.store.edit { it[key] = s.toJson() }
    }

    suspend fun selectProfile(packageName: String) {
        val pkg = packageName.trim()
        require(pkg.isNotEmpty() && pkg.length <= 255 && pkg.contains('.')) { "Nome de pacote inválido" }
        ctx.store.edit { prefs ->
            val current = prefs[key]?.let { runCatching { AppSettings.fromJson(it) }.getOrNull() } ?: AppSettings()
            prefs[key] = current.copy(selectedProfilePackage = pkg).toJson()
        }
    }

    /** Salva somente o perfil escolhido sem substituir as configurações globais nem outros perfis. */
    suspend fun saveProfile(packageName: String, profile: AppProfile, selected: Boolean = true) {
        val pkg = packageName.trim()
        require(pkg.isNotEmpty() && pkg.length <= 255 && pkg.contains('.')) { "Nome de pacote inválido" }
        ctx.store.edit { prefs ->
            val current = prefs[key]?.let { runCatching { AppSettings.fromJson(it) }.getOrNull() } ?: AppSettings()
            val profiles = current.profiles.toMutableMap().apply { put(pkg, profile) }
            prefs[key] = current.copy(
                profiles = profiles,
                selectedProfilePackage = if (selected) pkg else current.selectedProfilePackage
            ).toJson()
        }
    }
}
