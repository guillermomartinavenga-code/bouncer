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
