# Phase 10: Imersão Total — Tiles, Biomas & Efeitos Animados
## PLAN.md

**Phase:** 10 | **Status:** Ready to execute | **Date:** 2026-05-04

---

## Wave 1 — P0 URGENTE: DripSystem (Caverna Úmida)

### Task 1.1 — Criar DripParticle.kt e DripSystem.kt
**Priority:** P0 | **Files:** NEW

```
app/src/main/java/com/ericleber/joguinho/renderer/DripParticle.kt
app/src/main/java/com/ericleber/joguinho/renderer/DripSystem.kt
```

**DripParticle.kt — implementar exatamente:**
```kotlin
package com.ericleber.joguinho.renderer

data class DripParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vy: Float = 0f,
    var originY: Float = 0f,
    var state: DripState = DripState.FORMING,
    var scale: Float = 0f,
    var alpha: Float = 1f,
    var splashTimer: Float = 0f,
    var floorY: Float = 0f,
    var active: Boolean = false  // pool flag
)

enum class DripState { FORMING, FALLING, SPLASHING, DEAD }

data class DripSource(
    val worldX: Float,
    val ceilingY: Float,
    val floorY: Float,
    val intervalMs: Long,
    var timer: Long = 0L
)
```

**DripSystem.kt — implementar:**
- Pool pré-alocado de 20 `DripParticle` (não criar em runtime)
- `registerDripSource(tileX, tileY, floorY)` → 15% chance via `Random(mapSeed + tileX*100L + tileY)`
- `update(deltaMs: Long)` → atualizar estados FORMING/FALLING/SPLASHING/DEAD
- Gravidade `GRAVITY = 120f` px/s²
- `render(canvas: Canvas, cameraX: Float, cameraY: Float)` → 3 estilos por estado
- `reset()` → limpar drips e sources ao trocar de mapa
- `soundCallback: ((type: String, volume: Float) -> Unit)?` — chamado no splash/drip (integração suave com AudioManager)
- Cap: nunca mais de 20 gotas ativas simultâneas

**render por estado:**
```
FORMING  → drawOval(3*scale wide, 5*scale tall) no ceilingY
FALLING  → drawOval(4 wide, 7 tall) descendo
SPLASHING→ drawCircle stroke expandindo (r = 6*splashTimer), alpha fade
```

**UAT:**
- [ ] Gotas aparecem visualmente no teto da Caverna Úmida
- [ ] Caem com aceleração, não velocidade constante
- [ ] Splash acontece ao tocar o chão (anel de círculo)
- [ ] Nunca mais de 20 gotas ativas (checar com `activeCount()`)
- [ ] Nenhum `new DripParticle()` dentro de `update()` ou `render()`

---

### Task 1.2 — Integrar DripSystem no Renderer.kt
**Priority:** P0 | **Files:** MODIFY `renderer/Renderer.kt`

**O que fazer:**
1. Adicionar `private val dripSystem = DripSystem()` na classe `Renderer`
2. No método `render()`, após `// Partículas` (linha ~449), inserir:
   ```kotlin
   // DripSystem — renderizar ANTES do restore() do canvas.save()
   dripSystem.update(deltaMs)
   dripSystem.render(canvas, cameraX, cameraY)
   ```
3. Expor `fun getDripSystem(): DripSystem` para uso em `GameActivity`/`GameViewModel`
4. Em `release()`: adicionar `dripSystem.reset()`
5. Em `onMapEnd()`: adicionar `dripSystem.reset()`

**Ordem exata de render (CRÍTICO):**
```
[chão] → [decorativos] → [paredes+entidades Y-sorted] → [portal] →
[DripSystem.render()] → [AmbientLight overlay] → canvas.restore() → [HUD]
```

**UAT:**
- [ ] Gotas aparecem sobre o chão mas abaixo do HUD
- [ ] Gotas resetam ao trocar de mapa

---

### Task 1.3 — Registrar DripSources no PCGEngine
**Priority:** P0 | **Files:** MODIFY `pcg/PCGEngine.kt`

