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
        versionName = "0.1.0"
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
    // libmozc_helper.so を実行するには、ネイティブライブラリが APK から展開されている必要がある
    packaging { jniLibs { useLegacyPackaging = true } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
