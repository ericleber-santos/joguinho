# Codebase Structure

**Analysis Date:** 2026-05-04

## Directory Layout

```
joguinho/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ericleber/joguinho/
│   │   │   │   ├── audio/          # Sound & Music
│   │   │   │   ├── biome/          # Biome palettes & logic
│   │   │   │   ├── character/      # Entities (Hero, Spike, Enemies)
│   │   │   │   ├── core/           # Engine, Logic, State
│   │   │   │   ├── input/          # Controller & Joysticks
│   │   │   │   ├── pcg/            # Procedural Generation
│   │   │   │   ├── persistence/    # Room DB, DAOs
│   │   │   │   ├── renderer/       # Canvas rendering
│   │   │   │   ├── ui/             # Activities & Fragments
│   │   │   │   └── update/         # Google Play Updates
│   │   │   └── res/                # Assets (Sprites, Layouts, Strings)
│   │   └── test/                   # Unit Tests
│   └── build.gradle.kts            # App Config
└── gradle/                         # Build Wrapper & Versions
```

## Directory Purposes

**audio:**
- Purpose: Management of sound effects and music.
- Key files: `AudioManager.kt`.

**biome:**
- Purpose: Logic for world biomes and visual shifts.
- Key files: `BiomeWorld.kt`, `BiomePalette.kt`.

**core:**
- Purpose: The heartbeat of the game.
- Key files: `GameLogic.kt`, `GameLoop.kt`, `GameState.kt`.

**pcg:**
- Purpose: Maze and world generation algorithms.
- Key files: `BSPMazeGenerator.kt`.

**renderer:**
- Purpose: Drawing all game elements on the Android Canvas.
- Key files: `GameRenderer.kt`, `SpriteCache.kt`.

**persistence:**
- Purpose: Saving game progress and leaderboard.
- Key files: `AppDatabase.kt`, `SaveStateDao.kt`.

**ui:**
- Purpose: Screens and HUD.
- Key files: `GameActivity.kt`, `MainMenuActivity.kt`.

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/ericleber/joguinho/ui/MainMenuActivity.kt`: App Start.
- `app/src/main/java/com/ericleber/joguinho/ui/GameActivity.kt`: Game Start.

**Configuration:**
- `app/build.gradle.kts`: Dependencies and SDK levels.
- `gradle/libs.versions.toml`: Centralized version management.

**Core Logic:**
- `app/src/main/java/com/ericleber/joguinho/core/GameLogic.kt`: Gameplay rules.

**Testing:**
- `app/src/test/`: Local unit tests.
- `app/src/androidTest/`: Device-based integration tests.

## Naming Conventions

**Files:**
- PascalCase for Classes/Interfaces: `GameLogic.kt`, `InputController.kt`.

**Directories:**
- lowercase for packages: `core`, `renderer`.

## Where to Add New Code

**New Gameplay Mechanic:**
- logic: `app/src/main/java/com/ericleber/joguinho/core/GameLogic.kt` (but consider extracting if possible).
- state: `app/src/main/java/com/ericleber/joguinho/core/GameState.kt`.

**New Biome:**
- implementation: `app/src/main/java/com/ericleber/joguinho/biome/BiomePalette.kt`.

**New UI Screen:**
- implementation: `app/src/main/java/com/ericleber/joguinho/ui/`.
- layout: `app/src/main/res/layout/`.

**Utilities:**
- helpers: `app/src/main/java/com/ericleber/joguinho/core/Models.kt` or `Logger.kt`.

## Special Directories

**.planning/:**
- Purpose: AI-driven project management and documentation.
- Generated: Yes.
- Committed: Yes.

---

*Structure analysis: 2026-05-04*
