import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "bouncerWeb"
        browser {
            commonWebpackConfig {
                outputFileName = "bouncerWeb.js"
                // kotlinx-io (pulled in by the Ktor client) reads a bare `import.meta` in a Node-only
                // path that webpack can't rewrite, so the bundle is loaded as an ES module (index.html)
                // and must not be wrapped in eval(), where import.meta is a syntax error.
                devtool = "source-map"
                // The backend owns :8080, and its CORS rule only allows this dev origin.
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(port = 8081)
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared"))

            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.material3)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
