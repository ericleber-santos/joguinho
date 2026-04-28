package com.ericleber.joguinho.biome

import android.graphics.Color

/**
 * Paleta visual completa por bioma — estilo Stardew Valley cave.
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
    val backgroundColor: Int
)

/**
 * Função utilitária para gerar paletas dinamicamente baseadas em cores base.
 */
private fun createPalette(
    baseWall: Int,
    baseFloor: Int,
    accent: Int,
    flora: Int,
    bg: Int
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
        backgroundColor = bg
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
 * Paletas por bioma — Geradas dinamicamente para os 60 biomas.
 */
val BIOME_PALETTES: Map<Biome, BiomePalette> = Biome.entries.associateWith { biome ->
    when (biome) {
        // 1-10: Minas e Cavernas Iniciais (Marrons e Cinzas)
        Biome.MINA_ABANDONADA -> createPalette(0xFF3A3028.toInt(), 0xFFC4A882.toInt(), 0xFFF59E0B.toInt(), 0xFFB45309.toInt(), 0xFF0F0806.toInt())
        Biome.CAVERNA_UMIDA -> createPalette(0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFF63B3ED.toInt(), 0xFF4299E1.toInt(), 0xFF1A202C.toInt())
        Biome.TUNEIS_DE_TERRA -> createPalette(0xFF4A3728.toInt(), 0xFF8B4513.toInt(), 0xFFCD853F.toInt(), 0xFFD2691E.toInt(), 0xFF1E1408.toInt())
        Biome.MINA_DE_CARVAO -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF718096.toInt(), 0xFF4A5568.toInt(), 0xFF000000.toInt())
        Biome.CAVERNA_DE_CALCARIO -> createPalette(0xFF718096.toInt(), 0xFFE2E8F0.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt())

        // 11-20: Cristais e Cogumelos (Azuis e Roxos)
        Biome.JARDIM_DE_FUNGOS -> createPalette(0xFF44337A.toInt(), 0xFF6B46C1.toInt(), 0xFFD6BCFA.toInt(), 0xFF9F7AEA.toInt(), 0xFF2D1B4E.toInt())
        Biome.CAVERNA_DE_CRISTAL_AZUL -> createPalette(0xFF2A4365.toInt(), 0xFF3182CE.toInt(), 0xFF90CDF4.toInt(), 0xFF63B3ED.toInt(), 0xFF1A365D.toInt())
        Biome.TUNEIS_LUMINESCENTES -> createPalette(0xFF234E52.toInt(), 0xFF319795.toInt(), 0xFF81E6D9.toInt(), 0xFF4FD1C5.toInt(), 0xFF1D3131.toInt())
        Biome.GRUTA_DOS_COGUMELOS -> createPalette(0xFF702459.toInt(), 0xFFB83280.toInt(), 0xFFF687B3.toInt(), 0xFFED64A6.toInt(), 0xFF4A1239.toInt())
        Biome.MINA_DE_QUARTZO -> createPalette(0xFF4A5568.toInt(), 0xFFEDF2F7.toInt(), 0xFFFFFFFF.toInt(), 0xFFE2E8F0.toInt(), 0xFF1A202C.toInt())

        // 21-30: Água e Gelo (Cianos e Brancos)
        Biome.RIACHOS_SUBTERRANEOS -> createPalette(0xFF2C5282.toInt(), 0xFF4299E1.toInt(), 0xFFBEE3F8.toInt(), 0xFF90CDF4.toInt(), 0xFF1A365D.toInt())
        Biome.LAGO_CONGELADO -> createPalette(0xFF2A4365.toInt(), 0xFFEBF8FF.toInt(), 0xFFBEE3F8.toInt(), 0xFF90CDF4.toInt(), 0xFF1A365D.toInt())
        Biome.CAVERNA_DE_GELO -> createPalette(0xFFEBF8FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFBEE3F8.toInt(), 0xFF90CDF4.toInt(), 0xFF2A4365.toInt())
        Biome.TUNEIS_AQUATICOS -> createPalette(0xFF2B6CB0.toInt(), 0xFF3182CE.toInt(), 0xFF63B3ED.toInt(), 0xFF4299E1.toInt(), 0xFF1A365D.toInt())
        Biome.ABISMO_AZUL -> createPalette(0xFF1A365D.toInt(), 0xFF2A4365.toInt(), 0xFF3182CE.toInt(), 0xFF2B6CB0.toInt(), 0xFF000000.toInt())

        // 31-40: Vegetação e Raízes (Verdes)
        Biome.PLANTACOES_ABRIGOS -> createPalette(0xFF276749.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt())
        Biome.CAVERNA_DAS_RAIZES -> createPalette(0xFF38A169.toInt(), 0xFF2F855A.toInt(), 0xFFC6F6D5.toInt(), 0xFF9AE6B4.toInt(), 0xFF1C4532.toInt())
        Biome.FLORESTA_SUBTERRANEA -> createPalette(0xFF22543D.toInt(), 0xFF276749.toInt(), 0xFF48BB78.toInt(), 0xFF38A169.toInt(), 0xFF1C4532.toInt())
        Biome.JARDIM_DE_PEDRA -> createPalette(0xFF2D3748.toInt(), 0xFF2F855A.toInt(), 0xFF68D391.toInt(), 0xFF48BB78.toInt(), 0xFF1A202C.toInt())
        Biome.TUNEIS_VERDES -> createPalette(0xFF2F855A.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt())

        // 41-50: Rochas e Minerais (Cinzas e Metálicos)
        Biome.CONSTRUCOES_ROCHOSAS -> createPalette(0xFF4A5568.toInt(), 0xFF718096.toInt(), 0xFFA0AEC0.toInt(), 0xFFCBD5E0.toInt(), 0xFF1A202C.toInt())
        Biome.MINA_DE_FERRO -> createPalette(0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFFE2E8F0.toInt(), 0xFFA0AEC0.toInt(), 0xFF1A202C.toInt())
        Biome.CAVERNA_DE_GRANITO -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF718096.toInt(), 0xFF4A5568.toInt(), 0xFF000000.toInt())
        Biome.TUNEIS_DE_XISTO -> createPalette(0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF4A5568.toInt(), 0xFF2D3748.toInt(), 0xFF000000.toInt())
        Biome.ABISMO_DE_PEDRA -> createPalette(0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt())

        // 51-60: Ouro e Riquezas (Amarelos e Verdes)
        Biome.MINA_DE_OURO -> createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt())
        Biome.CAVERNA_DE_ESMERALDA -> createPalette(0xFF22543D.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt())
        Biome.TUNEIS_DE_RUBI -> createPalette(0xFF742A2A.toInt(), 0xFFC53030.toInt(), 0xFFFEB2B2.toInt(), 0xFFFC8181.toInt(), 0xFF2D1212.toInt())
        Biome.SALOES_DOURADOS -> createPalette(0xFF975A16.toInt(), 0xFFECC94B.toInt(), 0xFFFFFFF0.toInt(), 0xFFF6E05E.toInt(), 0xFF2D1B0E.toInt())
        Biome.TESOURO_SUBTERRANEO -> createPalette(0xFFB7791F.toInt(), 0xFFF6E05E.toInt(), 0xFFFFFFF0.toInt(), 0xFFFAF089.toInt(), 0xFF2D1B0E.toInt())

        // 61-70: Antiguidade e Ruínas (Bege e Mármore)
        Biome.RUINAS_ANTIGAS -> createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt())
        Biome.TUMULO_DOS_REIS -> createPalette(0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFFA0AEC0.toInt(), 0xFF718096.toInt(), 0xFF1A202C.toInt())
        Biome.CATACUMBAS_ESQUECIDAS -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFF2D3748.toInt(), 0xFF000000.toInt())
        Biome.TEMPLO_ROCHOSO -> createPalette(0xFF4A5568.toInt(), 0xFF718096.toInt(), 0xFFA0AEC0.toInt(), 0xFFCBD5E0.toInt(), 0xFF1A202C.toInt())
        Biome.SALOES_DE_MARMORE -> createPalette(0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt())

        // 71-80: Magia e Mistério (Roxos e Estelares)
        Biome.CAVERNA_ARCANA -> createPalette(0xFF44337A.toInt(), 0xFF6B46C1.toInt(), 0xFFD6BCFA.toInt(), 0xFF9F7AEA.toInt(), 0xFF2D1B4E.toInt())
        Biome.TUNEIS_DE_MANA -> createPalette(0xFF2A4365.toInt(), 0xFF3182CE.toInt(), 0xFF90CDF4.toInt(), 0xFF63B3ED.toInt(), 0xFF1A365D.toInt())
        Biome.ABISMO_ESTELAR -> createPalette(0xFF1A365D.toInt(), 0xFF2A4365.toInt(), 0xFF3182CE.toInt(), 0xFF2B6CB0.toInt(), 0xFF000000.toInt())
        Biome.GRUTA_DOS_DESEJOS -> createPalette(0xFF702459.toInt(), 0xFFB83280.toInt(), 0xFFF687B3.toInt(), 0xFFED64A6.toInt(), 0xFF4A1239.toInt())
        Biome.LABIRINTO_MAGICO -> createPalette(0xFF553C9A.toInt(), 0xFF805AD5.toInt(), 0xFFE9D8FD.toInt(), 0xFFB794F4.toInt(), 0xFF2D1B4E.toInt())

        // 81-90: Natureza e Aberturas (Verdes e Amarelos)
        Biome.POMARES_ABERTURAS -> createPalette(0xFF276749.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt())
        Biome.VALE_SUBTERRANEO -> createPalette(0xFF22543D.toInt(), 0xFF276749.toInt(), 0xFF48BB78.toInt(), 0xFF38A169.toInt(), 0xFF1C4532.toInt())
        Biome.CAVERNA_DO_SOL -> createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt())
        Biome.TUNEIS_DE_VENTO -> createPalette(0xFF718096.toInt(), 0xFFE2E8F0.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt())
        Biome.JARDIM_SUSPENSO -> createPalette(0xFF2F855A.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt())

        // 91-100: Escuridão e Vazio (Pretos e Cinzas)
        Biome.ABISMO_PROFUNDO -> createPalette(0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt())
        Biome.CAVERNA_DO_VAZIO -> createPalette(0xFF1A202C.toInt(), 0xFF000000.toInt(), 0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt())
        Biome.TUNEIS_SOMBRIOS -> createPalette(0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt())
        Biome.VALE_DAS_SOMBRAS -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFF2D3748.toInt(), 0xFF000000.toInt())
        Biome.NUCLEO_ESCURO -> createPalette(0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt())

        // 101-110: Fogo e Vulcão (Vermelhos e Laranjas)
        Biome.ERA_DINOSSAUROS -> createPalette(0xFF742A2A.toInt(), 0xFFC53030.toInt(), 0xFFFEB2B2.toInt(), 0xFFFC8181.toInt(), 0xFF2D1212.toInt())
        Biome.CAVERNA_DE_LAVA -> createPalette(0xFF9B2C2C.toInt(), 0xFFE53E3E.toInt(), 0xFFFFF5F5.toInt(), 0xFFFEB2B2.toInt(), 0xFF2D1212.toInt())
        Biome.TUNEIS_VULCANICOS -> createPalette(0xFF7B341E.toInt(), 0xFFC05621.toInt(), 0xFFFFFAF0.toInt(), 0xFFF6AD55.toInt(), 0xFF2D1B0E.toInt())
        Biome.FORJA_INFERNAL -> createPalette(0xFF742A2A.toInt(), 0xFF9B2C2C.toInt(), 0xFFE53E3E.toInt(), 0xFFC53030.toInt(), 0xFF2D1212.toInt())
        Biome.NUCLEO_DE_FOGO -> createPalette(0xFFC53030.toInt(), 0xFFE53E3E.toInt(), 0xFFF56565.toInt(), 0xFFFC8181.toInt(), 0xFF2D1212.toInt())

        // 111-120: O Fim da Jornada (Brancos e Dourados)
        Biome.ABISMO_FINAL -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFFFFFFFF.toInt(), 0xFFA0AEC0.toInt(), 0xFF000000.toInt())
        Biome.CAMINHO_DA_ETERNIDADE -> createPalette(0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt())
        Biome.SALOES_DO_DESTINO -> createPalette(0xFF975A16.toInt(), 0xFFECC94B.toInt(), 0xFFFFFFF0.toInt(), 0xFFF6E05E.toInt(), 0xFF2D1B0E.toInt())
        Biome.PORTAL_DO_TEMPO -> createPalette(0xFF44337A.toInt(), 0xFF6B46C1.toInt(), 0xFFD6BCFA.toInt(), 0xFF9F7AEA.toInt(), 0xFF2D1B4E.toInt())
        Biome.O_ULTIMO_PISO -> createPalette(0xFF000000.toInt(), 0xFFD69E2E.toInt(), 0xFFFFFFFF.toInt(), 0xFFECC94B.toInt(), 0xFF000000.toInt())
    }
}

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
        backgroundColor = applyDepthHueShift(palette.backgroundColor, floorNumber)
    )
}
