package org.akanework.gramophone.desktop.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.akanework.gramophone.desktop.settings.DesktopSettingsManager
import org.akanework.gramophone.desktop.settings.ThemeMode
import org.json.JSONObject
import java.io.File

object DesktopThemeManager {

    private fun parseHex(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return try {
            val clean = hex.trim().removePrefix("#")
            val colorInt = clean.toLong(16)
            if (clean.length == 6) {
                Color(colorInt or 0xFF000000)
            } else {
                Color(colorInt)
            }
        } catch (e: Exception) {
            fallback
        }
    }

    private fun loadInirColorScheme(): ColorScheme? {
        try {
            val noctaliaFile = File(System.getProperty("user.home"), ".config/noctalia/colors.json")
            if (noctaliaFile.exists()) {
                val json = JSONObject(noctaliaFile.readText())
                val primary = parseHex(json.optString("mPrimary"), Color(0xFFD0BCFF))
                val onPrimary = parseHex(json.optString("mOnPrimary"), Color(0xFF381E72))
                val primaryContainer = parseHex(json.optString("mPrimary"), Color(0xFF4F378B)).copy(alpha = 0.25f)
                val surface = parseHex(json.optString("mSurface"), Color(0xFF141218))
                val onSurface = parseHex(json.optString("mOnSurface"), Color(0xFFE6E0E9))
                val surfaceVariant = parseHex(json.optString("mSurfaceVariant"), Color(0xFF2B2930))
                val onSurfaceVariant = parseHex(json.optString("mOnSurfaceVariant"), Color(0xFFCAC4D0))
                val outline = parseHex(json.optString("mOutline"), Color(0xFF938F99))

                return darkColorScheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = primaryContainer,
                    onPrimaryContainer = primary,
                    surface = surface,
                    onSurface = onSurface,
                    surfaceVariant = surfaceVariant,
                    onSurfaceVariant = onSurfaceVariant,
                    surfaceContainer = surfaceVariant,
                    surfaceContainerLow = surface,
                    surfaceContainerHigh = surfaceVariant,
                    surfaceContainerHighest = surfaceVariant,
                    outline = outline,
                    outlineVariant = outline.copy(alpha = 0.5f)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getColorScheme(mode: ThemeMode): ColorScheme {
        return when (mode) {
            ThemeMode.SYSTEM -> {
                // Пытаемся подхватить цвета iNiR / Material You
                loadInirColorScheme() ?: darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    onPrimary = Color(0xFF381E72),
                    primaryContainer = Color(0xFF4F378B),
                    onPrimaryContainer = Color(0xFFEADDFF),
                    surface = Color(0xFF141218),
                    onSurface = Color(0xFFE6E0E9),
                    surfaceContainerLow = Color(0xFF1D1B20),
                    surfaceContainer = Color(0xFF211F26),
                    surfaceContainerHigh = Color(0xFF2B2930)
                )
            }
            ThemeMode.DARK -> {
                darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    onPrimary = Color(0xFF381E72),
                    primaryContainer = Color(0xFF4F378B),
                    onPrimaryContainer = Color(0xFFEADDFF),
                    surface = Color(0xFF141218),
                    onSurface = Color(0xFFE6E0E9),
                    surfaceContainerLow = Color(0xFF1D1B20),
                    surfaceContainer = Color(0xFF211F26),
                    surfaceContainerHigh = Color(0xFF2B2930)
                )
            }
            ThemeMode.LIGHT -> {
                lightColorScheme(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFEADDFF),
                    onPrimaryContainer = Color(0xFF21005D),
                    surface = Color(0xFFFEF7FF),
                    onSurface = Color(0xFF1D1B20),
                    surfaceContainerLow = Color(0xFFF7F2FA),
                    surfaceContainer = Color(0xFFF3EDF7),
                    surfaceContainerHigh = Color(0xFFECE6F0)
                )
            }
        }
    }
}
