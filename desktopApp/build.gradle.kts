import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.metadata.extractor)
}

sourceSets {
    main {
        kotlin.srcDirs("src/jvmMain/kotlin")
        resources.srcDirs("src/jvmMain/resources")
    }
}

compose.desktop {
    application {
        mainClass = "com.gallery.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PicasaGalleryViewer"
            packageVersion = "1.0.0"
            description = "Picasa-style Ultra-fast Offline Media Viewer"
            copyright = "© 2026 Gallery Team"

            windows {
                menuGroup = "Gallery"
                upgradeUuid = "6d7e263c-3965-4f4d-8b09-1a4176c12345"
            }
        }
    }
}
