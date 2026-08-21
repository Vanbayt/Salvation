@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.tasks.PackageAndroidArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.util.Properties

val aboutLibsVersion = "13.1.0" // keep in sync with plugin version
val kotlinVersion = "2.3.0"

plugins {
    id("com.android.application")
    id("com.android.built-in-kotlin")
    id("androidx.baselineprofile")
    kotlin("plugin.parcelize")
    kotlin("plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin")
    id("com.mikepenz.aboutlibraries.plugin.android")
    id("pt.jcosta.resourceplaceholders")
}

android {
    val releaseType = if (project.hasProperty("releaseType")) project.properties["releaseType"].toString()
        else readProperties(file("../package.properties")).getProperty("releaseType")
    val myVersionName = "." + "git rev-parse --short=7 HEAD".runCommand(workingDir = rootDir)
    if (releaseType.contains("\"")) {
        throw IllegalArgumentException("releaseType must not contain \"")
    }
    packaging {
        resources {
            // Это современный аналог той удаленной строки
            excludes += "/lib/**/lib*jni.so"
        }
        jniLibs {
            // Оставляем сжатие библиотек по умолчанию для совместимости
            useLegacyPackaging = true
        }
    }

    namespace = "org.akanework.gramophone"
    compileSdk = 36

    signingConfigs {
        create("release") {
            // Имя файла должно совпадать с тем, что ты закинул в папку app
            storeFile = file("salvation_key.jks")
            storePassword = "0150asdf"
            keyAlias = "key0"
            keyPassword = "0150asdf"
        }
        create("release2") {
            if (project.hasProperty("AKANE2_RELEASE_KEY_ALIAS")) {
                storeFile = file(project.properties["AKANE2_RELEASE_STORE_FILE"].toString())
                storePassword = project.properties["AKANE2_RELEASE_STORE_PASSWORD"].toString()
                keyAlias = project.properties["AKANE2_RELEASE_KEY_ALIAS"].toString()
                keyPassword = project.properties["AKANE2_RELEASE_KEY_PASSWORD"].toString()
            }
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        buildConfig = true
        prefab = true
        compose = true
        viewBinding = true
    }

    packaging {
        dex {
            useLegacyPackaging = false
        }
        jniLibs {
            useLegacyPackaging = false
            // https://issuetracker.google.com/issues/168777344#comment11
            pickFirsts += "lib/arm64-v8a/libdlfunc.so"
            pickFirsts += "lib/armeabi-v7a/libdlfunc.so"
            pickFirsts += "lib/x86/libdlfunc.so"
            pickFirsts += "lib/x86_64/libdlfunc.so"
        }
        resources {
            // https://youtrack.jetbrains.com/issue/KT-48019/Bundle-Kotlin-Tooling-Metadata-into-apk-artifacts
            excludes += "kotlin-tooling-metadata.json"
            // https://issuetracker.google.com/issues/152898926#comment7
            excludes += "META-INF/*.version"
            // https://github.com/Kotlin/kotlinx.coroutines?tab=readme-ov-file#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
            excludes += "DebugProbesKt.bin"
            // covered by AboutLicenses instead
            excludes += "META-INF/androidx/*/*/LICENSE.txt"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = file("lint.xml")
    }

    defaultConfig {
        applicationId = "org.akanework.gramophone"
        // Reasons to not support KK include me.zhanghai.android.fastscroll, WindowInsets for
        // bottom sheet padding, ExoPlayer requiring multidex, vector drawables and poor SD support
        // That said, supporting Android 5.0 costs tolerable amounts of tech debt, and we plan to
        // keep support for it for a while.
        minSdk = 23
        targetSdk = 35
        versionCode = 20
        versionName = "1.0.17"
        ndk {
            abiFilters.add("arm64-v8a")
        }
        if (releaseType != "Release") {
            versionNameSuffix = myVersionName
        }
        buildConfigField(
            "String",
            "MY_VERSION_NAME",
            "\"$versionName$myVersionName\""
        )
        buildConfigField(
            "String",
            "RELEASE_TYPE",
            "\"$releaseType\""
        )
        buildConfigField(
            "boolean",
            "DISABLE_MEDIA_STORE_FILTER",
            "false"
        )
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmarkRelease") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField(
                "boolean",
                "DISABLE_MEDIA_STORE_FILTER",
                "true"
            )
            matchingFallbacks += "release"
        }
        create("nonMinifiedRelease") {
            isMinifyEnabled = false
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField(
                "boolean",
                "DISABLE_MEDIA_STORE_FILTER",
                "true"
            )
            matchingFallbacks += "release"
        }
        create("profiling") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isProfileable = true
            matchingFallbacks += "release"
        }
        create("userdebug") {
            isMinifyEnabled = false
            isProfileable = true
            isJniDebuggable = true
            isPseudoLocalesEnabled = true
            matchingFallbacks += "release"
        }
        debug {
            isPseudoLocalesEnabled = true
            applicationIdSuffix = ".debug"
        }
        forEach {
            it.vcsInfo {
                include = false
            }
            if (project.hasProperty("AKANE_RELEASE_KEY_ALIAS") || project.hasProperty("signing2")) {
                it.signingConfig = signingConfigs[if (project.hasProperty("signing2"))
                    "release2" else "release"]
            }
            it.isCrunchPngs = false // for reproducible builds TODO how much size impact does this have? where are the pngs from? can we use webp?
        }
    }

    sourceSets {
        getByName("debug") {
            // This does NOT remove src/debug/ source sets, hence "debug" is a superset of "userdebug"
            // TODO it seems this broke and that caused Reflections to crash
            java.directories += "src/userdebug/java"
            kotlin.directories += "src/userdebug/kotlin"
            resources.directories += "src/userdebug/resources"
            res.directories += "src/userdebug/res"
            assets.directories += "src/userdebug/assets"
            aidl.directories += "src/userdebug/aidl"
            renderscript.directories += "src/userdebug/renderscript"
            baselineProfiles.directories += "src/userdebug/baselineProfiles"
            jniLibs.directories += "src/userdebug/jniLibs"
            shaders.directories += "src/userdebug/shaders"
            mlModels.directories += "src/userdebug/mlModels"
        }
    }

    // https://gitlab.com/IzzyOnDroid/repo/-/issues/491
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

