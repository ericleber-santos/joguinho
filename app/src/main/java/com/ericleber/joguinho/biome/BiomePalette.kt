package com.ericleber.joguinho.biome

/**
 * Paleta visual completa por bioma — estilo Stardew Valley cave.
 *
 * Princípios:
 * - Tons terrosos quentes para chão/paredes (marrons profundos)
 * - Pedras com cinza-azulado para variação
 * - Iluminação âmbar/dourada para tochas e cristais
 * - Sombras em roxo-escuro ou marrom-escuro (nunca preto puro)
 * - Flora subterrânea: azuis, roxos, cianos vibrantes
 */
data class BiomePalette(
    // --- Parede ---
    val wallColor: Int,         // face frontal (cor base da pedra)
    val wallTopColor: Int,      // topo (mais claro — luz de cima)
    val wallShadowColor: Int,   // face lateral direita (sombra profunda)
    val wallDetailColor: Int,   // veios/cristais embutidos na parede

    // --- Chão ---
    val floorColor: Int,        // tile base
    val floorVariant1: Int,     // variante com rachadura
    val floorVariant2: Int,     // variante com musgo/umidade
    val floorVariant3: Int,     // variante mais escura (depressão)
    val floorEdgeColor: Int,    // borda sutil entre tiles

    // --- Acento e luz ---
    val accentColor: Int,       // cristal/tocha/mineral brilhante
    val ambientLight: Int,      // cor da luz ambiente do bioma
    val glowColor: Int,         // halo de luz (cogumelos, cristais)
    val particleColor: Int,     // partículas (poeira, faíscas)

    // --- Flora ---
    val mushroomColor: Int,     // cogumelos do bioma
    val mushroomCapColor: Int,  // chapéu do cogumelo
    val crystalColor: Int,      // cristais decorativos
    val mossColor: Int,         // musgo/líquen nas paredes

    // --- Fundo ---
    val backgroundColor: Int    // fundo — roxo-escuro ou marrom-escuro (nunca preto puro)
)

/**
 * Paletas por bioma — estética "caverna encantada com segredos a descobrir".
 * Referência: ConcernedApe (Stardew Valley) adaptado para roguelike dark/fantasia.
 */
