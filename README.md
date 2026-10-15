# Offline Media Gallery for Android

An ultra-lightweight, RAM-optimized, completely offline media gallery application for Android supporting all existing picture and video formats, powered by an on-device offline AI classification engine.

- **Material 3 Design**: Built with Kotlin and Jetpack Compose featuring dynamic theming, fluid pinch-to-zoom timeline grid, sticky date headers, and full-screen viewer with ExoPlayer video playback.
- **Dedicated Albums & Folder Browser**: Instant local directory grouping and album navigation directly mapped to device storage.
- **Non-Destructive Photo Editor**: Real-time interactive adjustments for crop, aspect ratio presets (1:1, 4:5, 16:9, 3:2), arbitrary angle rotation (-45° to +45°), exposure, contrast, highlights, shadows, saturation, temperature, and tint.
- **Offline AI Smart Categories**: Automatic on-device classification into 8 categories (Nature, People, Food, Pets, Documents, City, Vehicles, Screenshots) running entirely on local silicon.

---

## Key Highlights

### 1. 100% Offline & Privacy-First
- **Zero Internet Permissions**: `android.permission.INTERNET` is not requested anywhere in the codebase.
- **On-Device AI Classification**: Uses an on-device feature classification engine operating purely on local pixel buffers (no cloud APIs, no external telemetry).

### 2. Extreme RAM & Performance Optimization (Google Play Standards)
- **Never Decodes Full-Resolution Bitmaps into RAM for Previews**: A 48MP camera photo requires nearly 200MB of RAM uncompressed. Our `ThumbnailEngine` computes exact power-of-two `inSampleSize` downsampling before decoding, keeping thumbnail memory at ~256KB per item (over **700× lower RAM**).
- **Two-Tier Cache System**:
  - **In-Memory LRU Cache**: Strict byte ceiling with automatic eviction of least-recently-used bitmaps to guarantee the app never exhausts RAM.
  - **Coil Image Pipeline**: Memory-mapped disk cache and proactive memory trimming (`onTrimMemory` hooks) to prevent LMKD terminations.
- **Strict Viewport-Bounded Decoding**: Decodes only up to device screen dimensions, recycling intermediate bitmaps during live editor gesture adjustments.

### 3. Comprehensive Format Support
- **Images**: JPEG (`.jpg`, `.jpeg`), PNG (`.png`), WebP (`.webp`), GIF (`.gif`), BMP (`.bmp`), HEIF/HEIC (`.heic`, `.heif`), AVIF (`.avif`), TIFF (`.tif`, `.tiff`), SVG (`.svg`), and Camera RAW formats (`.dng`, `.cr2`, `.nef`, `.arw`).
- **Videos**: MP4 (`.mp4`), MKV (`.mkv`), WebM (`.webm`), AVI (`.avi`), MOV (`.mov`), 3GP (`.3gp`), WMV (`.wmv`), FLV (`.flv`), TS (`.ts`), M4V (`.m4v`).

### 4. On-Device AI Classification Categories
The offline AI classifier automatically scans and tags images into 8 intuitive categories:
- 🌿 **Nature & Landscapes** (foliage, greenery, sky, mountains)
- 👤 **People & Portraits** (faces, portraits, skin tone chromaticity)
- 🍕 **Food & Dining** (meals, warm tones, dining scenes)
- 🐾 **Pets & Animals** (animals, wildlife)
- 📄 **Documents & Text** (scans, receipts, contracts, high horizontal edge density)
- 🏙️ **City & Architecture** (buildings, streets, structural edges)
- 🚗 **Vehicles & Travel** (cars, bikes, transport)
- 📱 **Screenshots** (device UI captures)

---

## Project Structure

```
Gallery/
├── core/                               // Core Business Logic & Models
│   ├── src/commonMain/kotlin/com/gallery/core/
│   │   ├── model/                      // MediaItem, MediaType, AiCategory, ExifData, Album, EditModels
│   │   ├── cache/                      // ThumbnailEngine, MemoryLruCache
│   │   ├── ai/                         // OfflineAiClassifier, PerceptualHasher
│   │   ├── edit/                       // ImageEditEngine (non-destructive pixel adjustments)
│   │   ├── updater/                    // UpdateChecker, UpdateInfo
│   │   └── util/                       // MediaFormatDetector, ExifExtractor
│   └── src/androidMain/kotlin/com/gallery/core/
│       ├── scanner/                    // MediaStoreScanner (ContentResolver reactive Flow)
│       └── edit/                       // AndroidImageEditor (Bitmap decoders & exporter)
│
└── androidApp/                         // Android App (Jetpack Compose Material 3)
    └── src/main/
        ├── AndroidManifest.xml         // Offline permissions (Zero internet permission)
        └── java/com/gallery/android/
            ├── MainActivity.kt         // NavigationBar & permission handling
            ├── GalleryApplication.kt   // Coil image loader configuration & onTrimMemory
            ├── ui/
            │   ├── screens/
            │   │   ├── TimelineScreen.kt      // Date-grouped grid with pinch-to-zoom columns
            │   │   ├── AlbumsScreen.kt        // Albums & folder navigation
            │   │   ├── PhotoEditorScreen.kt   // Non-destructive photo editor
            │   │   ├── AiCategoriesScreen.kt  // Smart categories grid
            │   │   └── MediaViewerScreen.kt   // Fullscreen viewer with ExoPlayer video
            │   ├── components/                // MediaGridItem, Dialogs
            │   ├── theme/                     // Material 3 dynamic color scheme
            │   └── viewmodel/                 // GalleryViewModel
            └── updater/                       // AndroidAppUpdater
```

---

## How to Run & Build

### Building the Android APK
Double-click `build-android.bat` or run:
```cmd
.\gradlew.bat :androidApp:assembleDebug
```
The APK is generated at:
`androidApp\build\outputs\apk\debug\androidApp-debug.apk`

### Running Automated Unit Tests
```cmd
.\gradlew.bat :core:testDebugUnitTest
```
