package org.akanework.gramophone.logic.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

object PlaybackLogger {
    private const val TAG = "PlaybackLogger"
    private const val MAX_MEMORY_LOGS = 500
    private const val LOG_FILE_NAME = "salvation_playback.log"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val memoryLogs = ConcurrentLinkedQueue<String>()
    private var logFile: File? = null
    private val logExecutor = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            logFile = File(dir, LOG_FILE_NAME)
            log("LOG_INIT", "PlaybackLogger initialized at ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PlaybackLogger file", e)
        }
    }

    fun log(tag: String, message: String) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val entry = "[$timestamp] [$tag] $message"

        Log.d(TAG, entry)

        memoryLogs.add(entry)
        while (memoryLogs.size > MAX_MEMORY_LOGS) {
            memoryLogs.poll()
        }

        val targetFile = logFile ?: return
        logExecutor.execute {
            try {
                FileWriter(targetFile, true).use { writer ->
                    writer.appendLine(entry)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file", e)
            }
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
        val targetFile = logFile
        logExecutor.execute {
            try {
                targetFile?.let { file ->
                    if (file.exists()) {
                        file.writeText("")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing log file", e)
            }
        }
        log("LOG_CLEARED", "Log file cleared by user")
    }

    fun getLogFile(): File? = logFile
}

