package com.example.keymapper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "keymapper")

class SettingsRepository(context: Context) {
    private val ctx = context.applicationContext
    private val key = stringPreferencesKey("settings_json")

    val flow: Flow<AppSettings> = ctx.store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p ->
        p[key]?.let { AppSettings.fromJson(it) } ?: AppSettings()
    }

    suspend fun save(s: AppSettings) {
        ctx.store.edit { it[key] = s.toJson() }
    }
}
