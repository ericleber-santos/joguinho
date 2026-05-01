package com.ericleber.joguinho.core

import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.core.MazeData
import com.ericleber.joguinho.persistence.SaveState

/** Marcos de andar que desbloqueiam conquistas (Requisito 10.2). */
val MARCOS_CONQUISTAS = setOf(1, 10, 20, 40, 60, 80, 100, 120)

/**
 * Fase atual do jogo — controla o fluxo de telas e estados do GameLoop.
 */
enum class GamePhase {
    MENU,
    LOADING,
    PLAYING,
    PAUSED,
    SCORE_SCREEN,
    TRANSITIONING_BIOME,
    GAME_OVER
}

/**
 * Evento de jogo emitido durante o frame para comunicação entre subsistemas.
 */
sealed class GameEvent {
    object HeroReceivedSlowdown : GameEvent()
    object HeroSurpassedObstacle : GameEvent()
    object HeroReachedExit : GameEvent()
    object MapCompleted : GameEvent()
    object FloorCompleted : GameEvent()
    data class AchievementUnlocked(val achievementId: String) : GameEvent()
    data class NewRecord(val floorNumber: Int, val timeMs: Long) : GameEvent()
}

/**
 * Estado global do jogo — Model central do padrão MVC adaptado.
 *
 * Contém todo o estado mutável necessário para um frame de jogo:
 * posições, timers, score, fase atual e eventos pendentes.
 *
 * Requisito 21.6
 */
class GameState {

    // --- Fase e fluxo ---
    var phase: GamePhase = GamePhase.MENU
    var exitAnimationTimerMs: Long = 0L // Timer para a animação de subida na escada
    var isExiting: Boolean = false      // Flag para travar movimento durante a animação

    // --- Identificação do Floor/Map ---
    var floorNumber: Int = 1
    var mapIndex: Int = 0
    var floorSeed: Long = System.currentTimeMillis()

    // --- Posições ---
    var heroPosition: Position = Position(0, 0)
    var heroDirection: Direction = Direction.SOUTH
    var heroStoppedDurationSec: Float = 0f
    var spikePosition: Position = Position(0, 0)

    // --- Estado do Hero ---
    var heroIsSlowedDown: Boolean = false
    var heroSlowdownRemainingMs: Long = 0L
    var heroLives: Int = 3
    var heroLastSlowdownTimeMs: Long = 0L
    var mapSlowdownCount: Int = 0 // Contador de lentidões no mapa atual
    
    // Buff de velocidade (+50% por 7s)
    var heroHasSpeedBuff: Boolean = false
    var heroSpeedBuffRemainingMs: Long = 0L
    
    // Cooldown de monstros (ID do monstro -> timestamp da última colisão)
    val monsterCollisionCooldowns: MutableMap<String, Long> = mutableMapOf()
    
    // Frase atual do Boss para exibição no HUD
    var bossMessage: String? = null
    var bossMessageTimerMs: Long = 0L

    // --- Estado do Spike ---
    var spikeIsSlowedDown: Boolean = false
    var spikeSlowdownRemainingMs: Long = 0L

    // --- Entidades do mapa ---
    var monsters: List<MonsterState> = emptyList()
    var traps: List<TrapState> = emptyList()
    var items: List<ItemState> = emptyList()

    // --- Boss Fight & Sobrevivência ---
    var bossFightState: BossFightState = BossFightState()
    var survivalElements: List<SurvivalElementState> = emptyList()
    var bossAoeZones: List<AoeZone> = emptyList()

    // --- Timers ---
    /** Tempo acumulado no Floor atual em milissegundos. */
    var floorTimerMs: Long = 0L
    
    /** Timer regressivo do mapa atual (5 minutos). */
    var mapTimerMs: Long = 300000L

    // --- Score e combo ---
    var accumulatedScore: Float = 0f
    var comboStreak: Int = 0
    var comboBonus: Float = 0f
    /** Indica se o Map atual foi completado sem nenhum Slowdown. */
    var currentMapClean: Boolean = true

    // --- Progressão ---
    var statistics: PlayerStatistics = PlayerStatistics()
    var achievements: MutableSet<String> = mutableSetOf()
    var personalBests: MutableMap<Int, Long> = mutableMapOf()

    // --- Personagem ativo ---
    var activeCharacterId: String = "hero"
    var activeSkinId: String = "default"

    // --- Labirinto atual ---
    var mazeData: MazeData? = null

    // --- Acessibilidade ---
    var highContrastMode: Boolean = false

    // --- Estado comportamental do Spike (para o Renderer) ---
    var spikeCompanionState: String = "SEGUINDO"

    // --- Bioma atual ---
    var devModeForcedBiome: Biome? = null

    val currentBiome: Biome
        get() = devModeForcedBiome ?: Biome.entries.firstOrNull { floorNumber in it.floorRange } ?: Biome.MINA_ABANDONADA

    // --- Eventos pendentes para o frame atual ---
    // CopyOnWriteArrayList garante leitura segura de múltiplas threads sem sincronização explícita
    private val _pendingEvents: MutableList<GameEvent> = java.util.concurrent.CopyOnWriteArrayList()
    val pendingEvents: List<GameEvent> get() = _pendingEvents

    fun emitEvent(event: GameEvent) {
        _pendingEvents.add(event)
    }

    /** Deve ser chamado ao final de cada frame para limpar os eventos processados. */
    fun clearEvents() {
        _pendingEvents.clear()
    }

    // --- Fila de notificações de conquistas para o HudRenderer (Requisito 10.3) ---
    private val _filaNotificacoesConquistas: ArrayDeque<String> = ArrayDeque()

