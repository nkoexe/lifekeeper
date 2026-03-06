package com.lifekeeper.app.data.preferences

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    fun displayName(): String = when (this) {
        SYSTEM -> "System"
        LIGHT  -> "Light"
        DARK   -> "Dark"
    }
}
