plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tritv.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tritv.iptv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Every Android TV and box is ARM; leaving out the x86 copies of the FFmpeg libraries
        // keeps the APK smaller. The Apple-silicon emulator is arm64 too.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Shared debug key so every CI build installs over the previous one (keeps the saved playlist).
    // The release build is signed with it too, so it installs over a debug build and back. Only
    // fit for sideloading; a store build would need its own key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // The build to put on a TV: not debuggable, so ART compiles it fully, and shrunk and
        // optimised by R8, which Compose relies on for its speed.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.tv.material)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.media3.ui)

    implementation(libs.kotlinx.coroutines.android)

    // Installs the baseline profiles (Compose's own and src/main/baseline-prof.txt) on sideloaded
    // installs, so start-up and the drawer run precompiled instead of interpreted.
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
}
