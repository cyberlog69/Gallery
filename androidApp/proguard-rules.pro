# Keep Compose + reflection-free model classes are safe by default, but keep the
# entry points R8 cannot infer through the manifest.
-keep class com.gallery.android.MainActivity { *; }
-keep class com.gallery.android.GalleryApplication { *; }

# Keep data classes used by the line-based metadata codec / GitHub JSON parser.
# They are constructed reflectively by nothing, but the names must survive so
# crash reports stay readable.
-keepclassmembers class com.gallery.core.model.** { *; }
-keepclassmembers class com.gallery.core.persistence.** { *; }

# OkHttp (used by the updater) ships its own rules; these silence the common
# optional-dependency warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
