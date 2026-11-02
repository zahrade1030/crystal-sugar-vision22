package com.example.data.security

import android.app.Activity
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.WindowManager
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager {

    private val KEY_ALIAS = "CrystalSugarKeyAlias"

    init {
        createKeyIfNeeded()
    }

    /**
     * Checks if device is rooted
     */
    fun isDeviceRooted(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true

        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    /**
     * Checks if app is running on an emulator
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Enables or disables screenshot protection on current screen
     */
    fun setScreenshotProtection(activity: Activity, enable: Boolean) {
        if (enable) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun createKeyIfNeeded() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    fun encryptString(plainText: String): String {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText // Fallback safely if key store cipher is unavailable
        }
    }

    fun decryptString(encryptedText: String): String {
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            if (combined.size < 12) return encryptedText

            val iv = ByteArray(12)
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            encryptedText
        }
    }
}
