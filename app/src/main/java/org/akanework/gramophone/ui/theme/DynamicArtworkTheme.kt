package org.akanework.gramophone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Интеллектуальная система извлечения и генерации динамической палитры Material 3 / PixelPlay из обложки.
 * Выбирает ключевой доминирующий/выразительный цвет обложки (без захвата грязно-серых теней)
 * и создает гармоничную, глубокую и контрастную палитру для плеера и элементов управления.
 */
object DynamicArtworkTheme {

    data class ArtworkColors(
        val seedColor: Color,
        val miniPlayerContainer: Color,
        val fullPlayerGradientTop: Color,
        val fullPlayerSecondaryGlow: Color,
        val fullPlayerGradientBottom: Color,
        val accentColor: Color,
        val playerPrimary: Color,
        val playerOnPrimary: Color,
        val playerContainer: Color,
        val playerOnContainer: Color,
        val playerActiveContainer: Color,
        val playerOnActiveContainer: Color
    )

    /**
     * Интеллектуальный выбор наиболее выразительного и представительного оттенка обложки.
     * Взвешивает насыщенность, яркость и площадь (population), отсеивая паразитные серые/коричневые тени.
     */
    fun selectBestSwatch(palette: Palette?): Palette.Swatch? {
        if (palette == null) return null
        val swatches = palette.swatches
        if (swatches.isEmpty()) return palette.dominantSwatch ?: palette.vibrantSwatch

        val maxPopulation = swatches.maxOfOrNull { it.population }?.toFloat() ?: 1f

        return swatches.maxByOrNull { swatch ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(swatch.rgb, hsl)
            val sat = hsl[1]
            val light = hsl[2]
            val popRatio = (swatch.population / maxPopulation).coerceIn(0f, 1f)

            // Отсекаем околочерные (< 0.08) и околобелые (> 0.92) цвета
            val lightnessScore = when {
                light < 0.08f || light > 0.92f -> 0.05f
                light in 0.22f..0.78f -> 1.0f
                else -> 0.6f
            }

            // Штрафуем ненасыщенные грязно-серые/коричневые тени, поощряем чистые хроматические цвета
            val saturationScore = when {
                sat < 0.16f -> 0.10f
                sat in 0.35f..0.95f -> 1.3f
                else -> sat
            }

            // Доля пикселей на обложке
            val populationScore = 0.35f + 0.65f * popRatio

            // Приоритет специализированных слотов Palette
            val roleBoost = when (swatch) {
                palette.vibrantSwatch -> 1.4f
                palette.dominantSwatch -> 1.25f
                palette.lightVibrantSwatch -> 1.2f
                palette.darkVibrantSwatch -> 1.15f
                palette.mutedSwatch -> 0.5f
                palette.darkMutedSwatch -> 0.4f
                palette.lightMutedSwatch -> 0.4f
                else -> 1.0f
            }

            saturationScore * lightnessScore * populationScore * roleBoost
        } ?: palette.dominantSwatch ?: palette.vibrantSwatch
    }

    fun calculateFromPalette(
        palette: Palette?,
        isDarkTheme: Boolean,
        defaultSurface: Color,
        defaultSurfaceContainer: Color
    ): ArtworkColors {
        val selectedSwatch = selectBestSwatch(palette)

        if (selectedSwatch == null) {
            val fallbackPrimary = if (isDarkTheme) Color(0xFFD0BCFF) else Color(0xFF6750A4)
            val fallbackOnPrimary = if (isDarkTheme) Color(0xFF381E72) else Color(0xFFFFFFFF)
            return ArtworkColors(
                seedColor = defaultSurfaceContainer,
                miniPlayerContainer = defaultSurfaceContainer,
                fullPlayerGradientTop = defaultSurface,
                fullPlayerSecondaryGlow = defaultSurface,
                fullPlayerGradientBottom = defaultSurface,
                accentColor = fallbackPrimary,
                playerPrimary = fallbackPrimary,
                playerOnPrimary = fallbackOnPrimary,
                playerContainer = defaultSurfaceContainer,
                playerOnContainer = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                playerActiveContainer = fallbackPrimary.copy(alpha = 0.35f),
                playerOnActiveContainer = if (isDarkTheme) Color.White else Color.Black
            )
        }

        val rawArgb = selectedSwatch.rgb
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rawArgb, hsl)
        val hue = hsl[0]
        val rawSat = hsl[1]
        val rawLight = hsl[2]

        val surfaceArgb = if (isDarkTheme) 0xFF121316.toInt() else 0xFFF8F9FA.toInt()
        val surfaceContainerArgb = if (isDarkTheme) 0xFF1E2024.toInt() else 0xFFE9ECEF.toInt()

