package org.akanework.gramophone.logic.stats

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

data class PlaybackEvent(
    val id: Long = 0,
    val userId: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val listenedMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val contextSource: String? = null,
    val isSynced: Boolean = false
)

data class TopTrackStat(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val coverUrl: String?,
    val playCount: Long,
    val totalListenedMs: Long
)

data class TopArtistStat(
    val artist: String,
    val coverUrl: String?,
    val playCount: Long,
    val totalListenedMs: Long
)

data class StatsSummary(
    val period: String,
    val totalListenedMs: Long,
    val totalPlaysCount: Long,
    val uniqueTracksCount: Long,
    val uniqueArtistsCount: Long,
    val topTracks: List<TopTrackStat>,
    val topArtists: List<TopArtistStat>,
    val streakDays: Int,
    val peakHour: Int,
    val favoriteDayOfWeek: String,
    val dayDistribution: List<Float> = listOf(0.15f, 0.15f, 0.15f, 0.15f, 0.15f, 0.15f, 0.15f),
    val hourlyDistribution: List<Float> = List(24) { 0.05f }
)

class PlaybackStatsDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playback_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                track_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                cover_url TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                listened_ms INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0,
                context_source TEXT,
                is_synced INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stats_user_time ON playback_events(user_id, timestamp);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stats_user_track ON playback_events(user_id, track_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stats_user_artist ON playback_events(user_id, artist);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Миграции при необходимости
    }

    fun insertEvent(event: PlaybackEvent): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("user_id", event.userId)
                put("track_id", event.trackId)
                put("title", event.title)
                put("artist", event.artist)
                put("album", event.album)
                put("cover_url", event.coverUrl)
                put("duration_ms", event.durationMs)
                put("listened_ms", event.listenedMs)
                put("timestamp", event.timestamp)
                put("is_completed", if (event.isCompleted) 1 else 0)
                put("context_source", event.contextSource)
                put("is_synced", if (event.isSynced) 1 else 0)
            }
            db.insert("playback_events", null, values)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    fun getUnsyncedEvents(limit: Int = 100): List<PlaybackEvent> {
        val result = mutableListOf<PlaybackEvent>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT id, user_id, track_id, title, artist, album, cover_url, duration_ms, listened_ms, timestamp, is_completed, context_source FROM playback_events WHERE is_synced = 0 ORDER BY id ASC LIMIT ?",
                arrayOf(limit.toString())
            )
            cursor.use {
                while (it.moveToNext()) {
                    result.add(
                        PlaybackEvent(
                            id = it.getLong(0),
                            userId = it.getString(1),
                            trackId = it.getString(2),
                            title = it.getString(3),
                            artist = it.getString(4),
                            album = it.getString(5),
                            coverUrl = it.getString(6),
                            durationMs = it.getLong(7),
                            listenedMs = it.getLong(8),
                            timestamp = it.getLong(9),
                            isCompleted = it.getInt(10) == 1,
                            contextSource = it.getString(11),
                            isSynced = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun markEventsSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        try {
            val db = writableDatabase
            val inClause = ids.joinToString(",")
            db.execSQL("UPDATE playback_events SET is_synced = 1 WHERE id IN ($inClause)")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getStatsSummary(userId: String, period: String): StatsSummary {
        val now = System.currentTimeMillis()
        val since = calculateSinceTimestamp(period, now)

        val db = readableDatabase
        val args = if (since > 0) arrayOf(userId, since.toString()) else arrayOf(userId)
        val timeFilter = if (since > 0) "AND timestamp >= ?" else ""

        var totalListenedMs = 0L
        var totalPlaysCount = 0L
        var uniqueTracksCount = 0L
        var uniqueArtistsCount = 0L

        // 1. Агрегатные метрики
        try {
            val q = "SELECT COALESCE(SUM(listened_ms), 0), COUNT(*), COUNT(DISTINCT track_id), COUNT(DISTINCT artist) FROM playback_events WHERE user_id = ? $timeFilter"
            db.rawQuery(q, args).use { cursor ->
                if (cursor.moveToFirst()) {
                    totalListenedMs = cursor.getLong(0)
                    totalPlaysCount = cursor.getLong(1)
                    uniqueTracksCount = cursor.getLong(2)
                    uniqueArtistsCount = cursor.getLong(3)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Топ 5 треков
        val topTracks = mutableListOf<TopTrackStat>()
        try {
            val trackQuery = """
                SELECT track_id, title, artist, album, cover_url, COUNT(*) as play_cnt, SUM(listened_ms) as sum_time
                FROM playback_events
                WHERE user_id = ? $timeFilter
                GROUP BY track_id, title, artist
                ORDER BY play_cnt DESC, sum_time DESC
                LIMIT 5
            """.trimIndent()
            db.rawQuery(trackQuery, args).use { cursor ->
                while (cursor.moveToNext()) {
                    topTracks.add(
                        TopTrackStat(
                            trackId = cursor.getString(0),
                            title = cursor.getString(1),
                            artist = cursor.getString(2),
                            album = cursor.getString(3),
                            coverUrl = cursor.getString(4),
                            playCount = cursor.getLong(5),
                            totalListenedMs = cursor.getLong(6)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Топ 5 артистов
        val topArtists = mutableListOf<TopArtistStat>()
        try {
            val artistQuery = """
                SELECT artist, MAX(cover_url), COUNT(*) as play_cnt, SUM(listened_ms) as sum_time
                FROM playback_events
                WHERE user_id = ? $timeFilter
                GROUP BY artist
                ORDER BY sum_time DESC, play_cnt DESC
                LIMIT 5
            """.trimIndent()
            db.rawQuery(artistQuery, args).use { cursor ->
                while (cursor.moveToNext()) {
                    topArtists.add(
                        TopArtistStat(
                            artist = cursor.getString(0),
                            coverUrl = cursor.getString(1),
                            playCount = cursor.getLong(2),
                            totalListenedMs = cursor.getLong(3)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Серия прослушиваний (Streak)
        val streak = calculateStreakDays(userId)

        // 5. Пиковые часы и любимый день
        val peakHour = calculatePeakHour(userId, since)
        val favoriteDay = calculateFavoriteDay(userId, since)

        // 6. Распределение по дням недели (ПН..ВС) и по часам суток (0..23)
        val dayDistribution = calculateDayDistribution(userId, since)
        val hourlyDistribution = calculateHourlyDistribution(userId, since)

        return StatsSummary(
            period = period,
            totalListenedMs = totalListenedMs,
            totalPlaysCount = totalPlaysCount,
            uniqueTracksCount = uniqueTracksCount,
            uniqueArtistsCount = uniqueArtistsCount,
            topTracks = topTracks,
            topArtists = topArtists,
            streakDays = streak,
            peakHour = peakHour,
            favoriteDayOfWeek = favoriteDay,
            dayDistribution = dayDistribution,
            hourlyDistribution = hourlyDistribution
        )
    }

    private fun calculateSinceTimestamp(period: String, nowMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        return when (period) {
            "week" -> nowMs - (7L * 24 * 60 * 60 * 1000)
            "month", "30days" -> nowMs - (30L * 24 * 60 * 60 * 1000)
            "6months" -> nowMs - (180L * 24 * 60 * 60 * 1000)
            "year", "2026" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            else -> 0L // Все время
        }
    }

    private fun calculateStreakDays(userId: String): Int {
        return try {
            val db = readableDatabase
            // Считаем уникальные даты (Дни) в порядке убывания
            val cursor = db.rawQuery(
                """
                SELECT DISTINCT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') as day_str
                FROM playback_events
                WHERE user_id = ?
                ORDER BY day_str DESC
                LIMIT 60
                """.trimIndent(),
                arrayOf(userId)
            )
            var streak = 0
            val cal = Calendar.getInstance()
            val todayStr = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

            cursor.use {
                val days = mutableListOf<String>()
                while (it.moveToNext()) {
                    days.add(it.getString(0))
                }
                if (days.isEmpty()) return 0
                if (days[0] != todayStr && days[0] != yesterdayStr) return 0

                streak = days.size.coerceAtLeast(1)
            }
            streak
        } catch (e: Exception) {
            1
        }
    }

    private fun calculatePeakHour(userId: String, since: Long): Int {
        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        return try {
            val db = readableDatabase
            val args = if (since > 0) arrayOf(userId, since.toString()) else arrayOf(userId)
            val timeFilter = if (since > 0) "AND timestamp >= ?" else ""
            val cursor = db.rawQuery(
                """
                SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as h, COUNT(*) as cnt, SUM(listened_ms) as sum_ms
                FROM playback_events
                WHERE user_id = ? $timeFilter
                GROUP BY h
                ORDER BY sum_ms DESC, cnt DESC
                LIMIT 1
                """.trimIndent(),
                args
            )
            cursor.use {
                if (it.moveToFirst() && it.getInt(1) > 0) it.getInt(0) else currentHour
            }
        } catch (e: Exception) {
            currentHour
        }
    }

    private fun calculateFavoriteDay(userId: String, since: Long): String {
        val days = arrayOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
        val cal = Calendar.getInstance()
        val todayName = days.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "Четверг" }
        return try {
            val db = readableDatabase
            val args = if (since > 0) arrayOf(userId, since.toString()) else arrayOf(userId)
            val timeFilter = if (since > 0) "AND timestamp >= ?" else ""
            val cursor = db.rawQuery(
                """
                SELECT CAST(strftime('%w', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as d, COUNT(*) as cnt, SUM(listened_ms) as sum_ms
                FROM playback_events
                WHERE user_id = ? $timeFilter
                GROUP BY d
                ORDER BY sum_ms DESC, cnt DESC
                LIMIT 1
                """.trimIndent(),
                args
            )
            cursor.use {
                if (it.moveToFirst() && it.getInt(1) > 0) {
                    val dayIdx = it.getInt(0)
                    days.getOrElse(dayIdx) { todayName }
                } else todayName
            }
        } catch (e: Exception) {
            todayName
        }
    }

    private fun calculateDayDistribution(userId: String, since: Long): List<Float> {
        // Дни: ПН (1), ВТ (2), СР (3), ЧТ (4), ПТ (5), СБ (6), ВС (0)
        val dowOrder = intArrayOf(1, 2, 3, 4, 5, 6, 0)
        val rawSums = FloatArray(7) { 0f }

        try {
            val db = readableDatabase
            val args = if (since > 0) arrayOf(userId, since.toString()) else arrayOf(userId)
            val timeFilter = if (since > 0) "AND timestamp >= ?" else ""
            val cursor = db.rawQuery(
                """
                SELECT CAST(strftime('%w', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as d, SUM(listened_ms) as sum_ms
                FROM playback_events
                WHERE user_id = ? $timeFilter
                GROUP BY d
                """.trimIndent(),
                args
            )
            cursor.use {
                while (it.moveToNext()) {
                    val d = it.getInt(0)
                    val sum = it.getFloat(1)
                    val idx = dowOrder.indexOf(d)
                    if (idx != -1) {
                        rawSums[idx] = sum
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val maxVal = rawSums.maxOrNull() ?: 0f
        return if (maxVal > 0f) {
            rawSums.map { (it / maxVal).coerceIn(0.12f, 1f) }
        } else {
            listOf(0.15f, 0.15f, 0.15f, 0.15f, 0.15f, 0.15f, 0.15f)
        }
    }

    private fun calculateHourlyDistribution(userId: String, since: Long): List<Float> {
        val hourlySums = FloatArray(24) { 0f }

        try {
            val db = readableDatabase
            val args = if (since > 0) arrayOf(userId, since.toString()) else arrayOf(userId)
            val timeFilter = if (since > 0) "AND timestamp >= ?" else ""
            val cursor = db.rawQuery(
                """
                SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as h, SUM(listened_ms) as sum_ms
                FROM playback_events
                WHERE user_id = ? $timeFilter
                GROUP BY h
                """.trimIndent(),
                args
            )
            cursor.use {
                while (it.moveToNext()) {
                    val h = it.getInt(0)
                    val sum = it.getFloat(1)
                    if (h in 0..23) {
                        hourlySums[h] = sum
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val maxVal = hourlySums.maxOrNull() ?: 0f
        return if (maxVal > 0f) {
            hourlySums.map { (it / maxVal).coerceIn(0.08f, 1f) }
        } else {
            List(24) { 0.08f }
        }
    }

    companion object {
        private const val DATABASE_NAME = "salvation_playback_stats.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var instance: PlaybackStatsDatabase? = null

        fun getInstance(context: Context): PlaybackStatsDatabase {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStatsDatabase(context).also { instance = it }
            }
        }
    }
}