val BIOME_PALETTES: Map<Biome, BiomePalette> = mapOf(

    // -------------------------------------------------------------------------
    // Andares 1–20: Mina Abandonada
    // Tons: marrom-pedra quente, tocha âmbar, veios de ouro
    // -------------------------------------------------------------------------
    Biome.MINA_ABANDONADA to BiomePalette(
        // Parede — pedra marrom-acinzentada com blocos visíveis
        wallColor       = 0xFF7A6050.toInt(),  // face frontal: marrom-pedra médio
        wallTopColor    = 0xFFB09070.toInt(),  // topo: marrom claro — contraste forte com chão
        wallShadowColor = 0xFF3A2818.toInt(),  // sombra lateral: marrom muito escuro
        wallDetailColor = 0xFFD97706.toInt(),  // veio de ouro âmbar

        // Chão — terra ESCURA, bem mais escura que a parede
        floorColor      = 0xFF2A1E14.toInt(),  // terra muito escura — quase preta com tom marrom
        floorVariant1   = 0xFF251A10.toInt(),  // rachadura — ainda mais escura
        floorVariant2   = 0xFF28221A.toInt(),  // musgo — tom levemente esverdeado
        floorVariant3   = 0xFF2E2018.toInt(),  // umidade — levemente mais clara
        floorEdgeColor  = 0xFF1A100A.toInt(),  // borda sutil

        // Acento e luz — tocha âmbar quente
        accentColor     = 0xFFF59E0B.toInt(),
        ambientLight    = 0xFFD97706.toInt(),
        glowColor       = 0xFFFCD34D.toInt(),
        particleColor   = 0xFFFBBF24.toInt(),

        // Flora
        mushroomColor   = 0xFF92400E.toInt(),
        mushroomCapColor= 0xFFB45309.toInt(),
        crystalColor    = 0xFFED8936.toInt(),
        mossColor       = 0xFF3D5A2A.toInt(),

        backgroundColor = 0xFF0F0806.toInt()   // fundo quase preto
    ),

    // -------------------------------------------------------------------------
    // Andares 21–40: Riachos Subterrâneos
    // Tons: pedra azul-fria, água ciano, cogumelos azuis luminescentes
    // -------------------------------------------------------------------------
    Biome.RIACHOS_SUBTERRANEOS to BiomePalette(
        wallColor       = 0xFF4A4E5A.toInt(),  // pedra cinza-azulada
        wallTopColor    = 0xFF6B7280.toInt(),  // topo: cinza médio
        wallShadowColor = 0xFF2D3142.toInt(),  // sombra: azul-escuro
        wallDetailColor = 0xFF06B6D4.toInt(),  // veio de água ciano

        floorColor      = 0xFF374151.toInt(),  // pedra úmida azul-escura
        floorVariant1   = 0xFF2D3748.toInt(),  // rachadura — mais escura
        floorVariant2   = 0xFF2A4A4A.toInt(),  // poça d'água — ciano escuro
        floorVariant3   = 0xFF3D4A5A.toInt(),  // umidade — azul médio
        floorEdgeColor  = 0xFF1E2A38.toInt(),

        accentColor     = 0xFF06B6D4.toInt(),  // água ciano brilhante
        ambientLight    = 0xFF0891B2.toInt(),
        glowColor       = 0xFF67E8F9.toInt(),  // halo ciano claro
        particleColor   = 0xFF7DD3FC.toInt(),  // gotículas azuis

        mushroomColor   = 0xFF1D4ED8.toInt(),  // haste azul-escura
        mushroomCapColor= 0xFF3B82F6.toInt(),  // chapéu azul luminescente
        crystalColor    = 0xFF06B6D4.toInt(),  // cristal ciano
        mossColor       = 0xFF164E63.toInt(),  // líquen azul-escuro

        backgroundColor = 0xFF0F172A.toInt()   // azul-marinho profundo
    ),

    // -------------------------------------------------------------------------
    // Andares 41–60: Plantações e Abrigos
    // Tons: terra verde, fungos roxos, raízes expostas
    // -------------------------------------------------------------------------
    Biome.PLANTACOES_ABRIGOS to BiomePalette(
        wallColor       = 0xFF3D4A2E.toInt(),  // pedra com musgo verde
        wallTopColor    = 0xFF526640.toInt(),  // topo: verde-médio
        wallShadowColor = 0xFF252E1C.toInt(),  // sombra: verde muito escuro
        wallDetailColor = 0xFF8B5CF6.toInt(),  // raiz roxa brilhante

        floorColor      = 0xFF3A4228.toInt(),  // terra com musgo
        floorVariant1   = 0xFF2E3820.toInt(),  // rachadura escura
        floorVariant2   = 0xFF4A5A30.toInt(),  // musgo denso — mais verde
        floorVariant3   = 0xFF3D4A38.toInt(),  // umidade esverdeada
        floorEdgeColor  = 0xFF1E2814.toInt(),

        accentColor     = 0xFF7C3AED.toInt(),  // roxo vibrante (fungos)
        ambientLight    = 0xFF6D28D9.toInt(),
        glowColor       = 0xFFA78BFA.toInt(),  // halo lilás
        particleColor   = 0xFF8B5CF6.toInt(),  // esporos roxos

        mushroomColor   = 0xFF5B21B6.toInt(),  // haste roxa escura
        mushroomCapColor= 0xFFA855F7.toInt(),  // chapéu roxo luminescente
        crystalColor    = 0xFF7C3AED.toInt(),  // cristal roxo
        mossColor       = 0xFF365314.toInt(),  // musgo verde-escuro

        backgroundColor = 0xFF1A1A2E.toInt()   // roxo-escuro profundo
    ),

    // -------------------------------------------------------------------------
    // Andares 61–80: Construções Rochosas
    // Tons: pedra cinza fria, cristais brancos/azuis, poeira
    // -------------------------------------------------------------------------
    Biome.CONSTRUCOES_ROCHOSAS to BiomePalette(
        wallColor       = 0xFF4A4E5A.toInt(),  // pedra cinza-azulada
        wallTopColor    = 0xFF6B7280.toInt(),  // topo: cinza claro
        wallShadowColor = 0xFF1F2937.toInt(),  // sombra: cinza muito escuro
        wallDetailColor = 0xFF9CA3AF.toInt(),  // veio de quartzo claro

        floorColor      = 0xFF374151.toInt(),  // pedra cinza escura
        floorVariant1   = 0xFF2D3748.toInt(),  // rachadura profunda
        floorVariant2   = 0xFF3D4A5A.toInt(),  // pedra polida
        floorVariant3   = 0xFF424A58.toInt(),  // pedra com poeira
        floorEdgeColor  = 0xFF1F2937.toInt(),

        accentColor     = 0xFF9CA3AF.toInt(),  // cristal quartzo
        ambientLight    = 0xFF6B7280.toInt(),
        glowColor       = 0xFFE5E7EB.toInt(),  // halo branco-frio
        particleColor   = 0xFFD1D5DB.toInt(),  // poeira cinza

        mushroomColor   = 0xFF4B5563.toInt(),  // haste cinza
        mushroomCapColor= 0xFF9CA3AF.toInt(),  // chapéu cinza-claro
        crystalColor    = 0xFFBAC8D3.toInt(),  // cristal azul-frio
        mossColor       = 0xFF374151.toInt(),  // líquen cinza

        backgroundColor = 0xFF111827.toInt()   // cinza-azulado muito escuro
    ),

    // -------------------------------------------------------------------------
    // Andares 81–100: Pomares e Aberturas
    // Tons: terra dourada, cogumelos ciano, cristais verdes
    // -------------------------------------------------------------------------
    Biome.POMARES_ABERTURAS to BiomePalette(
        wallColor       = 0xFF4A3728.toInt(),  // pedra marrom-quente
        wallTopColor    = 0xFF6B5040.toInt(),  // topo: marrom médio
        wallShadowColor = 0xFF2E1E14.toInt(),  // sombra: marrom escuro
        wallDetailColor = 0xFF10B981.toInt(),  // veio esmeralda

        floorColor      = 0xFF3D3020.toInt(),  // terra dourada escura
        floorVariant1   = 0xFF2E2418.toInt(),  // rachadura
        floorVariant2   = 0xFF3A4028.toInt(),  // musgo dourado
        floorVariant3   = 0xFF4A3C28.toInt(),  // terra clara
        floorEdgeColor  = 0xFF1E1810.toInt(),

        accentColor     = 0xFF10B981.toInt(),  // esmeralda brilhante
        ambientLight    = 0xFF059669.toInt(),
        glowColor       = 0xFF6EE7B7.toInt(),  // halo verde-claro
        particleColor   = 0xFF34D399.toInt(),  // partículas verdes

        mushroomColor   = 0xFF065F46.toInt(),  // haste verde-escura
        mushroomCapColor= 0xFF06B6D4.toInt(),  // chapéu ciano luminescente
        crystalColor    = 0xFF10B981.toInt(),  // cristal esmeralda
        mossColor       = 0xFF14532D.toInt(),  // musgo verde profundo

        backgroundColor = 0xFF0D1F17.toInt()   // verde-escuro profundo
    ),

    // -------------------------------------------------------------------------
    // Andares 101–120: Era dos Dinossauros
    // Tons: pedra vulcânica, lava vermelha, cristais âmbar
    // -------------------------------------------------------------------------
    Biome.ERA_DINOSSAUROS to BiomePalette(
        wallColor       = 0xFF4A2018.toInt(),  // pedra vulcânica marrom-vermelha
        wallTopColor    = 0xFF6B3020.toInt(),  // topo: vermelho-escuro
        wallShadowColor = 0xFF2E0E08.toInt(),  // sombra: quase preta com tom vermelho
        wallDetailColor = 0xFFEF4444.toInt(),  // veio de lava vermelho

        floorColor      = 0xFF3D1E10.toInt(),  // terra vulcânica escura
        floorVariant1   = 0xFF2E1408.toInt(),  // rachadura com brilho de lava
        floorVariant2   = 0xFF4A2818.toInt(),  // pedra avermelhada
        floorVariant3   = 0xFF3A2010.toInt(),  // cinza vulcânico
        floorEdgeColor  = 0xFF1E0C06.toInt(),

        accentColor     = 0xFFEF4444.toInt(),  // lava vermelha
        ambientLight    = 0xFFDC2626.toInt(),
        glowColor       = 0xFFFCA5A5.toInt(),  // halo vermelho-claro
        particleColor   = 0xFFF97316.toInt(),  // faíscas laranja

        mushroomColor   = 0xFF7F1D1D.toInt(),  // haste vermelho-escura
        mushroomCapColor= 0xFFEF4444.toInt(),  // chapéu vermelho brilhante
        crystalColor    = 0xFFF59E0B.toInt(),  // cristal âmbar
        mossColor       = 0xFF451A03.toInt(),  // líquen marrom-escuro

        backgroundColor = 0xFF1C0505.toInt()   // vermelho-escuro profundo
    )
)
