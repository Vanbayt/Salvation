package org.akanework.gramophone.logic.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.util.Log

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
            // Если файл сломан - жестко удаляем его
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(PREFS_NAME)
                } else {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                }

                // Пробуем создать заново с чистого листа
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

    fun getToken(context: Context): String? {
        if (cachedToken != null) return cachedToken

        val prefs = getSharedPrefs(context)
        cachedToken = prefs?.getString(KEY_TOKEN, null)

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