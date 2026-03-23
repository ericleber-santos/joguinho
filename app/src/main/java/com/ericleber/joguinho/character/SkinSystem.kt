package com.ericleber.joguinho.character

import android.graphics.Color

/**
 * Detalhe decorativo aplicado sobre o sprite base de uma skin.
 */
data class DecorativeDetail(
    val offsetX: Int,
    val offsetY: Int,
    val width: Int,
    val height: Int,
    val color: Int
)

/**
 * Definição de uma skin de personagem.
 * Requisitos: 24.4, 24.5
 */
data class SkinDefinition(
    val id: String,
    val displayName: String,
    val primaryColor: Int,
    val secondaryColor: Int,
    val accentColor: Int,
    val shapeVariant: Int = 0,
    val decorativeDetails: List<DecorativeDetail> = emptyList()
)

/**
 * Sistema de resolução de skins por ID.
 * Requisitos: 24.4, 24.5
 */
object SkinSystem {

    private val skins = mutableMapOf<String, SkinDefinition>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        register(
            SkinDefinition(
                id = "default",
                displayName = "Aventureiro",
                primaryColor = Color.rgb(60, 100, 180),
                secondaryColor = Color.rgb(40, 60, 120),
                accentColor = Color.rgb(100, 70, 30)
            )
        )
        register(
            SkinDefinition(
                id = "explorer",
                displayName = "Explorador",
                primaryColor = Color.rgb(80, 140, 60),
                secondaryColor = Color.rgb(50, 90, 40),
                accentColor = Color.rgb(200, 160, 80),
                decorativeDetails = listOf(
                    DecorativeDetail(offsetX = 8, offsetY = 2, width = 4, height = 2, color = Color.rgb(200, 160, 80))
                )
            )
        )
        register(
            SkinDefinition(
                id = "shadow",
                displayName = "Sombra",
                primaryColor = Color.rgb(40, 40, 60),
                secondaryColor = Color.rgb(20, 20, 40),
                accentColor = Color.rgb(180, 50, 200),
                shapeVariant = 1
            )
        )
    }

    fun register(skin: SkinDefinition) {
        skins[skin.id] = skin
    }

    /**
     * Resolve a skin pelo ID. Retorna a skin "default" se o ID não for encontrado.
     */
    fun resolveSkin(skinId: String): SkinDefinition =
        skins[skinId] ?: skins["default"]!!

    fun getAllSkins(): List<SkinDefinition> = skins.values.toList()
}
