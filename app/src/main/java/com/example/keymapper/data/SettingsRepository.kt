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
import org.json.JSONArray
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
            val current = readCurrentOrDefault(prefs)
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
        if (raw.length > MAX_BACKUP_CHARS) {
            prefs[recoveryBackupKey] = raw.take(MAX_BACKUP_CHARS)
            return AppSettings()
        }

        return try {
            val root = JSONObject(raw)
            var modified = false

            // JSONObject(raw) alone is not enough: a valid JSON object can still contain a
            // wrong type for a collection. Without sanitizing it, a later save could silently
            // replace all saved profiles with an empty map.
            val rawProfiles = root.opt("profiles")
            if (root.has("profiles") && rawProfiles != JSONObject.NULL && rawProfiles !is JSONObject) {
                prefs[recoveryBackupKey] = raw.take(MAX_BACKUP_CHARS)
                root.remove("profiles")
                modified = true
            } else if (rawProfiles is JSONObject) {
                val safeProfiles = JSONObject()
                val keys = rawProfiles.keys()
                var invalidEntries = false
                while (keys.hasNext()) {
                    val packageName = keys.next()
                    val profile = rawProfiles.opt(packageName)
                    if (packageName.isNotBlank() && packageName.length <= 255 &&
                        packageName.contains('.') && profile is JSONObject
                    ) {
                        safeProfiles.put(packageName, profile)
                    } else {
                        invalidEntries = true
                    }
                }
                if (invalidEntries) {
                    prefs[recoveryBackupKey] = raw.take(MAX_BACKUP_CHARS)
                    root.put("profiles", safeProfiles)
                    modified = true
                }
            }

            listOf("buttons", "games").forEach { field ->
                val value = root.opt(field)
                if (root.has(field) && value != JSONObject.NULL && value !is JSONArray) {
                    prefs[recoveryBackupKey] = raw.take(MAX_BACKUP_CHARS)
                    root.remove(field)
                    modified = true
                }
            }

            val normalized = if (modified) root.toString() else raw
            AppSettings.fromJson(normalized)
        } catch (t: Throwable) {
            // Keep a bounded copy before falling back to defaults; never let malformed persisted
            // JSON abort service startup or a settings transaction.
            prefs[recoveryBackupKey] = raw.take(MAX_BACKUP_CHARS)
            AppSettings()
        }
    }

    private companion object { const val MAX_BACKUP_CHARS = 2_000_000 }
}
