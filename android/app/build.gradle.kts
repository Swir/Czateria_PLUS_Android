plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.swir.czateriaplus"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.swir.czateriaplus.mobiledev"
        minSdk = 26
        targetSdk = 35
        versionCode = 80
        versionName = "0.8.0-mobile-rebuild"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
