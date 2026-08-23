package de.lifeos.android.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.lifeos.core.storage.BootStateTracker
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object BiometricAuthGate {
    private const val KEY_ALIAS = "LifeOS_Biometric_TEE_Key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun isBiometricAvailable(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun initBiometricKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getCipher(): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    /**
     * Biometrisches TEE-Gating - wird ERST nach BootEngine-Abschluss aktiviert.
     * Vor BootComplete: onFailure() aufrufen (kein Gate).
     * Nach BootComplete: BiometricPrompt anzeigen wenn verfügbar.
     */
    fun requestFingerprintAccess(activity: FragmentActivity, onSuccess: (Cipher) -> Unit, onFailure: () -> Unit) {
        // Vor BootEngine-Abschluss: Gate deaktiviert
        if (!BootStateTracker.isBootComplete) {
            onFailure()
            return
        }

        // Nach BootEngine: BiometricGate aktivieren wenn verfügbar
        if (!isBiometricAvailable(activity)) {
            onFailure()
            return
        }

        initBiometricKey()
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("LIFE-OS // BIOMETRISCHES TEE-GATE")
            .setSubtitle("Fingerabdruck zur Entschlüsselung scannen")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("ABBRECHEN")
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    result.cryptoObject?.cipher?.let { onSuccess(it) } ?: onFailure()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFailure() }
                override fun onAuthenticationFailed() { }
            }
        )
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(getCipher()))
    }
}
