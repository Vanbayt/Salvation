package org.akanework.gramophone.logic.api

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

interface TokenStorage {
    fun getToken(): String?
    fun saveToken(token: String?)
    fun clearToken()
}

object NetworkClient {
    private const val BASE_URL = "http://185.196.41.31/" // Твой IP

    private var retrofit: Retrofit? = null
    private var tokenStorage: TokenStorage? = null
    @Volatile
    private var currentContext: Context? = null

    fun setTokenStorage(storage: TokenStorage) {
        tokenStorage = storage
    }

    fun getApi(context: Context? = null): GramophoneApi {
        context?.applicationContext?.let { currentContext = it }
        if (retrofit == null) {
            // Создаем перехватчик (Interceptor), который добавляет токен
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val builder = original.newBuilder()

                    // Достаем токен через TokenStorage или fallback на AuthManager динамически
                    val ctx = currentContext ?: context?.applicationContext
                    val token = tokenStorage?.getToken() ?: ctx?.let { AuthManager.getToken(it) }

                    if (!token.isNullOrEmpty()) {
                        builder.header("Authorization", "Bearer $token")
                    }

                    val response = chain.proceed(builder.build())
                    if (response.code == 401) {
                        val ctx = currentContext ?: context?.applicationContext
                        if (ctx != null) {
                            AuthManager.clearToken(ctx)
                            val loginIntent = android.content.Intent(ctx, org.akanework.gramophone.ui.LoginActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            ctx.startActivity(loginIntent)
                        }
                    }
                    response
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
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