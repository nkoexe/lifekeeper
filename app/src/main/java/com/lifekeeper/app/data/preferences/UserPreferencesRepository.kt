package com.lifekeeper.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_THEME                    = stringPreferencesKey("theme")
        private val KEY_MIN_SESSION_DURATION_SEC = intPreferencesKey("min_session_duration_seconds")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e ->
            // Emit defaults when the file doesn't exist yet or is unreadable.
            if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw e
        }
        .map { prefs ->
            val rawTheme = prefs[KEY_THEME]
            UserPreferences(
                theme = if (rawTheme != null)
                    runCatching { ThemePreference.valueOf(rawTheme) }
                        .getOrDefault(ThemePreference.SYSTEM)
                else
                    ThemePreference.SYSTEM,
                minSessionDurationSeconds = prefs[KEY_MIN_SESSION_DURATION_SEC] ?: 60,
            )
        }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setMinSessionDuration(seconds: Int) {
        context.dataStore.edit { it[KEY_MIN_SESSION_DURATION_SEC] = seconds }
    }

    /** Clears all stored preferences, reverting to defaults. */
    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
