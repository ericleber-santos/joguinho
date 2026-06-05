package com.ericleber.joguinho.core

import kotlinx.serialization.Serializable
import java.util.Random

/**
 * Representa os tipos de upgrades disponíveis para o Hero e Spike.
 */
@Serializable
enum class UpgradeType {
    HERO_SPEED,             // Aumenta velocidade do Hero
    HERO_JUMP,              // Aumenta força de pulo do Hero
    HERO_DOUBLE_JUMP,       // Desbloqueia pulo duplo no ar
    HERO_MAX_LIVES,         // Cura 1 vida e aumenta o limite máximo
    HERO_WATER_COOLDOWN,    // Dispara água mais rápido (menos delay de dano)
    HERO_WATER_RANGE,       // Aumenta o alcance do jato
    SPIKE_DAMAGE,           // Aumenta o dano de ataque do Spike
    SPIKE_COOLDOWN,         // Diminui o cooldown de ataque do Spike
    SPIKE_SPEED,            // Aumenta a velocidade do Spike
    SPIKE_GOLDEN_SNIFFER    // Spike desenterra mais moedas
}

/**
 * Raridades das cartas de upgrades, definindo suas cores neon e taxas de drop.
 */
@Serializable
enum class UpgradeRarity {
    COMMON,      // Comum: Ciano
    RARE,        // Raro: Roxo
    EPIC,        // Épico: Ouro
    LEGENDARY    // Lendário: Magenta / Pulsante
}

/**
 * Classe que descreve uma Carta de Upgrade roguelite.
 */
@Serializable
data class UpgradeCard(
    val id: String,
    val title: String,
    val description: String,
    val type: UpgradeType,
    val rarity: UpgradeRarity
) {
    companion object {
        /**
         * Gera 3 opções de cartas aleatórias distintas para o jogador escolher.
         * Garante que não apareçam cartas de upgrades que o jogador já maximizou
         * (ex: Pulo Duplo só pode ser pego uma vez).
         */
        fun generateRandomOptions(random: Random, doubleJumpUnlocked: Boolean): List<UpgradeCard> {
            val allPossibleUpgrades = mutableListOf<UpgradeCard>()

            // Adiciona opções comuns
            allPossibleUpgrades.add(
                UpgradeCard("hero_speed", "Botas de Mercúrio", "+15% de velocidade de corrida", UpgradeType.HERO_SPEED, UpgradeRarity.COMMON)
            )
            allPossibleUpgrades.add(
                UpgradeCard("hero_jump", "Super Mola", "+15% de impulsão do pulo", UpgradeType.HERO_JUMP, UpgradeRarity.COMMON)
            )
            allPossibleUpgrades.add(
                UpgradeCard("hero_water_range", "Super Pressão", "+25% de alcance da água", UpgradeType.HERO_WATER_RANGE, UpgradeRarity.COMMON)
            )
            allPossibleUpgrades.add(
                UpgradeCard("spike_speed", "Patas Ágeis", "Spike corre 20% mais rápido", UpgradeType.SPIKE_SPEED, UpgradeRarity.COMMON)
            )
            allPossibleUpgrades.add(
                UpgradeCard("spike_damage", "Mordida de Ferro", "Mordida do Spike causa +2 de dano", UpgradeType.SPIKE_DAMAGE, UpgradeRarity.COMMON)
            )

            // Adiciona opções raras
            allPossibleUpgrades.add(
                UpgradeCard("hero_water_cooldown", "Esguicho Rápido", "+25% de velocidade de esguicho", UpgradeType.HERO_WATER_COOLDOWN, UpgradeRarity.RARE)
            )
            allPossibleUpgrades.add(
                UpgradeCard("spike_cooldown", "Cão Hiperativo", "Spike ataca 25% mais rápido", UpgradeType.SPIKE_COOLDOWN, UpgradeRarity.RARE)
            )
            allPossibleUpgrades.add(
                UpgradeCard("spike_golden_sniffer", "Faro de Ouro", "Spike ganha 30% chance de moedas extra", UpgradeType.SPIKE_GOLDEN_SNIFFER, UpgradeRarity.RARE)
            )

            // Adiciona opções épicas
            allPossibleUpgrades.add(
                UpgradeCard("hero_max_lives", "Vitalidade Divina", "+1 Vida Máxima e cura 1 Coração", UpgradeType.HERO_MAX_LIVES, UpgradeRarity.EPIC)
            )

            // Adiciona opções lendárias (apenas se o Pulo Duplo não estiver liberado)
            if (!doubleJumpUnlocked) {
                allPossibleUpgrades.add(
                    UpgradeCard("hero_double_jump", "Asas de Ícaro", "Permite realizar pulo duplo no ar", UpgradeType.HERO_DOUBLE_JUMP, UpgradeRarity.LEGENDARY)
                )
            }

            // Embaralha e seleciona 3 distintas
            val options = mutableListOf<UpgradeCard>()
            val pool = allPossibleUpgrades.toMutableList()
            
            val count = minOf(3, pool.size)
            for (i in 0 until count) {
                // Sorteia com peso de raridade (opcionalmente simplificado, ou selecionando direto aleatório da lista embaralhada)
                // Vamos embaralhar e pegar para garantir diversidade
                val idx = random.nextInt(pool.size)
                options.add(pool.removeAt(idx))
            }
            
            return options
        }
    }
}
