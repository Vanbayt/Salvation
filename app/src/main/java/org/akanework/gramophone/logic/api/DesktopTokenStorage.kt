package org.akanework.gramophone.logic.api

import java.io.File

/**
 * Хранилище токена для Desktop (Windows / Linux).
 * Сохраняет авторизационный JWT-токен в стандартную системную директорию настроек пользователя.
 */
class DesktopTokenStorage : TokenStorage {
    private val authFile: File by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        val baseDir = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "Salvation")
                else File(System.getProperty("user.home", "."), ".salvation")
            }
            else -> {
                val xdgConfig = System.getenv("XDG_CONFIG_HOME")
                if (!xdgConfig.isNullOrBlank()) File(xdgConfig, "salvation")
                else File(System.getProperty("user.home", "."), ".config/salvation")
            }
        }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        File(baseDir, "auth.token")
    }

    private var inMemoryToken: String? = null

    override fun getToken(): String? {
        if (inMemoryToken != null) return inMemoryToken
        return try {
            if (authFile.exists()) {
                val read = authFile.readText().trim()
                if (read.isNotEmpty()) {
                    inMemoryToken = read
                    read
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun saveToken(token: String?) {
        inMemoryToken = token
        try {
            if (token != null) {
                authFile.parentFile?.mkdirs()
                authFile.writeText(token.trim())
            } else {
                if (authFile.exists()) {
                    authFile.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun clearToken() {
        saveToken(null)
    }
}
