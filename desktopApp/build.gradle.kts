import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "com.vircas.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "VirCas"
            packageVersion = "0.2.0"
            description = "Offline-only VirCas virtual gaming hub for macOS"
            vendor = "VirCas"
            macOS {
                bundleID = "com.vircas.desktop"
                packageName = "VirCas"
            }
        }
    }
}
