# ======================================================================
# Salvation ProGuard / R8 Configuration
# Optimized for Android & Shared Multiplatform Desktop Architecture
# ======================================================================

# --- Basic / General Optimization & Debugging ---
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses

# Prevent stripping generic type arguments (critical for JSON serialization & reflection)
-keepattributes *Signature*

# --- Kotlin & Coroutines ---
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- JSON / Data Serialization (Gson & Multiplatform Models) ---
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Preserve all API and domain models shared across Android & Desktop
-keep class org.akanework.gramophone.logic.api.** { *; }
-keepclassmembers class org.akanework.gramophone.logic.api.** { *; }
-keep class org.akanework.gramophone.logic.offline.** { *; }
-keepclassmembers class org.akanework.gramophone.logic.offline.** { *; }
-keep class org.akanework.gramophone.logic.utils.data.** { *; }
-keepclassmembers class org.akanework.gramophone.logic.utils.data.** { *; }

# --- Network: Retrofit 2 & OkHttp 3/4/5 ---
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# --- Room Database & SQLite ---
-dontwarn androidx.room.**
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# --- Image Loading: Coil 3 ---
-dontwarn coil3.**
-keep class coil3.** { *; }

# --- Media3 / ExoPlayer & Audio Engine ---
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep class * extends androidx.media3.session.MediaLibraryService { *; }
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keep class org.akanework.gramophone.logic.GramophonePlaybackService { *; }
-keep class org.akanework.gramophone.logic.utils.exoplayer.** { *; }

# Media3 Util reflection
-keep class androidx.media3.common.util.Util {
    public static void setForegroundServiceNotification(...);
}

# --- JNI & Native Libraries (HiFiCore & ALAC Decoders) ---
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.nift4.**
-keep class org.nift4.gramophone.hificore.** { *; }
-keepclassmembers class org.nift4.gramophone.hificore.NativeTrack {
    *;
}
-keep class org.akanework.gramophone.alacdecoder.** { *; }
-keepclassmembers class org.akanework.gramophone.alacdecoder.** { *; }

# --- Jetpack Compose & UI Components (Shared Desktop & Mobile) ---
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep class org.akanework.gramophone.ui.components.desktop.** { *; }
-keepclassmembers class org.akanework.gramophone.ui.components.desktop.** { *; }
-keep class org.akanework.gramophone.ui.** { *; }
-keep class org.akanework.gramophone.logic.ui.ViewCompatInflater { *; }

# System audio / vendor warnings
-dontwarn android.media.**