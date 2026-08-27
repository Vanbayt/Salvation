package org.akanework.gramophone.desktop.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit

data class LoginResponse(
    @SerializedName("access_token") val access_token: String,
    @SerializedName("token_type") val token_type: String
)

interface GramophoneApi {
    @FormUrlEncoded
    @POST("/token")
    fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<LoginResponse>

    @GET("/search")
    fun searchMusic(@Query("query") query: String): Call<SearchResponse>

    @GET("/my/favorites")
    fun getFavorites(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("query") query: String? = null,
        @Query("sort_mode") sortMode: String = "newest"
    ): Call<List<Track>>

    @POST("/tracks/{id}/like")
    fun likeTrack(@Path("id") id: String): Call<ResponseBody>

    @DELETE("/tracks/{id}/like")
    fun unlikeTrack(@Path("id") id: String): Call<ResponseBody>

    // Albums
    @GET("/api/v1/me/favorite_albums")
    fun getFavoriteAlbums(): Call<List<Album>>

    @GET("/api/v1/albums/{id}")
    fun getAlbumPage(@Path("id") albumId: String): Call<Album>

    @POST("/api/v1/albums/{album_id}/like")
    fun toggleAlbumLike(@Path("album_id") albumId: String): Call<Map<String, String>>

    // Artists
    @GET("/api/v1/me/favorite_artists")
    fun getFavoriteArtists(): Call<List<Artist>>

    @GET("/api/v1/artists/{id}")
    fun getArtistProfile(@Path("id") artistId: String): Call<Artist>

    @GET("/api/v1/artists/{artist_id}/albums")
    fun getArtistDiscography(@Path("artist_id") artistId: String): Call<List<Album>>

    // Playlists
    @GET("/api/v1/my/playlists")
    fun getMyPlaylists(): Call<List<Playlist>>

    @POST("/api/v1/playlists")
    fun createPlaylist(@Body request: PlaylistCreateRequest): Call<Playlist>

    @DELETE("/api/v1/playlists/{id}")
    fun deletePlaylist(@Path("id") id: Int): Call<Void>

    @GET("/api/v1/playlists/{id}/tracks")
    fun getPlaylistTracks(@Path("id") id: Int): Call<List<Track>>
}

object DesktopNetworkClient {
    const val BASE_URL = "http://185.196.41.31/"
    private var retrofit: Retrofit? = null

    val tokenStorage: DesktopTokenStorage = DesktopTokenStorage()

    fun getApi(): GramophoneApi {
        if (retrofit == null) {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val builder = original.newBuilder()
                    val token = tokenStorage.getToken()
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
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(GramophoneApi::class.java)
    }
}

class DesktopTokenStorage {
    private val authFile: File by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        val baseDir = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "Salvation")
                else File(System.getProperty("user.home", "."), ".salvation")
            }
            else -> {
                val xdgConfig = System.getenv("XDG_CONFIG_HOME")
                if (!xdgConfig.isNullOrBlank()) File(xdgConfig, "salvation")
                else File(System.getProperty("user.home", "."), ".config/salvation")
            }
        }
        if (!baseDir.exists()) baseDir.mkdirs()
        File(baseDir, "auth.token")
    }

    private var inMemoryToken: String? = null

    fun getToken(): String? {
        if (inMemoryToken != null) return inMemoryToken
        return try {
            if (authFile.exists()) {
                val read = authFile.readText().trim()
                if (read.isNotEmpty()) {
                    inMemoryToken = read
                    read
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveToken(token: String?) {
        inMemoryToken = token
        try {
            if (token != null) {
                authFile.parentFile?.mkdirs()
                authFile.writeText(token.trim())
            } else {
                if (authFile.exists()) authFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
