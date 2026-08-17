# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
# -renamesourcefileattribute SourceFile

# enabling obfuscation would break some self-reflection in the app
-dontobfuscate

# Сохраняем информацию о дженериках (важно для Gson)
-keepattributes Signature
-keepattributes *Annotation*

# Не даем R8 ломать TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Также стоит сохранить твои модели данных, которые сериализует Gson
-keep class org.akanework.gramophone.logic.api.** { *; }
-keep class org.akanework.gramophone.logic.** { *; }

# reflection by androidx via theme attr viewInflaterClass
-keep class org.akanework.gramophone.logic.ui.ViewCompatInflater { *; }

# reflection by lyric getter xposed
-keep class androidx.media3.common.util.Util {
    public static void setForegroundServiceNotification(...);
}

# JNI
-keep class org.nift4.gramophone.hificore.NativeTrack {
    onAudioDeviceUpdate(...);
    onUnderrun(...);
    onMarker(...);
    onNewPos(...);
    onStreamEnd(...);
    onNewIAudioTrack(...);
    onNewTimestamp(...);
    onLoopEnd(...);
    onBufferEnd(...);
    onMoreData(...);
    onCanWriteMoreData(...);
}


# Сохраняем сигнатуры дженериков (угловые скобки)
-keepattributes Signature

# Сохраняем аннотации Retrofit (@POST, @Field и т.д.)
-keepattributes *Annotation*

# Запрещаем трогать всё, что лежит в папке с API
-keep class org.akanework.gramophone.logic.api.** { *; }

# --- ПРАВИЛА ДЛЯ RETROFIT И СИГНАТУР ---

# САМОЕ ВАЖНОЕ: Запрещаем R8 вырезать информацию о дженериках (<T>)
-keepattributes Signature

# Запрещаем вырезать аннотации (Retrofit использует их для @POST, @GET и т.д.)
-keepattributes *Annotation*

# Сохраняем все классы Retrofit и их методы
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Сохраняем модели данных, которые парсятся из JSON
-keepclassmembers class org.akanework.gramophone.logic.api.LoginResponse { *; }
-keepclassmembers class org.akanework.gramophone.logic.api.Track { *; }
-keep class org.akanework.gramophone.logic.api.** { *; }

# Сохраняем все UI-фрагменты, Compose-компоненты и адаптеры
-keep class org.akanework.gramophone.ui.** { *; }
-keep class org.akanework.gramophone.ui.fragments.** { *; }
-keep class org.akanework.gramophone.ui.components.** { *; }
-keep class org.akanework.gramophone.ui.adapters.** { *; }

-dontwarn android.media.**
-dontwarn org.nift4.**