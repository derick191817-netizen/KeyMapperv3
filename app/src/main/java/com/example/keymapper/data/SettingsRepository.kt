import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

// Recupera automaticamente um arquivo DataStore corrompido em vez de impedir a inicialização.
private val Context.store by preferencesDataStore(
    name = "keymapper",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class SettingsRepository(context: Context) {
    private val ctx = context.applicationContext
    private val key = stringPreferencesKey("settings_json")
    // Keeps a bounded copy if a malformed/oversized document is about to be replaced.
    private val recoveryBackupKey = stringPreferencesKey("settings_json_recovery_backup")

    val flow: Flow<AppSettings> = ctx.store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p ->
        val raw = p[key]
        if (raw.isNullOrBlank()) AppSettings()
        else runCatching { AppSettings.fromJson(raw) }.getOrElse { AppSettings() }
    }

    suspend fun save(s: AppSettings) {
        ctx.store.edit { prefs ->
            preserveInvalidRawSettings(prefs)
            val raw = prefs[key]
            val current = if (raw != null && isValidRootDocument(raw)) AppSettings.fromJson(raw) else AppSettings()
            // A global settings write must not roll back profiles saved concurrently by the service/editor.
            prefs[key] = s.copy(
                profiles = current.profiles,
                selectedProfilePackage = current.selectedProfilePackage
            ).toJson()
        }
    }

    suspend fun selectProfile(packageName: String) {
        val pkg = validatePackageName(packageName)
        ctx.store.edit { prefs ->
            val current = readCurrentOrDefault(prefs)
            prefs[key] = current.copy(selectedProfilePackage = pkg).toJson()
        }
    }

    /** Saves only one profile and never overwrites global settings or other valid profiles. */
    suspend fun saveProfile(packageName: String, profile: AppProfile, selected: Boolean = true) {
        val pkg = validatePackageName(packageName)
        ctx.store.edit { prefs ->
            val current = readCurrentOrDefault(prefs)
            val profiles = current.profiles.toMutableMap().apply { put(pkg, profile) }
            prefs[key] = current.copy(
                profiles = profiles,
                selectedProfilePackage = if (selected) pkg else current.selectedProfilePackage
            ).toJson()
        }
    }

    private fun validatePackageName(packageName: String): String {
        val pkg = packageName.trim()
        require(pkg.isNotEmpty() && pkg.length <= 255 && pkg.contains('.')) { "Nome de pacote inválido" }
        return pkg
    }

    private fun readCurrentOrDefault(prefs: MutablePreferences): AppSettings {
        val raw = prefs[key] ?: return AppSettings()
        if (!isValidRootDocument(raw)) {
            prefs[recoveryBackupKey] = raw.take(MAX_BACKUP_CHARS)
            return AppSettings()
        }
        return AppSettings.fromJson(raw)
    }

    private fun preserveInvalidRawSettings(prefs: MutablePreferences) {
        val raw = prefs[key] ?: return
        if (!isValidRootDocument(raw)) prefs[recoveryBackupKey] = raw.take(MAX_BACKUP_CHARS)
    }

    private fun isValidRootDocument(raw: String): Boolean =
        raw.length <= MAX_BACKUP_CHARS && runCatching { JSONObject(raw) }.isSuccess

    private companion object { const val MAX_BACKUP_CHARS = 2_000_000 }
}
