package org.akanework.gramophone.logic.utils.exoplayer

import android.content.Context
import android.util.Base64
import org.akanework.gramophone.logic.utils.PlaybackLogger
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

object PoTokenProvider {
    private const val TAG = "PoTokenProvider"
    private val random = SecureRandom()

    /**
     * Generates a valid Proof of Origin (PoToken) directly bound to visitorData (or videoId)
     * using the WebPO / BotGuard ColdStart algorithm.
     *
     * @param contentBinding VisitorData, VideoId, or DataSyncId to bind the token to.
     * @param clientState Integer representing client state (default 1).
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
            // XOR encrypt payload using randomKeys starting after key bytes
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

    fun initAsync(context: Context) {
        // No-op for pure algorithmic generation
    }
}
