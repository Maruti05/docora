# Docora

**A local-first, offline Android document workspace.**

Scan, import, view, organize, search and protect personal documents — entirely on device, with no account, no cloud and no network permission.

| | |
|---|---|
| **Package** | `com.vedica.labs.ind.app.docora` |
| **Language** | Kotlin 2.4.10 |
| **UI** | Jetpack Compose (Material 3) |
| **Min SDK** | 26 (Android 8.0) |
| **Target / Compile SDK** | 36 / 37 |
| **Build toolchain** | AGP 9.4.1 · Gradle 9.6.0 · JDK 21 |
| **Architecture** | MVVM + unidirectional data flow, Hilt DI, Room + FTS4 |

---

## Table of contents

- [Privacy model](#privacy-model)
- [Features](#features)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Build commands](#build-commands)
- [Permissions](#permissions)
- [Testing](#testing)
- [Roadmap](#roadmap)
- [License](#license)

---

## Privacy model

Docora's central constraint: **the app has no internet access.**

The manifest actively *removes* the `INTERNET` permission that libraries merge in by default:

```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
```

Consequences of this design:

- **No storage permissions for documents.** Every file is reached through the Storage Access Framework (SAF), which grants access per-URI rather than to the whole filesystem. `local.properties` and index metadata stay in app-private storage.
- **ML Kit text recognition runs on-device.** OCR never uploads an image.
- **Coil has no network fetcher registered.** The image loader can only resolve a `content://` URI or a cached local file.
- **Text encryption keys never leave the Keystore.** AES-256-GCM, non-exportable, hardware-backed.

The manifest comment states it plainly: *"Docora performs every core operation on device."*

---

## Features

> **Scope note:** every item below is implemented and verifiable in the source tree. Planned-but-unbuilt work is listed separately under [Roadmap](#roadmap) so the two are never confused.

### 📷 Scanning

- CameraX capture pipeline with **auto edge detection** and document perspective correction (`ScanImageMath`)
- **Auto-capture** via a pure-Kotlin stability heuristic (`AutoCaptureDecider`) — fires only when the frame is steady and well-exposed
- **6 capture modes**, each tuning detection and enhancement defaults: Auto, Document, ID Card, Receipt, Whiteboard, Photo
- **5 colour filters**: Original, Enhance, Grayscale, Black & White, Magic Colour
- **3 quality tiers** (long-edge 1600/2400/3200 px)
- Torch control (auto/on/off), front/back camera flip
- Multi-page capture merged into a single PDF via PDFBox
- Optional on-device OCR immediately after capture

### 📥 Import & storage

- SAF import — the primary ingestion path
- Optional **"On this device"** browsing via `MediaStore` (the only reason `READ_MEDIA_*` is declared)
- Accepts documents from other apps via `VIEW`, `SEND` and `SEND_MULTIPLE` intent filters
- Imports arrive tagged `SHARED_IN`; scans as `SCANNED`
- SHA-256 checksum per document for duplicate detection
- Launcher **shortcuts** and a **home-screen widget** for Scan / Recent / Favorites / Search

### 🗂 Library

- Sort by name, date modified, date created, size, type or last opened — either direction
- Four layouts: List, Compact, Grid, Large Grid
- Filter chips: All, PDF, Images, Documents, Archives, Favorites, **Needs OCR**
- Favourites, folders, tags, notes
- Trash with configurable retention (7 / 30 / 90 days / forever)
- Thumbnail cache with a configurable limit and a hard 512 MB ceiling (`MAX_THUMBNAIL_CACHE_MB`)

### 🔍 Search

Full-text search over an SQLite **FTS4** virtual table.

- Prefix-aware: typing `ins` matches `insurance` (`prefix = ["2","3"]` index)
- **Safe query parsing** (`SearchQuery`) — user text is tokenised from scratch, so FTS operators (`OR`, `NEAR`, `^`, `"`, `*`, `-`) are stripped rather than passed through or allowed to throw
- **Weighted, explainable ranking** (`SearchRanking`): title phrase `1.0` → title partial `0.78` → tag `0.66` → notes `0.5` → body `0.34` → category `0.26`, plus a recency bonus
- Every hit reports *which* field matched, so a result is never unexplained
- Ranking is computed in Kotlin because Android's FTS4 build has no `bm25()` and no `matchinfo()` scoring
- Recent-search history

### 👁 Viewing

- Native preview for PDF, image and text; other types are handed to a resolving external app
- PDF text extraction via PDFBox, page counts, `PdfRenderer` integration
- Print adapter (`feature/print`)
- Responsive: compact widths get a bottom bar, medium/expanded get a navigation rail

### 🔒 Security

| Feature | Implementation |
|---|---|
| App Lock | `BiometricPrompt` via AndroidX Biometric |
| Auto-lock | Immediately / 1 / 5 / 15 min / never |
| Secure Screen | `FLAG_SECURE` — blocks screenshots and task-preview thumbnails |
| Text encryption | AES-256-**GCM**, non-exportable Keystore key, fresh IV per call |
| Backup | `allowBackup=false`, `fullBackupContent=false` |
| Sharing | Raw paths are never exposed — only `FileProvider` content URIs |

The Keystore key is intentionally **not** bound to user authentication, so background workers can decrypt without a prompt. The source documents this trade-off explicitly: the key stays hardware-backed, while App Lock guards the UI.

### 🎨 Design & accessibility

- Custom design system: spacing scale, shapes, gradients, motion tokens, typography
- Material You dynamic colour, with a first-class dark theme
- `reduceMotion` setting **shortens** animations rather than removing them, so state changes stay visible
- RTL support (`supportsRtl`), 494 localised strings

---

## Architecture

```
ui/          Compose screens + ViewModels — immutable UiState, StateFlow
   ↓
domain/      (folded into core/model for v1)
   ↓
repository/  DocumentRepository · SearchRepository · FolderRepository · TagRepository
   ↓
data/        Room DAOs · SAF gateway · DataStore · handlers · OCR
```

Single `:app` module. The interfaces in `core/` keep the layers swappable for future modularisation — a deliberate v1 trade-off, documented in `settings.gradle.kts`.

**Principles enforced throughout**

- Unidirectional data flow; UI state is immutable
- The UI layer **never** touches files or database connections
- `DocoraResult<T>` wraps every fallible call; no exceptions cross layer boundaries
- Injectable `DispatcherProvider` keeps IO off the main thread and makes it unit-testable
- Dependency inversion via `DocumentHandler` — a new format is a new handler, not a `when` branch

**Document handlers** (`core/handler/`) — one per family, resolved by `DocumentHandlerRegistry`:

`PdfDocumentHandler` · `ImageDocumentHandler` · `TextDocumentHandler` · `OfficeDocumentHandler` · `ArchiveDocumentHandler`

---

## Project structure

```
Docora/
├── app/src/main/java/com/vedica/labs/ind/app/docora/
│   ├── core/
│   │   ├── common/       DocoraResult, DispatcherProvider, DocoraLog
│   │   ├── database/     Room entities, DAOs, converters, mappers
│   │   ├── di/           Hilt modules (Core, Database, Repository)
│   │   ├── files/        AppStorage, ThumbnailStore
│   │   ├── handler/      Per-format DocumentHandlers + registry
│   │   ├── imaging/      ScanImageMath, ScanImageProcessor, AutoCaptureDecider
│   │   ├── ocr/          OcrEngine (ML Kit, on-device)
│   │   ├── repository/   Repository interfaces + implementations
│   │   ├── scanner/      ScanDocumentWriter (images -> PDF)
│   │   ├── search/       SearchQuery, SearchRanking
│   │   ├── security/     AppLockManager, KeystoreCipher, SecureScreenController
│   │   ├── storage/      SafGateway, DeviceDocumentsSource
│   │   └── util/         Hashing, MimeTypes, FileSizeFormatter, DocumentLimits
│   ├── feature/          print/ · widget/
│   ├── ui/
│   │   ├── designsystem/ Theme, colors, spacing, motion, responsive
│   │   ├── components/   Cards, rows, document tiles
│   │   ├── home/ · search/ · settings/ · scanner/ · screens/
│   └── DocoraApplication.kt   Hilt + WorkManager + Coil bootstrap
├── gradle/libs.versions.toml   Version catalog (single source of truth)
└── PRD.md                     Original product specification (64 sections)
```

117 Kotlin files, ~600 KB of source.

---

## Tech stack

| Concern | Choice |
|---|---|
| DI | Hilt 2.60.1 + `androidx.hilt` |
| Database | Room 2.8.5, schema exported to `app/schemas/` |
| Search | SQLite **FTS4** — chosen over FTS5 because it is guaranteed on every supported API level |
| Preferences | DataStore Preferences 1.2.1 |
| Async | Coroutines 1.11.0 + Flow |
| Navigation | Navigation Compose 2.10.2 |
| Adaptive UI | Compose BOM 2026.09.00 + Material 3 Adaptive 1.3.0 |
| Camera | CameraX 1.6.2 |
| PDF | PDFBox Android 2.0.27.0 |
| OCR | ML Kit Text Recognition 16.0.1 (**on-device**) |
| Images | Coil 3.6.3 + coil-gif |
| Serialization | kotlinx-serialization-json 1.11.0 |

Third-party libraries are deliberately minimal — only those with no AndroidX equivalent. The rationale for each is recorded in `libs.versions.toml`.

---

## Getting started

### Prerequisites

| Tool | Version |
|---|---|
| JDK | **21** (pinned in `gradle/gradle-daemon-jvm.properties`) |
| Android SDK | Platform **37**, Build-Tools **37.0.0** |
| Gradle | 9.6.0 — *downloaded automatically by the wrapper* |

The Gradle wrapper and `gradle-wrapper.jar` are committed, so no local Gradle install is needed.

### Setup

1. **Clone the repository**

   ```bash
   git clone https://github.com/<your-username>/Docora.git
   cd Docora
   ```

2. **Point at your SDK** — `local.properties` is git-ignored, so create it locally:

   ```properties
   # Windows
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```

   ```properties
   # macOS / Linux
   sdk.dir=/Users/<you>/Library/Android/sdk
   ```

   Or let Android Studio generate it: **File -> Sync Project with Gradle Files**.

3. **Build and run**

   ```bash
   ./gradlew installDebug      # macOS / Linux
   .\gradlew.bat installDebug  # Windows
   ```

### Two build variants

| Variant | Application ID | Notes |
|---|---|---|
| `debug` | `com.vedica.labs.ind.app.docora.debug` | Installs side-by-side with release |
| `release` | `com.vedica.labs.ind.app.docora` | Minified + resource-shrunk (R8) |

Release signing is **intentionally not committed**. Supply your own keystore out-of-band; `.gitignore` already excludes `*.jks` and `*.keystore` so nothing sensitive can be committed by accident.

---

## Build commands

```bash
./gradlew assembleDebug        # build debug APK
./gradlew installDebug         # build + install
./gradlew bundleRelease        # release AAB
./gradlew clean                # clean build outputs

./gradlew lint                 # Android Lint (abortOnError = true)
./gradlew test                 # JVM unit tests
./gradlew connectedAndroidTest # instrumented tests (device/emulator)
```

`lint` is configured with `abortOnError = true`, so warnings are surfaced but only errors fail the build.

---

## Permissions

| Permission | When it is requested | Why |
|---|---|---|
| `CAMERA` | Just-in-time, when the scanner opens | Capture pages |
| `USE_BIOMETRIC` | Only when App Lock is enabled | Unlock the vault |
| `POST_NOTIFICATIONS` | Android 13+ | Background processing status |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Optional | The "On this device" row only |
| `READ_EXTERNAL_STORAGE` | `maxSdkVersion="32"` | Same, for older devices |

**Never requested:** `INTERNET` (explicitly removed), broad storage access. Camera hardware is declared `required="false"`, so the app installs on devices without a camera and degrades gracefully.

---

## Testing

The test infrastructure is configured — Hilt test runner, `HiltTestApplication`, JUnit 4, Truth, Turbine, Robolectric and Compose UI test are all wired up — but **the test source sets are currently thin**: `androidTest/` contains only the Hilt bootstrap classes, and there are no `src/test/` unit tests yet.

This is worth flagging for anyone picking the project up: several classes were written specifically to be testable in isolation (`SearchRanking`, `SearchQuery`, `AutoCaptureDecider`, `ScanImageMath`), and the code comments reference unit tests that do not yet exist. Writing them is the highest-value next step.

---

## Roadmap

Present in the PRD, **not yet implemented**:

- Background **WorkManager** workers — WorkManager is fully configured for Hilt (`HiltWorkerFactory`, on-demand initialisation, manifest `tools:node="remove"`) but **no workers are enqueued anywhere**. Text extraction and OCR currently run inline.
- Document annotation and editing
- PDF toolkit operations beyond scanning (merge, split, compress, reorder)
- Password-protected PDFs and digital signatures
- Cloud / cross-device sync, and any optional AI features (all AI is to be on-device per PRD §60)

---

## License

No license file is present in this repository. Add one before publishing — for a closed-source app, a proprietary notice; for an open-source release, the license file itself.

---

*This project is built from `PRD.md`, a 64-section product specification. The PRD describes the full target; this README documents what exists today.*