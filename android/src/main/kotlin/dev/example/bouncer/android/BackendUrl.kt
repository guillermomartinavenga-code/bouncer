package dev.example.bouncer.android

import android.os.Build

/**
 * Where this app finds the backend, which depends on where the app runs:
 * - emulator: `10.0.2.2` is its alias for the host machine;
 * - physical device: `localhost`, reached through `adb reverse tcp:8080 tcp:8080` (already allowed
 *   as cleartext by the network security config, so no wider cleartext traffic is opened).
 * A `-Pbouncer.backendUrl=...` build property overrides both.
 */
object BackendUrl {
    const val EMULATOR = "http://10.0.2.2:8080"
    const val DEVICE = "http://localhost:8080"

    fun resolve(): String = BuildConfig.BACKEND_URL_OVERRIDE.ifEmpty { if (isEmulator()) EMULATOR else DEVICE }

    private fun isEmulator(): Boolean =
        Build.HARDWARE == "goldfish" || Build.HARDWARE == "ranchu" ||
            Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("sdk_gphone")
}
