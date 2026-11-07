package org.akanework.gramophone.logic.recommendation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.util.Calendar

data class UserContextTelemetry(
    val deviceType: String,             // "bt_car", "headphones", "hi_fi_dac", "speaker", "phone"
    val localHour: Float,               // 0.0 .. 23.99 continuous local hour
    val dayOfWeek: Int,                 // 1 (Sun) .. 7 (Sat)
    val isCharging: Boolean,            // true if connected to power
    val batteryPct: Int,                // 0 .. 100 (-1 if unavailable)
    val ambientLux: Float,              // light level in lux (-1.0 if unavailable)
    val recentSkipCount: Int,           // skips in last 45s
    val skipVelocity: Float,            // skips per minute (derivative)
    val sessionDurationSec: Long,       // seconds since app/session started
    val latitude: Double? = null,
    val longitude: Double? = null
)

object ContextManager {
    private const val TAG = "ContextManager"
    private const val SKIP_WINDOW_MS = 45_000L // 45 seconds

    private val skipTimestamps = mutableListOf<Long>()
    private val sessionStartTimeMs: Long = SystemClock.elapsedRealtime()

    @Volatile
    private var lastAmbientLux: Float = -1.0f
    private var isSensorListenerRegistered = false

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
     * Gathers rich multi-dimensional sensory, circadian, and behavioral telemetry
     */
    fun gatherTelemetry(context: Context): UserContextTelemetry {
        val deviceType = detectDeviceType(context)
        val skipCount = getRecentSkipCount()
        val skipVel = (skipCount.toFloat() / (SKIP_WINDOW_MS / 1000f)) * 60f

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY) + (cal.get(Calendar.MINUTE) / 60.0f)
        val dow = cal.get(Calendar.DAY_OF_WEEK)

        var batteryPct = -1
        var isCharging = false
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryPct = (level * 100) / scale
            }
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read battery state: ${e.message}")
        }

        // Initialize light sensor listener if not yet registered
        registerLightSensorIfNeeded(context)

        val sessionDuration = (SystemClock.elapsedRealtime() - sessionStartTimeMs) / 1000L

        // Passive cached location without active GPS lock or runtime prompts
        var lat: Double? = null
        var lon: Double? = null
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val loc = lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
            }
        } catch (_: SecurityException) {
            // Permission not granted, ignore gracefully
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get passive location: ${e.message}")
        }

        return UserContextTelemetry(
            deviceType = deviceType,
            localHour = hour,
            dayOfWeek = dow,
            isCharging = isCharging,
            batteryPct = batteryPct,
            ambientLux = lastAmbientLux,
            recentSkipCount = skipCount,
            skipVelocity = skipVel,
            sessionDurationSec = sessionDuration,
            latitude = lat,
            longitude = lon
        )
    }

    private fun registerLightSensorIfNeeded(context: Context) {
        if (isSensorListenerRegistered) return
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            val lightSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
            sm.registerListener(object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null && event.values.isNotEmpty()) {
                        lastAmbientLux = event.values[0]
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            isSensorListenerRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Light sensor registration failed: ${e.message}")
        }
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
