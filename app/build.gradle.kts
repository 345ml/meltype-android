plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.horota.meltype"
    compileSdk = 35
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "dev.horota.meltype"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
        // 16KB のページサイズの端末 (Android 15 以降の要件) でも読み込めるようにする
        externalNativeBuild { cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" } }
    }

    signingConfigs {
        create("release") {
            val keystore = System.getenv("MELTYPE_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("MELTYPE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MELTYPE_KEY_ALIAS")
                keyPassword = System.getenv("MELTYPE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("MELTYPE_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
    // Task 2 の成果物 (libMeltypeNative.so, libmozc_helper.so) を APK に入れる
    sourceSets["main"].jniLibs.srcDirs("../build/jniLibs")
    // ライセンス表記 (設定画面の「ライセンス」)。copyNotices が作る
    sourceSets["main"].assets.srcDirs(layout.buildDirectory.dir("generated/notices"))
    // libmozc_helper.so を実行するには、ネイティブライブラリが APK から展開されている必要がある
    packaging { jniLibs { useLegacyPackaging = true } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// Mozc (BSD-3-Clause) と .NET ランタイム (MIT) は、配布するバイナリにライセンス表記を付ける必要がある。
// ファイル名は settings/Notices.kt の FILES と合わせる。build/notices は mozc/build.sh と core-native/build.sh が作る。
val copyNotices by tasks.registering(Copy::class) {
    into(layout.buildDirectory.dir("generated/notices/notices"))
    from(rootProject.file("THIRD-PARTY-NOTICES.md"))
    from(rootProject.file("LICENSE"))
    from(rootProject.file("upstream/THIRD-PARTY-NOTICES.md")) { rename { "UPSTREAM-THIRD-PARTY-NOTICES.md" } }
    from(rootProject.file("build/notices"))
}
tasks.named("preBuild") { dependsOn(copyNotices) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
