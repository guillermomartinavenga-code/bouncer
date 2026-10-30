package dev.example.bouncer.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dev.example.bouncer.shared.client.TokenStorage
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Keeps the access token in memory only (it lives 5 minutes) and persists just the refresh token,
 * encrypted with an Android Keystore RSA key that has `setUserAuthenticationRequired(true)` and
 * no validity window — so every *decryption* needs a fresh biometric authentication, while
 * *encryption* (done with the exported public key) never prompts. That asymmetry matters: the
 * refresh token rotates on every refresh, and re-saving it must not interrupt the user.
 *
 * After a cold start the saved session is "locked": [getRefreshToken] returns null until
 * [unlockWith] is called with a biometric-authenticated cipher.
 */
class KeystoreTokenStorage(context: Context) : TokenStorage {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Volatile private var accessToken: String? = null
    @Volatile private var refreshToken: String? = null

    /** A session was saved on disk (it may still be locked behind the biometric prompt). */
    fun hasSavedSession(): Boolean = prefs.contains(KEY_ENCRYPTED_REFRESH_TOKEN)

    /** A saved session exists but its refresh token hasn't been decrypted in this process yet. */
    fun isLocked(): Boolean = hasSavedSession() && refreshToken == null

    /** A decrypt-mode cipher to hand to BiometricPrompt; decryption only works once it authenticates. */
    fun createDecryptCipher(): Cipher {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
        }
    }

    /** Decrypts the saved refresh token with a cipher that BiometricPrompt has just authenticated. */
    fun unlockWith(authenticatedCipher: Cipher) {
        val encrypted = prefs.getString(KEY_ENCRYPTED_REFRESH_TOKEN, null) ?: return
        val plain = authenticatedCipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
        refreshToken = String(plain, Charsets.UTF_8)
    }

    /** Forgets the saved session and its key (e.g. biometrics changed, or the user chose another account). */
    fun discardSavedSession() {
        accessToken = null
        refreshToken = null
        prefs.edit().remove(KEY_ENCRYPTED_REFRESH_TOKEN).apply()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    override suspend fun getAccessToken(): String? = accessToken

    override suspend fun getRefreshToken(): String? = refreshToken

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        try {
            val encrypted = createEncryptCipher().doFinal(refreshToken.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_ENCRYPTED_REFRESH_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            // Typically: no secure lock screen set up, so an auth-required key can't be created.
            // The session still works in memory; it just can't survive an app restart.
            Log.w(TAG, "Could not persist the refresh token; session will be memory-only", e)
        }
    }

    override suspend fun clear() {
        accessToken = null
        refreshToken = null
        prefs.edit().remove(KEY_ENCRYPTED_REFRESH_TOKEN).apply()
    }

    private fun createEncryptCipher(): Cipher {
        if (!keyStore.containsAlias(KEY_ALIAS)) generateKeyPair()
        val keystorePublicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        // Re-wrap the public key outside the Keystore so encrypting doesn't demand user authentication.
        val publicKey = KeyFactory.getInstance(keystorePublicKey.algorithm)
            .generatePublic(X509EncodedKeySpec(keystorePublicKey.encoded))
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC)
        }
    }

    private fun generateKeyPair() {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(2048)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            // Authentication on every use, with no time window (the time-window-free
            // setUserAuthenticationParameters variant needs API 30; minSdk here is 29).
            .setUserAuthenticationRequired(true)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    private companion object {
        const val TAG = "KeystoreTokenStorage"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bouncer_refresh_token_key"
        const val PREFS_NAME = "bouncer_session"
        const val KEY_ENCRYPTED_REFRESH_TOKEN = "encrypted_refresh_token"
        const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        val OAEP_SPEC = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}
