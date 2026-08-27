package org.akanework.gramophone.desktop.settings

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class DesktopSettingsData(
    @SerializedName("theme_mode") var themeMode: ThemeMode = ThemeMode.SYSTEM,
    @SerializedName("dynamic_cover_color") var isDynamicCoverColor: Boolean = true,
    @SerializedName("replay_gain") var isReplayGain: Boolean = true,
    @SerializedName("bit_perfect") var isBitPerfect: Boolean = true,
    @SerializedName("mono_audio") var isMonoAudio: Boolean = false,
    @SerializedName("flac_preferred") var isFlacPreferred: Boolean = true,
    @SerializedName("karaoke_mode") var isKaraokeMode: Boolean = true,
    @SerializedName("highlight_active_line") var isHighlightActiveLine: Boolean = true,
    @SerializedName("auto_translate_lyrics") var isAutoTranslateLyrics: Boolean = true,
    @SerializedName("offline_only") var isOfflineOnly: Boolean = false
)

object DesktopSettingsManager {
    private val gson = Gson()
    private val configFile: File by lazy {
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
        if (!baseDir.exists()) baseDir.mkdirs()
        File(baseDir, "settings.json")
    }

    private var currentData = DesktopSettingsData()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isDynamicCoverColor = MutableStateFlow(true)
    val isDynamicCoverColor: StateFlow<Boolean> = _isDynamicCoverColor.asStateFlow()

    private val _isReplayGain = MutableStateFlow(true)
    val isReplayGain: StateFlow<Boolean> = _isReplayGain.asStateFlow()

    private val _isBitPerfect = MutableStateFlow(true)
    val isBitPerfect: StateFlow<Boolean> = _isBitPerfect.asStateFlow()

    private val _isMonoAudio = MutableStateFlow(false)
    val isMonoAudio: StateFlow<Boolean> = _isMonoAudio.asStateFlow()

    private val _isFlacPreferred = MutableStateFlow(true)
    val isFlacPreferred: StateFlow<Boolean> = _isFlacPreferred.asStateFlow()

    private val _isKaraokeMode = MutableStateFlow(true)
    val isKaraokeMode: StateFlow<Boolean> = _isKaraokeMode.asStateFlow()

    private val _isHighlightActiveLine = MutableStateFlow(true)
    val isHighlightActiveLine: StateFlow<Boolean> = _isHighlightActiveLine.asStateFlow()

    private val _isAutoTranslateLyrics = MutableStateFlow(true)
    val isAutoTranslateLyrics: StateFlow<Boolean> = _isAutoTranslateLyrics.asStateFlow()

    private val _isOfflineOnly = MutableStateFlow(false)
    val isOfflineOnly: StateFlow<Boolean> = _isOfflineOnly.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        try {
            if (configFile.exists()) {
                val text = configFile.readText().trim()
                if (text.isNotEmpty()) {
                    currentData = gson.fromJson(text, DesktopSettingsData::class.java) ?: DesktopSettingsData()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentData = DesktopSettingsData()
        }

        _themeMode.value = currentData.themeMode
        _isDynamicCoverColor.value = currentData.isDynamicCoverColor
        _isReplayGain.value = currentData.isReplayGain
        _isBitPerfect.value = currentData.isBitPerfect
        _isMonoAudio.value = currentData.isMonoAudio
        _isFlacPreferred.value = currentData.isFlacPreferred
        _isKaraokeMode.value = currentData.isKaraokeMode
        _isHighlightActiveLine.value = currentData.isHighlightActiveLine
        _isAutoTranslateLyrics.value = currentData.isAutoTranslateLyrics
        _isOfflineOnly.value = currentData.isOfflineOnly
    }

    private fun save() {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(gson.toJson(currentData))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        currentData.themeMode = mode
        _themeMode.value = mode
        save()
    }

    fun setDynamicCoverColor(enabled: Boolean) {
        currentData.isDynamicCoverColor = enabled
        _isDynamicCoverColor.value = enabled
        save()
    }

    fun setReplayGain(enabled: Boolean) {
        currentData.isReplayGain = enabled
        _isReplayGain.value = enabled
        save()
    }

    fun setBitPerfect(enabled: Boolean) {
        currentData.isBitPerfect = enabled
        _isBitPerfect.value = enabled
        save()
    }

    fun setMonoAudio(enabled: Boolean) {
        currentData.isMonoAudio = enabled
        _isMonoAudio.value = enabled
        save()
    }

    fun setFlacPreferred(enabled: Boolean) {
        currentData.isFlacPreferred = enabled
        _isFlacPreferred.value = enabled
        save()
    }

    fun setKaraokeMode(enabled: Boolean) {
        currentData.isKaraokeMode = enabled
        _isKaraokeMode.value = enabled
        save()
    }

    fun setHighlightActiveLine(enabled: Boolean) {
        currentData.isHighlightActiveLine = enabled
        _isHighlightActiveLine.value = enabled
        save()
    }

    fun setAutoTranslateLyrics(enabled: Boolean) {
        currentData.isAutoTranslateLyrics = enabled
        _isAutoTranslateLyrics.value = enabled
        save()
    }

    fun setOfflineOnly(enabled: Boolean) {
        currentData.isOfflineOnly = enabled
        _isOfflineOnly.value = enabled
        save()
    }
}
