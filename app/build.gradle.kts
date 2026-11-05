import java.util.Base64
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.google.devtools.ksp)
}

val debugKeystoreBase64 = "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"

val debugKeystoreFile = file("debug.keystore")
if (!debugKeystoreFile.exists()) {
    val decoded = Base64.getDecoder().decode(debugKeystoreBase64)
    debugKeystoreFile.writeBytes(decoded)
}

android {
    namespace = "com.motogeek.telemetry"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.motogeek.telemetry"
        minSdk = 29
        targetSdk = 35
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = if (ciRunNumber != null && ciRunNumber > 0) 100 + ciRunNumber else 100
        versionName = if (ciRunNumber != null && ciRunNumber > 0) "1.0.$ciRunNumber" else "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {

        getByName("debug") {
            storeFile = debugKeystoreFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")

            if (keystoreFile != null && keystorePassword != null && keyAlias != null && keyPassword != null && file(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")

            if (keystoreFile != null && keystorePassword != null && keyAlias != null && keyPassword != null && file(keystoreFile).exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation("com.google.api-client:google-api-client:1.33.0")
    implementation("com.google.api-client:google-api-client-gson:1.33.0")
    implementation("com.google.http-client:google-http-client-gson:1.39.2")
    implementation("com.google.http-client:google-http-client-android:1.39.2")
    implementation(libs.google.api.services.drive)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.http-client:google-http-client-gson:1.39.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation("androidx.test:core:1.5.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

dependencies {
    implementation("androidx.fragment:fragment-ktx:1.8.1")
}
