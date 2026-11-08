package org.akanework.gramophone.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import org.akanework.gramophone.logic.getBooleanStrict

object AppThemeHelper {
    // Pure Black for OLED background & surfaces (100% physically turned off pixels on OLED)
    val OledBackground = Color(0xFF000000)
    val OledSurface = Color(0xFF000000)
    val OledSurfaceDim = Color(0xFF000000)
    val OledSurfaceBright = Color(0xFF1E1E22)
    val OledSurfaceContainerLowest = Color(0xFF000000)
    val OledSurfaceContainerLow = Color(0xFF0A0A0C)
    val OledSurfaceContainer = Color(0xFF121214)
    val OledSurfaceContainerHigh = Color(0xFF18181C)
    val OledSurfaceContainerHighest = Color(0xFF202026)
    val OledSurfaceVariant = Color(0xFF16161A)
    val OledOutline = Color(0xFF2E2E34)
    val OledOutlineVariant = Color(0xFF222228)

    fun isPureDark(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return prefs.getBooleanStrict("pureDark", false)
    }

    fun isDarkTheme(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val mode = prefs.getString("theme_mode", "0")
        return when (mode) {
            "1" -> true
            "2" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun getColorScheme(
        context: Context,
        isDark: Boolean = isDarkTheme(context),
        isPureDark: Boolean = isPureDark(context)
    ): ColorScheme {
        val hasDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val baseScheme = when {
            hasDynamic && isDark -> dynamicDarkColorScheme(context)
            hasDynamic && !isDark -> dynamicLightColorScheme(context)
            isDark -> darkColorScheme()
            else -> lightColorScheme()
        }

        return if (isDark && isPureDark) {
            baseScheme.copy(
                background = OledBackground,
                surface = OledSurface,
                surfaceDim = OledSurfaceDim,
                surfaceBright = OledSurfaceBright,
                surfaceContainerLowest = OledSurfaceContainerLowest,
                surfaceContainerLow = OledSurfaceContainerLow,
                surfaceContainer = OledSurfaceContainer,
                surfaceContainerHigh = OledSurfaceContainerHigh,
                surfaceContainerHighest = OledSurfaceContainerHighest,
                surfaceVariant = OledSurfaceVariant,
                outline = OledOutline,
                outlineVariant = OledOutlineVariant
            )
        } else {
            baseScheme
        }
    }
}

/**
 * Universal Composable Theme wrapper that reacts to theme changes and pureDark OLED mode in real time.
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var isDark by remember { mutableStateOf(AppThemeHelper.isDarkTheme(context)) }
    var isPureDark by remember { mutableStateOf(AppThemeHelper.isPureDark(context)) }

    DisposableEffect(context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pureDark" || key == "theme_mode") {
                isDark = AppThemeHelper.isDarkTheme(context)
                isPureDark = AppThemeHelper.isPureDark(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val colorScheme = remember(isDark, isPureDark, context) {
        AppThemeHelper.getColorScheme(context, isDark, isPureDark)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
