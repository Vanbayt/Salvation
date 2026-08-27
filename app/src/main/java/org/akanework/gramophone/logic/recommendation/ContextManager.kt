package org.akanework.gramophone.logic.recommendation

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

object ContextManager {
    private const val TAG = "ContextManager"
    private const val SKIP_WINDOW_MS = 45_000L // 45 seconds

    private val skipTimestamps = mutableListOf<Long>()

    /**
     * Records a skip action and prunes old events outside the time window
     */
    @Synchronized
    fun recordSkip(): Int {
        val now = System.currentTimeMillis()
        skipTimestamps.add(now)
        pruneOldSkips(now)
        val count = skipTimestamps.size
        Log.d(TAG, "Recorded skip, recent skip count = $count")
        return count
    }

    /**
     * Resets or prunes skips
     */
    @Synchronized
    fun getRecentSkipCount(): Int {
        val now = System.currentTimeMillis()
        pruneOldSkips(now)
        return skipTimestamps.size
    }

    @Synchronized
    fun resetSkips() {
        skipTimestamps.clear()
    }

    private fun pruneOldSkips(now: Long) {
        val cutoff = now - SKIP_WINDOW_MS
        skipTimestamps.removeAll { it < cutoff }
    }

    /**
     * Determines current active audio output device
     */
    fun detectDeviceType(context: Context): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return "phone"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (device in devices) {
                    when (device.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                            val name = device.productName.toString().lowercase()
                            return if (name.contains("car") || name.contains("auto") || name.contains("bmw") || name.contains("audi") || name.contains("toyota")) {
                                "bt_car"
                            } else {
                                "headphones"
                            }
                        }
                        AudioDeviceInfo.TYPE_USB_DEVICE,
                        AudioDeviceInfo.TYPE_USB_HEADSET -> return "hi_fi_dac"
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> return "headphones"
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothA2dpOn) return "headphones"
                @Suppress("DEPRECATION")
                if (audioManager.isWiredHeadsetOn) return "headphones"
            }

            "phone"
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting audio device: ${e.message}")
            "phone"
        }
    }
}
