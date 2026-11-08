package org.akanework.gramophone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Интеллектуальная система извлечения и генерации динамической палитры Material 3 / PixelPlay из обложки.
 * Включает детектор монохромных (ч/б) и слабонасыщенных обложек, плавное масштабирование сатурации
 * и гармоничную адаптацию под темную и светлую темы.
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
        val playerOnActiveContainer: Color,
        val isMonochrome: Boolean = false
    )

    /**
     * Интеллектуальный выбор наиболее выразительного и представительного оттенка обложки.
     * Корректно определяет монохромность и отбирает доминирующий/выразительный тон.
     */
    fun selectBestSwatch(palette: Palette?): Palette.Swatch? {
        if (palette == null) return null
        val swatches = palette.swatches
        if (swatches.isEmpty()) return palette.dominantSwatch ?: palette.vibrantSwatch

        val maxPopulation = swatches.maxOfOrNull { it.population }?.toFloat() ?: 1f
        val maxSaturation = swatches.maxOfOrNull {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(it.rgb, hsl)
            hsl[1]
        } ?: 0f

        // Если обложка в целом монохромная (maxSat < 0.10), выбираем наиболее представительный свотч по площади и контрасту
        if (maxSaturation < 0.10f) {
            return palette.dominantSwatch ?: swatches.maxByOrNull { swatch ->
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(swatch.rgb, hsl)
                val light = hsl[2]
                val popRatio = (swatch.population / maxPopulation).coerceIn(0f, 1f)
                val lightScore = if (light in 0.15f..0.85f) 1.0f else 0.5f
                popRatio * lightScore
            } ?: swatches.first()
        }

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

            // Штрафуем ненасыщенные грязно-серые тени при наличии выразительных цветов
            val saturationScore = when {
                sat < 0.14f -> 0.10f
                sat in 0.30f..0.95f -> 1.3f
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
                palette.mutedSwatch -> 0.6f
                palette.darkMutedSwatch -> 0.5f
                palette.lightMutedSwatch -> 0.5f
                else -> 1.0f
            }

            saturationScore * lightnessScore * populationScore * roleBoost
        } ?: palette.dominantSwatch ?: palette.vibrantSwatch
    }

    fun calculateFromPalette(
        palette: Palette?,
        isDarkTheme: Boolean,
        defaultSurface: Color,
        defaultSurfaceContainer: Color,
        isPureDark: Boolean = false
    ): ArtworkColors {
        val selectedSwatch = selectBestSwatch(palette)

        if (selectedSwatch == null) {
            val fallbackPrimary = if (isDarkTheme) Color(0xFFD0BCFF) else Color(0xFF6750A4)
            val fallbackOnPrimary = if (isDarkTheme) Color(0xFF381E72) else Color(0xFFFFFFFF)
            val bgBottom = if (isDarkTheme && isPureDark) Color.Black else defaultSurface
            return ArtworkColors(
                seedColor = defaultSurfaceContainer,
                miniPlayerContainer = defaultSurfaceContainer,
                fullPlayerGradientTop = defaultSurface,
                fullPlayerSecondaryGlow = defaultSurface,
                fullPlayerGradientBottom = bgBottom,
                accentColor = fallbackPrimary,
                playerPrimary = fallbackPrimary,
                playerOnPrimary = fallbackOnPrimary,
                playerContainer = defaultSurfaceContainer,
                playerOnContainer = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                playerActiveContainer = fallbackPrimary.copy(alpha = 0.35f),
                playerOnActiveContainer = if (isDarkTheme) Color.White else Color.Black,
                isMonochrome = true
            )
        }

        val rawArgb = selectedSwatch.rgb
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rawArgb, hsl)
        val hue = hsl[0]
        val rawSat = hsl[1]
        val rawLight = hsl[2]

        val swatches = palette?.swatches.orEmpty()
        val maxSaturation = if (swatches.isNotEmpty()) {
            swatches.maxOf { s ->
                val h = FloatArray(3)
                ColorUtils.colorToHSL(s.rgb, h)
                h[1]
            }
        } else rawSat

        val totalPop = swatches.sumOf { it.population }.toFloat().coerceAtLeast(1f)
        val weightedAvgSat = if (swatches.isNotEmpty()) {
            swatches.sumOf { s ->
                val h = FloatArray(3)
                ColorUtils.colorToHSL(s.rgb, h)
                (h[1] * s.population).toDouble()
            }.toFloat() / totalPop
        } else rawSat

        // 🔥 Детектор монохрома / ч/б обложки:
        // 1. Все свотчи имеют сверхнизкую насыщенность (< 0.12f), ИЛИ
        // 2. Средневзвешенная насыщенность изображения < 0.08f (при отсутствии ярких пятен), ИЛИ
        // 3. Выбранный свотч практически полностью ахроматический (< 0.08f)
        val isMonochrome = maxSaturation < 0.12f || (weightedAvgSat < 0.08f && maxSaturation < 0.20f) || (rawSat < 0.08f)

        val surfaceArgb = if (isDarkTheme) 0xFF121316.toInt() else 0xFFF8F9FA.toInt()
        val surfaceContainerArgb = if (isDarkTheme) 0xFF1E2024.toInt() else 0xFFE9ECEF.toInt()

        if (isMonochrome) {
            // === 🌟 ПРЕМИАЛЬНЫЙ МОНОХРОМНЫЙ РЕЖИМ (Ч/Б И СЕРЫЕ ОБЛОЖКИ) ===
            if (isDarkTheme) {
                val monoSurfaceTop = if (isPureDark) Color(0xFF101014) else Color(0xFF1E2025)
                val monoSecondaryGlow = if (isPureDark) Color(0xFF0A0A0C) else Color(0xFF16171B)
                val monoSurfaceBottom = if (isPureDark) Color.Black else Color(0xFF0F1013)
                val monoMiniContainer = if (isPureDark) Color(0xFF141418) else Color(0xFF222429)

                val monoPrimary = Color(0xFFFFFFFF)
                val monoOnPrimary = Color(0xFF121316)
                val monoContainer = if (isPureDark) Color(0xFF18181E) else Color(0xFF2C2E35)
                val monoOnContainer = Color(0xFFF1F5F9)
                val monoActiveContainer = if (isPureDark) Color(0xFF2E313A) else Color(0xFF474A54)
                val monoOnActiveContainer = Color(0xFFFFFFFF)
                val monoAccent = Color(0xFFFFFFFF)

                return ArtworkColors(
                    seedColor = Color(rawArgb),
                    miniPlayerContainer = monoMiniContainer,
                    fullPlayerGradientTop = monoSurfaceTop,
                    fullPlayerSecondaryGlow = monoSecondaryGlow,
                    fullPlayerGradientBottom = monoSurfaceBottom,
                    accentColor = monoAccent,
                    playerPrimary = monoPrimary,
                    playerOnPrimary = monoOnPrimary,
                    playerContainer = monoContainer,
                    playerOnContainer = monoOnContainer,
                    playerActiveContainer = monoActiveContainer,
                    playerOnActiveContainer = monoOnActiveContainer,
                    isMonochrome = true
                )
            } else {
                val monoSurfaceTop = Color(0xFFF8FAFC)
                val monoSecondaryGlow = Color(0xFFEFF2F6)
                val monoSurfaceBottom = Color(0xFFE2E8F0)
                val monoMiniContainer = Color(0xFFE2E4E9)

                val monoPrimary = Color(0xFF1E293B)
                val monoOnPrimary = Color(0xFFFFFFFF)
                val monoContainer = Color(0xFFE2E8F0)
                val monoOnContainer = Color(0xFF0F172A)
                val monoActiveContainer = Color(0xFFCBD5E1)
                val monoOnActiveContainer = Color(0xFF0F172A)
                val monoAccent = Color(0xFF1E293B)

                return ArtworkColors(
                    seedColor = Color(rawArgb),
                    miniPlayerContainer = monoMiniContainer,
                    fullPlayerGradientTop = monoSurfaceTop,
                    fullPlayerSecondaryGlow = monoSecondaryGlow,
                    fullPlayerGradientBottom = monoSurfaceBottom,
                    accentColor = monoAccent,
                    playerPrimary = monoPrimary,
                    playerOnPrimary = monoOnPrimary,
                    playerContainer = monoContainer,
                    playerOnContainer = monoOnContainer,
                    playerActiveContainer = monoActiveContainer,
                    playerOnActiveContainer = monoOnActiveContainer,
                    isMonochrome = true
                )
            }
        }

        // === 🎨 ЦВЕТНОЙ / ПАСТЕЛЬНЫЙ РЕЖИМ С ПЛАВНЫМ МАСШТАБИРОВАНИЕМ САТУРАЦИИ ===
        if (isDarkTheme) {
            // === ТЕМНАЯ ТЕМА ===
            // 1. Плавный адаптивный контроль насыщенности фона (пастель остается мягкой, яркое — глубоким)
            val controlledSat = if (rawSat < 0.25f) (rawSat * 1.5f).coerceIn(0.12f, 0.35f) else (rawSat * 1.05f).coerceIn(0.35f, 0.82f)
            val gradientTopLight = 0.16f + (rawLight * 0.08f).coerceIn(0f, 0.05f)
            val gradientTopArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.95f, gradientTopLight))

            // Вторичное свечение (+20° по цветовому кругу)
            val secondaryHue = (hue + 20f) % 360f
            val secondaryGlowArgb = ColorUtils.HSLToColor(floatArrayOf(secondaryHue, controlledSat * 0.80f, (gradientTopLight * 0.75f).coerceAtLeast(0.09f)))
            val gradientBottomArgb = if (isPureDark) 0xFF000000.toInt() else ColorUtils.blendARGB(surfaceArgb, gradientTopArgb, 0.20f)

            // 2. Мини-плеер (мягкий тонированный контейнер)
            val miniContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.60f, if (isPureDark) 0.10f else 0.15f))
            val finalMiniArgb = if (isPureDark) ColorUtils.blendARGB(0xFF121214.toInt(), miniContainerArgb, 0.50f) else ColorUtils.blendARGB(surfaceContainerArgb, miniContainerArgb, 0.70f)

            // 3. Главная кнопка Play/Pause и слайдер (акцентная подсветка)
            val playLight = 0.76f.coerceAtLeast(rawLight * 0.85f).coerceIn(0.70f, 0.84f)
            val playSat = if (rawSat < 0.25f) (rawSat * 1.8f).coerceIn(0.20f, 0.45f) else (rawSat * 1.15f).coerceIn(0.50f, 0.95f)
            val playerPrimaryArgb = ColorUtils.HSLToColor(floatArrayOf(hue, playSat, playLight))
            val playerOnPrimaryArgb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.90f, 0.10f))

            // 4. Вторичные кнопки управления (Prev, Next, Capsule)
            val playerContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.40f, 0.22f))
            val playerOnContainerArgb = 0xFFF1F5F9.toInt()

            // 5. Активные переключатели (Shuffle, Repeat, Like, Lyrics)
            val activeSat = if (rawSat < 0.25f) (rawSat * 1.6f).coerceIn(0.20f, 0.45f) else rawSat.coerceIn(0.55f, 0.90f)
            val playerActiveContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, activeSat, 0.34f))
            val playerOnActiveContainerArgb = 0xFFFFFFFF.toInt()

            // 6. Акцентный цвет
            val accentSat = if (rawSat < 0.25f) (rawSat * 2.0f).coerceIn(0.25f, 0.55f) else rawSat.coerceIn(0.65f, 0.98f)
            val accentArgb = ColorUtils.HSLToColor(floatArrayOf(hue, accentSat, 0.72f))

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
                playerOnActiveContainer = Color(playerOnActiveContainerArgb),
                isMonochrome = false
            )
        } else {
            // === СВЕТЛАЯ ТЕМА ===
            val controlledSat = if (rawSat < 0.25f) (rawSat * 1.3f).coerceIn(0.10f, 0.30f) else (rawSat * 1.0f).coerceIn(0.25f, 0.55f)
            val gradientTopLight = 0.88f + (rawLight * 0.05f).coerceIn(0f, 0.04f)
            val gradientTopArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.70f, gradientTopLight))

            val secondaryHue = (hue + 20f) % 360f
            val secondaryGlowArgb = ColorUtils.HSLToColor(floatArrayOf(secondaryHue, controlledSat * 0.60f, (gradientTopLight + 0.04f).coerceAtMost(0.96f)))
            val gradientBottomArgb = ColorUtils.blendARGB(surfaceArgb, gradientTopArgb, 0.25f)

            val miniContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.45f, 0.92f))
            val finalMiniArgb = ColorUtils.blendARGB(surfaceContainerArgb, miniContainerArgb, 0.65f)

            val playLight = 0.38f.coerceIn(0.30f, 0.45f)
            val playSat = if (rawSat < 0.25f) (rawSat * 1.8f).coerceIn(0.20f, 0.50f) else (rawSat * 1.15f).coerceIn(0.55f, 0.95f)
            val playerPrimaryArgb = ColorUtils.HSLToColor(floatArrayOf(hue, playSat, playLight))
            val playerOnPrimaryArgb = 0xFFFFFFFF.toInt()

            val playerContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, controlledSat * 0.30f, 0.90f))
            val playerOnContainerArgb = 0xFF1E293B.toInt()

            val activeSat = if (rawSat < 0.25f) (rawSat * 1.6f).coerceIn(0.18f, 0.45f) else rawSat.coerceIn(0.50f, 0.85f)
            val playerActiveContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, activeSat, 0.82f))
            val playerOnActiveContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.90f, 0.15f))

            val accentSat = if (rawSat < 0.25f) (rawSat * 2.0f).coerceIn(0.25f, 0.55f) else rawSat.coerceIn(0.65f, 0.98f)
            val accentArgb = ColorUtils.HSLToColor(floatArrayOf(hue, accentSat, 0.42f))

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
                playerOnActiveContainer = Color(playerOnActiveContainerArgb),
                isMonochrome = false
            )
        }
    }
}
