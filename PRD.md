# Build a Production-Grade Android Document Workspace

You are a senior Android architect, Kotlin engineer, performance engineer, security engineer, and UX/product designer.

Build a **production-ready, local-first Android document management application** using **Kotlin + Jetpack Compose**.

The application must be significantly more polished, faster, cleaner, and more capable than a conventional Android PDF reader or file manager.

The core product concept is:

> **One beautiful, fast, private place to scan, import, view, organize, search, edit, convert, annotate, protect, and share personal documents.**

The application must be designed primarily for Android phones, while also supporting tablets and foldable devices through responsive/adaptive layouts.

Do not build a toy/demo application. Build the project with production architecture, proper error handling, lifecycle awareness, cancellation, state restoration, accessibility, performance optimization, security, testing, and maintainability.

---

# 1. Technology Requirements

Use:

* Kotlin
* Jetpack Compose
* Material 3
* AndroidX
* Navigation Compose
* ViewModel
* Kotlin Coroutines
* Kotlin Flow
* Hilt for dependency injection
* Room for local database
* SQLite FTS5 for full-text search
* DataStore for preferences
* WorkManager for background processing
* Android Storage Access Framework (SAF)
* CameraX
* Android Keystore
* BiometricPrompt
* Coil for image loading/caching
* Kotlin Serialization where appropriate

Use the latest stable versions available at implementation time.

Do not blindly use experimental APIs when a stable API provides the same capability.

Use version catalogs (`libs.versions.toml`) for dependency management.

Use Gradle Kotlin DSL.

Use Kotlin compiler/Compose configuration appropriate for the current stable Android toolchain.

Target the latest stable Android SDK while maintaining a reasonable minimum Android version.

---

# 2. Core Architectural Principles

Use a scalable architecture similar to:

```text
UI
 ↓
Presentation
 ↓
Domain / Use Cases
 ↓
Repository
 ↓
Data Sources
 ↓
Android / Storage / Database
```

Prefer:

* unidirectional data flow
* immutable UI state
* StateFlow
* structured concurrency
* lifecycle-aware collection
* suspend functions
* cancellation propagation
* dependency inversion
* interface-based document handlers

Avoid:

* global mutable state
* unnecessary singletons
* blocking the main thread
* large ViewModels
* database access from composables
* unnecessary recompositions
* unnecessary object allocations
* deprecated Android APIs
* unnecessary third-party dependencies

The UI layer must never directly manipulate files or database connections.

---

# 3. Application Identity

Use a clean placeholder application name such as:

**Docora**

Make the name easy to change later.

Package name:

```text
com.vedica.labs.ind.app.docora
```

Structure the project so the package name and branding can easily be changed.

---

# 4. Primary Product Goals

The application must provide:

1. Document management
2. Document viewing
3. PDF viewing
4. Image viewing
5. Document scanning
6. PDF creation
7. PDF manipulation
8. OCR
9. Full-text search
10. File organization
11. Tags
12. Favorites
13. Recent documents
14. Document metadata
15. Rename/move/copy/delete
16. Share
17. Print
18. Document conversion where technically feasible
19. Annotations
20. Document protection
21. Duplicate detection
22. Storage analysis
23. Offline-first operation
24. Strong privacy
25. Fast UI
26. Beautiful modern UX

---

# 5. Supported File Types

Design a pluggable `DocumentHandler` architecture.

Initially support:

### Documents

* PDF
* TXT
* DOC
* DOCX
* XLS
* XLSX
* PPT
* PPTX
* CSV
* ODT
* ODS
* ODP

### Images

* JPEG
* PNG
* WEBP
* HEIF/HEIC
* GIF
* BMP
* TIFF where supported

### Archives

* ZIP

For formats that cannot be natively rendered, provide:

```text
Open with another application
```

Do NOT claim support for a format unless the application can actually process it.

---

# 6. Android Storage Access Framework

Use Android's official Storage Access Framework.

Support:

* ACTION_OPEN_DOCUMENT
* ACTION_OPEN_DOCUMENT_TREE
* ACTION_CREATE_DOCUMENT
* ACTION_GET_CONTENT where appropriate
* persistable URI permissions
* ContentResolver
* DocumentFile where appropriate
* MIME type filtering

