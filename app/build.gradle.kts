plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val dictionaryManifestUrl = providers.gradleProperty("DICTIONARY_MANIFEST_URL").orElse("").get()
val safeManifestUrl = dictionaryManifestUrl.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "pt.rjp.aikeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.rjp.aikeyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 111
        versionName = "1.1.1"
        buildConfigField("String", "DICTIONARY_MANIFEST_URL", "\"$safeManifestUrl\"")
    }

    buildFeatures {
        buildConfig = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
}