resourcePlaceholders {
    files.set(listOf("xml/shortcuts.xml"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs = listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-Xannotation-default-target=param-property", // can remove later
        )
    }
}

base {
    archivesName = "Gramophone-${android.defaultConfig.versionName}${android.defaultConfig.versionNameSuffix ?: ""}"
}

baselineProfile {
    dexLayoutOptimization = true
}

// https://stackoverflow.com/a/77745844
tasks.withType<PackageAndroidArtifact> {
    doFirst { appMetadata.asFile.orNull?.writeText("") }
}

aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("config")
        filterVariants.add("release")
    }
    library {
        requireLicense = true
    }
    export {
        // Remove the "generated" timestamp to allow for reproducible builds
        excludeFields = listOf("generated")
    }
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")
    }
}

dependencies {
    // --- Наши локальные модули ---
    implementation(project(":hificore"))
    implementation(project(":misc:alacdecoder"))

    // --- Media3 (Официальные готовые библиотеки) ---
    val media3Version = "1.9.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common-ktx:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    // Я убрал exoplayer-midi, так как он нам не нужен для стриминга Salvation

    // --- Compose & UI ---
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")

    // --- AndroidX Core & Lifecycle ---
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.collection:collection-ktx:1.5.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.3.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.mediarouter:mediarouter:1.8.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.transition:transition-ktx:1.6.0")

    // --- Сеть и Парсинг ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

    // --- Изображения ---
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil:3.0.4")

    // --- Корутины ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- Утилиты и безопасность ---
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.mikepenz:aboutlibraries-compose-m3:$aboutLibsVersion")
    implementation("com.google.android.material:material:1.13.0")
    implementation("me.zhanghai.android.fastscroll:library:1.3.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // --- Профилирование и тестирование ---
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    "baselineProfile"(project(":baselineprofile"))

    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    "userdebugImplementation"(kotlin("reflect", kotlinVersion))
    debugImplementation(kotlin("reflect", kotlinVersion))

    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
}

fun String.runCommand(
    workingDir: File = File(".")
): String = providers.exec {
    setWorkingDir(workingDir)
    commandLine(split(' '))
}.standardOutput.asText.get().removeSuffixIfPresent("\n")

fun readProperties(propertiesFile: File) = Properties().apply {
    propertiesFile.inputStream().use { fis ->
        load(fis)
    }
}