The application must work correctly with:

* internal storage
* SD cards
* Downloads
* Documents
* Android document providers
* cloud-backed document providers exposed through SAF

Do not assume that every URI maps to a traditional filesystem path.

Never depend on:

```text
/storage/emulated/0/...
```

for general document access.

Store document URIs rather than assuming physical filesystem paths.

Gracefully handle:

* revoked URI permissions
* deleted files
* moved files
* unavailable providers
* read-only providers
* temporary access
* I/O failures

---

# 7. Document Database

Use Room.

Design normalized entities approximately around:

```text
Document
Folder
Tag
DocumentTag
Collection
DocumentMetadata
OcrText
DocumentPage
Thumbnail
RecentDocument
Favorite
Annotation
ScanSession
SearchIndex
```

Avoid unnecessary duplication.

Document entity should include concepts such as:

```text
id
uri
displayName
mimeType
extension
size
createdAt
modifiedAt
lastOpenedAt
pageCount
folderId
isFavorite
isTrashed
isEncrypted
ocrStatus
thumbnailUri/reference
checksum/hash
```

Use appropriate indexes.

Use foreign keys where appropriate.

Do not store large binary documents directly inside Room unless there is a compelling technical reason.

---

# 8. Full-Text Search

Implement local full-text search using SQLite FTS5.

Search should cover:

* filename
* document title
* tags
* metadata
* extracted PDF text
* OCR text
* notes

Example:

User searches:

```text
insurance
```

The application should find:

```text
insurance.pdf
car_policy.pdf
scan_0043.jpg
vehicle_document.pdf
```

if the indexed content contains the term.

Support:

* prefix search
* ranking
* filtering
* category filtering
* file-type filtering
* date filtering
* tag filtering
* favorites
* folder filtering

Search must be fast even with thousands of documents.

Never perform a full filesystem scan on every search.

---

# 9. Background Indexing

Use WorkManager.

Background jobs:

* document indexing
* OCR
* thumbnail generation
* metadata extraction
* text extraction
* duplicate detection
* checksum generation

Use:

* constraints
* cancellation
* retry policies
* exponential backoff
* progress reporting

Do not perform expensive processing on the main thread.

Show processing state in the UI.

Example:

```text
Indexing documents...
██████████████░░░░ 74%

124 / 168 documents
```

---

# 10. PDF Viewer

Implement a high-performance PDF viewer.

Requirements:

* smooth vertical scrolling
* page-by-page rendering
* pinch-to-zoom
* double-tap zoom
* page thumbnails
* page navigation
* page number indicator
* jump to page
* search within PDF
* text selection where supported
* annotations
* highlight
* underline
* freehand drawing
* notes
* bookmarks
* rotation
* share
* print

Use Android-native/PDFium-compatible rendering technology where appropriate.

Do not render every page into memory simultaneously.

Use:

* lazy rendering
* page caching
* bitmap reuse
* controlled memory usage
* cancellation when pages leave the viewport

Large PDFs must not cause OutOfMemoryError.

---

# 11. PDF Operations

Provide:

### Page operations

* reorder pages
* delete pages
* rotate pages
* duplicate pages
* extract pages
* split PDF
* merge PDFs

### Creation

Allow:

```text
Images → PDF
Scanned pages → PDF
Multiple documents → PDF
```

Allow users to choose:

* page size
* orientation
* margins
* image quality
* compression level

### PDF metadata

Allow editing where technically supported:

* title
* author
* subject
* keywords

Do not pretend that arbitrary PDF text/layout editing is equivalent to editing a DOCX document.

---

# 12. Document Scanner

Build a professional document scanner using:

* CameraX
* image analysis
* OpenCV where useful
* on-device image processing

Scanner workflow:

```text
Camera
 ↓
Document detection
 ↓
Corner detection
 ↓
Perspective correction
 ↓
Crop
 ↓
Deskew
 ↓
Enhancement
 ↓
OCR
 ↓
PDF/JPEG
```

