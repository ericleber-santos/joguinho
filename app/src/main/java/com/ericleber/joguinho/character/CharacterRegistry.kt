package com.ericleber.joguinho.character

/**
 * Registro centralizado de personagens jogáveis.
 * Garante que o Hero padrão esteja sempre disponível como fallback.
 * Requisito: 24.7
 */
object CharacterRegistry {

    private val characters = mutableMapOf<String, PlayableCharacter>()

    init {
        // Registra o Hero padrão na inicialização
        register(Hero())
    }

    fun register(character: PlayableCharacter) {
        characters[character.id] = character
    }

    /**
     * Retorna o personagem pelo ID. Faz fallback para "hero" se não encontrado.
     */
    fun get(id: String): PlayableCharacter =
        characters[id] ?: characters["hero"]
            ?: error("Hero padrão não registrado no CharacterRegistry")

    fun getAll(): List<PlayableCharacter> = characters.values.toList()

    fun isRegistered(id: String): Boolean = characters.containsKey(id)
}
