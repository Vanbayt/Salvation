package org.akanework.gramophone.logic.utils

import java.io.File

/**
 * Кроссплатформенное управление путями файлов, кэша и загрузок для Android, Windows и Linux.
 */
object SalvationPaths {

    private var androidFilesDir: File? = null
    private var androidCacheDir: File? = null

    fun initAndroid(filesDir: File, cacheDir: File) {
        androidFilesDir = filesDir
        androidCacheDir = cacheDir
    }

    fun getConfigDir(): File {
        if (androidFilesDir != null) return androidFilesDir!!

        val os = System.getProperty("os.name", "").lowercase()
        val dir = when {
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
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCacheDir(): File {
        if (androidCacheDir != null) return androidCacheDir!!

        val os = System.getProperty("os.name", "").lowercase()
        val dir = when {
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA")
                if (!localAppData.isNullOrBlank()) File(localAppData, "Salvation\\Cache")
                else File(System.getProperty("user.home", "."), ".salvation/cache")
            }
            else -> {
                val xdgCache = System.getenv("XDG_CACHE_HOME")
                if (!xdgCache.isNullOrBlank()) File(xdgCache, "salvation")
                else File(System.getProperty("user.home", "."), ".cache/salvation")
            }
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMusicDownloadDir(): File {
        val os = System.getProperty("os.name", "").lowercase()
        val userHome = System.getProperty("user.home", ".")
        val dir = when {
            os.contains("win") -> File(userHome, "Music\\Salvation")
            else -> File(userHome, "Music/Salvation")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