Provide scanning modes:

* Auto
* Manual
* ID/document
* Receipt
* Whiteboard
* Photo

Features:

* automatic capture
* manual capture
* flash
* focus
* exposure
* retake
* crop
* rotate
* reorder pages
* delete page
* add page
* filters
* brightness
* contrast
* grayscale
* black-and-white
* original
* save as JPEG
* save as PDF

Make scanner interaction fast and minimal-click.

---

# 13. OCR

Implement on-device OCR.

Evaluate suitable Android-compatible OCR technology such as:

* ML Kit Text Recognition
* Tesseract
* other suitable open-source/on-device OCR engines

Do not make a cloud API mandatory.

OCR must support the languages realistically supported by the selected engine.

Architecture:

```text
Image
 ↓
Preprocessing
 ↓
OCR
 ↓
Extracted text
 ↓
Room
 ↓
FTS5
```

OCR should run asynchronously.

Allow users to:

* view extracted text
* copy text
* search text
* re-run OCR
* export extracted text

Show OCR status:

```text
Not processed
Processing
Completed
Failed
```

---

# 14. Image Document Editor

For scanned images and image documents support:

* crop
* rotate
* flip
* resize
* compression
* brightness
* contrast
* grayscale
* black-and-white
* sharpening
* perspective correction
* annotation
* draw
* text overlay

Never modify the original file destructively unless the user explicitly chooses overwrite.

Prefer creating a new version/export.

---

# 15. DOCX / Office Documents

Do not build a DOCX renderer from scratch.

Create an abstraction:

```text
DocumentHandler
 ├── PdfHandler
 ├── ImageHandler
 ├── TextHandler
 ├── OfficeHandler
 └── ArchiveHandler
```

For Office formats:

* detect MIME type
* display metadata
* provide preview where technically possible
* provide "Open with" fallback
* support conversion to PDF where a suitable open-source/local engine is technically feasible

Do not bundle an enormous desktop office suite into the Android application merely to claim Office support.

Prioritize:

1. reliable opening
2. metadata
3. indexing
4. preview
5. conversion

before attempting full Office editing.

---

# 16. File Management

Support:

* rename
* copy
* move
* delete
* restore
* permanent delete
* create folder
* move to folder
* multi-select
* bulk delete
* bulk move
* bulk tag
* bulk favorite
* bulk share where Android permits
* sort
* filter

Sorting:

* name
* date modified
* date created
* size
* type
* last opened

Views:

* list
* compact list
* grid
* large grid

Remember the user's selected view and sorting preference.

---

# 17. Trash / Recycle Bin

Implement an application-level trash system where technically possible.

Features:

* move to trash
* restore
* delete permanently
* empty trash
* automatic cleanup policy

Clearly communicate when a filesystem/provider operation cannot be safely reversed.

---

# 18. Tags and Collections

Allow:

```text
Work
Personal
Finance
Education
Vehicle
Travel
Medical
Projects
Receipts
Important
```

Users can create custom tags.

Collections should be virtual rather than unnecessarily duplicating files.

Examples:

```text
Favorites
Recent
Scanned
PDFs
Images
Documents
Large files
Recently modified
```

---

# 19. Smart Organization

Implement rule-based classification first.

Examples:

```text
invoice.pdf
→ Finance / Invoice

passport.jpg
→ Personal / Identity

car_insurance.pdf
→ Vehicle / Insurance
```

Use filename, MIME type, extracted text, and OCR text.

Do not require an LLM.

Make AI classification an optional future module.

---

# 20. Duplicate Detection

Implement duplicate detection using:

* file size
* MIME type
* cryptographic hash/checksum

For images, optionally implement perceptual similarity in a later version.

UI:

```text
Possible duplicates

invoice.pdf
invoice_copy.pdf

2.4 MB each

[Compare] [Keep one] [Ignore]
```

Never automatically delete duplicates.

---

# 21. Document Comparison

Future/advanced capability:

Allow users to select two PDFs/documents.

Provide:

* side-by-side viewing
* synchronized scrolling
* page comparison
* text difference where technically feasible