**O que fazer:**
- Ler `BiomePalette` do bioma atual para verificar se é Caverna Úmida / bioma com goteira
- Após gerar o mapa, iterar tiles de teto (tile[x][y] == WALL e tile[x][y+1] == FLOOR):
  ```kotlin
  if (biome.name.contains("UMIDA") || biome.name.contains("RIACHO") || biome.name.contains("AQUATICO")) {
      val floorY = (ty + 1) * TILE_SIZE.toFloat()
      dripSystem.registerDripSource(tx, ty, floorY)
  }
  ```
- `dripSystem` passado via construtor ou setter (não singleton)

**UAT:**
- [ ] Fontes de goteira registradas apenas para biomas de água/umidade
- [ ] Múltiplas rodadas do mesmo mapa (mesma seed) produzem MESMAS fontes

---

## Wave 2 — P1: TileVariant + BiomeTileSet

### Task 2.1 — Criar TileVariant.kt e BiomeTileSet.kt
**Priority:** P1 | **Files:** NEW

```
app/src/main/java/com/ericleber/joguinho/renderer/TileVariant.kt
app/src/main/java/com/ericleber/joguinho/renderer/BiomeTileSet.kt
```

**TileVariant.kt:**
```kotlin
package com.ericleber.joguinho.renderer

enum class PropPlacement { WALL, FLOOR, CEILING, CORNER }

data class PropDef(
    val drawFn: String,      // ex: "pickaxe", "mushroom", "crystal", "root"
    val placement: PropPlacement,
    val chance: Float = 0.1f,
    val animated: Boolean = false,
    val animKey: String = ""  // "moss_wave", "mushroom_pulse"
)

data class TileVariant(
    val textureKey: String,         // chave de textura no hash determinístico
    val variantKeys: List<String>,  // variantes alternativas
    val propDefs: List<PropDef> = emptyList(),
    val weight: Float = 1.0f
)
```

