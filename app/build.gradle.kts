import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing keys stay outside the repository; without them the debug build still works.
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystoreFile.exists()) load(FileInputStream(keystoreFile))
}

android {
    namespace = "com.drbaysinger.audiobc"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.drbaysinger.soundbc"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        if (keystoreFile.exists()) create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation("junit:junit:4.13.2")
}
