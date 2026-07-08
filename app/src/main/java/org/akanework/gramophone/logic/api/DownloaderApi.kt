package org.akanework.gramophone.logic.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// 1. Модель данных (Точно совпадает с тем, что шлет Python из функции search)
data class RemoteTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,    // Может быть null
    val cover: String?,    // Картинка альбома
    val source: String,    // "deezer"
    val duration: Int      // Длительность в секундах
)

// 2. Интерфейс команд
interface DownloaderService {
    // В Python мы назвали параметр "query", поэтому тут тоже @Query("query")
    @GET("search")
    suspend fun search(@Query("query") query: String): List<RemoteTrack>
}

// 3. Объект-клиент
object DownloaderClient {
    // Твой новый VPS
    private const val BASE_URL = "http://185.196.41.31/"
    // Твой секретный ключ (должен совпадать с main.py)
    const val API_KEY = "0150asdf" // Проверь, какой ты поставил в main.py!

    // Перехватчик: добавляет ключ "x-access-token" в каждый запрос
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("x-access-token", API_KEY)
            .build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        // Ждем подключения до 60 секунд (на случай медленного VPS)
        .connectTimeout(60, TimeUnit.SECONDS)
        // 0 означает БЕСКОНЕЧНОЕ ожидание чтения.
        // Это критически важно для стриминга, чтобы плеер не закрывал
        // соединение, если сервер буферизирует данные.
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: DownloaderService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DownloaderService::class.java)
    }

    // Хелпер: генерирует прямую ссылку для ExoPlayer
    // ExoPlayer не использует Retrofit, ему нужна просто строка-ссылка
    fun getStreamUrl(trackId: String): String {
        return "${BASE_URL}stream/$trackId"
    }
}