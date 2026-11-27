plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.trinineba.quotesonlock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.trinineba.quotesonlock"
        minSdk = 26
        // Google Play: new apps and updates must target Android 16 (API 36) from 31 August 2026.
        targetSdk = 36
        // CI numbers each build; the in-app updater compares this with the release's "Build N".
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    signingConfigs {
        // Shared debug key (public, not a secret) so each CI build installs over the previous one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The Play upload key, kept out of the repository: CI writes it from repository secrets
        // (UPLOAD_KEYSTORE_B64, UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS, UPLOAD_KEY_PASSWORD).
        val uploadStore = System.getenv("UPLOAD_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
        if (uploadStore != null) create("upload") {
            storeFile = uploadStore
            storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // Smaller and faster than a debug build: unused library code and resources are stripped.
            // Names are kept (no obfuscation) because settings store some names, e.g. the chosen theme.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Same shared key as earlier builds, so a release build installs over the app already on phones.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Where the app comes from (store) and how much of it there is (edition).
    //   github: the build on GitHub releases, with its own updater (installs from GitHub, needs the internet).
    //   play:   the Google Play build: Play updates it, so no updater, no install permission and no internet.
    //   full:   everything. A lite edition can be added here later (its own applicationIdSuffix and source set).
    flavorDimensions += listOf("store", "edition")
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "UPDATES", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "UPDATES", "false")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
        }
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "LITE", "false")
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
        buildConfig = true
    }
    testOptions {
        // Plain JVM tests: Android calls that sneak in return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
