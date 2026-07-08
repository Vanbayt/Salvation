package org.akanework.gramophone.logic

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.Track

// Обертка для сохранения типа объекта вместе с его данными
private data class HistoryItemWrapper(
    val type: String, // "TRACK", "ALBUM", "ARTIST"
    val id: String,   // Чтобы легко находить и удалять дубликаты
    val payload: String // Сам объект в виде JSON-строки
)

object HistoryManager {
    private const val PREFS_NAME = "salvation_history_prefs"
    private const val KEY_HISTORY = "playback_history"
    private const val MAX_HISTORY_ITEMS = 15 // Храним последние 15 элементов

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Сохраняет Трек, Альбом или Артиста в историю.
     */
    fun saveToHistory(context: Context, item: Any) {
        val prefs = getPrefs(context)
        val currentHistory = getRawHistory(context).toMutableList()

        // 1. Определяем тип и сериализуем объект
        val wrapper = when (item) {
            is Track -> HistoryItemWrapper("TRACK", item.id.toString(), gson.toJson(item))
            is Album -> HistoryItemWrapper("ALBUM", item.id, gson.toJson(item))
            is Artist -> HistoryItemWrapper("ARTIST", item.id.toString(), gson.toJson(item))
            else -> return // Неизвестный тип не сохраняем
        }

        // 2. Удаляем дубликат (если этот элемент уже был в истории, он поднимется наверх)
        currentHistory.removeAll { it.id == wrapper.id && it.type == wrapper.type }

        // 3. Добавляем в самое начало списка
        currentHistory.add(0, wrapper)

        // 4. Ограничиваем размер истории
        if (currentHistory.size > MAX_HISTORY_ITEMS) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }

        // 5. Сохраняем обновленный список в память
        prefs.edit().putString(KEY_HISTORY, gson.toJson(currentHistory)).apply()
    }

    /**
     * Читает сырые обертки из SharedPreferences.
     */
    private fun getRawHistory(context: Context): List<HistoryItemWrapper> {
        val json = getPrefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<HistoryItemWrapper>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Возвращает готовый список объектов (Track, Album, Artist) для адаптера.
     */
    fun getHistory(context: Context): List<Any> {
        val rawList = getRawHistory(context)
        return rawList.mapNotNull { wrapper ->
            try {
                when (wrapper.type) {
                    "TRACK" -> gson.fromJson(wrapper.payload, Track::class.java)
                    "ALBUM" -> gson.fromJson(wrapper.payload, Album::class.java)
                    "ARTIST" -> gson.fromJson(wrapper.payload, Artist::class.java)
                    else -> null
                }
            } catch (e: Exception) {
                null // Если структура объекта поменялась, просто игнорируем ошибку
            }
        }
    }
}