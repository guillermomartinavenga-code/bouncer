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

        // Where the app finds the backend. The default is the emulator's alias for the host machine;
        // a physical device overrides it, e.g. -Pbouncer.backendUrl=http://localhost:8080 with `adb reverse`.
        val backendUrl = providers.gradleProperty("bouncer.backendUrl").orElse("http://10.0.2.2:8080").get()
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendUrl\"")
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
