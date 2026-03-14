package com.mdfy.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mdfy.app.ui.screens.settings.AppTheme

// ── Цвета MDfy ───────────────────────────────────────────────────────────────
// Основной акцент — глубокий фиолетово-синий
private val MdfyPrimary       = Color(0xFF6C63FF)
private val MdfyPrimaryDark   = Color(0xFF9D97FF)
private val MdfyAccent        = Color(0xFFFF6584)   // Акцент для Boosty/like

private val LightScheme = lightColorScheme(
    primary          = MdfyPrimary,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFE8E6FF),
    secondary        = MdfyAccent,
    background       = Color(0xFFFAF9FF),
    surface          = Color.White,
    surfaceVariant   = Color(0xFFF0EFFF),
)

private val DarkScheme = darkColorScheme(
    primary          = MdfyPrimaryDark,
    onPrimary        = Color(0xFF1A1040),
    primaryContainer = Color(0xFF3D3680),
    secondary        = MdfyAccent,
    background       = Color(0xFF0F0D1A),
    surface          = Color(0xFF1A1730),
    surfaceVariant   = Color(0xFF252240),
)

/**
 * Тема MDfy.
 *
 * @param appTheme  Выбор пользователя из настроек (SYSTEM / LIGHT / DARK)
 * @param content   Содержимое в теме
 *
 * На Android 12+ используется Dynamic Color если appTheme == SYSTEM,
 * иначе применяется фирменная палитра MDfy.
 */
@Composable
fun MdfyTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT  -> false
        AppTheme.DARK   -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        // Dynamic Color (Material You) — только на Android 12+ и только в авторежиме
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appTheme == AppTheme.SYSTEM -> {
            if (isDark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        isDark -> DarkScheme
        else   -> LightScheme
    }

    // Синхронизируем цвет статус-бара с темой
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = MdfyTypography,
        content     = content
    )
}
