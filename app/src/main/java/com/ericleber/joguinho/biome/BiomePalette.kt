package com.ericleber.joguinho.biome

import android.graphics.Color

/**
 * Tipo de detalhe decorativo sobreposto às paredes.
 * Usado pelo TileRenderer para pintar detalhes específicos de cada bioma.
 */
enum class WallDetailType {
    NONE,          // Sem detalhe
    MOSS,          // Musgo verde na base da parede
    ICE_DRIP,      // Gota de gelo estilizada
    EMBER,         // Ponto laranja brilhante (brasa)
    CRYSTAL_VEIN,  // Faixa azul/indígo diagonal
    RUNE_GLOW      // Símbolo simplificado que pulsa fraco
}

/**
 * Paleta visual completa por bioma — estilo Stardew Valley cave.
 * Campos novos (Fase 10.2) definem identidade de gameplay e atmosfera:
 *  - [lightingMode]      modo de iluminação do LightingSystem
 *  - [wallDetailType]    detalhe decorativo de parede
 *  - [floorVariantCount] número de variantes visuais do piso (1-4)
 *  - [hasDrips]          se o bioma tem goteiras de teto
 *  - [hasAmbush]         se o bioma suporta monstros de emboscada
 */
data class BiomePalette(
    val wallColor: Int,
    val wallTopColor: Int,
    val wallShadowColor: Int,
    val wallDetailColor: Int,
    val floorColor: Int,
    val floorVariant1: Int,
    val floorVariant2: Int,
    val floorVariant3: Int,
    val floorEdgeColor: Int,
    val accentColor: Int,
    val ambientLight: Int,
    val glowColor: Int,
    val particleColor: Int,
    val mushroomColor: Int,
    val mushroomCapColor: Int,
    val crystalColor: Int,
    val mossColor: Int,
    val backgroundColor: Int,
    // --- Fase 10.2: Identidade de Bioma ---
    val lightingMode: LightingMode = LightingMode.SUBTERRANEAN,
    val wallDetailType: WallDetailType = WallDetailType.NONE,
    val floorVariantCount: Int = 2,
    val hasDrips: Boolean = false,
    val hasAmbush: Boolean = false
)

/**
 * Função utilitária para gerar paletas dinamicamente baseadas em cores base.
 * Os campos de identidade (lightingMode, wallDetailType, etc.) são opcionais
 * e usam defaults seguros para não quebrar biomas legados.
 */
private fun createPalette(
    baseWall: Int,
    baseFloor: Int,
    accent: Int,
    flora: Int,
    bg: Int,
    lightingMode: LightingMode = LightingMode.SUBTERRANEAN,
    wallDetailType: WallDetailType = WallDetailType.NONE,
    floorVariantCount: Int = 2,
    hasDrips: Boolean = false,
    hasAmbush: Boolean = false
): BiomePalette {
    return BiomePalette(
        wallColor = baseWall,
        wallTopColor = clarear(baseWall, 0.2f),
        wallShadowColor = escurecer(baseWall, 0.4f),
        wallDetailColor = accent,
        floorColor = baseFloor,
        floorVariant1 = escurecer(baseFloor, 0.15f),
        floorVariant2 = clarear(baseFloor, 0.1f),
        floorVariant3 = escurecer(baseFloor, 0.1f),
        floorEdgeColor = escurecer(baseFloor, 0.3f),
        accentColor = accent,
        ambientLight = accent,
        glowColor = clarear(accent, 0.4f),
        particleColor = accent,
        mushroomColor = escurecer(flora, 0.3f),
        mushroomCapColor = flora,
        crystalColor = accent,
        mossColor = escurecer(baseWall, 0.2f),
        backgroundColor = bg,
        lightingMode = lightingMode,
        wallDetailType = wallDetailType,
        floorVariantCount = floorVariantCount,
        hasDrips = hasDrips,
        hasAmbush = hasAmbush
    )
}

private fun clarear(cor: Int, percent: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(cor, hsv)
    hsv[2] = (hsv[2] + percent).coerceIn(0f, 1f)
    return Color.HSVToColor(hsv)
}

private fun escurecer(cor: Int, percent: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(cor, hsv)
    hsv[2] = (hsv[2] - percent).coerceIn(0f, 1f)
    return Color.HSVToColor(hsv)
}

/**
 * Paletas por mundo — Geradas dinamicamente para os 11 mundos.
 */
