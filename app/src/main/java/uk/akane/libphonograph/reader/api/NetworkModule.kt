package uk.akane.libphonograph.reader.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {
    // ВСТАВЬ СЮДА СВОЙ IP ИЗ TAILSCALE (как в браузере), ОБЯЗАТЕЛЬНО СЛЕШ / В КОНЦЕ
    private const val BASE_URL = "http://100.90.101.37:4533/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val api: NavidromeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NavidromeApi::class.java)
    }
}