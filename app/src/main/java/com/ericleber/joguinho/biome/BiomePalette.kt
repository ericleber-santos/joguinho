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
 * Paletas por bioma — Geradas dinamicamente para os 60 biomas.
 */
val BIOME_PALETTES: Map<Biome, BiomePalette> = Biome.entries.associateWith { biome ->
    when (biome) {
        // 1-10: Minas e Cavernas Iniciais
        Biome.MINA_ABANDONADA -> createPalette(0xFF3A3028.toInt(), 0xFFC4A882.toInt(), 0xFFF59E0B.toInt(), 0xFFB45309.toInt(), 0xFF0F0806.toInt(),
            wallDetailType = WallDetailType.MOSS, floorVariantCount = 2, hasAmbush = true, hasDrips = true)
        Biome.CAVERNA_UMIDA -> createPalette(0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFF63B3ED.toInt(), 0xFF4299E1.toInt(), 0xFF1A202C.toInt(),
            wallDetailType = WallDetailType.MOSS, floorVariantCount = 3, hasDrips = true, hasAmbush = true)
        Biome.TUNEIS_DE_TERRA -> createPalette(0xFF4A3728.toInt(), 0xFF8B4513.toInt(), 0xFFCD853F.toInt(), 0xFFD2691E.toInt(), 0xFF1E1408.toInt(),
            wallDetailType = WallDetailType.MOSS, floorVariantCount = 2)
        Biome.MINA_DE_CARVAO -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF718096.toInt(), 0xFF4A5568.toInt(), 0xFF000000.toInt(),
            floorVariantCount = 1, hasAmbush = true)
        Biome.CAVERNA_DE_CALCARIO -> createPalette(0xFF718096.toInt(), 0xFFE2E8F0.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt(),
            wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 2)

        // 11-20: Cristais e Cogumelos
        Biome.JARDIM_DE_FUNGOS -> createPalette(0xFF44337A.toInt(), 0xFF6B46C1.toInt(), 0xFFD6BCFA.toInt(), 0xFF9F7AEA.toInt(), 0xFF2D1B4E.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4, hasDrips = true)
        Biome.CAVERNA_DE_CRISTAL_AZUL -> createPalette(0xFF2A4365.toInt(), 0xFF3182CE.toInt(), 0xFF90CDF4.toInt(), 0xFF63B3ED.toInt(), 0xFF1A365D.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 3)
        Biome.TUNEIS_LUMINESCENTES -> createPalette(0xFF234E52.toInt(), 0xFF319795.toInt(), 0xFF81E6D9.toInt(), 0xFF4FD1C5.toInt(), 0xFF1D3131.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 3, hasAmbush = true)
        Biome.MINA_DE_QUARTZO -> createPalette(0xFF4A5568.toInt(), 0xFFEDF2F7.toInt(), 0xFFFFFFFF.toInt(), 0xFFE2E8F0.toInt(), 0xFF1A202C.toInt(),
            wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 2)
        Biome.GRUTA_DOS_COGUMELOS -> createPalette(0xFF702459.toInt(), 0xFFB83280.toInt(), 0xFFF687B3.toInt(), 0xFFED64A6.toInt(), 0xFF4A1239.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4, hasDrips = true)

        // 21-30: Água e Gelo
        Biome.LAGO_CONGELADO -> createPalette(0xFF2A4365.toInt(), 0xFFEBF8FF.toInt(), 0xFFBEE3F8.toInt(), 0xFF90CDF4.toInt(), 0xFF1A365D.toInt(),
            wallDetailType = WallDetailType.ICE_DRIP, hasDrips = true, floorVariantCount = 2)
        Biome.CAVERNA_DE_GELO -> createPalette(0xFFEBF8FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFBEE3F8.toInt(), 0xFF90CDF4.toInt(), 0xFF2A4365.toInt(),
            wallDetailType = WallDetailType.ICE_DRIP, hasDrips = true, floorVariantCount = 2)
        Biome.RIACHOS_SUBTERRANEOS -> createPalette(0xFF2C5282.toInt(), 0xFF4299E1.toInt(), 0xFFBEE3F8.toInt(), 0xFF90CDF4.toInt(), 0xFF1A365D.toInt(),
            wallDetailType = WallDetailType.MOSS, hasDrips = true, floorVariantCount = 3)
        Biome.TUNEIS_AQUATICOS -> createPalette(0xFF2B6CB0.toInt(), 0xFF3182CE.toInt(), 0xFF63B3ED.toInt(), 0xFF4299E1.toInt(), 0xFF1A365D.toInt(),
            hasDrips = true, hasAmbush = true, floorVariantCount = 3)
        Biome.ABISMO_AZUL -> createPalette(0xFF1A365D.toInt(), 0xFF2A4365.toInt(), 0xFF3182CE.toInt(), 0xFF2B6CB0.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.VOID_DARK, hasAmbush = true)

        // 31-40: Vegetação e Raízes
        Biome.PLANTACOES_ABRIGOS -> createPalette(0xFF276749.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4)
        Biome.CAVERNA_DAS_RAIZES -> createPalette(0xFF38A169.toInt(), 0xFF2F855A.toInt(), 0xFFC6F6D5.toInt(), 0xFF9AE6B4.toInt(), 0xFF1C4532.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.MOSS, hasDrips = true, floorVariantCount = 4)
        Biome.FLORESTA_SUBTERRANEA -> createPalette(0xFF22543D.toInt(), 0xFF276749.toInt(), 0xFF48BB78.toInt(), 0xFF38A169.toInt(), 0xFF1C4532.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4, hasAmbush = true)
        Biome.JARDIM_DE_PEDRA -> createPalette(0xFF2D3748.toInt(), 0xFF2F855A.toInt(), 0xFF68D391.toInt(), 0xFF48BB78.toInt(), 0xFF1A202C.toInt(),
            wallDetailType = WallDetailType.MOSS, floorVariantCount = 3)
        Biome.TUNEIS_VERDES -> createPalette(0xFF2F855A.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt(),
            wallDetailType = WallDetailType.MOSS, floorVariantCount = 3, hasAmbush = true)

        // 41-50: Rochas e Minerais
        Biome.CONSTRUCOES_ROCHOSAS -> createPalette(0xFF4A5568.toInt(), 0xFF718096.toInt(), 0xFFA0AEC0.toInt(), 0xFFCBD5E0.toInt(), 0xFF1A202C.toInt(),
            floorVariantCount = 2, hasAmbush = true)
        Biome.MINA_DE_FERRO -> createPalette(0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFFE2E8F0.toInt(), 0xFFA0AEC0.toInt(), 0xFF1A202C.toInt(),
            wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 2)
        Biome.CAVERNA_DE_GRANITO -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF718096.toInt(), 0xFF4A5568.toInt(), 0xFF000000.toInt(),
            hasAmbush = true, floorVariantCount = 1)
        Biome.TUNEIS_DE_XISTO -> createPalette(0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF4A5568.toInt(), 0xFF2D3748.toInt(), 0xFF000000.toInt(),
            floorVariantCount = 1)
        Biome.ABISMO_DE_PEDRA -> createPalette(0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.VOID_DARK, hasAmbush = true)

        // 51-60: Ouro e Riquezas
        Biome.MINA_DE_OURO -> createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt(),
            wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 3, hasAmbush = true)
        Biome.CAVERNA_DE_ESMERALDA -> createPalette(0xFF22543D.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 3)
        Biome.TUNEIS_DE_RUBI -> createPalette(0xFF742A2A.toInt(), 0xFFC53030.toInt(), 0xFFFEB2B2.toInt(), 0xFFFC8181.toInt(), 0xFF2D1212.toInt(),
            wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 2, hasAmbush = true)
        Biome.SALOES_DOURADOS -> createPalette(0xFF975A16.toInt(), 0xFFECC94B.toInt(), 0xFFFFFFF0.toInt(), 0xFFF6E05E.toInt(), 0xFF2D1B0E.toInt(),
            wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 3)
        Biome.TESOURO_SUBTERRANEO -> createPalette(0xFFB7791F.toInt(), 0xFFF6E05E.toInt(), 0xFFFFFFF0.toInt(), 0xFFFAF089.toInt(), 0xFF2D1B0E.toInt(),
            wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 4, hasAmbush = true)

        // 61-70: Ruínas e Antiguidade
        Biome.RUINAS_ANTIGAS -> createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt(),
            wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 3, hasAmbush = true)
        Biome.TUMULO_DOS_REIS -> createPalette(0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFFA0AEC0.toInt(), 0xFF718096.toInt(), 0xFF1A202C.toInt(),
            wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 2, hasAmbush = true)
        Biome.CATACUMBAS_ESQUECIDAS -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFF2D3748.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.VOID_DARK, wallDetailType = WallDetailType.MOSS, hasDrips = true, hasAmbush = true)
        Biome.TEMPLO_ROCHOSO -> createPalette(0xFF4A5568.toInt(), 0xFF718096.toInt(), 0xFFA0AEC0.toInt(), 0xFFCBD5E0.toInt(), 0xFF1A202C.toInt(),
            wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 3)
        Biome.SALOES_DE_MARMORE -> createPalette(0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt(),
            floorVariantCount = 3)

        // 71-80: Magia e Mistério
        Biome.CAVERNA_ARCANA -> createPalette(0xFF44337A.toInt(), 0xFF6B46C1.toInt(), 0xFFD6BCFA.toInt(), 0xFF9F7AEA.toInt(), 0xFF2D1B4E.toInt(),
            lightingMode = LightingMode.MOONLIGHT, wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 4, hasAmbush = true)
        Biome.TUNEIS_DE_MANA -> createPalette(0xFF2A4365.toInt(), 0xFF3182CE.toInt(), 0xFF90CDF4.toInt(), 0xFF63B3ED.toInt(), 0xFF1A365D.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.CRYSTAL_VEIN, floorVariantCount = 3)
        Biome.ABISMO_ESTELAR -> createPalette(0xFF1A365D.toInt(), 0xFF2A4365.toInt(), 0xFF3182CE.toInt(), 0xFF2B6CB0.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.MOONLIGHT, hasAmbush = true)
        Biome.GRUTA_DOS_DESEJOS -> createPalette(0xFF702459.toInt(), 0xFFB83280.toInt(), 0xFFF687B3.toInt(), 0xFFED64A6.toInt(), 0xFF4A1239.toInt(),
            lightingMode = LightingMode.BIOLUMINESCENT, wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 4)
        Biome.LABIRINTO_MAGICO -> createPalette(0xFF553C9A.toInt(), 0xFF805AD5.toInt(), 0xFFE9D8FD.toInt(), 0xFFB794F4.toInt(), 0xFF2D1B4E.toInt(),
            lightingMode = LightingMode.MOONLIGHT, wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 4, hasAmbush = true)

        // 81-90: Superfície Aberta
        Biome.POMARES_ABERTURAS -> createPalette(0xFF276749.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt(),
            lightingMode = LightingMode.DAYLIGHT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4)
        Biome.VALE_SUBTERRANEO -> createPalette(0xFF22543D.toInt(), 0xFF276749.toInt(), 0xFF48BB78.toInt(), 0xFF38A169.toInt(), 0xFF1C4532.toInt(),
            lightingMode = LightingMode.DAYLIGHT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4)
        Biome.CAVERNA_DO_SOL -> createPalette(0xFF744210.toInt(), 0xFFD69E2E.toInt(), 0xFFFAF089.toInt(), 0xFFECC94B.toInt(), 0xFF2D1B0E.toInt(),
            lightingMode = LightingMode.DAYLIGHT, floorVariantCount = 3)
        Biome.TUNEIS_DE_VENTO -> createPalette(0xFF718096.toInt(), 0xFFE2E8F0.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt(),
            lightingMode = LightingMode.DAYLIGHT, floorVariantCount = 3)
        Biome.JARDIM_SUSPENSO -> createPalette(0xFF2F855A.toInt(), 0xFF38A169.toInt(), 0xFF9AE6B4.toInt(), 0xFF68D391.toInt(), 0xFF1C4532.toInt(),
            lightingMode = LightingMode.DAYLIGHT, wallDetailType = WallDetailType.MOSS, floorVariantCount = 4, hasAmbush = true)

        // 91-100: Abismo do Vazio
        Biome.ABISMO_PROFUNDO -> createPalette(0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.VOID_DARK, hasAmbush = true)
        Biome.CAVERNA_DO_VAZIO -> createPalette(0xFF1A202C.toInt(), 0xFF000000.toInt(), 0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.VOID_DARK, hasAmbush = true)
        Biome.TUNEIS_SOMBRIOS -> createPalette(0xFF2D3748.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.VOID_DARK, hasAmbush = true)
        Biome.VALE_DAS_SOMBRAS -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFF4A5568.toInt(), 0xFF2D3748.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.VOID_DARK, hasAmbush = true)
        Biome.NUCLEO_ESCURO -> createPalette(0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF1A202C.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.VOID_DARK, hasAmbush = true)

        // 101-110: Núcleo de Fogo
        Biome.ERA_DINOSSAUROS -> createPalette(0xFF742A2A.toInt(), 0xFFC53030.toInt(), 0xFFFEB2B2.toInt(), 0xFFFC8181.toInt(), 0xFF2D1212.toInt(),
            lightingMode = LightingMode.LAVA_GLOW, wallDetailType = WallDetailType.EMBER, floorVariantCount = 2, hasAmbush = true)
        Biome.CAVERNA_DE_LAVA -> createPalette(0xFF9B2C2C.toInt(), 0xFFE53E3E.toInt(), 0xFFFFF5F5.toInt(), 0xFFFEB2B2.toInt(), 0xFF2D1212.toInt(),
            lightingMode = LightingMode.LAVA_GLOW, wallDetailType = WallDetailType.EMBER, floorVariantCount = 2)
        Biome.TUNEIS_VULCANICOS -> createPalette(0xFF7B341E.toInt(), 0xFFC05621.toInt(), 0xFFFFFAF0.toInt(), 0xFFF6AD55.toInt(), 0xFF2D1B0E.toInt(),
            lightingMode = LightingMode.LAVA_GLOW, wallDetailType = WallDetailType.EMBER, floorVariantCount = 2, hasAmbush = true)
        Biome.FORJA_INFERNAL -> createPalette(0xFF742A2A.toInt(), 0xFF9B2C2C.toInt(), 0xFFE53E3E.toInt(), 0xFFC53030.toInt(), 0xFF2D1212.toInt(),
            lightingMode = LightingMode.LAVA_GLOW, wallDetailType = WallDetailType.EMBER, floorVariantCount = 1, hasAmbush = true)
        Biome.NUCLEO_DE_FOGO -> createPalette(0xFFC53030.toInt(), 0xFFE53E3E.toInt(), 0xFFF56565.toInt(), 0xFFFC8181.toInt(), 0xFF2D1212.toInt(),
            lightingMode = LightingMode.LAVA_GLOW, wallDetailType = WallDetailType.EMBER, floorVariantCount = 1, hasAmbush = true)

        // 111-120: O Cosmos
        Biome.ABISMO_FINAL -> createPalette(0xFF1A202C.toInt(), 0xFF2D3748.toInt(), 0xFFFFFFFF.toInt(), 0xFFA0AEC0.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.MOONLIGHT, wallDetailType = WallDetailType.RUNE_GLOW, hasAmbush = true)
        Biome.CAMINHO_DA_ETERNIDADE -> createPalette(0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD5E0.toInt(), 0xFFA0AEC0.toInt(), 0xFF2D3748.toInt(),
            lightingMode = LightingMode.MOONLIGHT, floorVariantCount = 3)
        Biome.SALOES_DO_DESTINO -> createPalette(0xFF975A16.toInt(), 0xFFECC94B.toInt(), 0xFFFFFFF0.toInt(), 0xFFF6E05E.toInt(), 0xFF2D1B0E.toInt(),
            lightingMode = LightingMode.MOONLIGHT, wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 4)
        Biome.PORTAL_DO_TEMPO -> createPalette(0xFF44337A.toInt(), 0xFF6B46C1.toInt(), 0xFFD6BCFA.toInt(), 0xFF9F7AEA.toInt(), 0xFF2D1B4E.toInt(),
            lightingMode = LightingMode.MOONLIGHT, wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 4, hasAmbush = true)
        Biome.O_ULTIMO_PISO -> createPalette(0xFF000000.toInt(), 0xFFD69E2E.toInt(), 0xFFFFFFFF.toInt(), 0xFFECC94B.toInt(), 0xFF000000.toInt(),
            lightingMode = LightingMode.MOONLIGHT, wallDetailType = WallDetailType.RUNE_GLOW, floorVariantCount = 4, hasAmbush = true)
        else -> createPalette(0xFF3A3028.toInt(), 0xFFC4A882.toInt(), 0xFFF59E0B.toInt(), 0xFFB45309.toInt(), 0xFF0F0806.toInt())
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
        backgroundColor = applyDepthHueShift(palette.backgroundColor, floorNumber),
        // Campos de identidade: nunca sofrem HueShift
        lightingMode = palette.lightingMode,
        wallDetailType = palette.wallDetailType,
        floorVariantCount = palette.floorVariantCount,
        hasDrips = palette.hasDrips,
        hasAmbush = palette.hasAmbush
    )
}