        if (isDarkTheme) {
            // === ТЕМНАЯ ТЕМА ===
            // 1. Глубокий благородный фон плеера с выразительным оттенком обложки
            val controlledSat = rawSat.coerceIn(0.40f, 0.80f)
            val gradientTopLight = 0.16f + (rawLight * 0.08f).coerceIn(0f, 0.05f)
            val gradientTopArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.95f, gradientTopLight))

            // Вторичное свечение (+20° по цветовому кругу)
            val secondaryHue = (hue + 20f) % 360f
            val secondaryGlowArgb = ColorUtils.HSLToColor(floatArrayOf(secondaryHue, controlledSat * 0.80f, (gradientTopLight * 0.75f).coerceAtLeast(0.09f)))
            val gradientBottomArgb = ColorUtils.blendARGB(surfaceArgb, gradientTopArgb, 0.20f)

            // 2. Мини-плеер (мягкий тонированный контейнер)
            val miniContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.60f, 0.15f))
            val finalMiniArgb = ColorUtils.blendARGB(surfaceContainerArgb, miniContainerArgb, 0.70f)

            // 3. Главная кнопка Play/Pause и слайдер (яркий, выразительный неоновый акцент)
            val playLight = 0.76f.coerceAtLeast(rawLight * 0.85f).coerceIn(0.70f, 0.84f)
            val playSat = rawSat.coerceIn(0.55f, 0.95f)
            val playerPrimaryArgb = ColorUtils.HSLToColor(floatArrayOf(hue, playSat, playLight))
            val playerOnPrimaryArgb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.90f, 0.10f))

            // 4. Вторичные кнопки управления (Prev, Next, Capsule)
            val playerContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.40f, 0.22f))
            val playerOnContainerArgb = 0xFFF1F5F9.toInt()

            // 5. Активные переключатели (Shuffle, Repeat, Like, Lyrics)
            val playerActiveContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, rawSat.coerceIn(0.55f, 0.90f), 0.34f))
            val playerOnActiveContainerArgb = 0xFFFFFFFF.toInt()

            // 6. Акцентный цвет
            val accentArgb = ColorUtils.HSLToColor(floatArrayOf(hue, rawSat.coerceIn(0.65f, 0.98f), 0.72f))

            return ArtworkColors(
                seedColor = Color(rawArgb),
                miniPlayerContainer = Color(finalMiniArgb),
                fullPlayerGradientTop = Color(gradientTopArgb),
                fullPlayerSecondaryGlow = Color(secondaryGlowArgb),
                fullPlayerGradientBottom = Color(gradientBottomArgb),
                accentColor = Color(accentArgb),
                playerPrimary = Color(playerPrimaryArgb),
                playerOnPrimary = Color(playerOnPrimaryArgb),
                playerContainer = Color(playerContainerArgb),
                playerOnContainer = Color(playerOnContainerArgb),
                playerActiveContainer = Color(playerActiveContainerArgb),
                playerOnActiveContainer = Color(playerOnActiveContainerArgb)
            )
        } else {
            // === СВЕТЛАЯ ТЕМА ===
            val controlledSat = rawSat.coerceIn(0.25f, 0.55f)
            val gradientTopLight = 0.88f + (rawLight * 0.05f).coerceIn(0f, 0.04f)
            val gradientTopArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.70f, gradientTopLight))

            val secondaryHue = (hue + 20f) % 360f
            val secondaryGlowArgb = ColorUtils.HSLToColor(floatArrayOf(secondaryHue, controlledSat * 0.60f, (gradientTopLight + 0.04f).coerceAtMost(0.96f)))
            val gradientBottomArgb = ColorUtils.blendARGB(surfaceArgb, gradientTopArgb, 0.25f)

            val miniContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.45f, 0.92f))
            val finalMiniArgb = ColorUtils.blendARGB(surfaceContainerArgb, miniContainerArgb, 0.65f)

            // Главная кнопка Play/Pause в светлой теме: глубокий контрастный цвет
            val playLight = 0.38f.coerceIn(0.30f, 0.45f)
            val playSat = rawSat.coerceIn(0.60f, 0.95f)
            val playerPrimaryArgb = ColorUtils.HSLToColor(floatArrayOf(hue, playSat, playLight))
            val playerOnPrimaryArgb = 0xFFFFFFFF.toInt()

            val playerContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.30f, 0.90f))
            val playerOnContainerArgb = 0xFF1E293B.toInt()

            val playerActiveContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, rawSat.coerceIn(0.50f, 0.85f), 0.82f))
            val playerOnActiveContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.90f, 0.15f))

            val accentArgb = ColorUtils.HSLToColor(floatArrayOf(hue, rawSat.coerceIn(0.65f, 0.98f), 0.42f))

            return ArtworkColors(
                seedColor = Color(rawArgb),
                miniPlayerContainer = Color(finalMiniArgb),
                fullPlayerGradientTop = Color(gradientTopArgb),
                fullPlayerSecondaryGlow = Color(secondaryGlowArgb),
                fullPlayerGradientBottom = Color(gradientBottomArgb),
                accentColor = Color(accentArgb),
                playerPrimary = Color(playerPrimaryArgb),
                playerOnPrimary = Color(playerOnPrimaryArgb),
                playerContainer = Color(playerContainerArgb),
                playerOnContainer = Color(playerOnContainerArgb),
                playerActiveContainer = Color(playerActiveContainerArgb),
                playerOnActiveContainer = Color(playerOnActiveContainerArgb)
            )
        }
    }
}
