package com.ericleber.joguinho.biome

import android.graphics.Color
import com.ericleber.joguinho.renderer.PortalColors

/**
 * Os 8 Mundos do Aventuras com Spike.
 *
 * Cada BiomeWorld agrupa biomas com a mesma identidade visual, atmosférica
 * e de gameplay. Define:
 *  - [portalColors]    cores primária e de acento do portal interdimensional
 *  - [displayEmoji]    emoji/label exibido acima do portal
 *  - [displayName]     nome completo do mundo
 *  - [lightingMode]    modo de iluminação do LightingSystem
 *  - [minCorridorWidth] largura mínima de corredor gerada pelo BSP (em tiles)
 */
enum class BiomeWorld(
    val displayName: String,
    val displayEmoji: String,
    val portalColors: PortalColors,
    val lightingMode: LightingMode,
    val minCorridorWidth: Int,
    val isOpenAir: Boolean = false // Se verdadeiro, o teto é transparente/céu visível
) {

    /** Andares 1–20: Minas e Cavernas Iniciais */
    ENTRANHAS(
        displayName     = "Entranhas da Terra",
        displayEmoji    = "⛏️",
        portalColors    = PortalColors(
            primary = Color.rgb(80, 160, 220),   // Azul cristal
            accent  = Color.rgb(140, 210, 255)
        ),
        lightingMode    = LightingMode.SUBTERRANEAN,
        minCorridorWidth = 2
    ),

    /** Andares 11–20: Árvores gigantes que funcionam como paredes */
    FLORESTA_DE_ARVORES(
        displayName     = "Floresta de Gigantes",
        displayEmoji    = "🌳",
        portalColors    = PortalColors(
            primary = Color.rgb(60, 200, 100),   // Verde folha
            accent  = Color.rgb(180, 255, 140)
        ),
        lightingMode    = LightingMode.DAYLIGHT,
        minCorridorWidth = 4,
        isOpenAir       = true
    ),

    /** Andares 21–30: Água, Gelo e Riachos */
    ABISMOS_AQUATICOS(
        displayName     = "Abismos Aquáticos",
        displayEmoji    = "🌊",
        portalColors    = PortalColors(
            primary = Color.rgb(30, 120, 200),   // Azul-água
            accent  = Color.rgb(100, 200, 240)
        ),
        lightingMode    = LightingMode.SUBTERRANEAN,
        minCorridorWidth = 2
    ),

    /** Andares 31–40: Raízes, Vegetação e Fungos */
    JARDIM_PROFUNDO(
        displayName     = "Jardim Profundo",
        displayEmoji    = "🌿",
        portalColors    = PortalColors(
            primary = Color.rgb(60, 160, 60),    // Verde musgo
            accent  = Color.rgb(140, 230, 100)
        ),
        lightingMode    = LightingMode.BIOLUMINESCENT,
        minCorridorWidth = 3
    ),

    /** Andares 41–60: Rochas, Minérios, Ouro e Riquezas */
    MINAS_RIQUEZAS(
        displayName     = "Minas das Riquezas",
        displayEmoji    = "💎",
        portalColors    = PortalColors(
            primary = Color.rgb(220, 170, 30),   // Dourado âmbar
            accent  = Color.rgb(255, 220, 80)
        ),
        lightingMode    = LightingMode.SUBTERRANEAN,
        minCorridorWidth = 2
    ),

    /** Andares 61–70: Ruínas e Templos Ancestrais */
    RUINAS_ANCESTRAIS(
        displayName     = "Ruínas Ancestrais",
        displayEmoji    = "🏛️",
        portalColors    = PortalColors(
            primary = Color.rgb(190, 140, 70),   // Pedra dourada
            accent  = Color.rgb(240, 200, 120)
        ),
        lightingMode    = LightingMode.SUBTERRANEAN,
        minCorridorWidth = 3
    ),

    /** Andares 71–80: Magia, Mana e Abismo Estelar */
    REINO_DA_MAGIA(
        displayName     = "Reino da Magia",
        displayEmoji    = "🔮",
        portalColors    = PortalColors(
            primary = Color.rgb(100, 60, 200),   // Índigo mágico
            accent  = Color.rgb(200, 160, 255)
        ),
        lightingMode    = LightingMode.MOONLIGHT,
        minCorridorWidth = 3
    ),

    /** Andares 81–90: Superfície Aberta — Sol entra aqui */
    SUPERFICIE_ABERTA(
        displayName     = "Superfície Aberta",
        displayEmoji    = "☀️",
        portalColors    = PortalColors(
            primary = Color.rgb(255, 220, 60),   // Dourado solar
            accent  = Color.rgb(255, 255, 180)
        ),
        lightingMode    = LightingMode.DAYLIGHT,
        minCorridorWidth = 4
    ),

    /** Andares 91–100: Abismos da Escuridão */
    ABISMO_DO_VAZIO(
        displayName     = "Abismo do Vazio",
        displayEmoji    = "👻",
        portalColors    = PortalColors(
            primary = Color.rgb(60, 60, 80),     // Cinza fantasma
            accent  = Color.rgb(160, 160, 200)
        ),
        lightingMode    = LightingMode.VOID_DARK,
        minCorridorWidth = 2
    ),

    /** Andares 101–110: Núcleo de Fogo e Vulcões */
    NUCLEO_DE_FOGO(
        displayName     = "Núcleo de Fogo",
        displayEmoji    = "🌋",
        portalColors    = PortalColors(
            primary = Color.rgb(230, 90, 20),    // Laranja magma
            accent  = Color.rgb(255, 160, 60)
        ),
        lightingMode    = LightingMode.LAVA_GLOW,
        minCorridorWidth = 2
    ),

    /** Andares 111–120: O Cosmos — Base Lunar, Estrelas e Vazio */
    BASE_LUNAR(
        displayName     = "Base Lunar",
        displayEmoji    = "🌙",
        portalColors    = PortalColors(
            primary = Color.rgb(200, 220, 255),  // Azul lunar
            accent  = Color.rgb(255, 255, 255)
        ),
        lightingMode    = LightingMode.MOONLIGHT,
        minCorridorWidth = 5,
        isOpenAir       = true
    );

    val firstFloor: Int
        get() = ordinal * 2 + 1

    companion object {
        /** Retorna o BiomeWorld correspondente ao número de andar. Cada andar tem um bioma único, ciclando entre os disponíveis. */
        fun fromFloor(floorNumber: Int): BiomeWorld {
            val worlds = entries
            return worlds[((floorNumber - 1) % 22) / 2]
        }
    }
}

/**
 * Modo de iluminação do LightingSystem.
 * Define o comportamento do overlay de luz para cada BiomeWorld.
 */
enum class LightingMode {
    /** Escuro, halo do player + cogumelos/cristais pulsantes. (Padrão atual) */
    SUBTERRANEAN,

    /** Luz solar suave — overlay amarelo-dourado, raios de sol em SkyShafts. */
    DAYLIGHT,

    /** Lua e estrelas — overlay azul-anil suave, pontos brancos no teto. */
    MOONLIGHT,

    /** Pulsação bioluminescente verde/azul em todo o bitmap de overlay. */
    BIOLUMINESCENT,

    /** Calor de lava — overlay laranja/vermelho, micro-tremor de 1px. */
    LAVA_GLOW,

    /** Escuridão máxima — halo do player reduzido, tensão elevada. */
    VOID_DARK
}
