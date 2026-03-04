package com.lifekeeper.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// minSdk = 31 (Android S) guarantees dynamic color is always available,
// so no static fallback palette is needed. Color.kt documents the M3 baseline
// token values for design reference only.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LifekeeperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme)
        dynamicDarkColorScheme(context)
    else
        dynamicLightColorScheme(context)

    MaterialTheme(
        colorScheme  = colorScheme,
        typography   = AppTypography,
        motionScheme = MotionScheme.expressive(),
        content      = content,
    )
}
