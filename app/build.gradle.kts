plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dichgame.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dichgame.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "2.0"
    }
    // Khóa ký: dùng khóa trong thư mục keystore/ (cùng khóa cho debug và release nên cài đè lên nhau được).
    // Muốn dùng khóa riêng: đặt các biến môi trường KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD (hoặc secrets trên GitHub).
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

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