Do not claim semantic document comparison until implemented correctly.

---

# 22. Document Preview / Thumbnails

Generate thumbnails asynchronously.

Use caching.

For PDFs:

```text
PDF → first-page thumbnail
```

For Office:

```text
Office → preview if supported
```

For images:

```text
Image → optimized thumbnail
```

Do not load original multi-megapixel images into a grid.

Use appropriately sized thumbnails.

---

# 23. Beautiful UI / UX

The UI is a major product requirement.

Do NOT make the application look like:

* a generic file explorer
* a Windows file manager
* a basic PDF reader
* a conventional settings-heavy Android application

Create a premium modern design.

Use:

* Jetpack Compose
* Material 3 foundations
* custom design system
* adaptive layouts
* subtle motion
* meaningful transitions
* excellent typography
* generous spacing
* visual hierarchy
* contextual actions
* bottom sheets
* gesture support
* drag and drop where appropriate

Avoid:

* excessive rounded cards
* excessive gradients
* unnecessary animations
* visual clutter
* too many buttons
* excessive floating action buttons
* tiny touch targets

The UI must feel fast and purposeful.

---

# 24. Home Screen

Design a dashboard such as:

```text
Documents

Good morning

Search documents...

┌─────────────────────────────────┐
│ Recent                          │
│                                 │
│ PDF   PDF   IMG   DOCX          │
└─────────────────────────────────┘

Collections

Documents       248
Scans            84
Favorites        16

Categories

Work       Finance       Personal
Travel     Vehicle       Education

Recent documents
────────────────────────────
Insurance.pdf
Salary.pdf
Passport.jpg
Project.docx
```

Do not blindly copy this design.

Create a better information architecture after considering usability.

---

# 25. Global Search

Search must be accessible from the main screen.

Provide:

* instant results
* search suggestions
* recent searches
* filters
* file-type chips
* date filters
* tag filters

Search UI should feel similar to a modern system search experience.

---

# 26. Bottom Navigation

Consider a minimal navigation model such as:

```text
Home
Documents
Scan
Search
Settings
```

Do not add navigation items simply to fill space.

On tablets/foldables, adapt to a navigation rail or expanded navigation layout.

---

# 27. Contextual Actions

When a document is selected:

```text
Rename
Move
Copy
Delete
Favorite
Tag
Share
Print
Details
Open with
```

For PDFs:

```text
Annotate
Merge
Split
Extract
Compress
Protect
```

Actions must be contextual to the selected file type.

Never show irrelevant actions.

---

# 28. Multi-Selection

Support long-press and multi-select.

Example:

```text
3 selected

Move   Tag   Share   Delete
```

Use Compose selection patterns.

Make bulk operations efficient.

---

# 29. Android Integration

Take advantage of Android platform capabilities.

Implement where appropriate:

* Share Sheet
* ACTION_SEND
* ACTION_SEND_MULTIPLE
* ACTION_VIEW
* FileProvider
* app shortcuts
* notifications
* widgets where useful
* print framework
* document provider integration
* biometric authentication
* system back handling
* edge-to-edge
* predictive back where stable
* large-screen adaptive layouts
* picture-in-picture only if genuinely useful
* drag and drop
* clipboard APIs
* accessibility APIs

Do not implement Android features merely for novelty.

---

# 30. App Shortcuts

Provide useful launcher shortcuts:

```text
Scan document
Search documents
Recent documents
Favorites
```

---

# 31. Widget

Consider a minimal Android widget:

```text
Documents

Scan
Search
Recent
```

The widget must remain lightweight.

---

# 32. Security and Privacy

This application may contain highly sensitive documents.

Security is a first-class requirement.

Use:

* Android Keystore
* BiometricPrompt
* secure key management
* encrypted sensitive metadata where appropriate
* private app storage where appropriate
* `FLAG_SECURE` for sensitive screens if appropriate
* no unnecessary INTERNET permission
* no cloud upload by default
* no third-party analytics by default

Never log:

* document contents
* OCR text
* filenames containing sensitive information
* document URIs
* authentication data

Production logging must remove sensitive information.

---

# 33. App Lock

