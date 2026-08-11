package org.akanework.gramophone.logic.utils

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object PlaybackLogger {
    private const val TAG = "PlaybackLogger"
    private const val MAX_MEMORY_LOGS = 300
    private const val LOG_FILE_NAME = "salvation_playback.log"
    private const val REMOTE_LOG_URL = "http://185.196.41.31/api/v1/app_logs"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val memoryLogs = ConcurrentLinkedQueue<String>()
    private val pendingRemoteLogs = ConcurrentLinkedQueue<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            logFile = File(dir, LOG_FILE_NAME)
            log("LOG_INIT", "PlaybackLogger initialized at ${logFile?.absolutePath}")

            // Schedule periodic batch flush every 2 seconds
            executor.scheduleWithFixedDelay({
                flushRemoteLogs()
            }, 2, 2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PlaybackLogger file", e)
        }
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] [$tag] $message"

        Log.d(TAG, entry)

        memoryLogs.add(entry)
        pendingRemoteLogs.add(entry)

        while (memoryLogs.size > MAX_MEMORY_LOGS) {
            memoryLogs.poll()
        }

        logFile?.let { file ->
            try {
                FileWriter(file, true).use { writer ->
                    writer.appendLine(entry)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file", e)
            }
        }
    }

    private fun flushRemoteLogs() {
        if (pendingRemoteLogs.isEmpty()) return
        val batch = mutableListOf<String>()
        while (batch.size < 50 && pendingRemoteLogs.isNotEmpty()) {
            pendingRemoteLogs.poll()?.let { batch.add(it) }
        }
        if (batch.isEmpty()) return

        try {
            val json = JSONObject().apply {
                val array = JSONArray()
                batch.forEach { array.put(it) }
                put("logs", array)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(REMOTE_LOG_URL)
                .post(body)
                .build()

            httpClient.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to flush remote logs to server: ${e.message}")
        }
    }

    fun getLogs(): String {
        return if (memoryLogs.isNotEmpty()) {
            memoryLogs.joinToString("\n")
        } else {
            logFile?.takeIf { it.exists() }?.readText() ?: "Логи пока пусты."
        }
    }

    fun clearLogs() {
        memoryLogs.clear()
        pendingRemoteLogs.clear()
        try {
            logFile?.let { file ->
                if (file.exists()) {
                    file.writeText("")
                }
            }
            log("LOG_CLEARED", "Log file cleared by user")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing log file", e)
        }
    }

    fun getLogFile(): File? = logFile
}
