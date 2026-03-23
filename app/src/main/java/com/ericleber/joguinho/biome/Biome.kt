package com.ericleber.joguinho.biome

enum class Biome(
    val floorRange: IntRange,
    val displayName: String
) {
    MINA_ABANDONADA(1..20, "Mina Abandonada"),
    RIACHOS_SUBTERRANEOS(21..40, "Riachos Subterrâneos"),
    PLANTACOES_ABRIGOS(41..60, "Plantações e Abrigos"),
    CONSTRUCOES_ROCHOSAS(61..80, "Construções Rochosas"),
    POMARES_ABERTURAS(81..100, "Pomares e Aberturas"),
    ERA_DINOSSAUROS(101..120, "Era dos Dinossauros");

    companion object {
        fun fromFloor(floorNumber: Int): Biome =
            entries.first { floorNumber in it.floorRange }
    }
}
