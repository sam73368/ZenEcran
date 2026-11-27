plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zenecran"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zenecran"
        minSdk = 26
        targetSdk = 35
        // Numéro de build fourni par GitHub Actions : chaque version publiée est plus récente que la précédente.
        val build = System.getenv("ZEN_BUILD")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    // Clé de signature fixe (déchiffrée par le workflow) : permet d'installer les mises à jour par-dessus.
    val ksFile = System.getenv("KEYSTORE_FILE")
    val ksPass = System.getenv("KEYSTORE_PASSWORD")
    if (ksFile != null && ksPass != null) {
        signingConfigs {
            create("zen") {
                storeFile = file(ksFile)
                storePassword = ksPass
                keyAlias = "zen"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        debug {
            if (ksFile != null && ksPass != null) signingConfig = signingConfigs.getByName("zen")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
