# MDfy ProGuard Rules

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# ── Jetpack Compose ───────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

# ── Retrofit + OkHttp ────────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Kotlinx Serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep @kotlinx.serialization.Serializable class * { *; }

# ── Media3 / ExoPlayer ───────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Spotify Auth ──────────────────────────────────────────────────────────────
-keep class com.spotify.** { *; }
-dontwarn com.spotify.**

# ── jaudiotagger (ID3 теги) ───────────────────────────────────────────────────
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ── Coil ─────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Общие правила ────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
