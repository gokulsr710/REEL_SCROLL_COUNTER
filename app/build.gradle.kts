import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val firebaseProps = Properties().apply {
    val f = rootProject.file("firebase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.reelcount"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.reelcounter.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "3.1"
        // Challenge mode server (from firebase.properties; empty = challenge mode off)
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseProps.getProperty("apiKey", "")}\"")
        buildConfigField("String", "FIREBASE_DB_URL", "\"${firebaseProps.getProperty("databaseUrl", "")}\"")
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