**BiomeTileSet.kt:**
```kotlin
package com.ericleber.joguinho.renderer

import com.ericleber.joguinho.biome.Biome

data class BiomeTileSet(
    val biome: Biome,
    val wallVariants: List<TileVariant>,
    val floorVariants: List<TileVariant>,
    val ambientOverlayColor: Int,       // ARGB com alpha ~33-50% (PorterDuff MULTIPLY)
    val particleConfig: AmbientParticleConfig? = null
)

data class AmbientParticleConfig(
    val maxCount: Int,
    val particleSizePx: Float,
    val color: Int,
    val vyMin: Float,
    val vyMax: Float,
    val spawnRate: Float = 0.3f       // partículas por segundo por tile visível
)

/** Registro central de BiomeTileSets para os 5 biomas prioritários. */
object BiomeTileSets {
    fun forBiome(biome: Biome): BiomeTileSet? = ALL[biome]

    private val ALL: Map<Biome, BiomeTileSet> by lazy { buildAll() }

    private fun buildAll(): Map<Biome, BiomeTileSet> = mapOf(
        buildMina(),
        buildCavernaUmida(),
        buildJardimFungos(),
        buildCavernaCalcario(),
        buildTuneisTerra()
    ).associateBy { it.biome }

    private fun buildMina() = BiomeTileSet(
        biome = Biome.MINA_ABANDONADA,
        wallVariants = listOf(
            TileVariant("mine_wall_base", listOf("mine_wall_crack", "mine_wall_ore"),
                listOf(
                    PropDef("pickaxe_stuck", PropPlacement.WALL, 0.05f),
                    PropDef("wood_support", PropPlacement.CORNER, 0.08f),
                    PropDef("rusty_sign", PropPlacement.WALL, 0.03f)
                ))
        ),
        floorVariants = listOf(
            TileVariant("mine_floor_base", listOf("mine_floor_gravel", "mine_floor_track"),
                listOf(
                    PropDef("coal_chunk", PropPlacement.FLOOR, 0.06f),
                    PropDef("rail_piece", PropPlacement.FLOOR, 0.04f),
                    PropDef("fossil_bone", PropPlacement.FLOOR, 0.02f)
                ))
        ),
        ambientOverlayColor = 0x553D2B0A.toInt(),
        particleConfig = AmbientParticleConfig(15, 1.5f, 0xFFD4A96A.toInt(), -15f, -5f)
    )

    private fun buildCavernaUmida() = BiomeTileSet(
        biome = Biome.CAVERNA_UMIDA,
        wallVariants = listOf(
            TileVariant("wet_wall_dry", listOf("wet_wall_moss_light", "wet_wall_moss_heavy"),
                listOf(
                    PropDef("moss_patch", PropPlacement.WALL, 0.20f, animated = true, "moss_wave"),
                    PropDef("water_streak", PropPlacement.WALL, 0.15f)
                ))
        ),
        floorVariants = listOf(
            TileVariant("wet_floor_dry", listOf("wet_floor_puddle", "wet_floor_algae"))
        ),
        ambientOverlayColor = 0x550A1A2A.toInt(),
        particleConfig = null  // DripSystem gerencia tudo
    )

    private fun buildJardimFungos() = BiomeTileSet(
        biome = Biome.JARDIM_DE_FUNGOS,
        wallVariants = listOf(
            TileVariant("fungus_wall_base", listOf("fungus_wall_sprout", "fungus_wall_mycelium"),
                listOf(
                    PropDef("mushroom_small", PropPlacement.WALL, 0.12f, animated = true, "mushroom_pulse"),
                    PropDef("mycelium_web", PropPlacement.CORNER, 0.08f)
                ))
        ),
        floorVariants = listOf(
            TileVariant("fungus_floor_base", listOf("fungus_floor_spore_trail", "fungus_floor_mushroom_med"),
                listOf(
                    PropDef("mushroom_large", PropPlacement.FLOOR, 0.08f, animated = true, "mushroom_pulse")
                ))
        ),
        ambientOverlayColor = 0x551A0A2A.toInt(),
        particleConfig = AmbientParticleConfig(25, 2.5f, 0xFF7FFF00.toInt(), -20f, -8f)
    )

    private fun buildCavernaCalcario() = BiomeTileSet(
        biome = Biome.CAVERNA_DE_CALCARIO,
        wallVariants = listOf(
            TileVariant("limestone_wall_clean", listOf("limestone_wall_fossil", "limestone_wall_crystal"),
                listOf(
                    PropDef("crystal_cluster", PropPlacement.WALL, 0.10f),
                    PropDef("fossil_embedded", PropPlacement.WALL, 0.06f)
                ))
        ),
        floorVariants = listOf(
            TileVariant("limestone_floor_white", listOf("limestone_floor_crystal_shards", "limestone_floor_karst"))
        ),
        ambientOverlayColor = 0x330A1A2A.toInt(),
        particleConfig = AmbientParticleConfig(8, 1.0f, 0xFFE8E8E8.toInt(), -5f, 5f)
    )

    private fun buildTuneisTerra() = BiomeTileSet(
        biome = Biome.TUNEIS_DE_TERRA,
        wallVariants = listOf(
            TileVariant("earth_wall_compact", listOf("earth_wall_root", "earth_wall_larva"),
                listOf(
                    PropDef("root_diagonal", PropPlacement.WALL, 0.15f),
                    PropDef("larva_visible", PropPlacement.WALL, 0.05f)
                ))
        ),
        floorVariants = listOf(
            TileVariant("earth_floor_loose", listOf("earth_floor_paw_print", "earth_floor_insect_nest"),
                listOf(
                    PropDef("root_ceiling_long", PropPlacement.CEILING, 0.08f)
                ))
        ),
        ambientOverlayColor = 0x552A1008.toInt(),
        particleConfig = AmbientParticleConfig(12, 2.0f, 0xFF8B4513.toInt(), 10f, 25f)
    )
}
```

**UAT:**
- [ ] `BiomeTileSets.forBiome(Biome.MINA_ABANDONADA)` retorna set válido
- [ ] Cada bioma tem cores de overlay distintas e corretas
- [ ] Props com `animated=true` têm `animKey` definido

---

