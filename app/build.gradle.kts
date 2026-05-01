plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gazeboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gazeboard"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // LiteRT NPU JIT runtimes are arm64-only on Snapdragon devices.
        ndk {
            abiFilters.add("arm64-v8a")
        }

        // Required for Qualcomm NPU runtime libraries shipped as dynamic features.
        packaging {
            jniLibs {
                useLegacyPackaging = true
            }
        }
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
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Include .tflite files in assets without compression (required for mmap)
    androidResources {
        noCompress += "tflite"
    }

    // LiteRT NPU runtime for the Galaxy S25 Ultra Snapdragon 8 Elite
    // target (Qualcomm_SM8750), which maps to runtime v79.
    dynamicFeatures.add(":litert_npu_runtime_libraries:qualcomm_runtime_v79")

    bundle {
        deviceTargetingConfig = file("device_targeting_configuration.xml")
        deviceGroup {
            enableSplit = true
            defaultGroup = "other"
        }
    }
}

dependencies {
    // Strings referenced by the LiteRT NPU runtime dynamic feature manifests.
    implementation(project(":litert_npu_runtime_libraries:runtime_strings"))

    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ML Kit face detection — replaces unreliable android.media.FaceDetector
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.android.gms:play-services-tasks:18.1.0")

    // Jetpack core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // LiteRT — CompiledModel API (NOT the old Interpreter)
    // This is the pass/fail gate for the hackathon — do not change to tflite-task-vision
    implementation("com.google.ai.edge.litert:litert:2.1.1")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// KGP 2.0 no longer auto-registers this task for kotlin-android subprojects,
// but Android Studio's Tooling API still requests it during sync.
if (tasks.findByName("prepareKotlinBuildScriptModel") == null) {
    tasks.register("prepareKotlinBuildScriptModel")
}
