plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.lifeos.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.lifeos.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "3.8-HARDENED"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -O3 -march=armv8-a+simd -ffast-math"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Biometrie (Class 3 Biometric Strong)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Verschlüsselter Tresor
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Coroutines & Core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")

    // Jetpack Compose & Lifecycle
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    testImplementation("io.mockk:mockk:1.13.9")
}