### Task 2.2 — Sorteio Determinístico de Variantes em TileRenderer
**Priority:** P1 | **Files:** MODIFY `renderer/TileRenderer.kt`

**O que fazer:**
1. Adicionar campo `private var currentBiomeTileSet: BiomeTileSet? = null`
2. Adicionar método:
   ```kotlin
   fun setBiomeTileSet(set: BiomeTileSet?) { currentBiomeTileSet = set }
   ```
3. No início de `renderFloorTile()` e `renderWallTile()`, verificar se existe `BiomeTileSet`:
   ```kotlin
   val tileSet = currentBiomeTileSet
   if (tileSet != null) {
       renderWithVariant(canvas, x, y, tileW, tileH, tileSet, tileX, tileY, isWall)
       return
   }
   // fallback: código legado existente continua abaixo
   ```
4. Implementar `renderWithVariant()`:
   ```kotlin
   private fun renderWithVariant(canvas, x, y, tileW, tileH, tileSet, tileX, tileY, isWall) {
       val rng = Random(tileSet.biome.ordinal.toLong() + tileX * 1000L + tileY)
       val variants = if (isWall) tileSet.wallVariants else tileSet.floorVariants
       val chosen = variants.firstOrNull() ?: return
       // 60% base, 40% variante
       val useVariant = rng.nextFloat() > 0.6f && chosen.variantKeys.isNotEmpty()
       val key = if (useVariant) chosen.variantKeys.random(rng) else chosen.textureKey
       drawTileByKey(canvas, x, y, tileW, tileH, key)
   }
   ```
5. `drawTileByKey()` mapeia a string-key para o bloco de código de render existente
   (ex: `"mine_wall_crack"` → chama `texturaMina()` com variante rachadura destacada)

**UAT:**
- [ ] Mesma seed + mesma posição = mesma variante SEMPRE
- [ ] Troca de bioma via `setBiomeTileSet()` muda imediatamente o visual
- [ ] Código legado ainda funciona quando `currentBiomeTileSet == null`

---

## Wave 3 — P2: AmbientParticleSystem + Luz + Animações

### Task 3.1 — AmbientParticleSystem.kt
**Priority:** P2 | **Files:** NEW `renderer/AmbientParticleSystem.kt`

**O que fazer:**
- Pool pré-alocado de 50 `AmbientParticle` (struct simples: x, y, vx, vy, life, maxLife, active)
- `init(config: AmbientParticleConfig, screenBounds: RectF)` — configura o sistema
- `update(deltaMs: Long, screenBounds: RectF)` — move, fade, recicla no pool
- `render(canvas: Canvas)` — drawRect/drawCircle por partícula com alpha
- `clear()` — desativa todas do pool
- Spawn rate controlado: não mais que `config.maxCount` ativos

```kotlin
class AmbientParticleSystem {
    private val pool = Array(50) { AmbientParticle() }
    private var config: AmbientParticleConfig? = null
    private val rng = Random()

    fun init(cfg: AmbientParticleConfig, bounds: RectF) { config = cfg; spawnInitial(bounds) }

    fun update(deltaMs: Long, bounds: RectF) {
        val cfg = config ?: return
        var active = 0
        pool.forEach { p ->
            if (!p.active) return@forEach
            p.x += p.vx * deltaMs / 1000f
            p.y += p.vy * deltaMs / 1000f
            p.life -= deltaMs
            p.alpha = (p.life.toFloat() / p.maxLife).coerceIn(0f, 1f)
            if (p.life <= 0 || !bounds.contains(p.x, p.y)) p.active = false
            else active++
        }
        // Spawn novas se abaixo do max
        if (active < cfg.maxCount) spawnOne(bounds, cfg)
    }

    fun render(canvas: Canvas) {
        val cfg = config ?: return
        val paint = Paint().apply { isAntiAlias = false }
        pool.filter { it.active }.forEach { p ->
            paint.color = cfg.color
            paint.alpha = (p.alpha * 200).toInt()
            val s = cfg.particleSizePx / 2f
            canvas.drawRect(p.x - s, p.y - s, p.x + s, p.y + s, paint)
        }
    }
}
```