Support:

* biometric unlock
* device credential fallback where appropriate
* automatic lock after configurable inactivity

Settings:

```text
Immediately
After 1 minute
After 5 minutes
After 15 minutes
Never
```

Do not store biometric credentials yourself.

Use Android's authentication framework.

---

# 34. Privacy Model

The default architecture should be:

```text
Device
 │
 ├── Documents
 ├── Database
 ├── OCR
 ├── Search
 └── Processing
```

No mandatory server.

No mandatory account.

No mandatory cloud.

All core features must work offline unless a specific Android provider requires network connectivity.

---

# 35. Performance Requirements

Treat performance as a core feature.

The application must:

* avoid unnecessary startup work
* avoid blocking the main thread
* lazy-load documents
* lazy-load thumbnails
* paginate large lists
* use efficient database queries
* cache thumbnails
* cancel obsolete rendering
* avoid memory leaks
* avoid loading complete PDFs into memory
* avoid loading full-resolution images into grids
* use background processing
* minimize recomposition
* use stable Compose state
* use immutable models where appropriate

Benchmark:

* startup time
* scrolling
* PDF opening
* large PDF rendering
* OCR
* search
* thumbnail generation
* database operations
* memory usage
* battery usage

Use Android Studio profiling and Macrobenchmark where appropriate.

---

# 36. Large Document Testing

Explicitly test:

* 1-page PDF
* 100-page PDF
* 500-page PDF
* 1,000-page PDF
* 1 MB image
* 20 MB image
* 100 MB PDF
* thousands of documents
* thousands of thumbnails

The application must degrade gracefully.

Do not assume normal documents are small.

---

# 37. Offline Architecture

The application must remain fully usable without Internet.

Test with network disabled.

Core operations must still work:

* viewing
* scanning
* OCR if the selected OCR engine is offline
* search
* tagging
* renaming
* moving
* PDF creation
* PDF manipulation
* annotations
* organization

---

# 38. Accessibility

Follow Android accessibility guidelines.

Support:

* TalkBack
* content descriptions
* semantic Compose nodes
* sufficient touch targets
* keyboard navigation where relevant
* scalable text
* reduced motion where appropriate
* screen-reader-friendly document actions

Do not rely exclusively on icons.

---

# 39. Theming

Support:

* Light
* Dark
* System default

Use dynamic color when appropriate, but maintain a coherent product identity.

Allow a future custom accent color system.

Do not hardcode colors throughout composables.

Create a central design system:

```text
Color
Typography
Spacing
Shapes
Elevation
Motion
Icons
```

---

# 40. Animations

Animations must communicate state changes.

Use:

* shared transitions where appropriate
* animated content
* list insertion/removal
* bottom-sheet transitions
* navigation transitions
* scanner state transitions

Avoid animation that delays interaction.

Performance always takes priority over visual effects.

---

# 41. Error Handling

Every external operation must have explicit error handling.

Examples:

```text
Unable to open document
Permission revoked
File no longer exists
Storage unavailable
Unsupported format
PDF corrupted
OCR failed
Not enough storage
Operation cancelled
Provider unavailable
```

Messages must be actionable.

Bad:

```text
Error occurred
```

Better:

```text
This document is no longer available at its original location.

[Choose another file]
```

---

# 42. Storage Management

Provide a storage analyzer:

```text
Documents
2.4 GB

PDF
1.2 GB
Images
780 MB
Other
420 MB
```

Provide:

* largest files
* duplicate candidates
* trash size
* cache size
* thumbnails size

Never delete user files automatically without explicit permission.

---

# 43. Import Flow

When importing files:

```text
Select files
 ↓
Analyze
 ↓
Generate metadata
 ↓
Generate thumbnails
 ↓
Extract text
 ↓
Index
```

Do not make the user wait for every background operation before showing imported documents.

Immediately display the imported document and process indexing asynchronously.

---

# 44. Scan Flow

Optimize for minimal clicks:

```text
Open Scan
 ↓
Capture
 ↓
Auto-detect
 ↓
Review pages
 ↓
Save
```

After saving:

```text
Document created
OCR processing...
```

