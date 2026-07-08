package uk.akane.libphonograph.reader.api

import retrofit2.http.GET
import retrofit2.http.Query

interface NavidromeApi {
    // Запрос 10 случайных песен для проверки
    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(
        @Query("u") user: String,
        @Query("p") pass: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "GramophoneMod",
        @Query("f") format: String = "json",
        @Query("size") size: Int = 10
    ): SubsonicResponseWrapper
}