# Offline Media Gallery for Android

A lightweight media gallery for Android built with Kotlin and Jetpack Compose. It
browses the device library through `MediaStore`, plays video with Media3, edits
photos non-destructively, and runs a small **heuristic** on-device analysis pass
for categories, search and duplicate detection.

No model binary is bundled. Nothing about your photos leaves the device, with one
exception that you can ignore entirely: the in-app updater contacts GitHub to check
for new releases, and that is the only reason the app declares `INTERNET`.

## What it actually does

- **Timeline, albums, viewer**: date-grouped grid, folder albums, pinch-to-zoom
  columns, full-screen viewer with ExoPlayer, share, and favourite.
- **Non-destructive editor**: crop with aspect presets, straighten, and
  exposure/contrast/saturation/temperature/tint. Edits are stored as JSON, and
  exporting can save a copy or overwrite in place with a `.bak` backup.
- **Offline category pass**: a decision tree over colour and edge statistics tags
  photos as Nature, People, Food, Documents, City, Screenshots, or Uncategorized.
  This is a heuristic, not a neural network; accuracy is best-effort.
- **Keyword search**: file name, album, tags and category names, lightly re-ranked
  by a 256-D hand-crafted colour/structure descriptor. It is not CLIP and does not
  understand arbitrary concepts.
- **Face candidates**: skin-chroma blob detection, grouped by appearance similarity
  into "People" clusters. This is not identity recognition and it does not learn
  who someone is.
- **Document layout**: detects text-like bands and light page backgrounds so
  receipts/scans land in Documents. It does **not** transcribe text; there is no
  OCR engine in this project.
- **Duplicate groups**: 64-bit dHash comparison.
- **Persistence**: favourites, tags, edit state, person names and cached analysis
  survive restarts via an atomic file-backed store in `filesDir`.

## Privacy and permissions

| Permission | Why |
| --- | --- |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Read the photo/video library (Android 13+). |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14+ partial "Select photos" access. |
| `READ_EXTERNAL_STORAGE` (≤ API 32) | Legacy library read. |
| `WRITE_EXTERNAL_STORAGE` (≤ API 28) | Legacy delete support. |
| `INTERNET` | Update checks against the GitHub releases API only. |
| `POST_NOTIFICATIONS` | Optional update-ready notification. |
| `REQUEST_INSTALL_PACKAGES` | Install a downloaded update APK. |

There is no analytics, no crash reporting and no upload of media or metadata.

## Project layout

```
core/
  commonMain/  models, heuristic analysis (OfflineVisionEngine,
               FaceDetectionEngine, DocumentLayoutAnalyzer, PerceptualHasher),
               edit engine, search, metadata store + codec, updater parsing
  androidMain/ MediaStore scanner, bitmap decode/edit, atomic file persistence,
               MediaAnalyzer (256 px descriptor pass, 640 px document pass)
androidApp/
  MainActivity, GalleryViewModel, Compose screens, Coil/Media3, AndroidAppUpdater
```

## Build and test

```cmd
set JAVA_HOME=C:\path\to\jdk-17-or-21
gradlew.bat :core:testDebugUnitTest
gradlew.bat :androidApp:assembleDebug
```

Release builds use `keystore.properties` (or `GALLERY_KEYSTORE_FILE`,
`GALLERY_KEYSTORE_PASSWORD`, `GALLERY_KEY_ALIAS`, `GALLERY_KEY_PASSWORD`) when
present. Without them the release variant is left unsigned rather than being
signed with the debug key. R8 shrinking is enabled for release.

## Known limitations

- The category pass is colour/edge heuristics; it confuses scenes with similar
  palettes. Night shots, snow and low-contrast photos are weak.
- Face detection misses profile faces, small faces and dark skin under poor light.
  The People shelf only shows groups that appear in at least 3 photos, with low
  confidence candidates filtered out, but it still groups by appearance rather than
  identity. Treat it as a convenience shelf, not face recognition.
- Document and receipt detection finds layout, not words. Text search over
  document contents is not available.
- Search requires a textual signal (name, album, tag, category) to return results;
  the descriptor alone only re-ranks.
- The updater trusts the GitHub release JSON and verifies the APK's SHA-256 only
  when the release asset includes a `digest` field.
