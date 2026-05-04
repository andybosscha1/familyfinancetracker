package com.timmat.financetracker.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores a salted SHA-256 hash of the user’s app-lock PIN in
 * [EncryptedSharedPreferences] (Android Keystore-backed). A random 16-byte salt
 * is generated per-device on first PIN set.
 *
 * The hash never leaves the device. We intentionally do NOT store the raw PIN.
 */
@Singleton
class AppLockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun isPinSet(): Boolean =
        prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) { "PIN must be 4–6 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(KEY_HASH, hash.toHex())
            .putString(KEY_SALT, salt.toHex())
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        if (!isPinSet()) return false
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val salt = saltHex.fromHex()
        val candidate = hash(pin, salt).toHex()
        // Constant-time-ish compare.
        if (candidate.length != storedHash.length) return false
        var diff = 0
        for (i in candidate.indices) {
            diff = diff or (candidate[i].code xor storedHash[i].code)
        }
        return diff == 0
    }

    fun clear() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "Invalid hex" }
        return ByteArray(length / 2) { i ->
            ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
        }
    }

    private companion object {
        const val PREFS_NAME = "finance_tracker_lock_secure"
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
    }
}
