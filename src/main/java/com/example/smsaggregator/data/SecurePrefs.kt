package com.example.smsaggregator.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for sensitive values (currently the Gemini API key).
 *
 * Backed by Jetpack Security's EncryptedSharedPreferences. If encryption is unavailable
 * on a device (rare keystore failures), it transparently falls back to regular prefs so
 * the app keeps working — secrets are simply not encrypted in that edge case.
 *
 * On first read it migrates any legacy plaintext key out of the old prefs file.
 */
object SecurePrefs {

    private const val SECURE_FILE = "sms_agg_secure"
    private const val LEGACY_FILE = "sms_agg_prefs"
    private const val KEY_GEMINI = "gemini_api_key"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val resolved = try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    SECURE_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e("SecurePrefs", "Encrypted prefs unavailable, falling back to plain prefs", e)
                context.applicationContext.getSharedPreferences(SECURE_FILE, Context.MODE_PRIVATE)
            }
            cached = resolved
            return resolved
        }
    }

    fun getGeminiApiKey(context: Context): String {
        val secure = prefs(context)
        secure.getString(KEY_GEMINI, null)?.let { return it }

        // One-time migration of a legacy plaintext key.
        val legacy = context.applicationContext
            .getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        val old = legacy.getString(KEY_GEMINI, "") ?: ""
        if (old.isNotBlank()) {
            secure.edit().putString(KEY_GEMINI, old).apply()
            legacy.edit().remove(KEY_GEMINI).apply()
            return old
        }
        return ""
    }

    fun setGeminiApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_GEMINI, key.trim()).apply()
    }
}