Do not force the user through unnecessary configuration screens.

---

# 45. File Naming

For scanned documents, generate sensible names.

Example:

```text
Scan_2026-09-27_0930.pdf
```

If OCR can confidently identify a title:

```text
Vehicle_Insurance_2026.pdf
```

Allow users to change it.

Never silently overwrite an existing document.

---

# 46. PDF Compression

Provide compression presets:

```text
Original
High quality
Balanced
Small size
```

Show estimated output size before processing when feasible.

Do not destroy the original unless explicitly requested.

---

# 47. Sharing

Use Android's standard sharing mechanisms.

Support:

* share single file
* share multiple files
* share extracted text
* share PDF
* share scanned document

Use secure URI permissions.

Never expose arbitrary internal filesystem paths.

---

# 48. Printing

Use Android Print Framework.

Support printing where the document type permits.

---

# 49. Document Details Screen

Display:

```text
Insurance.pdf

Type
PDF

Size
2.4 MB

Pages
12

Created
27 Sep 2026

Modified
27 Sep 2026

Location
Documents / Vehicle

Tags
Insurance
Vehicle
Important
```

Actions:

```text
Rename
Move
Tags
Share
Open with
```

---

# 50. Settings

Organize settings into:

### Appearance

* Theme
* View mode
* Sort order
* Grid size

### Security

* App lock
* Auto-lock timeout
* Secure screen

### Scanner

* Default quality
* Auto capture
* Default PDF size
* OCR

### Storage

* Trash retention
* Thumbnail cache
* Cache cleanup

### Search

* Indexing
* OCR indexing
* Search scope

### About

* version
* licenses
* privacy
* open-source acknowledgements

---

# 51. Open-Source Dependency Policy

Prefer mature open-source libraries.

Before adding any dependency:

1. Check license.
2. Check Android compatibility.
3. Check maintenance status.
4. Check repository activity.
5. Check transitive dependencies.
6. Check APK/AAB size impact.
7. Check known vulnerabilities.
8. Check whether Android provides a native API that eliminates the dependency.

Do not add libraries merely because they are popular.

Avoid unnecessary dependency proliferation.

Maintain a dependency/license inventory.

Do not use proprietary SDKs for core functionality unless explicitly justified.

---

# 52. Suggested Library Categories

Evaluate libraries/technologies in these categories:

### Android UI

* Jetpack Compose
* Material 3

### Architecture

* AndroidX ViewModel
* Lifecycle
* Navigation
* Hilt

### Storage

* Room
* SQLite FTS5
* DataStore
* Storage Access Framework

### Camera

* CameraX

### Images

* Coil
* Android Bitmap APIs

### Image processing

* OpenCV where necessary

### OCR

* ML Kit and open-source alternatives must be evaluated for licensing, offline operation, accuracy, and APK size

### PDF

Evaluate:

* Android PdfRenderer
* PDFium-based solutions
* Apache PDFBox where Android compatibility/performance is acceptable
* other mature open-source PDF libraries

Do not select a PDF library solely by feature count.

### Background

* WorkManager

### Security

* Android Keystore
* BiometricPrompt

---

# 53. Testing

Implement:

### Unit tests

For:

* repositories
* use cases
* file classification
* MIME detection
* search
* metadata
* document naming
* duplicate detection
* PDF operations

### Instrumentation tests

For:

* SAF
* permissions
* Room
* scanner
* document import
* file operations
* biometric flows where testable

### Compose UI tests

Test:

* navigation
* search
* selection
* document actions
* scanning flow
* settings
* dark mode

### Performance tests

Use Macrobenchmark where appropriate.

---

# 54. Test Data

Create a development-only test dataset containing:

* PDF
* DOCX
* XLSX
* PPTX
* TXT
* JPEG
* PNG
* HEIC
* large files
* corrupted files
* duplicate files
* OCR images
* multilingual documents

Never ship test documents containing real personal data.

---

# 55. Build Quality

The project must compile cleanly.

No:

```text
TODO
FIXME
NotImplementedException
placeholder click handlers
fake data
mock UI pretending to work
```

