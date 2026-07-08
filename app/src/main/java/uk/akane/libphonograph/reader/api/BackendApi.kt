package uk.akane.libphonograph.reader.api

import retrofit2.http.GET
import retrofit2.http.Query

interface BackendApi {
    @GET("search")
    suspend fun search(@Query("query") query: String): List<BackendTrackDto>
}