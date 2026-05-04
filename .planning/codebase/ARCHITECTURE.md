<!-- refreshed: 2026-05-04 -->
# Architecture

**Analysis Date:** 2026-05-04

## System Overview

```text
┌─────────────────────────────────────────────────────────────┐
│                      UI / Activities                         │
│   `com.ericleber.joguinho.ui`                                │
├──────────────────┬──────────────────┬───────────────────────┤
│  MainMenuActivity│   GameActivity   │    ScoreActivity      │
└────────┬─────────┴────────┬─────────┴──────────┬────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    Core Engine / Logic                       │
│         `com.ericleber.joguinho.core`                        │
│   (GameLoop, GameLogic, GameState, Models)                   │
└─────────────────────────────────────────────────────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌──────────────────┬──────────────────┬───────────────────────┤
│       PCG        │     Renderer     │     Persistence       │
│  `...joguinho.pcg`│ `...joguinho.rend`│ `...joguinho.persist` │
└──────────────────┴──────────────────┴───────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| UI | Handles user interface, activities, and menus. | `app/src/main/java/com/ericleber/joguinho/ui/` |
| Core | Orchestrates the game loop, logic updates, and state. | `app/src/main/java/com/ericleber/joguinho/core/` |
| PCG | Procedural Content Generation for mazes and biomes. | `app/src/main/java/com/ericleber/joguinho/pcg/` |
| Renderer | Handles drawing entities, biomes, and UI overlays. | `app/src/main/java/com/ericleber/joguinho/renderer/` |
| Persistence| Local database management for scores and state. | `app/src/main/java/com/ericleber/joguinho/persistence/` |
| Biome | Manages biome-specific logic and visuals. | `app/src/main/java/com/ericleber/joguinho/biome/` |
| Character | Player (Hero), Companion (Spike), and AI logic. | `app/src/main/java/com/ericleber/joguinho/character/` |

## Pattern Overview

**Overall:** Game Loop with SurfaceView.

**Key Characteristics:**
- **Decoupled Update/Draw:** Logic runs in a dedicated thread (`GameLoop`), rendering uses `SurfaceHolder` lockCanvas/unlockCanvas.
- **State-Driven:** `GameState.kt` holds all mutable data, which is modified by `GameLogic` and read by `Renderer`.
- **Procedural Generation:** BSP (Binary Space Partitioning) and WFC (Wave Function Collapse) patterns for level design.

## Layers

**UI Layer:**
- Purpose: Entry point and menu systems.
- Location: `app/src/main/java/com/ericleber/joguinho/ui/`
- Contains: Activities, ViewModels, Layouts.

**Domain Layer (Core):**
- Purpose: Central game engine.
- Location: `app/src/main/java/com/ericleber/joguinho/core/`
- Contains: `GameLogic.kt` (Logic), `GameLoop.kt` (Ticker).

**Data Layer (Persistence):**
- Purpose: Local storage.
- Location: `app/src/main/java/com/ericleber/joguinho/persistence/`
- Contains: Room Database, DAOs, Entities.

## Data Flow

### Primary Request Path (Gameplay)

1. `GameActivity` initializes `SurfaceView` and starts `GameLoop`.
2. `GameLoop` calls `GameLogic.update()` every tick (~60fps).
3. `GameLogic` modifies `GameState` (physics, combat, input).
4. `GameLoop` calls `Renderer.render(gameState)` to update the display.

### State Management:
- Global mutable state in `GameState.kt`.
- Thread safety: `GameLoop` ensures updates happen sequentially, though UI interactions might require synchronization.

## Entry Points

**MainMenuActivity:**
- Location: `app/src/main/java/com/ericleber/joguinho/ui/MainMenuActivity.kt`
- Triggers: App Launch.
- Responsibilities: Navigation to Game, Settings, and Scores.

**GameActivity:**
- Location: `app/src/main/java/com/ericleber/joguinho/ui/GameActivity.kt`
- Triggers: "Start" button in Menu.
- Responsibilities: Main gameplay container and lifecycle management.

## Architectural Constraints

- **Single Game Thread:** All logic must happen in the `GameLoop` thread to avoid race conditions.
- **SurfaceView Synchronization:** Rendering must lock and unlock the canvas properly.
- **Global state:** `GameState.kt` is a singleton-like object passed around, making it a central point of failure if not managed carefully.

## Anti-Patterns

### God Object
**What happens:** `GameLogic.kt` (43KB) handles almost everything: physics, combat, item interactions, floor transitions.
**Why it's wrong:** High coupling and difficult to test/maintain.
**Do this instead:** Extract specialized managers (e.g., `CombatManager`, `PhysicsManager`).

## Error Handling

**Strategy:** Graceful degradation and logging.

**Patterns:**
- Try-catch around canvas locking to prevent crashes during lifecycle changes.
- `Logger.kt` for debugging.

---

*Architecture analysis: 2026-05-04*
