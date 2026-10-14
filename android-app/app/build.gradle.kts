plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ccodeKeystorePath = System.getenv("CCODE_KEYSTORE") ?: ""
val ccodeKeystorePassword = System.getenv("CCODE_KEYSTORE_PASSWORD") ?: ""
val ccodeKeyAlias = System.getenv("CCODE_KEY_ALIAS") ?: "ccode-release"

android { namespace = "com.ccode.code"; compileSdk = 35
    signingConfigs {
        create("release") {
            if (ccodeKeystorePath.isNotBlank()) {
                storeFile = file(ccodeKeystorePath)
                storePassword = ccodeKeystorePassword
                keyAlias = ccodeKeyAlias
                keyPassword = ccodeKeystorePassword
            }
        }
    }
    defaultConfig {
        applicationId = "com.ccode.code"; minSdk = 31; targetSdk = 35; versionCode = 17; versionName = "0.2.6"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
}

kotlin { jvmToolchain(21) }

android {
    buildFeatures { compose = true }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.android.tools.build:apksig:8.5.2")
}
