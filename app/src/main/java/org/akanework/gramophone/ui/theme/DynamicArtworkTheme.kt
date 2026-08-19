package org.akanework.gramophone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Гармоничная генерация мягких тональных цветов из обложки трека в стиле Material 3 / PixelPlayer.
 * Устраняет резкий неон и чрезмерный контраст, балансируя насыщенность и яркость.
 */
object DynamicArtworkTheme {

    data class ArtworkColors(
        val seedColor: Color,
        val miniPlayerContainer: Color,
        val fullPlayerGradientTop: Color,
        val fullPlayerSecondaryGlow: Color,
        val accentColor: Color
    )

    fun calculateFromPalette(
        palette: Palette?,
        isDarkTheme: Boolean,
        defaultSurface: Color,
        defaultSurfaceContainer: Color
    ): ArtworkColors {
        val fallbackSwatch = palette?.dominantSwatch
        // Выбираем более спокойные и гармоничные оттенки (Muted -> Vibrant -> Dominant)
        val selectedSwatch = palette?.mutedSwatch
            ?: palette?.darkMutedSwatch
            ?: palette?.lightMutedSwatch
            ?: palette?.vibrantSwatch
            ?: palette?.darkVibrantSwatch
            ?: fallbackSwatch

        if (selectedSwatch == null) {
            return ArtworkColors(
                seedColor = defaultSurfaceContainer,
                miniPlayerContainer = defaultSurfaceContainer,
                fullPlayerGradientTop = defaultSurfaceContainer,
                fullPlayerSecondaryGlow = defaultSurfaceContainer,
                accentColor = defaultSurfaceContainer
            )
        }

        val rawArgb = selectedSwatch.rgb
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rawArgb, hsl)
        val hue = hsl[0]
        val rawSat = hsl[1]
        val rawLight = hsl[2]

        val surfaceArgb = if (isDarkTheme) 0xFF141414.toInt() else 0xFFF7F7F7.toInt()
        val surfaceContainerArgb = if (isDarkTheme) 0xFF222222.toInt() else 0xFFE6E6E6.toInt()

        if (isDarkTheme) {
            // Ограничиваем насыщенность, чтобы фон не кричал, и держим глубокую благородную темную тональность
            val controlledSat = rawSat.coerceIn(0.20f, 0.45f)
            val miniContainerLight = 0.15f + (rawLight * 0.05f).coerceIn(0f, 0.04f)
            val gradientTopLight = 0.20f + (rawLight * 0.06f).coerceIn(0f, 0.05f)

            val miniContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat, miniContainerLight))
            val gradientTopArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 1.05f, gradientTopLight))

            // Вторичное свечение со смещением спектра (+24°)
            val secondaryHue = (hue + 24f) % 360f
            val secondaryGlowArgb = ColorUtils.HSLToColor(floatArrayOf(secondaryHue, controlledSat * 0.75f, gradientTopLight * 0.9f))

            // Мягкое наложение на surfaceContainer для идеальной читаемости
            val finalMiniArgb = ColorUtils.blendARGB(surfaceContainerArgb, miniContainerArgb, 0.65f)

            return ArtworkColors(
                seedColor = Color(rawArgb),
                miniPlayerContainer = Color(finalMiniArgb),
                fullPlayerGradientTop = Color(gradientTopArgb),
                fullPlayerSecondaryGlow = Color(secondaryGlowArgb),
                accentColor = Color(rawArgb)
            )
        } else {
            // Для светлой темы: нежные пастельные тона
            val controlledSat = rawSat.coerceIn(0.16f, 0.36f)
            val miniContainerLight = 0.92f + (rawLight * 0.04f).coerceIn(0f, 0.04f)
            val gradientTopLight = 0.89f + (rawLight * 0.05f).coerceIn(0f, 0.05f)

            val miniContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat, miniContainerLight))
            val gradientTopArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat, gradientTopLight))
            val secondaryHue = (hue + 24f) % 360f
            val secondaryGlowArgb = ColorUtils.HSLToColor(floatArrayOf(secondaryHue, controlledSat * 0.75f, gradientTopLight))

            val finalMiniArgb = ColorUtils.blendARGB(surfaceContainerArgb, miniContainerArgb, 0.55f)

            return ArtworkColors(
                seedColor = Color(rawArgb),
                miniPlayerContainer = Color(finalMiniArgb),
                fullPlayerGradientTop = Color(gradientTopArgb),
                fullPlayerSecondaryGlow = Color(secondaryGlowArgb),
                accentColor = Color(rawArgb)
            )
        }
    }
}
