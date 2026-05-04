# Coding Conventions

**Analysis Date:** 2026-05-04

## Naming Patterns

**Files:**
- PascalCase: `GameLogic.kt`, `SpriteCache.kt`.

**Functions:**
- camelCase: `updateGame()`, `handleInput()`.

**Variables:**
- camelCase: `isMoving`, `currentBiome`.

**Types:**
- PascalCase: `GameState`, `Direction`.

## Code Style

**Formatting:**
- standard Android/Kotlin style.
- Indent: 4 spaces.

**Linting:**
- Standard Android Lint rules (config in `build.gradle.kts`).

## Import Organization

**Order:**
1. Android/Java/Kotlin standard libraries (`android.*`, `java.*`, `kotlin.*`).
2. Third-party libraries (`androidx.*`, `com.google.*`).
3. Project-specific imports (`com.ericleber.joguinho.*`).

**Path Aliases:**
- None used (standard relative paths/packages).

## Error Handling

**Patterns:**
- Try-catch blocks used for risky I/O (Persistence, Audio) and Canvas locking.
- Return nullable types or default values for soft failures.

## Logging

**Framework:** `Logger.kt` (custom wrapper around `android.util.Log`).

**Patterns:**
- `Logger.d()` for debug info.
- `Logger.e()` for critical failures with tag-based filtering.

## Comments

**When to Comment:**
- Portuguese (PT-BR) documentation for requirements and complex logic blocks.
- Requirements (e.g., `// Requisito 22.7`) are often cited in comments.

**JSDoc/TSDoc:**
- KDoc style for public functions and classes.

## Function Design

**Size:** Preference for short functions, though `GameLogic.kt` contains some very large ones.

**Parameters:** mostly direct arguments; some state objects passed to renderers.

**Return Values:** mostly unit for update methods; expressions for math/logic.

## Module Design

**Exports:** Public classes/functions for cross-package communication.

**Barrel Files:** Not applicable in Kotlin (package-level visibility used).

---

*Convention analysis: 2026-05-04*