unless explicitly isolated behind development-only code.

No silent failures.

No swallowed exceptions.

Use meaningful logging only in debug builds.

---

# 56. Compose Performance

Follow Compose performance principles.

Avoid:

* unstable parameters
* unnecessary state reads
* expensive calculations in composition
* unnecessary derived state
* loading images synchronously
* database operations in composition
* creating large collections during recomposition

Use:

* `remember`
* `derivedStateOf` where justified
* stable models
* lazy layouts
* lifecycle-aware state collection

Measure before optimizing.

---

# 57. Adaptive UI

Support:

### Phone

Bottom navigation.

### Tablet

Navigation rail/sidebar.

### Foldable

Responsive two-pane layout where useful.

Example:

```text
┌────────────────────────────────────────────┐
│ Documents                                  │
├──────────────┬─────────────────────────────┤
│ File list    │ Document preview            │
│              │                             │
│ Invoice.pdf  │          PDF                │
│ Passport.jpg│                             │
│ Report.docx  │                             │
└──────────────┴─────────────────────────────┘
```

Use window-size-aware/adaptive Compose APIs rather than hardcoding device dimensions.

---

# 58. Product Differentiation

Do not compete purely on:

```text
"PDF viewer"
```

Position the product around:

> **A private personal document workspace.**

The application should unify:

```text
File manager
+
PDF reader
+
Scanner
+
OCR
+
Document organizer
+
Search engine
+
PDF toolkit
+
Secure vault
```

without becoming visually cluttered.

---

# 59. Future Architecture

Design interfaces so future features can be added without rewriting the application.

Potential future modules:

```text
Cloud sync
Cross-device sync
AI document classification
AI document summarization
Document Q&A
Semantic search
Smart metadata extraction
Invoice extraction
Receipt extraction
Document comparison
Digital signatures
Password-protected PDFs
Cloud backup
Web companion
Desktop companion
```

Do NOT implement these now unless explicitly requested.

The current architecture should simply leave clean extension points.

---

# 60. AI Policy

AI must never be required for basic functionality.

Do not send documents to external AI providers by default.

Future AI functionality should support:

```text
Local/on-device processing
```

where technically feasible.

If AI is added later, clearly separate:

```text
Core document engine
```

from:

```text
Optional AI engine
```

---

# 61. Security Threat Model

Consider:

* unauthorized physical access
* malicious document files
* malicious content providers
* URI permission abuse
* path traversal
* oversized files
* decompression bombs
* corrupted PDFs
* malicious archives
* insecure temporary files
* clipboard leakage
* screenshot leakage
* log leakage

Validate file sizes and types.

Do not extract arbitrary archive contents into unsafe paths.

Use safe temporary directories and cleanup.

---

# 62. UX Principles

The application should follow:

### Discoverability

Users should understand what they can do without tutorials.

### Minimal interaction

Common actions should require as few taps as practical.

### Contextual controls

Show actions relevant to the current document.

### Fast feedback

Immediately acknowledge operations.

### Progressive disclosure

Advanced functionality should not clutter the primary interface.

### Consistency

All document types should share common management interactions.

---

# 63. Visual Design Direction

Create a premium design language inspired by modern productivity applications but do NOT copy any existing application.

Use:

* clean typography
* strong spacing system
* subtle surfaces
* excellent empty states
* elegant document thumbnails
* meaningful iconography
* subtle motion
* high-quality dark mode
* responsive layouts
* clear selection states
* polished bottom sheets

The application should feel like a **premium Android-native product**, not a web page embedded in an Android shell.

---

# 64. Empty States

Create useful empty states.

Example:

```text
No documents yet

Scan your first document
or import files from your device.

[Scan document]
[Import files]
```

Do not use generic:

```text
Nothing here.
```

---

# 65. Onboarding

Keep onboarding extremely short.

Maximum:

```text
2–3 screens
```

Explain:

1. Local/private document management
2. Import and scan
3. Search and organization

Do not require account creation.

Do not request permissions before they are needed.

Use just-in-time permission requests.

---

# 66. Permission Strategy

Do not request broad storage permissions unnecessarily.

