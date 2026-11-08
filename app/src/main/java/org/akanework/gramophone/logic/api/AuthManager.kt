package org.akanework.gramophone.logic.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.util.Base64
import android.util.Log
import org.json.JSONObject

object AuthManager {
    private const val PREFS_NAME = "secure_gramophone_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private var cachedToken: String? = null

    // Умная функция для получения хранилища
    private fun getSharedPrefs(context: Context): SharedPreferences? {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        return try {
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("AUTH_DEBUG", "Хранилище повреждено! Пытаемся восстановить...", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(PREFS_NAME)
                } else {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                }

                EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (ex: Exception) {
                Log.e("AUTH_DEBUG", "Не удалось восстановить хранилище :(", ex)
                null
            }
        }
    }

    /**
     * Проверяет срок действия JWT токена по claim `exp`.
     */
    fun isTokenExpired(token: String?): Boolean {
        if (token.isNullOrBlank()) return true
        val parts = token.split(".")
        if (parts.size < 2) return true
        return try {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            if (json.has("exp")) {
                val expSeconds = json.getLong("exp")
                val nowSeconds = System.currentTimeMillis() / 1000
                // Добавляем буфер в 5 секунд на рассинхронизацию часов
                nowSeconds >= (expSeconds - 5)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w("AuthManager", "Failed to decode/validate JWT token: ${e.message}")
            true
        }
    }

    /**
     * Проверяет наличие валидного и неистекшего токена.
     */
    fun isAuthorized(context: Context): Boolean {
        val token = getToken(context) ?: return false
        return !isTokenExpired(token)
    }

    fun getToken(context: Context): String? {
        if (cachedToken != null) {
            if (isTokenExpired(cachedToken)) {
                clearToken(context)
                return null
            }
            return cachedToken
        }

        val prefs = getSharedPrefs(context)
        val token = prefs?.getString(KEY_TOKEN, null)
        if (token != null && isTokenExpired(token)) {
            clearToken(context)
            return null
        }
        cachedToken = token
        return cachedToken
    }

    fun saveToken(context: Context, token: String) {
        val prefs = getSharedPrefs(context)
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
        cachedToken = token
    }

    fun clearToken(context: Context) {
        val prefs = getSharedPrefs(context)
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
        cachedToken = null
    }
}