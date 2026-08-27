import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.desktop.windows_x64)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    // Корутины
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Сеть
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Изображения
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // JSON
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "org.akanework.gramophone.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Salvation"
            packageVersion = "1.0.0"
            modules("java.sql", "java.desktop", "java.logging", "java.naming", "jdk.unsupported", "java.management", "java.instrument")
        }
    }
}

tasks.withType<JavaExec> {
    environment("_JAVA_AWT_WM_NONREPARENTING", "1")
}
