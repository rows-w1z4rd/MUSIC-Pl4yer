plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.suna.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.suna.player"
        minSdk = 33
        targetSdk = 36
        // On GitHub Actions every build gets a higher number, so the phone accepts it as an update.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        // The version people see, set once in gradle.properties.
        versionName = providers.gradleProperty("appVersion").get()
    }

    // The release key lives outside the repo: on GitHub it comes from the repository secrets,
    // on this PC from the same environment variables. Without them, release builds use the debug key.
    val releaseKey = System.getenv("SUNAPLAY_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey
                storePassword = System.getenv("SUNAPLAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SUNAPLAY_KEY_ALIAS")
                keyPassword = System.getenv("SUNAPLAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            // Lets a test build from this PC sit next to the GitHub build on the phone.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Database layouts are kept in the repo so future versions can upgrade the data safely.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.coil.compose)
    implementation(libs.haze)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
