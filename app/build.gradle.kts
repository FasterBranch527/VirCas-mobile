import java.net.URI
import java.security.MessageDigest
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val ciRunNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 0
val stableDevKeystoreSource = file("signing/vircas-dev.keystore.b64")
val stableDevKeystore = layout.buildDirectory.file("signing/vircas-dev.keystore").get().asFile

// CI runners are ephemeral, so Android's default debug.keystore changes between runs.
// Materialize one project-owned key so every distributable VirCas APK keeps the same signer.
if (!stableDevKeystore.exists()) {
    stableDevKeystore.parentFile.mkdirs()
    stableDevKeystore.writeBytes(
        Base64.getDecoder().decode(stableDevKeystoreSource.readText().trim())
    )
}

private data class PennyAsset(
    val relativePath: String,
    val gitBlobSha: String
)

private fun gitBlobSha(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val pennyAssetBase =
    "https://raw.githubusercontent.com/Ryanholly3/virGeo/1b1768875a3c83cf0b10bf07c430f85c35b3e0dc/js/res/penny_coin"

val pennyAssets = listOf(
    PennyAsset("scene.gltf", "9a4bb64b9b1a7a376d4d8663fe9238e3faa38580"),
    PennyAsset("scene.bin", "ef9cf25149234c808fa4c6fda977af2e148ff227"),
    PennyAsset("textures/01_-_Default_baseColor.jpeg", "fbc0d0dc3cc252d3eb94054c88cdad01e5087437"),
    PennyAsset("textures/01_-_Default_normal.png", "a6cfc2f1d3b96524235adc3814f56b6fabdba18a"),
    PennyAsset("textures/02_-_Default_baseColor.jpeg", "a9534177663e1d6d768019e907b8e299e5d2d898"),
    PennyAsset("textures/02_-_Default_normal.png", "754e551c0f3dd108ce74bc17e958b50b07992a22"),
    PennyAsset("textures/03_-_Default_baseColor.jpeg", "e21595e52ceaebbf0e3b8e9e75c4a914aa8e7e16")
)

val preparePennyAssets = tasks.register("preparePennyAssets") {
    group = "build setup"
    description = "Downloads and verifies the pinned CC BY Lincoln penny model used by Coin Flip."

    doLast {
        val modelDir = layout.projectDirectory.dir("src/main/assets/models/penny").asFile

        pennyAssets.forEach { asset ->
            val destination = modelDir.resolve(asset.relativePath)
            val existingBytes = destination.takeIf { it.isFile }?.readBytes()
            val existingValid = existingBytes != null && gitBlobSha(existingBytes) == asset.gitBlobSha

            if (!existingValid) {
                val remote = URI("$pennyAssetBase/${asset.relativePath}").toURL()
                val downloaded = remote.openStream().use { input -> input.readBytes() }
                val actualSha = gitBlobSha(downloaded)
                check(actualSha == asset.gitBlobSha) {
                    "Penny asset checksum mismatch for ${asset.relativePath}: expected ${asset.gitBlobSha}, got $actualSha"
                }

                destination.parentFile.mkdirs()
                destination.writeBytes(downloaded)
                logger.lifecycle("Prepared verified penny asset: ${asset.relativePath}")
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(preparePennyAssets)
}

android {
    namespace = "com.vircas.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vircas.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 10_000 + ciRunNumber
        versionName = "0.2.$ciRunNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("stable") {
            storeFile = stableDevKeystore
            storePassword = "vircas-dev-signing"
            keyAlias = "vircas-dev"
            keyPassword = "vircas-dev-signing"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            signingConfig = signingConfigs.getByName("stable")
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.github.sceneview:sceneview:2.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
