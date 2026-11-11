plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Số bản dựng do GitHub Actions truyền vào (-PbuildNo=<số lần chạy>), build ở máy thì là 0.
val buildNo = (project.findProperty("buildNo") as String?)?.toIntOrNull() ?: 0

// Thư viện đọc giọng offline (sherpa-onnx, Apache-2.0). File .aar khá nặng nên không để trong git:
// Gradle tự tải về thư mục app/libs/ ở lần build đầu tiên.
val sherpaVersion = "2.0.1"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar").asFile
val downloadSherpa by tasks.registering {
    outputs.file(sherpaAar)
    onlyIf { !sherpaAar.exists() }
    doLast {
        sherpaAar.parentFile.mkdirs()
        val url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/${sherpaAar.name}").toURL()
        val part = File(sherpaAar.path + ".part")
        url.openStream().use { input -> part.outputStream().use { output -> input.copyTo(output) } }
        check(part.length() > 1_000_000) { "Tải thư viện sherpa-onnx lỗi" }
        part.renameTo(sherpaAar)
    }
}

android {
    namespace = "com.dichgame.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dichgame.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 20 + buildNo
        versionName = if (buildNo > 0) "2.0.$buildNo" else "2.0"
        buildConfigField("int", "BUILD_NO", "$buildNo")
        // Bỏ x86 32-bit (gần như không còn máy nào) để APK nhẹ hơn.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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
    buildFeatures { buildConfig = true }
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

tasks.named("preBuild") { dependsOn(downloadSherpa) }

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    // Giọng đọc offline: sherpa-onnx (file .aar tải tự động ở trên) + giải nén gói giọng .tar.bz2
    implementation(files(sherpaAar).builtBy(downloadSherpa))
    implementation("org.apache.commons:commons-compress:1.26.2")
}
