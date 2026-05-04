# External Integrations

**Analysis Date:** 2026-05-04

## APIs & External Services

**App Management:**
- Google Play In-App Updates - Used for notifying and applying app updates.
  - SDK/Client: `com.google.android.play:app-update`
  - Implementation: `com.ericleber.joguinho.update`

## Data Storage

**Databases:**
- SQLite (Local)
  - Connection: Managed via Room.
  - Client: Room Persistence Library (`AppDatabase`).
  - Schema Location: `app/schemas` (for migrations).

**File Storage:**
- Local filesystem only.
- `FileProvider` - Used for sharing images (e.g., Score sharing).
  - Authority: `${applicationId}.provider`

**Caching:**
- `SpriteCache` - Custom in-memory cache for game assets (bitmaps).
  - Implementation: `com.ericleber.joguinho.renderer.SpriteCache`

## Authentication & Identity

**Auth Provider:**
- None (Local session only).

## Monitoring & Observability

**Error Tracking:**
- None detected (Local logging only).

**Logs:**
- `Logger.kt` - Custom wrapper around `android.util.Log`.

## CI/CD & Deployment

**Hosting:**
- Google Play Store (target).

**CI Pipeline:**
- None detected in repository.

## Environment Configuration

**Required env vars:**
- None required for local build (standard Android properties).

**Secrets location:**
- `local.properties` (for developer-specific paths/keys).

## Webhooks & Callbacks

**Incoming:**
- None.

**Outgoing:**
- None.

---

*Integration audit: 2026-05-04*
