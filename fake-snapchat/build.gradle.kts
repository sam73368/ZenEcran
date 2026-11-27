// Fausse appli utilisée UNIQUEMENT par les tests automatiques sur émulateur (jamais publiée).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.snapchat.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.snapchat.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
