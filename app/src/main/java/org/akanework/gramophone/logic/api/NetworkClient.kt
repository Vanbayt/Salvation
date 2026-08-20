package org.akanework.gramophone.logic.api

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val BASE_URL = "http://185.196.41.31/" // Твой IP

    private var retrofit: Retrofit? = null

    fun getApi(context: Context): GramophoneApi {
        val appContext = context.applicationContext
        if (retrofit == null) {
            // Создаем перехватчик (Interceptor), который добавляет токен
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val builder = original.newBuilder()

                    // Достаем токен через appContext
                    val token = AuthManager.getToken(appContext)

                    // --- ДОБАВЛЯЕМ ЛОГ ДЛЯ ПРОВЕРКИ ---
                    android.util.Log.d("AUTH_DEBUG", "Отправляем запрос на: ${original.url}")
                    android.util.Log.d("AUTH_DEBUG", "Токен из AuthManager: $token")
                    // ----------------------------------

                    if (!token.isNullOrEmpty()) {
                        builder.header("Authorization", "Bearer $token")
                    }

                    chain.proceed(builder.build())
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client) // Подключаем наш умный HTTP клиент
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(GramophoneApi::class.java)
    }
}