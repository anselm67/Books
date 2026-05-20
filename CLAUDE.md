# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (Kotlin) for managing a personal book library — scan ISBNs to look up metadata, organize with labels, search full-text, import/export as ZIP archives.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "com.anselm.books.ExampleUnitTest"

# Lint
./gradlew lint

# Clean
./gradlew clean
```

Minimum SDK: 31 (Android 12). Compile/target SDK: 36. JVM target: 17.

## Architecture

**Pattern:** MVVM with Repository, single-Activity with Navigation Component.

### Key layers

**Application singleton (`BooksApplication`)** — central service locator. Holds the Room database, `BookRepository`, `ImageRepository`, `LookupService`, and the shared OkHttp client (rate-limited via a custom request interceptor). Progress reporting for long-running operations is coordinated here.

**Data layer (`database/`)**
- `BookDatabase` — Room database with five entities: `Book`, `BookFTS` (FTS4), `Label`, `LabelFTS` (FTS4), `BookLabels` (join).
- `BookDao` — complex queries with filtering, sorting, and Paging 3 support.
- `BookRepository` — single async facade over the DAO; all DB access goes through here.
- `Query` — encapsulates filter/sort state passed through the UI.

**ViewModel (`BookViewModel`)** — Paging 3 (`Pager` / `PagingData`) with 50-item pages; filters and sort order drive a `MutableStateFlow` that invalidates the pager.

**UI (`ui/`)**
- Navigation Component + Safe Args for type-safe fragment arguments.
- View Binding throughout (no `findViewById`).
- Fragments: `home` (list), `scan` (CameraX + ML Kit barcode), `search` (FTS), `details`, `edit/*`, `pager`, `cleanup`, `settings`.

**Lookup (`lookup/`)**
- `LookupService` coordinates multiple ISBN lookup backends in priority order.
- Backends: `GoogleBooksClient`, `BnfClient`, `OclcClient`, `OpenLibraryClient`, `iTuneClient`, `AmazonImageClient`.
- Base classes `JsonClient` / `XmlClient` / `SimpleClient` share OkHttp wiring and coroutine dispatch.
- Active backends are configured via SharedPreferences.

**Image handling**
- `ImageRepository` — local cache with MD5-based deduplication; images stored under app files dir.
- Glide 5 for loading (KSP-generated API).
- HEIF write support via `HeifWriter`.

**Import/Export (`ImportExport`)** — ZIP-based archive of the JSON-serialised library and cover images; uses `FileProvider` for sharing.

## Active migration goals

The app is undergoing a planned modernisation in three phases, worked incrementally so it stays functional throughout:

1. **Dependency update** — bump all libraries in `gradle/libs.versions.toml` to their latest stable versions and resolve any resulting API breakage. Do this first so new Compose code is written against current APIs.

2. **Launcher icon refresh** — replace the current icon with an improved adaptive icon design.

3. **Jetpack Compose migration (incremental)** — convert the UI screen by screen, embedding `ComposeView` inside existing fragments while the XML/Navigation Component shell remains. Migration order: Settings first, then negotiate the next screen after each one lands. Do not do a full rewrite in one pass. When migrating a screen:
   - Keep the existing Fragment and navigation graph entry; replace the XML layout body with a `ComposeView`.
   - Use Coil instead of Glide for any new Compose image-loading code (Glide is View-oriented).
   - Compose Navigation replaces the XML nav graph only once all screens are migrated.

## Dependencies (version catalog)

Versions are defined in `gradle/libs.versions.toml`. Key libraries:

| Area | Library |
|------|---------|
| UI | AndroidX AppCompat, Material 3, ConstraintLayout, Navigation |
| Lifecycle | Lifecycle ViewModel/LiveData, Paging 3 |
| Database | Room 2.8 (KSP) with FTS4 |
| Networking | OkHttp 5 |
| Images | Glide 5 (KSP), CameraX 1.5, HEIF |
| Scanning | ML Kit Barcode 17 |
| Language | Kotlin 2.1, KSP 2.1, Parcelize, Reflect |
