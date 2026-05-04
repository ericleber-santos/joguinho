# Phase 10: Imersão Total — Tiles, Biomas & Efeitos Animados - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning
**Source:** PRD Express Path (user prompt — Revitalização Visual)

<domain>
## Phase Boundary

Esta fase transforma a experiência visual do jogo de "cavernas repetitivas com cor diferente" para
biomas com identidade única e imediata. O jogador deve reconhecer o bioma em menos de 1 segundo
só pelo visual, sem ler o HUD.

**Escopo:**
- Sistema de goteiras animadas (`DripSystem`) para Caverna Úmida
- Variantes de tile por bioma (`TileVariant`) com sorteio determinístico
- Tiles decorativos (`BiomeTileSet`) com props exclusivos por bioma
- Sistema de partículas ambiente (`AmbientParticleSystem`) por bioma
- Overlay de cor de luz ambiente por bioma (PorterDuff MULTIPLY)
- AutoTile de bordas de transição suaves

**Fora de escopo:**
- Personagem (homem com pistolinha d'água): INTOCÁVEL
- Spike (cachorrinho companheiro): INTOCÁVEL
- Qualquer mudança em GameLogic, InputController, HudRenderer relativo ao player
- Novos biomas além dos 5 prioritários: Mina Abandonada, Caverna Úmida, Jardim de Fungos, Caverna de Calcário, Túneis de Terra
</domain>

<decisions>
## Implementation Decisions

### Tarefa 1 — DripSystem (P0 URGENTE)
- Remover completamente o sprite estático de gota no chão da Caverna Úmida
- Implementar `DripParticle.kt` com estados: FORMING → FALLING → SPLASHING → DEAD
- Implementar `DripSystem.kt` com `registerDripSource()` chamado na geração de mapa
- Máximo 20 gotas simultâneas por tela (restrição técnica Android)
- Som `SoundManager.playDrip()` e `SoundManager.playSplash()` já existentes no AudioManager
- Usar object pooling: pool pré-alocado de `DripParticle`, não criar a cada frame
- `DripSystem` deve integrar ao `Renderer.kt` na ordem correta de renderização (antes do HUD)
- `registerDripSource()` chamado em `PCGEngine.kt` ou `BSPMazeGenerator.kt` durante geração

### Tarefa 2 — TileVariant / Sorteio Determinístico
- Criar `data class TileVariant` com `baseSprite: Int`, `variantSprites: List<Int>`, `propSprites: List<PropDef>`, `weight: Float`
- Criar `data class PropDef` com `sprite`, `placement: PropPlacement`, `chance: Float`, `animated: Boolean`
- Sorteio via `Random(seed + tileX * 1000L + tileY)` — determinístico: mesmo mapa = mesma variante SEMPRE
- 60% usa sprite base, 40% usa variante
- Props gerados NA CRIAÇÃO DO MAPA, nunca em runtime por frame
- TileRenderer já usa sistema de hash por (tileX, tileY) — estender este padrão

### Tarefa 3 — BiomeTileSet por Bioma
- Criar `data class BiomeTileSet` com wallTiles, floorTiles, ceilingTiles, ambientColor, ambientParticles
- Mapear os 5 biomas prioritários:
  - `MINA_ABANDONADA` → sépia + ferrugem + ouro | poeira de carvão
  - `CAVERNA_UMIDA` → azul-petróleo + musgo + bioluminescência | DripSystem (não partículas)
  - `JARDIM_DE_FUNGOS` → magenta + neon-rosa + esporo verde | esporos flutuantes
  - `CAVERNA_DE_CALCARIO` → branco + gelo + cristal azul | poeira branca
  - `TUNEIS_DE_TERRA` → ocre + marrom + raiz + inseto | terra caindo
- Musgo animado (Caverna Úmida): `scaleY = 1f + sin(time * 0.5f) * 0.02f` — oscilação 2px
- Cogumelos animados (Jardim de Fungos): `scale = 0.95f + sin(time * 1.5f) * 0.05f` — pulso 2s

### Tarefa 4 — AmbientParticleSystem
- Criar `AmbientParticleSystem(biome: BiomeType)` com `ParticleConfig` por bioma
- Max 50 partículas ativas total na tela (somando todos os sistemas)
- Object pooling obrigatório — pool pré-alocado de `AmbientParticle`
- CAVERNA_UMIDA: `maxCount = 0` (gerenciado pelo DripSystem)
- Integrar ao loop de render existente em `Renderer.kt`

### Tarefa 5 — Cor de Luz Ambiente (renderAmbientLight)
- Aplicado APÓS renderizar todos tiles e sprites, ANTES do HUD
- Usar `PorterDuff.Mode.MULTIPLY` via `xfermode` no Paint
- Um `Paint` de cor sólida com alpha ~33-50% desenhado sobre o canvas inteiro
- Salvar/restaurar canvas layer com `saveLayer` para isolar o MULTIPLY do HUD
- Cores definidas: Mine=0x553D2B0A, Wet=0x550A1A2A, Fungus=0x551A0A2A, Limestone=0x330A1A2A, Earth=0x552A1008

### Tarefa 6 — AutoTile (Bordas de Transição)
- Implementar bitmask de 4 bits (top=8, right=4, bottom=2, left=1) → 16 combinações
- `autotileMap: Map<Int, Int>` em `BiomeTileSet` mapeando máscara → sprite
- Atalho aceitável: 4 sprites de canto arredondado como overlay transparente se 16 sprites for excessivo
- Calcular bitmask na criação do mapa, não a cada frame

### Restrições Técnicas Android (não negociáveis)
- Máximo 50 partículas ativas na tela (todos os sistemas somados)
- Object pooling para partículas e gotas: sem `new` em hot loop
- Sprites em Bitmap cache via `SpriteCache` existente
- Props gerados na criação do mapa, nunca por frame
- DripSystem: máximo 20 gotas simultâneas
- BiomeTileSet já mapeado no TileRenderer.kt — estender o pattern existente de `biomeAtual`

### Integração com Código Existente
- `TileRenderer.kt` já tem `setBiome(biome)` e hash por posição — usar como base
- `ParticleSystem.kt` já tem `Particle`, `ParticleConfig`, `emit()`, `update()`, `render()`
- `BiomePalette.kt` já tem cores por bioma — extrair `ambientColor` daqui
- `Renderer.kt` controla a ordem de render — inserir DripSystem e AmbientLight no lugar correto
- `PCGEngine.kt` / `BSPMazeGenerator.kt` é onde props e DripSources devem ser registrados

### Prioridades de Implementação
- P0 (URGENTE): DripSystem — gota estática é o maior problema visual
- P1: TileVariant + BiomeTileSet para os 5 biomas prioritários
- P2: AmbientParticleSystem + Cor de Luz + Animações de musgo/cogumelo
- P3: AutoTile de bordas suaves

### Agent's Discretion
- Estrutura exata do object pool para DripParticle e AmbientParticle
- Se usar `MutableList` com índice de head/tail ou `ArrayDeque` para o pool
- Localização exata no `Renderer.kt` para inserir as chamadas (antes/depois de cada layer)
- Se `DripSource` é inner class de `DripSystem` ou data class separada
</decisions>

<canonical_refs>
## Canonical References

Downstream agents MUST read these before planning or implementing.

### Engine Core
- `app/src/main/java/com/ericleber/joguinho/renderer/TileRenderer.kt` — renderizador de tiles existente com texturaMina, texturaRiacho, etc. Estender aqui.
- `app/src/main/java/com/ericleber/joguinho/renderer/ParticleSystem.kt` — sistema de partículas existente com `Particle`, `ParticleConfig`, `emit()`. DripParticle deve ser separado.
- `app/src/main/java/com/ericleber/joguinho/renderer/Renderer.kt` — render loop principal. Aqui inserir DripSystem.render() e renderAmbientLight().
- `app/src/main/java/com/ericleber/joguinho/biome/BiomePalette.kt` — paletas existentes por bioma. Fonte de cores para BiomeTileSet.
- `app/src/main/java/com/ericleber/joguinho/biome/Biome.kt` — enum com 120 biomas e `fromFloor()`.
- `app/src/main/java/com/ericleber/joguinho/pcg/PCGEngine.kt` — geração procedural. Props e DripSources registrados aqui.
- `app/src/main/java/com/ericleber/joguinho/core/GameState.kt` — estado do jogo, inclui bioma atual.

### Bioma e Paleta
- `app/src/main/java/com/ericleber/joguinho/biome/BiomeWorld.kt` — mapeamento de biomas em grupos de mundos. Verificar antes de alterar Biome.kt.

### Audio
- `app/src/main/java/com/ericleber/joguinho/audio/AudioManager.kt` — verificar se `playDrip()` e `playSplash()` existem antes de criar DripSystem.
</canonical_refs>

<specifics>
## Specific Ideas

### DripParticle States (do PRD)
```
FORMING  → cresce no teto (1.5s, scale 0→1)
FALLING  → cai com gravidade (GRAVITY = 120f px/s²)
SPLASHING → círculo expand + fade (0.3s)
DEAD     → remover do pool
```

### Paletas por Bioma (do PRD)
```
MINA_ABANDONADA: sépia #8B6914, ferrugem #A0522D, carvão #1A1008, ouro #D4A017
CAVERNA_UMIDA: azul-petróleo #1A3A4A, musgo #2D5A3D, pedra úmida #3A4A5A, bio #4A9AFF
JARDIM_DE_FUNGOS: magenta #4A0A5A, roxo #7B2FBE, neon-rosa #FF4DFF, esporo #7FFF00
CAVERNA_DE_CALCARIO: branco #E8E8E8, gelo #9AB0C0, cristal #4A8FFF, sombra #2A3A4A
TUNEIS_DE_TERRA: ocre #A0522D, marrom #6B3020, raiz #D4A870, inseto #FF6B2B
```

### Props por Bioma (do PRD)
```
MINA: picareta cravada (5%), suporte madeira (8%), placa enferrujada (3%), carvão (6%), trilho (4%)
CAVERNA_UMIDA: musgo animado em paredes (sin wave scaleY), poça refletindo (chão)
FUNGOS: cogumelo com pulso scale = 0.95 + sin(t*1.5)*0.05, esporos (partícula verde)
CALCARIO: cristal cluster com highlight especular, fóssil embutido
TERRA: raízes diagonais (2-4 por sala), larva visível, ninho aberto, pegada animal
```
</specifics>

<deferred>
## Deferred Ideas

- Sons ambiente distintos por bioma (P3) — dependente de AudioManager refactor
- AutoTile completo com 16 sprites por bioma (P3) — atalho de 4 cantos aceitável na fase
- Biomas além dos 5 prioritários (Mina de Carvão, Cristal Azul, etc.) — fase futura
- Halos de bioluminescência individuais em tiles com musgo — P2 reduzido (overlay global suficiente)
- Props grandes (24x32, 32x32) — limitados a props médios (16x24) nesta fase por bitmap cache
</deferred>

---

*Phase: 10-imersao-total*
*Context gathered: 2026-05-04 via PRD Express Path*