val BIOME_PALETTES: Map<BiomeWorld, BiomePalette> = mapOf(
    BiomeWorld.ENTRANHAS to createPalette(0xFF3A3028.toInt(), 0xFFC4A882.toInt(), 0xFFF59E0B.toInt(), 0xFFB45309.toInt(), 0xFF0F0806.toInt(),
        lightingMode = LightingMode.SUBTERRANEAN, wallDetailType = WallDetailType.MOSS, floorVariantCount = 2, hasAmbush = true, hasDrips = true),
    
    BiomeWorld.FLORESTA_DE_ARVORES to createPalette(0xFF22543D.toInt(), 0xFF276749.toInt(), 0xFF48BB78.toInt(), 0xFF38A169.toInt(), 0xFF1C4532.toInt(),
        lightingMode = LightingMode.DAYLIGHT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4, hasAmbush = true),
    
    BiomeWorld.ABISMOS_AQUATICOS to createPalette(0xFF2A4365.toInt(), 0xFFEBF8FF.toInt(), 0xFFBEE3F8.toInt(), 0xFF90CDF4.toInt(), 0xFF1A365D.toInt(),
        lightingMode = LightingMode.SUBTERRANEAN, wallDetailType = WallDetailType.ICE_DRIP, hasDrips = true, floorVariantCount = 2),
    
    BiomeWorld.JARDIM_PROFUNDO to createPalette(0xFF234E52.toInt(), 0xFF319795.toInt(), 0xFF81E6D9.toInt(), 0xFF4FD1C5.toInt(), 0xFF1D3131.toInt(),
        lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4, hasDrips = true),
    
    BiomeWorld.MINAS_RIQUEZAS to createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt(),
        lightingMode = LightingMode.SUBTERRANEAN, wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 3, hasAmbush = true),
    
    BiomeWorld.RUINAS_ANCESTRAIS to createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt(),
        lightingMode = LightingMode.SUBTERRANEAN, wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 3, hasAmbush = true),
    
    BiomeWorld.REINO_DA_MAGIA to createPalette(0xFF44337A.toInt(), 0xFF6B46C1.toInt(), 0xFFD6BCFA.toInt(), 0xFF9F7AEA.toInt(), 0xFF2D1B4E.toInt(),
        lightingMode = LightingMode.MOONLIGHT, wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 4, hasAmbush = true),
    
    BiomeWorld.SUPERFICIE_ABERTA to createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt(),
        lightingMode = LightingMode.DAYLIGHT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 3),
    
    BiomeWorld.ABISMO_DO_VAZIO to createPalette(0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(),
        lightingMode = LightingMode.VOID_DARK, hasAmbush = true),
    
    BiomeWorld.NUCLEO_DE_FOGO to createPalette(0xFF9B2C2C.toInt(), 0xFFE53E3E.toInt(), 0xFFFFF5F5.toInt(), 0xFFFEB2B2.toInt(), 0xFF2D1212.toInt(),
        lightingMode = LightingMode.LAVA_GLOW, wallDetailType = WallDetailType.EMBER, floorVariantCount = 2, hasAmbush = true),
    
    BiomeWorld.BASE_LUNAR to createPalette(0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt(),
        lightingMode = LightingMode.MOONLIGHT, floorVariantCount = 3, hasAmbush = true)
)

/**
 * Aplica um "Hue Shift" na cor baseada na profundidade (floorNumber).
 * Reduz a luminosidade e empurra o matiz para tons mais frios (azul/roxo).
 */
fun applyDepthHueShift(color: Int, floorNumber: Int): Int {
    if (floorNumber <= 1) return color
    
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    
    // Profundidade máxima considerada para o cálculo = 100 andares
    val depthFactor = (floorNumber.coerceAtMost(100) / 100f)
    
    // Matiz alvo (Azul escuro / Roxo = ~250 graus)
    val targetHue = 250f
    
    // Desloca até 25% em direção ao azul na profundidade 100
    val shiftAmount = depthFactor * 0.25f
    
    var diff = targetHue - hsv[0]
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    
    hsv[0] = (hsv[0] + diff * shiftAmount + 360f) % 360f
    
    // Reduz a luminosidade em até 35% na profundidade 100
    hsv[2] = (hsv[2] * (1f - (depthFactor * 0.35f))).coerceIn(0f, 1f)
    
    return Color.HSVToColor(hsv)
}

/**
 * Aplica o Hue Shift em toda a paleta.
 */
fun applyDepthHueShiftToPalette(palette: BiomePalette, floorNumber: Int): BiomePalette {
    if (floorNumber <= 1) return palette
    return BiomePalette(
        wallColor = applyDepthHueShift(palette.wallColor, floorNumber),
        wallTopColor = applyDepthHueShift(palette.wallTopColor, floorNumber),
        wallShadowColor = applyDepthHueShift(palette.wallShadowColor, floorNumber),
        wallDetailColor = applyDepthHueShift(palette.wallDetailColor, floorNumber),
        floorColor = applyDepthHueShift(palette.floorColor, floorNumber),
        floorVariant1 = applyDepthHueShift(palette.floorVariant1, floorNumber),
        floorVariant2 = applyDepthHueShift(palette.floorVariant2, floorNumber),
        floorVariant3 = applyDepthHueShift(palette.floorVariant3, floorNumber),
        floorEdgeColor = applyDepthHueShift(palette.floorEdgeColor, floorNumber),
        accentColor = applyDepthHueShift(palette.accentColor, floorNumber),
        ambientLight = applyDepthHueShift(palette.ambientLight, floorNumber),
        glowColor = applyDepthHueShift(palette.glowColor, floorNumber),
        particleColor = applyDepthHueShift(palette.particleColor, floorNumber),
        mushroomColor = applyDepthHueShift(palette.mushroomColor, floorNumber),
        mushroomCapColor = applyDepthHueShift(palette.mushroomCapColor, floorNumber),
        crystalColor = applyDepthHueShift(palette.crystalColor, floorNumber),
        mossColor = applyDepthHueShift(palette.mossColor, floorNumber),
        backgroundColor = applyDepthHueShift(palette.backgroundColor, floorNumber),
        // Campos de identidade: nunca sofrem HueShift
        lightingMode = palette.lightingMode,
        wallDetailType = palette.wallDetailType,
        floorVariantCount = palette.floorVariantCount,
        hasDrips = palette.hasDrips,
        hasAmbush = palette.hasAmbush
    )
}

