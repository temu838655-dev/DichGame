plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Số bản dựng do GitHub Actions truyền vào (-PbuildNo=<số lần chạy>), build ở máy thì là 0.
val buildNo = (project.findProperty("buildNo") as String?)?.toIntOrNull() ?: 0

android {
    namespace = "com.dichgame.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dichgame.tv"
        minSdk = 26
        targetSdk = 34
        versionCode = 3 + buildNo
        versionName = "1.2"
        buildConfigField("int", "BUILD_NO", "$buildNo")
    }
    signingConfigs {
        System.getenv("KEYSTORE_FILE")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = requireNotNull(System.getenv("KEYSTORE_PASSWORD"))
                keyAlias = requireNotNull(System.getenv("KEY_ALIAS"))
                keyPassword = requireNotNull(System.getenv("KEY_PASSWORD"))
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
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
    buildFeatures { buildConfig = true }
    // Phần cập nhật trong app dùng chung với app điện thoại (update-common/)
    sourceSets.getByName("main").java.srcDir("$rootDir/update-common/src")
}