    /**
     * Retorna o nome da próxima conquista pendente de exibição, ou null se a fila estiver vazia.
     * O HudRenderer deve chamar este método para obter notificações sem interromper o gameplay.
     */
    fun proximaNotificacaoConquista(): String? = _filaNotificacoesConquistas.removeFirstOrNull()

    /** Verifica se há conquistas pendentes de exibição. */
    fun temNotificacaoConquistaPendente(): Boolean = _filaNotificacoesConquistas.isNotEmpty()

    /**
     * Verifica se o andar atual corresponde a um marco de conquista e, se sim,
     * desbloqueia a conquista e enfileira a notificação para o HudRenderer.
     * Requisitos: 10.2, 10.3
     */
    fun verificarConquistasAndar() {
        if (floorNumber !in MARCOS_CONQUISTAS) return
        val idConquista = "andar_$floorNumber"
        if (idConquista in achievements) return
        achievements.add(idConquista)
        _filaNotificacoesConquistas.addLast(idConquista)
        emitEvent(GameEvent.AchievementUnlocked(idConquista))
    }

    /**
     * Deve ser chamado quando o herói completa um andar.
     * Atualiza estatísticas, verifica conquistas e emite eventos.
     * Requisitos: 10.2, 10.3, 10.4
     */
    fun completarAndar(tempoAndarMs: Long) {
        statistics = statistics.copy(
            totalMapsCompleted = statistics.totalMapsCompleted + 1,
            totalPlayTimeMs = statistics.totalPlayTimeMs + tempoAndarMs,
            totalMaxComboStreaks = maxOf(statistics.totalMaxComboStreaks, comboStreak)
        )
        emitEvent(GameEvent.FloorCompleted)
        verificarConquistasAndar()
    }

    // --- Score ---

    /**
     * Calcula o Score final do Floor.
     * Fórmula: baseScore = (10000 / tempoEmSegundos) * (1 + comboBonus)
     * Requisito 6.2
     */
    fun calculateFloorScore(): Float {
        val tempoEmSegundos = floorTimerMs / 1000f
        if (tempoEmSegundos <= 0f) return 0f
        return (10000f / tempoEmSegundos) * (1f + comboBonus)
    }

    /**
     * Incrementa o ComboStreak e aplica bônus de 10% a cada múltiplo de 5.
     * Requisito 5.7, 5.8
     */
    fun incrementComboStreak() {
        comboStreak++
        if (comboStreak % 5 == 0) {
            comboBonus += 0.10f
        }
    }

    /** Reseta o ComboStreak ao receber Slowdown. */
    fun resetComboStreak() {
        comboStreak = 0
        comboBonus = 0f
        currentMapClean = false
        statistics = statistics.copy(
            totalSlowdownsReceived = statistics.totalSlowdownsReceived + 1
        )
    }

    // --- Persistência ---

    /** Cria um SaveState a partir do estado atual. */
    fun toSaveState(): SaveState = SaveState(
        floorNumber = floorNumber,
        mapIndex = mapIndex,
        floorSeed = floorSeed,
        heroPosition = heroPosition,
        heroState = HeroState(
            position = heroPosition,
            direction = heroDirection,
            isSlowedDown = heroIsSlowedDown,
            slowdownRemainingMs = heroSlowdownRemainingMs,
            lives = heroLives,
            lastSlowdownTimeMs = heroLastSlowdownTimeMs
        ),
        spikePosition = spikePosition,
        spikeState = SpikeState(
            position = spikePosition,
            isSlowedDown = spikeIsSlowedDown,
            slowdownRemainingMs = spikeSlowdownRemainingMs
        ),
        monsters = monsters,
        traps = traps,
        bossFightState = bossFightState,
        survivalElements = survivalElements,
        bossAoeZones = bossAoeZones,
        floorTimerMs = floorTimerMs,
        accumulatedScore = accumulatedScore,
        comboStreak = comboStreak,
        comboBonus = comboBonus,
        statistics = statistics,
        achievements = achievements.toSet(),
        personalBests = personalBests.toMap(),
        activeCharacterId = activeCharacterId,
        activeSkinId = activeSkinId
    )

    /** Restaura o estado a partir de um SaveState. */
    fun restoreFrom(save: SaveState) {
        floorNumber = save.floorNumber
        mapIndex = save.mapIndex
        floorSeed = save.floorSeed
        heroPosition = save.heroPosition
        heroDirection = save.heroState.direction
        heroIsSlowedDown = save.heroState.isSlowedDown
        heroSlowdownRemainingMs = save.heroState.slowdownRemainingMs
        heroLives = save.heroState.lives
        heroLastSlowdownTimeMs = save.heroState.lastSlowdownTimeMs
        spikePosition = save.spikePosition
        spikeIsSlowedDown = save.spikeState.isSlowedDown
        spikeSlowdownRemainingMs = save.spikeState.slowdownRemainingMs
        monsters = save.monsters
        traps = save.traps
        bossFightState = save.bossFightState
        survivalElements = save.survivalElements
        bossAoeZones = save.bossAoeZones
        floorTimerMs = save.floorTimerMs
        accumulatedScore = save.accumulatedScore
        comboStreak = save.comboStreak
        comboBonus = save.comboBonus
        statistics = save.statistics
        achievements = save.achievements.toMutableSet()
        personalBests = save.personalBests.toMutableMap()
        activeCharacterId = save.activeCharacterId
        activeSkinId = save.activeSkinId
        phase = GamePhase.PLAYING
    }
}
