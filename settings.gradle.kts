@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.philburk")
            }
        }
    }
}

rootProject.name = "Gramophone"

// ВОЗВРАЩАЕМ НАШ КАСТОМНЫЙ АУДИОФИЛЬСКИЙ ФОРК
includeBuild(file("media3").toPath().toRealPath().toAbsolutePath().toString()) {
    dependencySubstitution {
        substitute(module("androidx.media3:media3-common")).using(project(":lib-common"))
        substitute(module("androidx.media3:media3-common-ktx")).using(project(":lib-common-ktx"))
        substitute(module("androidx.media3:media3-exoplayer")).using(project(":lib-exoplayer"))
        substitute(module("androidx.media3:media3-exoplayer-midi")).using(project(":lib-decoder-midi"))
        substitute(module("androidx.media3:media3-session")).using(project(":lib-session"))

        // 👇 ВОТ ЭТИ СТРОКИ ИСПРАВЯТ ОШИБКУ MANIFEST MERGER 👇
        // Теперь Gradle не пойдет в интернет за этими модулями, а возьмет наши локальные
        substitute(module("androidx.media3:media3-datasource")).using(project(":lib-datasource"))
        substitute(module("androidx.media3:media3-database")).using(project(":lib-database"))
        substitute(module("androidx.media3:media3-datasource-okhttp")).using(project(":lib-datasource-okhttp"))
    }
}

include(":misc:audiofxstub")
include(":misc:audiofxstub2")
include(":misc:audiofxfwd")
include(":misc:alacdecoder")
include(":hificore")
include(":app")
include(":baselineprofile")