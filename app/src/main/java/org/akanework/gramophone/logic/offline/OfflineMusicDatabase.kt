package org.akanework.gramophone.logic.offline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

data class OfflineTrackRecord(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val filePath: String,
    val coverPath: String? = null,
    val format: String = "M4A",
    val fileSize: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isLossless: Boolean = false
)

class OfflineMusicDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "salvation_offline.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_OFFLINE_TRACKS = "offline_tracks"

        @Volatile
        private var instance: OfflineMusicDatabase? = null

        fun getInstance(context: Context): OfflineMusicDatabase {
            return instance ?: synchronized(this) {
                instance ?: OfflineMusicDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_OFFLINE_TRACKS (
                track_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                file_path TEXT NOT NULL,
                cover_path TEXT,
                format TEXT NOT NULL DEFAULT 'M4A',
                file_size INTEGER NOT NULL DEFAULT 0,
                downloaded_at INTEGER NOT NULL DEFAULT 0,
                is_lossless INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_offline_track_time ON $TABLE_OFFLINE_TRACKS(downloaded_at);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_offline_artist ON $TABLE_OFFLINE_TRACKS(artist);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Миграции при изменении структуры БД
    }

    fun insertTrack(record: OfflineTrackRecord): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("track_id", record.trackId)
                put("title", record.title)
                put("artist", record.artist)
                put("album", record.album)
                put("duration_ms", record.durationMs)
                put("file_path", record.filePath)
                put("cover_path", record.coverPath)
                put("format", record.format)
                put("file_size", record.fileSize)
                put("downloaded_at", record.downloadedAt)
                put("is_lossless", if (record.isLossless) 1 else 0)
            }
            db.insertWithOnConflict(TABLE_OFFLINE_TRACKS, null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeTrack(trackId: String): Boolean {
        return try {
            val record = getTrack(trackId)
            if (record != null) {
                try {
                    val audioFile = File(record.filePath)
                    if (audioFile.exists()) audioFile.delete()
                } catch (_: Exception) {}
                if (!record.coverPath.isNullOrEmpty()) {
                    try {
                        val coverFile = File(record.coverPath)
                        if (coverFile.exists()) coverFile.delete()
                    } catch (_: Exception) {}
                }
            }
            val db = writableDatabase
            db.delete(TABLE_OFFLINE_TRACKS, "track_id = ?", arrayOf(trackId)) > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isTrackDownloaded(trackId: String): Boolean {
        val path = getLocalPath(trackId) ?: return false
        return File(path).exists()
    }

    fun getLocalPath(trackId: String): String? {
        return try {
            val db = readableDatabase
            db.query(
                TABLE_OFFLINE_TRACKS,
                arrayOf("file_path"),
                "track_id = ?",
                arrayOf(trackId),
                null, null, null, "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(0)
                    if (!path.isNullOrEmpty() && File(path).exists()) path else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getTrack(trackId: String): OfflineTrackRecord? {
        return try {
            val db = readableDatabase
            db.query(
                TABLE_OFFLINE_TRACKS,
                null,
                "track_id = ?",
                arrayOf(trackId),
                null, null, null, "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    OfflineTrackRecord(
                        trackId = cursor.getString(cursor.getColumnIndexOrThrow("track_id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        artist = cursor.getString(cursor.getColumnIndexOrThrow("artist")),
                        album = cursor.getString(cursor.getColumnIndexOrThrow("album")),
                        durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
                        filePath = cursor.getString(cursor.getColumnIndexOrThrow("file_path")),
                        coverPath = cursor.getString(cursor.getColumnIndexOrThrow("cover_path")),
                        format = cursor.getString(cursor.getColumnIndexOrThrow("format")),
                        fileSize = cursor.getLong(cursor.getColumnIndexOrThrow("file_size")),
                        downloadedAt = cursor.getLong(cursor.getColumnIndexOrThrow("downloaded_at")),
                        isLossless = cursor.getInt(cursor.getColumnIndexOrThrow("is_lossless")) == 1
                    )
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAllDownloadedTrackIds(): Set<String> {
        val ids = mutableSetOf<String>()
        try {
            val db = readableDatabase
            db.query(
                TABLE_OFFLINE_TRACKS,
                arrayOf("track_id", "file_path"),
                null, null, null, null, null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val path = cursor.getString(1)
                    if (id != null && path != null && File(path).exists()) {
                        ids.add(id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ids
    }

    fun getAllDownloadedTracks(): List<OfflineTrackRecord> {
        val list = mutableListOf<OfflineTrackRecord>()
        try {
            val db = readableDatabase
            db.query(
                TABLE_OFFLINE_TRACKS,
                null,
                null, null, null, null, "downloaded_at DESC"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow("file_path"))
                    if (path != null && File(path).exists()) {
                        list.add(
                            OfflineTrackRecord(
                                trackId = cursor.getString(cursor.getColumnIndexOrThrow("track_id")),
                                title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                                artist = cursor.getString(cursor.getColumnIndexOrThrow("artist")),
                                album = cursor.getString(cursor.getColumnIndexOrThrow("album")),
                                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
                                filePath = path,
                                coverPath = cursor.getString(cursor.getColumnIndexOrThrow("cover_path")),
                                format = cursor.getString(cursor.getColumnIndexOrThrow("format")),
                                fileSize = cursor.getLong(cursor.getColumnIndexOrThrow("file_size")),
                                downloadedAt = cursor.getLong(cursor.getColumnIndexOrThrow("downloaded_at")),
                                isLossless = cursor.getInt(cursor.getColumnIndexOrThrow("is_lossless")) == 1
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
