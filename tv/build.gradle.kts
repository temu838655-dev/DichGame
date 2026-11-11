plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dichgame.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dichgame.tv"
        minSdk = 26
        targetSdk = 34
        versionCode = 3 + ((project.findProperty("buildNo") as String?)?.toIntOrNull() ?: 0)
        versionName = "1.2"
    }
    // Cùng cách ký với app điện thoại, xem app/build.gradle.kts.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "$rootDir/keystore/dichgame.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "dichgame123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "dichgame"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "dichgame123"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
