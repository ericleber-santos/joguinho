package com.ericleber.joguinho.biome

data class BiomePalette(
    val wallColor: Int,
    val floorColor: Int,
    val accentColor: Int,
    val ambientLight: Int,
    val particleColor: Int,
    val backgroundColor: Int
)

val BIOME_PALETTES: Map<Biome, BiomePalette> = mapOf(
    Biome.MINA_ABANDONADA to BiomePalette(
        wallColor       = 0xFF4A3728.toInt(),
        floorColor      = 0xFF2C1F14.toInt(),
        accentColor     = 0xFFD4A017.toInt(),
        ambientLight    = 0xFFFF8C00.toInt(),
        particleColor   = 0xFF8B6914.toInt(),
        backgroundColor = 0xFF1A1008.toInt()
    ),
    Biome.RIACHOS_SUBTERRANEOS to BiomePalette(
        wallColor       = 0xFF2E4A3E.toInt(),
        floorColor      = 0xFF1A3028.toInt(),
        accentColor     = 0xFF4FC3F7.toInt(),
        ambientLight    = 0xFF00BCD4.toInt(),
        particleColor   = 0xFF81D4FA.toInt(),
        backgroundColor = 0xFF0D1F1A.toInt()
    ),
    Biome.PLANTACOES_ABRIGOS to BiomePalette(
        wallColor       = 0xFF3D5A2A.toInt(),
        floorColor      = 0xFF2A3D1A.toInt(),
        accentColor     = 0xFF8BC34A.toInt(),
        ambientLight    = 0xFFAED581.toInt(),
        particleColor   = 0xFF558B2F.toInt(),
        backgroundColor = 0xFF1A2810.toInt()
    ),
    Biome.CONSTRUCOES_ROCHOSAS to BiomePalette(
        wallColor       = 0xFF5D4E37.toInt(),
        floorColor      = 0xFF3D3020.toInt(),
        accentColor     = 0xFFBDBDBD.toInt(),
        ambientLight    = 0xFF9E9E9E.toInt(),
        particleColor   = 0xFFD7CCC8.toInt(),
        backgroundColor = 0xFF1C1510.toInt()
    ),
    Biome.POMARES_ABERTURAS to BiomePalette(
        wallColor       = 0xFF4A6741.toInt(),
        floorColor      = 0xFF2D4A28.toInt(),
        accentColor     = 0xFFFF8F00.toInt(),
        ambientLight    = 0xFFFFF176.toInt(),
        particleColor   = 0xFFFFCC02.toInt(),
        backgroundColor = 0xFF1A2E15.toInt()
    ),
    Biome.ERA_DINOSSAUROS to BiomePalette(
        wallColor       = 0xFF6D4C41.toInt(),
        floorColor      = 0xFF4A2E28.toInt(),
        accentColor     = 0xFF8BC34A.toInt(),
        ambientLight    = 0xFFFF7043.toInt(),
        particleColor   = 0xFFBCAAA4.toInt(),
        backgroundColor = 0xFF2A1510.toInt()
    )
)