**UAT:**
- [ ] Partículas sobem (biomas de poeira/esporo) ou descem (terra)
- [ ] Nenhuma partícula visível em Caverna Úmida (maxCount=0)
- [ ] Nenhum `new AmbientParticle()` dentro de `update()`

---

### Task 3.2 — renderAmbientLight em Renderer.kt
**Priority:** P2 | **Files:** MODIFY `renderer/Renderer.kt`

**O que fazer:**
1. Adicionar `private val ambientLightPaint = Paint()`
2. Adicionar método:
   ```kotlin
   private fun renderAmbientLight(canvas: Canvas, biome: Biome) {
       val tileSet = BiomeTileSets.forBiome(biome) ?: return
       val overlayColor = tileSet.ambientOverlayColor
       // MULTIPLY não pode ser aplicado sobre HUD — usar saveLayer
       val sc = canvas.saveLayer(0f, 0f, screenWidth.toFloat(),
           screenHeight * fracaoAreaJogo, null)
       ambientLightPaint.apply {
           color = overlayColor
           xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
       }
       canvas.drawRect(0f, 0f, screenWidth.toFloat(),
           screenHeight * fracaoAreaJogo, ambientLightPaint)
       ambientLightPaint.xfermode = null
       canvas.restoreToCount(sc)
   }
   ```
3. Chamar `renderAmbientLight(canvas, gameState.currentBiome)` APÓS `dripSystem.render()` e ANTES de `canvas.restore()`

**UAT:**
- [ ] Mina Abandonada tem tom sépia suave
- [ ] Caverna de Calcário é mais clara (alpha 0x33 vs 0x55)
- [ ] HUD não é afetado pela cor (ainda legível e em cores corretas)
- [ ] Performance: verificar que `saveLayer` não causa queda de FPS em mid-range

---

### Task 3.3 — Animações de Musgo e Cogumelos (PropAnimator)
**Priority:** P2 | **Files:** NEW `renderer/PropAnimator.kt`

**O que fazer:**
- Criar objeto `PropAnimator` com `fun mossScaleY(timeMs: Long): Float` e `fun mushroomScale(timeMs: Long): Float`
- `mossScaleY = 1f + sin(timeMs / 1000f * 0.5f) * 0.02f` (ondulação 2% em 2s)
- `mushroomScale = 0.95f + sin(timeMs / 1000f * 1.5f) * 0.05f` (pulso 5% em ~4s)
- Integrar ao `TileRenderer.renderDecorativeTile()` para props com `animated=true`
- Receber `frameTotal` (já presente no Renderer) via parâmetro

**UAT:**
- [ ] Musgo em Caverna Úmida oscila levemente (quase imperceptível, sutil)
- [ ] Cogumelos no Jardim de Fungos pulsam ritmo diferente do musgo
- [ ] Animação é suave, sem jitter

---

## Wave 4 — P3: AutoTile de Bordas

### Task 4.1 — AutoTile com bitmask de 4 bits
**Priority:** P3 | **Files:** MODIFY `renderer/TileRenderer.kt`

**O que fazer:**
1. Adicionar `fun getAutotileMask(tx: Int, ty: Int, mazeData: MazeData): Int`:
   ```kotlin
   val top    = mazeData.isWall(tx, ty - 1)
   val right  = mazeData.isWall(tx + 1, ty)
   val bottom = mazeData.isWall(tx, ty + 1)
   val left   = mazeData.isWall(tx - 1, ty)
   return (if (top) 8 else 0) or (if (right) 4 else 0) or
          (if (bottom) 2 else 0) or (if (left) 1 else 0)
   ```
2. Atalho mínimo viável (não 16 sprites): desenhar overlay translúcido de canto arredondado quando `mask != 15` (parede não-rodeada)
3. `renderTransitionOverlay(canvas, x, y, tileW, tileH, mask)` — 4 cantos com alpha 60%

**UAT:**
- [ ] Tiles de parede isolados têm cantos arredondados visíveis
- [ ] Sem artefato visual em tiles de parede rodeados por outras paredes (mask=15 = sem overlay)

