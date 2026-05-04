# Technology Stack

**Analysis Date:** 2026-05-04

## Languages

**Primary:**
- Kotlin 2.0.21 - Core language for Android development.

**Secondary:**
- Java - Used in some generated sources and legacy Android components.
- SQL - Used via Room for database queries.

## Runtime

**Environment:**
- Android SDK (minSdk 24, targetSdk 36)

**Package Manager:**
- Gradle 8.x - Build system and dependency management.
- Lockfile: `gradle.lockfile` (not detected, uses standard Gradle dependency resolution).

## Frameworks

**Core:**
- Android Appcompat 1.7.1 - Base UI components.
- Android Material 1.13.0 - Design system components.
- Room 2.6.1 - Persistence layer (SQLite).
- WorkManager 2.9.1 - Background task scheduling.
- Play App Update 2.1.0 - In-app update integration.

**Testing:**
- JUnit 4.13.2 - Unit testing framework.
- Kotest 5.9.1 - Advanced Kotlin testing framework.
- Espresso 3.7.0 - UI testing framework.

**Build/Dev:**
- Android Gradle Plugin (AGP) 8.13.2 - Android build tools.
- KSP (Kotlin Symbol Processing) 2.0.21-1.0.28 - Annotation processing for Room.
- Kotlin Serialization 1.7.3 - JSON processing.

## Key Dependencies

**Critical:**
- `androidx.room:room-runtime:2.6.1` - Local database management.
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` - Game state serialization.

**Infrastructure:**
- `com.google.android.play:app-update:2.1.0` - Ensuring users have the latest version.
- `androidx.work:work-runtime-ktx:2.9.1` - Background synchronization (if applicable).

## Configuration

**Environment:**
- Configured via `gradle.properties` and `local.properties`.
- Package namespace: `com.ericleber.joguinho`.

**Build:**
- `build.gradle.kts` (root and app module).
- `settings.gradle.kts`.
- `libs.versions.toml` (Version Catalog).

## Platform Requirements

**Development:**
- Android Studio / IntelliJ IDEA with Kotlin plugin.
- JDK 11 or higher.

**Production:**
- Android devices running Android 7.0 (API 24) or higher.

---

*Stack analysis: 2026-05-04*
