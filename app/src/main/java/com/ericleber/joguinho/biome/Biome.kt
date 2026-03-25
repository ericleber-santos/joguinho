package com.ericleber.joguinho.biome

enum class Biome(
    val floorRange: IntRange,
    val displayName: String
) {
    // 1-10: Minas e Cavernas Iniciais
    MINA_ABANDONADA(1..2, "Mina Abandonada"),
    CAVERNA_UMIDA(3..4, "Caverna Úmida"),
    TUNEIS_DE_TERRA(5..6, "Túneis de Terra"),
    MINA_DE_CARVAO(7..8, "Mina de Carvão"),
    CAVERNA_DE_CALCARIO(9..10, "Caverna de Calcário"),

    // 11-20: Cristais e Cogumelos
    JARDIM_DE_FUNGOS(11..12, "Jardim de Fungos"),
    CAVERNA_DE_CRISTAL_AZUL(13..14, "Caverna de Cristal Azul"),
    TUNEIS_LUMINESCENTES(15..16, "Túneis Luminescentes"),
    GRUTA_DOS_COGUMELOS(17..18, "Gruta dos Cogumelos"),
    MINA_DE_QUARTZO(19..20, "Mina de Quartzo"),

    // 21-30: Água e Gelo
    RIACHOS_SUBTERRANEOS(21..22, "Riachos Subterrâneos"),
    LAGO_CONGELADO(23..24, "Lago Congelado"),
    CAVERNA_DE_GELO(25..26, "Caverna de Gelo"),
    TUNEIS_AQUATICOS(27..28, "Túneis Aquáticos"),
    ABISMO_AZUL(29..30, "Abismo Azul"),

    // 31-40: Vegetação e Raízes
    PLANTACOES_ABRIGOS(31..32, "Plantações e Abrigos"),
    CAVERNA_DAS_RAIZES(33..34, "Caverna das Raízes"),
    FLORESTA_SUBTERRANEA(35..36, "Floresta Subterrânea"),
    JARDIM_DE_PEDRA(37..38, "Jardim de Pedra"),
    TUNEIS_VERDES(39..40, "Túneis Verdes"),

    // 41-50: Rochas e Minerais
    CONSTRUCOES_ROCHOSAS(41..42, "Construções Rochosas"),
    MINA_DE_FERRO(43..44, "Mina de Ferro"),
    CAVERNA_DE_GRANITO(45..46, "Caverna de Granito"),
    TUNEIS_DE_XISTO(47..48, "Túneis de Xisto"),
    ABISMO_DE_PEDRA(49..50, "Abismo de Pedra"),

    // 51-60: Ouro e Riquezas
    MINA_DE_OURO(51..52, "Mina de Ouro"),
    CAVERNA_DE_ESMERALDA(53..54, "Caverna de Esmeralda"),
    TUNEIS_DE_RUBI(55..56, "Túneis de Rubi"),
    SALOES_DOURADOS(57..58, "Salões Dourados"),
    TESOURO_SUBTERRANEO(59..60, "Tesouro Subterrâneo"),

    // 61-70: Antiguidade e Ruínas
    RUINAS_ANTIGAS(61..62, "Ruínas Antigas"),
    TUMULO_DOS_REIS(63..64, "Túmulo dos Reis"),
    CATACUMBAS_ESQUECIDAS(65..66, "Catacumbas Esquecidas"),
    TEMPLO_ROCHOSO(67..68, "Templo Rochoso"),
    SALOES_DE_MARMORE(69..70, "Salões de Mármore"),

    // 71-80: Magia e Mistério
    CAVERNA_ARCANA(71..72, "Caverna Arcana"),
    TUNEIS_DE_MANA(73..74, "Túneis de Mana"),
    ABISMO_ESTELAR(75..76, "Abismo Estelar"),
    GRUTA_DOS_DESEJOS(77..78, "Gruta dos Desejos"),
    LABIRINTO_MAGICO(79..80, "Labirinto Mágico"),

    // 81-90: Natureza e Aberturas
    POMARES_ABERTURAS(81..82, "Pomares e Aberturas"),
    VALE_SUBTERRANEO(83..84, "Vale Subterrâneo"),
    CAVERNA_DO_SOL(85..86, "Caverna do Sol"),
    TUNEIS_DE_VENTO(87..88, "Túneis de Vento"),
    JARDIM_SUSPENSO(89..90, "Jardim Suspenso"),

    // 91-100: Escuridão e Vazio
    ABISMO_PROFUNDO(91..92, "Abismo Profundo"),
    CAVERNA_DO_VAZIO(93..94, "Caverna do Vazio"),
    TUNEIS_SOMBRIOS(95..96, "Túneis Sombrios"),
    VALE_DAS_SOMBRAS(97..98, "Vale das Sombras"),
    NUCLEO_ESCURO(99..100, "Núcleo Escuro"),

    // 101-110: Fogo e Vulcão
    ERA_DINOSSAUROS(101..102, "Era dos Dinossauros"),
    CAVERNA_DE_LAVA(103..104, "Caverna de Lava"),
    TUNEIS_VULCANICOS(105..106, "Túneis Vulcânicos"),
    FORJA_INFERNAL(107..108, "Forja Infernal"),
    NUCLEO_DE_FOGO(109..110, "Núcleo de Fogo"),

    // 111-120: O Fim da Jornada
    ABISMO_FINAL(111..112, "Abismo Final"),
    CAMINHO_DA_ETERNIDADE(113..114, "Caminho da Eternidade"),
    SALOES_DO_DESTINO(115..116, "Salões do Destino"),
    PORTAL_DO_TEMPO(117..118, "Portal do Tempo"),
    O_ULTIMO_PISO(119..120, "O Último Piso");

    companion object {
        fun fromFloor(floorNumber: Int): Biome =
            entries.firstOrNull { floorNumber in it.floorRange } ?: MINA_ABANDONADA
    }
}
