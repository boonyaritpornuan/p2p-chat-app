plugins {
    alias(libs.plugins.android.application)
    // alias(libs.plugins.kotlin.android) // Removed again to avoid conflict with KMP
    alias(libs.plugins.compose.compiler) // Add Compose Compiler plugin
}

android {
    namespace = "org.example.p2p_chat_app.android"
    compileSdk = 34 // TODO: Check latest stable SDK

    defaultConfig {
        applicationId = "org.example.p2p_chat_app.android"
        minSdk = 24 // TODO: Check requirements
        targetSdk = 34 // TODO: Check latest stable SDK
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    // kotlinOptions are likely handled by KMP plugin via shared module dependency
    // kotlinOptions {
    //     jvmTarget = "1.8"
    // }
    buildFeatures {
        compose = true // Enable Compose
    }
    // composeOptions are not needed when using the Compose Compiler Gradle plugin with Kotlin 2.0+
    // composeOptions {
    //     kotlinCompilerExtensionVersion = "..." 
    // }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared"))

    // Compose Dependencies (using BOM)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.viewmodel.compose)

    // Debug dependencies
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest) // If needed for tests

    // Android Test dependencies
    // androidTestImplementation(libs.androidx.test.ext.junit)
    // androidTestImplementation(libs.androidx.test.espresso.core)
    // androidTestImplementation(libs.compose.ui.test.junit4)
}

