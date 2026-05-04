# Codebase Concerns

**Analysis Date:** 2026-05-04

## Tech Debt

**Core Game Logic:**
- Issue: `GameLogic.kt` is a "God Object" (43KB) handling physics, combat, state transitions, and environmental logic.
- Files: `app/src/main/java/com/ericleber/joguinho/core/GameLogic.kt`
- Impact: High risk of regression during changes; difficult to unit test isolated mechanics.
- Fix approach: Extract specialized managers (e.g., `CombatSystem`, `CollisionSystem`, `EnvironmentManager`).

**Custom Game Engine Maintenance:**
- Issue: Relying on a custom `SurfaceView` renderer rather than a mature engine.
- Files: `app/src/main/java/com/ericleber/joguinho/renderer/`
- Impact: Complex edge cases in performance optimization and varied device refresh rates.
- Fix approach: Maintain strict separation between state and rendering to simplify transition to a dedicated engine if needed.

## Security Considerations

**Persistence Security:**
- Risk: Room database is stored in the clear (not encrypted).
- Files: `app/src/main/java/com/ericleber/joguinho/persistence/`
- Current mitigation: Standard Android internal storage protection.
- Recommendations: Use SQLCipher if sensitive player data or monetization items are added.

## Performance Bottlenecks

**Procedural Generation Costs:**
- Problem: BSP Maze generation and entity placement could cause stuttering.
- Files: `app/src/main/java/com/ericleber/joguinho/pcg/BSPMazeGenerator.kt`
- Cause: Synchronous execution during floor transitions.
- Improvement path: Ensure PCG runs strictly in a background thread with a loading screen.

**Rendering Overhead:**
- Problem: Custom isometric grid rendering and Z-sorting.
- Files: `app/src/main/java/com/ericleber/joguinho/renderer/`
- Cause: High number of draw calls per frame.
- Improvement path: Optimize `SpriteCache` and use dirty-rect rendering where possible.

## Fragile Areas

**Biome System:**
- Files: `app/src/main/java/com/ericleber/joguinho/biome/BiomePalette.kt`
- Why fragile: Recently expanded to 120 biomes; manual color mapping and lighting modes.
- Safe modification: Use the `LightingSystem` reactively and verify with automated visual audits.

## Test Coverage Gaps

**Core Logic Testing:**
- What's not tested: Complex combat interactions and monster AI pathing.
- Files: `app/src/main/java/com/ericleber/joguinho/core/GameLogic.kt`, `app/src/main/java/com/ericleber/joguinho/character/SpikeAI.kt`
- Risk: Critical gameplay bugs could go unnoticed after engine refactors.
- Priority: High.

---

*Concerns audit: 2026-05-04*