---

## Integração Final no Renderer e Cleanup

### Task 5.1 — Integração de BiomeTileSet no Renderer
**Priority:** P1 | **Files:** MODIFY `renderer/Renderer.kt`

**O que fazer:**
1. Adicionar `private val ambientParticleSystem = AmbientParticleSystem()`
2. No método `render()`, depois de `tileRenderer.setBiome(gameState.currentBiome)`:
   ```kotlin
   val tileSet = BiomeTileSets.forBiome(gameState.currentBiome)
   tileRenderer.setBiomeTileSet(tileSet)
   // Re-init particles quando bioma muda
   if (tileSet?.particleConfig != null && lastBiome != gameState.currentBiome) {
       val bounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight * fracaoAreaJogo)
       ambientParticleSystem.init(tileSet.particleConfig, bounds)
       lastBiome = gameState.currentBiome
   }
   ```
3. Adicionar `ambientParticleSystem.update(deltaMs, screenBounds)` e `ambientParticleSystem.render(canvas)` após `dripSystem.render()`
4. Em `release()`: `ambientParticleSystem.clear()`

**UAT:**
- [ ] Ao trocar de mapa para bioma diferente, partículas mudam imediatamente
- [ ] Sem crash ao renderizar mapa sem `BiomeTileSet` mapeado (biomas 6..120)

---

### Task 5.2 — Remover sprite estático de gota da Caverna Úmida
**Priority:** P0 | **Files:** MODIFY `renderer/TileRenderer.kt` ou `pcg/PCGEngine.kt`

**O que fazer:**
- Buscar por qualquer código que desenhe sprites estáticos de gota no chão de Caverna Úmida:
  - Em `TileRenderer.renderFloorTile()`, verificar se há lógica para bioma `UMIDA`
  - Em `Renderer.kt` linha 226-227 há um `continue` para UMIDO/PANTANO — verificar se havia props de gota
  - Remover qualquer `drawOval` / sprite de gota estático associado ao bioma úmido no `renderDecorativeTile()`
- Confirmar que `DripSystem` é a única fonte de gotas animadas

**UAT:**
- [ ] Nenhuma gota estática sólida visível no chão da Caverna Úmida
- [ ] Apenas gotas animadas do DripSystem aparecem

---

## Acceptance Criteria Gerais da Fase 10

| Critério | Método de Verificação |
|----------|-----------------------|
| Bioma identificável em <1s | Observar 5 biomas consecutivos sem olhar HUD |
| DripSystem: 20 gotas max | Log de `dripSystem.activeCount()` nunca > 20 |
| 60fps em mid-range | Android Profiler: frame time < 16.67ms |
| Nenhum `new` em hot loop | Code review de update()/render() dos sistemas |
| Determinismo de variantes | Reiniciar app com mesma seed → mesmo tile layout |
| HUD legível com overlay | Screenshot com AmbientLight ativo + HUD visível |
| Personagem e Spike intocados | Diff de CharacterRenderer.kt = 0 mudanças |

---

## Ordem de Execução Recomendada

```
Wave 1 (P0): Task 1.1 → 1.2 → 1.3   [DripSystem completo]
Wave 2 (P1): Task 2.1 → 2.2 → 5.1 → 5.2   [BiomeTileSet + integração]
Wave 3 (P2): Task 3.2 → 3.1 → 3.3   [Luz ambiente + partículas + animações]
Wave 4 (P3): Task 4.1   [AutoTile — somente se tempo permitir]
```

**Arquivos novos a criar:**
- `renderer/DripParticle.kt`
- `renderer/DripSystem.kt`
- `renderer/TileVariant.kt`
- `renderer/BiomeTileSet.kt`
- `renderer/AmbientParticleSystem.kt`
- `renderer/PropAnimator.kt`

**Arquivos a modificar:**
- `renderer/Renderer.kt` (integração geral)
- `renderer/TileRenderer.kt` (variantes + autotile)
- `pcg/PCGEngine.kt` (DripSources + props no mapa)
