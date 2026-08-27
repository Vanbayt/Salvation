package org.akanework.gramophone.logic.utils.exoplayer

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.utils.PlaybackLogger
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object PoTokenProvider {
    private const val TAG = "PoTokenProvider"
    private const val PREFS_NAME = "salvation_potoken_prefs"
    private const val KEY_VDATA_PREFIX = "visitor_data_"
    private const val KEY_TIMESTAMP_PREFIX = "timestamp_"
    private const val CACHE_EXPIRY_MS = 24 * 3600 * 1000L // 24 hours

    private val random = SecureRandom()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isWarming = AtomicBoolean(false)

    private val memoryVisitorData = ConcurrentHashMap<String, String>()
    private val memoryPoTokens = ConcurrentHashMap<String, String>()

    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
            prefs = it
        }
    }

    /**
     * Retrieves cached visitorData for clientKey from RAM or SharedPreferences.
     */
    fun getPersistedVisitorData(context: Context, clientKey: String): String? {
        val mem = memoryVisitorData[clientKey]
        if (!mem.isNullOrEmpty()) return mem

        try {
            val p = getPrefs(context)
            val saved = p.getString("$KEY_VDATA_PREFIX$clientKey", null)
            if (!saved.isNullOrEmpty()) {
                memoryVisitorData[clientKey] = saved
                return saved
            }
        } catch (e: Exception) {
            PlaybackLogger.log(TAG, "Failed reading visitorData prefs: ${e.message}")
        }
        return null
    }

    /**
     * Persists visitorData for clientKey in RAM and SharedPreferences.
     */
    fun saveVisitorData(context: Context, clientKey: String, visitorData: String) {
        if (visitorData.isEmpty()) return
        memoryVisitorData[clientKey] = visitorData
        try {
            getPrefs(context).edit()
                .putString("$KEY_VDATA_PREFIX$clientKey", visitorData)
                .putLong("$KEY_TIMESTAMP_PREFIX$clientKey", System.currentTimeMillis())
                .apply()
            // Pre-warm PoToken for this visitorData
            val token = generatePoToken(visitorData)
            if (token.isNotEmpty()) {
                memoryPoTokens[clientKey] = token
            }
        } catch (e: Exception) {
            PlaybackLogger.log(TAG, "Failed saving visitorData prefs: ${e.message}")
        }
    }

    /**
     * Returns pre-warmed PoToken if available for clientKey.
     */
    fun getWarmPoToken(clientKey: String): String? {
        return memoryPoTokens[clientKey]
    }

    /**
     * Generates a valid Proof of Origin (PoToken) directly bound to visitorData (or videoId)
     * using the WebPO / BotGuard ColdStart algorithm.
     */
    fun generatePoToken(contentBinding: String, clientState: Int = 1): String {
        if (contentBinding.isEmpty()) return ""
        try {
            val contentBindingBytes = contentBinding.toByteArray(StandardCharsets.UTF_8)
            val timestamp = (System.currentTimeMillis() / 1000L).toInt()
            val k0 = (random.nextInt(256) and 0xFF).toByte()
            val k1 = (random.nextInt(256) and 0xFF).toByte()

            val header = byteArrayOf(
                k0,
                k1,
                0,
                clientState.toByte(),
                ((timestamp shr 24) and 0xFF).toByte(),
                ((timestamp shr 16) and 0xFF).toByte(),
                ((timestamp shr 8) and 0xFF).toByte(),
                (timestamp and 0xFF).toByte()
            )

            val packet = ByteArray(2 + header.size + contentBindingBytes.size)
            packet[0] = 34.toByte() // 0x22
            packet[1] = (header.size + contentBindingBytes.size).toByte()
            System.arraycopy(header, 0, packet, 2, header.size)
            System.arraycopy(contentBindingBytes, 0, packet, 2 + header.size, contentBindingBytes.size)

            val randomKeys = byteArrayOf(k0, k1)
            val keyLength = randomKeys.size
            for (i in 2 + keyLength until packet.size) {
                val keyIndex = (i - 2) % keyLength
                packet[i] = (packet[i].toInt() xor randomKeys[keyIndex].toInt()).toByte()
            }

            val poToken = Base64.encodeToString(packet, Base64.URL_SAFE or Base64.NO_WRAP)
            PlaybackLogger.log(TAG, "Generated ColdStart PoToken for binding len ${contentBinding.length} -> PoToken len ${poToken.length}")
            return poToken
        } catch (e: Exception) {
            PlaybackLogger.log(TAG, "Failed to generate PoToken: ${e.message}")
            return ""
        }
    }

    /**
     * Asynchronously pre-warms visitorData and PoTokens in the background.
     * Loads disk cache into memory, and if expired or missing, triggers background fetch.
     */
    fun initAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val p = getPrefs(appContext)
                val profiles = listOf("VISIONOS", "ANDROID_VR", "WEB_REMIX")
                val now = System.currentTimeMillis()

                for (clientKey in profiles) {
                    val saved = p.getString("$KEY_VDATA_PREFIX$clientKey", null)
                    val timestamp = p.getLong("$KEY_TIMESTAMP_PREFIX$clientKey", 0L)
                    val isExpired = (now - timestamp) > CACHE_EXPIRY_MS

                    if (!saved.isNullOrEmpty()) {
                        memoryVisitorData[clientKey] = saved
                        ClientTrackResolver.setVisitorData(clientKey, saved)
                        val warmToken = generatePoToken(saved)
                        if (warmToken.isNotEmpty()) {
                            memoryPoTokens[clientKey] = warmToken
                        }
                    }

                    if (saved.isNullOrEmpty() || isExpired) {
                        if (isWarming.compareAndSet(false, true)) {
                            try {
                                PlaybackLogger.log(TAG, "Warming fresh visitorData for $clientKey (Expired: $isExpired)...")
                                val freshVData = ClientTrackResolver.fetchVisitorDataDirect(clientKey)
                                if (freshVData.isNotEmpty()) {
                                    saveVisitorData(appContext, clientKey, freshVData)
                                    ClientTrackResolver.setVisitorData(clientKey, freshVData)
                                    PlaybackLogger.log(TAG, "Pre-warmed visitorData for $clientKey: ${freshVData.take(20)}...")
                                }
                            } finally {
                                isWarming.set(false)
                            }
                        }
                    }
                }
                PlaybackLogger.log(TAG, "PoTokenProvider initAsync completed. Pre-warmed profiles: ${memoryVisitorData.keys}")
            } catch (e: Exception) {
                PlaybackLogger.log(TAG, "PoTokenProvider initAsync error: ${e.message}")
            }
        }
    }
}

