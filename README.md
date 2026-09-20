# Cross-Platform Offline Media Gallery (Android & Windows)

An ultra-lightweight, RAM-optimized, completely offline media gallery application supporting all existing picture and video formats, powered by an on-device offline AI classification engine.

- **Android Client**: Modern Material 3 interface built with Kotlin and Jetpack Compose featuring dynamic theming, fluid pinch-to-zoom timeline grid, sticky date headers, **dedicated Albums & folder browser**, AI smart categories, Material 3 vector branding, and full-screen viewer with video playback.
- **Windows Desktop Client**: Faithful recreation of the classic **Google Picasa Photo Viewer** — instant startup, sleek translucent dark backdrop, **iconic 5-blade Picasa aperture logo & taskbar icon**, floating control pill, smooth cursor-directed pan/zoom, and bottom thumbnail filmstrip.

---

## Key Highlights

### 1. 100% Offline & Privacy-First
- **Zero Internet Permissions**: `android.permission.INTERNET` is not requested anywhere in the codebase.
- **On-Device AI Classification**: Uses an on-device feature classification engine operating purely on local pixel buffers (no cloud APIs, no external telemetry).

### 2. RAM & Performance Optimization
- **Never Decodes Full-Resolution Bitmaps into RAM for Previews**: A 48MP camera photo requires nearly 200MB of RAM uncompressed. Our `ThumbnailEngine` computes exact power-of-two `inSampleSize` downsampling before decoding, keeping thumbnail memory at ~256KB per item (over **700× lower RAM**).
- **Two-Tier Cache System**:
  - **In-Memory LRU Cache**: Strict byte ceiling (default 32MB) with automatic eviction of least-recently-used bitmaps to guarantee the app never exhausts RAM.
  - **Disk Cache**: Persistent pre-rendered thumbnails for instant subsequent directory openings.
- **Streaming Directory Scanning**: Desktop scanning uses Java NIO `Files.newDirectoryStream` to incrementally stream items without buffering tens of thousands of file handles into RAM.

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

## Desktop UX: The Classic Picasa Photo Viewer Experience

The Windows client brings back the beloved Google Picasa Photo Viewer workflow:
- **Floating Translucent Control Pill**:
  - `◀` / `▶` Previous & Next photo navigation
  - Zoom Slider, Zoom In (`+`), Zoom Out (`-`), and 1:1 Actual Size / Fit toggle
  - Rotate Left (↺) and Rotate Right (↻)
  - 🎬 Slideshow Mode with auto-advance
  - ℹ️ Detailed EXIF metadata inspector (Camera make, model, aperture, shutter speed, ISO, focal length, date)
  - 🏷️ AI Classification badge (e.g. `🌿 Nature 94%`)
  - 📂 Folder selector
- **Bottom Thumbnail Filmstrip**:
  - Horizontally scrolling carousel of neighboring photos in the directory.
  - Cyan selection highlight and auto-scroll to keep the active photo centered.
- **Smooth Viewport & Gestures**:
  - Mouse wheel zoom smoothly scales toward cursor.
  - Click-and-drag to pan zoomed images.
- **Keyboard Shortcuts**:
  - `Left` / `Right Arrow`: Previous / Next photo
  - `Spacebar`: Start / Pause slideshow
  - `Escape`: Close viewer / Exit fullscreen
  - `+` / `-`: Zoom in / Zoom out
  - `0`: Reset zoom to Fit
  - `R`: Rotate 90° clockwise (`Ctrl+R` for counterclockwise)
  - `F`: Toggle bottom filmstrip visibility
  - `I`: Toggle EXIF information overlay
  - `Delete`: Delete current photo

---

## Project Structure

```
Gallery/
├── core/                               // Shared Multiplatform Core
│   ├── src/commonMain/kotlin/com/gallery/core/
│   │   ├── model/                      // MediaItem, MediaType, AiCategory, ExifData, Album
│   │   ├── cache/                      // ThumbnailEngine, MemoryLruCache
│   │   ├── ai/                         // OfflineAiClassifier (feature-based on-device classifier)
│   │   └── util/                       // MediaFormatDetector, ExifExtractor
│   ├── src/jvmMain/kotlin/com/gallery/core/
│   │   ├── scanner/                    // DesktopFileScanner (NIO streaming)
│   │   └── decoder/                    // DesktopImageDecoder (subsampled ImageReader)
│   └── src/androidMain/kotlin/com/gallery/core/
│       └── scanner/                    // MediaStoreScanner (ContentResolver reactive Flow)
│
├── desktopApp/                         // Windows Desktop App (Compose Multiplatform)
│   └── src/jvmMain/kotlin/com/gallery/desktop/
│       ├── Main.kt                     // Window entry point & keyboard bindings
│       ├── ui/
│       │   ├── PicasaViewerScreen.kt   // Main viewer canvas & gestures
│       │   ├── FloatingControlBar.kt   // Floating translucent Picasa pill
│       │   ├── Filmstrip.kt            // Bottom thumbnail carousel
│       │   ├── ExifPanel.kt            // Metadata inspection overlay
│       │   └── theme/                  // Picasa dark translucent styling
│       └── viewmodel/                  // DesktopGalleryViewModel
│
└── androidApp/                         // Android App (Jetpack Compose Material 3)
    └── src/main/
        ├── AndroidManifest.xml         // Offline permissions (Zero internet permission)
        └── java/com/gallery/android/
            ├── MainActivity.kt         // NavigationBar & permission handling
            ├── GalleryApplication.kt   // Coil image loader configuration
            ├── ui/
            │   ├── screens/
            │   │   ├── TimelineScreen.kt      // Date-grouped grid with pinch-to-zoom columns
            │   │   ├── AiCategoriesScreen.kt  // Smart categories grid
            │   │   └── MediaViewerScreen.kt   // Fullscreen viewer with ExoPlayer video
            │   ├── components/                // MediaGridItem
            │   └── theme/                     // Material 3 dynamic color scheme
```

---

## How to Run & Build

### Running the Windows Desktop App
Double-click `run-desktop.bat` or run:
```cmd
run-desktop.bat
```
Alternatively, via Gradle:
```cmd
.\gradlew.bat :desktopApp:packageUberJarForCurrentOS
```
The standalone executable JAR is generated at:
`desktopApp\build\compose\jars\PicasaGalleryViewer-windows-x64-1.0.0.jar`

### Building the Android APK
Double-click `build-android.bat` or run:
```cmd
.\gradlew.bat :androidApp:assembleDebug
```
The APK is generated at:
`androidApp\build\outputs\apk\debug\androidApp-debug.apk`

### Running Automated Unit Tests
```cmd
.\gradlew.bat :core:jvmTest
```
