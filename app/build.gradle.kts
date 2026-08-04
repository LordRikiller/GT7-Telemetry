plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gt7telemetry"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gt7telemetry"
        minSdk = 26
        targetSdk = 34
        // Version is baked in for local builds and overridden from a pushed
        // semver tag by the release workflow (VERSION_CODE / VERSION_NAME).
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 100
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The keystore is committed on purpose (private repo; password below) so
        // that every build — local or CI — signs with the SAME key. Android
        // refuses to update an installed app whose signing certificate changed,
        // so a drifting key silently breaks in-place updates and the in-app
        // updater along with them. A secret that can go missing is a worse
        // failure mode than a committed key in a private repo.
        //
        // The env overrides allow moving it to a CI secret later. Note the
        // isNotBlank() guard: unlike Groovy's Elvis, Kotlin's ?: only falls back
        // on null — and CI passes "" for an *unset* secret, which would
        // otherwise become an empty password.
        create("release") {
            fun env(name: String, fallback: String): String =
                System.getenv(name)?.takeIf(String::isNotBlank) ?: fallback

            storeFile = file(env("GT7_KEYSTORE_FILE", "../credentials/gt7.keystore"))
            storePassword = env("GT7_KEYSTORE_PASSWORD", "gt7telemetry")
            keyAlias = env("GT7_KEY_ALIAS", "gt7")
            keyPassword = env("GT7_KEY_PASSWORD", "gt7telemetry")
        }
    }

    buildTypes {
        release {
            // Without this the release APK is unsigned and cannot be installed
            // at all — and an ad-hoc debug key would differ per CI run, which
            // permanently breaks in-place updates.
            signingConfig = signingConfigs.getByName("release")

            // R8 minification is disabled until it can be verified on a real
            // device — proguard rules are kept ready for when it's re-enabled
            // and tested.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
