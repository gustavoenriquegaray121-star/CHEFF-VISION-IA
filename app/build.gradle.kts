plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gustavo.chefvisionia"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gustavo.chefvisionia"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Esta línea es la que conecta con el "Secreto" de GitHub que creamos
        val geminiKey = System.getenv("GEMINI_API_KEY") ?: project.findProperty("GEMINI_API_KEY")?.toString() ?: ""
        
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        buildConfigField("boolean", "IS_DEBUG_BUILD", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.camera:camera-camera2:1.3.2")
    implementation("androidx.camera:camera-lifecycle:1.3.2")
    implementation("androidx.camera:camera-view:1.3.2")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
