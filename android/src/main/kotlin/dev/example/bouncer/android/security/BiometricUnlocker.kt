package dev.example.bouncer.android.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Shows the biometric prompt that gates decrypting the saved refresh token. */
class BiometricUnlocker(
    private val activity: FragmentActivity,
    private val storage: KeystoreTokenStorage,
) {
    fun unlock(onUnlocked: () -> Unit, onFailed: (String) -> Unit) {
        val cipher = try {
            storage.createDecryptCipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            storage.discardSavedSession()
            onFailed("Your biometrics changed, so the saved session was discarded. Please log in again.")
            return
        } catch (e: Exception) {
            storage.discardSavedSession()
            onFailed("The saved session could not be opened. Please log in again.")
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher == null) {
                    onFailed("Biometric authentication did not unlock the session.")
                    return
                }
                try {
                    storage.unlockWith(authenticatedCipher)
                    onUnlocked()
                } catch (e: Exception) {
                    onFailed("The saved session could not be decrypted.")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailed(errString.toString())
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Bouncer")
            .setSubtitle("Authenticate to use your saved session")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
}
