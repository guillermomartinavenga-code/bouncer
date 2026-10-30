package dev.example.bouncer.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import dev.example.bouncer.android.security.BiometricUnlocker
import dev.example.bouncer.android.security.KeystoreTokenStorage
import dev.example.bouncer.android.ui.BouncerApp
import org.koin.android.ext.android.get

/** FragmentActivity (not just ComponentActivity) because BiometricPrompt requires one. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = get<KeystoreTokenStorage>()
        val unlocker = BiometricUnlocker(this, storage)
        setContent { BouncerApp(storage = storage, unlocker = unlocker) }
    }
}
