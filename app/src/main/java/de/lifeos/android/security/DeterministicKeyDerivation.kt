package de.lifeos.android.security

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object DeterministicKeyDerivation {

    fun deriveMasterKey(context: Context, userBiometricSeed: String = "LIFEOS_BIOMETRIC_SEED"): ByteArray {
        // Hardware-gebundener Identifikator (Snapdragon SoC / Android ID)
        val hardwareId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "FALLBACK_HW_ID"

        val salt = MessageDigest.getInstance("SHA-512")
            .digest((hardwareId + "LIFEOS_IMMUTABLE_SALT_2026").toByteArray())

        val spec = PBEKeySpec(userBiometricSeed.toCharArray(), salt, 256000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return factory.generateSecret(spec).encoded
    }
}
