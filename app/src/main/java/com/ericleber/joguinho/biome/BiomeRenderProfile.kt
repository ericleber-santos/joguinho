package com.ericleber.joguinho.biome

import android.graphics.Color
import kotlin.math.roundToInt

enum class BiomeVisualTheme {
    EARTHEN_CAVE,
    SUNLIT_FOREST,
    AQUATIC_ICE,
    BIO_GARDEN,
    TREASURE_MINE,
    ANCIENT_RUINS,
    ARCANE_REALM,
    SUNLIT_SURFACE,
    VOID_ABYSS,
    LAVA_CORE,
    LUNAR_OUTPOST
}

data class BiomeRenderProfile(
    val theme: BiomeVisualTheme,
    val skyTopColor: Int,
    val skyBottomColor: Int,
    val ambientOverlayColor: Int,
    val heroLightRadiusMultiplier: Float,
    val portalLightRadiusMultiplier: Float,
    val monsterLightIntensity: Float,
    val useStars: Boolean,
    val useSun: Boolean,
    val horizonColor: Int,
    val silhouetteColor: Int,
    val atmosphereColor: Int,
    val atmosphereAlpha: Int,
    val nearParticleColor: Int
)

fun buildBiomeRenderProfile(world: BiomeWorld, palette: BiomePalette): BiomeRenderProfile {
    return when (world) {
        BiomeWorld.ENTRANHAS -> BiomeRenderProfile(
            theme = BiomeVisualTheme.EARTHEN_CAVE,
            skyTopColor = darken(palette.backgroundColor, 0.08f),
            skyBottomColor = palette.backgroundColor,
            ambientOverlayColor = Color.argb(120, 10, 8, 6),
            heroLightRadiusMultiplier = 4.8f,
            portalLightRadiusMultiplier = 5.8f,
            monsterLightIntensity = 0.10f,
            useStars = false,
            useSun = false,
            horizonColor = darken(palette.wallColor, 0.55f),
            silhouetteColor = darken(palette.wallColor, 0.68f),
            atmosphereColor = palette.floorVariant2,
            atmosphereAlpha = 32,
            nearParticleColor = palette.floorVariant2
        )
        BiomeWorld.FLORESTA_DE_ARVORES -> BiomeRenderProfile(
            theme = BiomeVisualTheme.SUNLIT_FOREST,
            skyTopColor = Color.rgb(92, 164, 255),
            skyBottomColor = Color.rgb(190, 228, 255),
            ambientOverlayColor = Color.argb(36, 40, 72, 44),
            heroLightRadiusMultiplier = 3.8f,
            portalLightRadiusMultiplier = 5.6f,
            monsterLightIntensity = 0.08f,
            useStars = false,
            useSun = true,
            horizonColor = lighten(palette.accentColor, 0.12f),
            silhouetteColor = darken(palette.wallColor, 0.38f),
            atmosphereColor = Color.rgb(245, 255, 210),
            atmosphereAlpha = 38,
            nearParticleColor = Color.rgb(180, 255, 150)
        )
        BiomeWorld.ABISMOS_AQUATICOS -> BiomeRenderProfile(
            theme = BiomeVisualTheme.AQUATIC_ICE,
            skyTopColor = Color.rgb(18, 52, 96),
            skyBottomColor = Color.rgb(40, 102, 160),
            ambientOverlayColor = Color.argb(82, 16, 44, 74),
            heroLightRadiusMultiplier = 4.6f,
            portalLightRadiusMultiplier = 5.8f,
            monsterLightIntensity = 0.16f,
            useStars = false,
            useSun = false,
            horizonColor = palette.floorVariant2,
            silhouetteColor = darken(palette.wallColor, 0.42f),
            atmosphereColor = Color.rgb(170, 220, 255),
            atmosphereAlpha = 54,
            nearParticleColor = Color.rgb(210, 240, 255)
        )
        BiomeWorld.JARDIM_PROFUNDO -> BiomeRenderProfile(
            theme = BiomeVisualTheme.BIO_GARDEN,
            skyTopColor = Color.rgb(10, 34, 24),
            skyBottomColor = Color.rgb(22, 72, 48),
            ambientOverlayColor = Color.argb(86, 8, 26, 18),
            heroLightRadiusMultiplier = 4.4f,
            portalLightRadiusMultiplier = 5.8f,
            monsterLightIntensity = 0.24f,
            useStars = false,
            useSun = false,
            horizonColor = darken(palette.accentColor, 0.35f),
            silhouetteColor = darken(palette.wallColor, 0.48f),
            atmosphereColor = palette.glowColor,
            atmosphereAlpha = 58,
            nearParticleColor = lighten(palette.accentColor, 0.25f)
        )
        BiomeWorld.MINAS_RIQUEZAS -> BiomeRenderProfile(
            theme = BiomeVisualTheme.TREASURE_MINE,
            skyTopColor = darken(palette.backgroundColor, 0.04f),
            skyBottomColor = palette.backgroundColor,
            ambientOverlayColor = Color.argb(94, 28, 18, 8),
            heroLightRadiusMultiplier = 4.6f,
            portalLightRadiusMultiplier = 5.9f,
            monsterLightIntensity = 0.14f,
            useStars = false,
            useSun = false,
            horizonColor = darken(palette.wallColor, 0.50f),
            silhouetteColor = darken(palette.wallColor, 0.62f),
            atmosphereColor = palette.crystalColor,
            atmosphereAlpha = 38,
            nearParticleColor = palette.accentColor
        )
        BiomeWorld.RUINAS_ANCESTRAIS -> BiomeRenderProfile(
            theme = BiomeVisualTheme.ANCIENT_RUINS,
            skyTopColor = Color.rgb(28, 26, 22),
            skyBottomColor = Color.rgb(70, 56, 36),
            ambientOverlayColor = Color.argb(96, 28, 20, 12),
            heroLightRadiusMultiplier = 4.7f,
            portalLightRadiusMultiplier = 6.0f,
            monsterLightIntensity = 0.10f,
            useStars = false,
            useSun = false,
            horizonColor = darken(palette.wallColor, 0.42f),
            silhouetteColor = darken(palette.wallColor, 0.58f),
            atmosphereColor = palette.floorVariant2,
            atmosphereAlpha = 34,
            nearParticleColor = palette.accentColor
        )
        BiomeWorld.REINO_DA_MAGIA -> BiomeRenderProfile(
            theme = BiomeVisualTheme.ARCANE_REALM,
            skyTopColor = Color.rgb(16, 14, 46),
            skyBottomColor = Color.rgb(44, 30, 92),
            ambientOverlayColor = Color.argb(88, 14, 10, 34),
            heroLightRadiusMultiplier = 4.9f,
            portalLightRadiusMultiplier = 6.0f,
            monsterLightIntensity = 0.22f,
            useStars = true,
            useSun = false,
            horizonColor = darken(palette.crystalColor, 0.18f),
            silhouetteColor = darken(palette.wallColor, 0.45f),
            atmosphereColor = palette.glowColor,
            atmosphereAlpha = 52,
            nearParticleColor = lighten(palette.accentColor, 0.20f)
        )
        BiomeWorld.SUPERFICIE_ABERTA -> BiomeRenderProfile(
            theme = BiomeVisualTheme.SUNLIT_SURFACE,
            skyTopColor = Color.rgb(106, 184, 255),
            skyBottomColor = Color.rgb(226, 242, 255),
            ambientOverlayColor = Color.argb(28, 52, 74, 40),
            heroLightRadiusMultiplier = 3.9f,
            portalLightRadiusMultiplier = 5.6f,
            monsterLightIntensity = 0.07f,
            useStars = false,
            useSun = true,
            horizonColor = lighten(palette.accentColor, 0.22f),
            silhouetteColor = darken(palette.wallColor, 0.30f),
            atmosphereColor = Color.rgb(255, 246, 196),
            atmosphereAlpha = 26,
            nearParticleColor = Color.rgb(255, 248, 190)
        )
        BiomeWorld.ABISMO_DO_VAZIO -> BiomeRenderProfile(
            theme = BiomeVisualTheme.VOID_ABYSS,
            skyTopColor = Color.rgb(0, 0, 0),
            skyBottomColor = Color.rgb(12, 10, 24),
            ambientOverlayColor = Color.argb(172, 0, 0, 8),
            heroLightRadiusMultiplier = 3.7f,
            portalLightRadiusMultiplier = 5.6f,
            monsterLightIntensity = 0.20f,
            useStars = true,
            useSun = false,
            horizonColor = Color.rgb(22, 18, 42),
            silhouetteColor = Color.rgb(6, 6, 14),
            atmosphereColor = palette.accentColor,
            atmosphereAlpha = 34,
            nearParticleColor = palette.accentColor
        )
        BiomeWorld.NUCLEO_DE_FOGO -> BiomeRenderProfile(
            theme = BiomeVisualTheme.LAVA_CORE,
            skyTopColor = Color.rgb(28, 8, 4),
            skyBottomColor = Color.rgb(92, 18, 0),
            ambientOverlayColor = Color.argb(90, 32, 8, 0),
            heroLightRadiusMultiplier = 4.5f,
            portalLightRadiusMultiplier = 6.2f,
            monsterLightIntensity = 0.16f,
            useStars = false,
            useSun = false,
            horizonColor = Color.rgb(180, 52, 8),
            silhouetteColor = Color.rgb(20, 4, 0),
            atmosphereColor = Color.rgb(255, 132, 20),
            atmosphereAlpha = 50,
            nearParticleColor = Color.rgb(255, 180, 40)
        )
        BiomeWorld.BASE_LUNAR -> BiomeRenderProfile(
            theme = BiomeVisualTheme.LUNAR_OUTPOST,
            skyTopColor = Color.rgb(6, 8, 28),
            skyBottomColor = Color.rgb(34, 42, 86),
            ambientOverlayColor = Color.argb(68, 10, 18, 34),
            heroLightRadiusMultiplier = 4.2f,
            portalLightRadiusMultiplier = 5.7f,
            monsterLightIntensity = 0.10f,
            useStars = true,
            useSun = false,
            horizonColor = Color.rgb(130, 146, 182),
            silhouetteColor = Color.rgb(56, 68, 88),
            atmosphereColor = Color.rgb(186, 214, 255),
            atmosphereAlpha = 28,
            nearParticleColor = Color.rgb(220, 232, 255)
        )
    }
}

private fun darken(color: Int, factor: Float): Int {
    val r = (Color.red(color) * (1f - factor)).roundToInt().coerceIn(0, 255)
    val g = (Color.green(color) * (1f - factor)).roundToInt().coerceIn(0, 255)
    val b = (Color.blue(color) * (1f - factor)).roundToInt().coerceIn(0, 255)
    return Color.rgb(r, g, b)
}

private fun lighten(color: Int, factor: Float): Int {
    val r = (Color.red(color) + (255 - Color.red(color)) * factor).roundToInt().coerceIn(0, 255)
    val g = (Color.green(color) + (255 - Color.green(color)) * factor).roundToInt().coerceIn(0, 255)
    val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).roundToInt().coerceIn(0, 255)
    return Color.rgb(r, g, b)
}