Prefer modern Android APIs and SAF.

Request:

* camera permission only when scanning
* notification permission only when notifications are useful
* biometric/device authentication only when user enables app lock

Explain permissions before requesting them when appropriate.

---

# 67. Notifications

Use notifications only when useful.

Examples:

```text
OCR completed
Document processing completed
Large import completed
```

Allow users to disable nonessential notifications.

---

# 68. Backup / Export

Provide an explicit local backup/export mechanism.

Potential format:

```text
Docora Backup
 ├── database metadata
 ├── tags
 ├── folders
 └── optionally copied documents
```

Clearly distinguish:

```text
Metadata backup
```

from:

```text
Full document backup
```

Do not assume Android Auto Backup alone is sufficient for user-visible document backup.

---

# 69. Version 1 MVP

The first production release should prioritize:

### Must have

* Compose UI
* document import
* SAF
* folders
* rename
* move
* copy
* delete
* favorites
* tags
* PDF viewer
* image viewer
* search
* Room
* FTS5
* scanner
* PDF creation
* OCR
* share
* dark mode
* biometric app lock
* responsive UI

### Version 2

* PDF merge
* PDF split
* page extraction
* annotations
* compression
* duplicate detection
* storage analyzer
* Office preview/conversion improvements

### Version 3

* advanced comparison
* smart classification
* semantic search
* optional local AI
* advanced document extraction

Do not over-engineer Version 1 with unnecessary features.

---

# 70. Deliverables

Produce:

1. Complete Android Studio project
2. Gradle configuration
3. Version catalog
4. Package structure
5. Compose design system
6. Navigation
7. Room schema
8. DAOs
9. repositories
10. use cases
11. ViewModels
12. document handlers
13. SAF integration
14. PDF viewer
15. image viewer
16. scanner
17. OCR pipeline
18. search/indexing
19. WorkManager jobs
20. security layer
21. settings
22. error handling
23. unit tests
24. instrumentation tests
25. Compose UI tests
26. performance benchmarks where practical
27. README
28. dependency/license documentation

---

# 71. Implementation Rules

Before implementing a complex feature:

1. Identify the Android-native API.
2. Identify open-source libraries that are actually necessary.
3. Check license compatibility.
4. Check maintenance status.
5. Check Android compatibility.
6. Evaluate APK/AAB size.
7. Evaluate performance.
8. Implement behind an interface.
9. Add tests.
10. Integrate with the UI.

Do not introduce dependencies without justification.

Do not use deprecated APIs when a current Android API exists.

Do not sacrifice security for convenience.

Do not sacrifice performance for unnecessary animations.

Do not sacrifice usability for feature count.

---

# 72. Development Process

Implement incrementally.

First build:

```text
Project
 ↓
Architecture
 ↓
Design system
 ↓
Navigation
 ↓
Room
 ↓
SAF
 ↓
Document list
 ↓
PDF/image viewer
 ↓
Search
 ↓
Scanner
 ↓
OCR
 ↓
PDF operations
 ↓
Security
 ↓
Advanced features
```

After each major module:

* compile
* run tests
* inspect UI
* check memory
* check lifecycle behavior
* check error handling
* check accessibility

Do not implement the entire project as one giant code generation operation.

---

# 73. Final Quality Standard

The finished application must feel like a serious commercial Android application.

Evaluate it against:

### Performance

Does it remain responsive with thousands of files?

### UX

Can a user scan/import and find a document within seconds?

### UI

Does it look polished enough to compete visually with premium productivity applications?

### Security

Are sensitive documents protected appropriately?

### Reliability

What happens when a file disappears, permissions are revoked, storage is full, or a document is corrupted?

### Maintainability

Can another engineer add a new document format without rewriting the application?

### Android integration

Does the application use Android's native capabilities rather than unnecessarily recreating them?

### Privacy

Can the application perform its core functionality without uploading user documents?

These criteria are more important than the number of features.

Start by creating the project architecture, dependency catalog, database model, document-handler interfaces, Compose design system, and initial navigation. Then implement the MVP incrementally and verify each subsystem before moving to the next.
