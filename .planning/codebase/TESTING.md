# Testing Patterns

**Analysis Date:** 2026-05-04

## Test Framework

**Runner:**
- JUnit 4.13.2
- Kotest 5.9.1 (JUnit 5 Runner)

**Assertion Library:**
- Kotest assertions and JUnit asserts.

**Run Commands:**
```bash
./gradlew test         # Run all unit tests
./gradlew connectedCheck # Run instrumented tests on device
```

## Test File Organization

**Location:**
- separate: `app/src/test/java/` (Unit) and `app/src/androidTest/java/` (Instrumented).

**Naming:**
- PascalCase with `Test` suffix: `CicloDeVidaIntegracaoTest.kt`, `ExampleUnitTest.kt`.

**Structure:**
```
app/src/test/java/com/ericleber/joguinho/
app/src/androidTest/java/com/ericleber/joguinho/
```

## Test Structure

**Suite Organization:**
```kotlin
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
```

**Patterns:**
- **Given-When-Then:** common in integration tests.
- **Mocking:** `FakeSurfaceHolder.kt` used to simulate Android Canvas environment.

## Mocking

**Framework:** Manual fakes and potentially Room testing library.

**Patterns:**
```kotlin
val fakeHolder = FakeSurfaceHolder()
// pass to renderer to verify drawing calls
```

**What to Mock:**
- Android Framework components (SurfaceHolder, Bitmaps).
- Database DAOs (using in-memory Room DB).

## Fixtures and Factories

**Test Data:**
- Manual creation of `GameState` and `Hero` objects.

**Location:**
- `app/src/androidTest/java/com/ericleber/joguinho/` for shared fakes.

## Coverage

**Requirements:** None explicitly enforced in build files.

## Test Types

**Unit Tests:**
- Basic logic verification (PCG math, State changes).

**Integration Tests:**
- lifecycle testing (`CicloDeVidaIntegracaoTest.kt`).
- persistence flows (`FluxoPersistenciaIntegracaoTest.kt`).

**E2E Tests:**
- Espresso used for basic UI interaction verification.

---

*Testing analysis: 2026-05-04*
