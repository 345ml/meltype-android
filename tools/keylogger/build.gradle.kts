plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.horota.meltype.keylogger"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.horota.meltype.keylogger"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "spike"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
