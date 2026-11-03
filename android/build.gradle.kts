plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.example.bouncer.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.example.bouncer.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Optional override of where the app finds the backend, e.g. -Pbouncer.backendUrl=http://192.168.1.5:8080.
        // Empty means "decide at runtime" (see BackendUrl): the emulator's alias for the host, or
        // localhost on a physical device (which needs `adb reverse tcp:8080 tcp:8080`).
        val backendUrl = providers.gradleProperty("bouncer.backendUrl").orElse("").get()
        buildConfigField("String", "BACKEND_URL_OVERRIDE", "\"$backendUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
