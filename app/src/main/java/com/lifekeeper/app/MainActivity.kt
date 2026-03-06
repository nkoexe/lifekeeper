package com.lifekeeper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lifekeeper.app.data.preferences.ThemePreference
import com.lifekeeper.app.data.preferences.UserPreferences
import com.lifekeeper.app.ui.navigation.NavGraph
import com.lifekeeper.app.ui.theme.LifekeeperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LifekeeperApp
        setContent {
            val prefs by app.userPreferencesRepository.preferences
                .collectAsState(initial = UserPreferences())
            val darkTheme = when (prefs.theme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT  -> false
                ThemePreference.DARK   -> true
            }
            LifekeeperTheme(darkTheme = darkTheme) {
                NavGraph()
            }
        }
    }
}
