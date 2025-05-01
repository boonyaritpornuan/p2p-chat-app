plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "1.8" }
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.experimental.ExperimentalObjCName")
            }
        }
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.webrtc.kmp)
            // Define expect declarations for libsignal here
        }
        androidMain.dependencies {
            implementation(libs.libsignal.client) // Add libsignal for Android
            // No specific Android dependency needed for webrtc-kmp based on README
        }
        iosMain.dependencies {
            // No specific iOS dependency needed for webrtc-kmp based on README
            // Native WebRTC SDK is added via Cocoapods in the iOS project itself
            // libsignal Swift library will also be added via Cocoapods/SPM in the iOS project
        }
    }
}

android {
    namespace = "org.example.p2p_chat_app.shared"
    compileSdk = 34 // TODO: Check latest stable SDK
    defaultConfig {
        minSdk = 24 // TODO: Check requirements
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    // Required for libsignal-client packaging options
    packaging {
        resources {
            excludes += setOf("libsignal_jni*.dylib", "signal_jni*.dll", "libsignal_jni_testing.so")
        }
    }
}

