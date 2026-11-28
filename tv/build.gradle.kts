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
    // Không có fallback sang khóa cũ hoặc mật khẩu mặc định.
    // Release chỉ được ký khi CI cung cấp đủ thông tin từ GitHub Secrets.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "$buildDir/UNCONFIGURED_RELEASE_KEYSTORE")
            storePassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()
            keyAlias = System.getenv("KEY_ALIAS").orEmpty()
            keyPassword = System.getenv("KEY_PASSWORD").orEmpty()
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
        // Debug dùng debug keystore riêng của Android Gradle Plugin, không dùng khóa Release.
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
