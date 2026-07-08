package org.akanework.gramophone.logic.api

import androidx.annotation.Keep
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.PUT
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part

@Keep
interface GramophoneApi {

    @FormUrlEncoded
    @POST("/token")
    fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<LoginResponse>

    @GET("/search")
    fun searchMusic(@Query("query") query: String): Call<SearchResponse>

    // --- МЕТОДЫ ДЛЯ ЛАЙКОВ ---
    @POST("/tracks/{id}/like")
    fun likeTrack(@Path("id") id: String): Call<ResponseBody>

    @DELETE("/tracks/{id}/like")
    fun unlikeTrack(@Path("id") id: String): Call<ResponseBody>

    @GET("/my/favorites")
    fun getFavorites(
        @Query("skip") skip: Int,
        @Query("limit") limit: Int,
        @Query("query") query: String? = null,
        @Query("sort_mode") sortMode: String = "newest"
    ): Call<List<Track>>

    @GET("/api/v1/artists/{id}")
    fun getArtistProfile(@Path("id") artistId: String): Call<Artist>

    @GET("/api/v1/albums/{id}")
    fun getAlbumPage(@Path("id") albumId: String): Call<Album>

    @GET("/api/v1/artists/{artist_id}")
    fun getArtistPage(@Path("artist_id") artistId: String): Call<Artist>

    @POST("/api/v1/artists/{artist_id}/like")
    fun toggleArtistLike(@Path("artist_id") artistId: String): retrofit2.Call<Map<String, String>>

    @POST("/api/v1/albums/{album_id}/like")
    fun toggleAlbumLike(@Path("album_id") albumId: String): retrofit2.Call<Map<String, String>>

    @GET("/api/v1/me/favorite_artists")
    fun getFavoriteArtists(): retrofit2.Call<List<Artist>>

    @GET("/api/v1/me/favorite_albums")
    fun getFavoriteAlbums(): retrofit2.Call<List<Album>>

    @GET("/api/v1/artists/{artist_id}/albums")
    fun getArtistDiscography(@Path("artist_id") artistId: String): retrofit2.Call<List<Album>>

    // ================= ПЛЕЙЛИСТЫ =================

    @GET("/api/v1/my/playlists")
    fun getMyPlaylists(): Call<List<Playlist>>

    @POST("/api/v1/playlists")
    fun createPlaylist(@Body request: PlaylistCreateRequest): Call<Playlist>

    @DELETE("/api/v1/playlists/{id}")
    fun deletePlaylist(@Path("id") id: Int): Call<Void>

    @GET("/api/v1/playlists/{id}/tracks")
    fun getPlaylistTracks(@Path("id") id: Int): Call<List<Track>>

    @POST("/api/v1/playlists/{id}/tracks")
    fun addTrackToPlaylist(
        @Path("id") playlistId: Int,
        @Body request: PlaylistTrackAddRequest
    ): Call<Void>

    @DELETE("/api/v1/playlists/{playlist_id}/tracks/{track_id}")
    fun removeTrackFromPlaylist(
        @Path("playlist_id") playlistId: Int,
        @Path("track_id") trackId: Int
    ): Call<Void>

    @PUT("/api/v1/playlists/{id}/reorder")
    fun reorderPlaylistTracks(
        @Path("id") playlistId: Int,
        @Body request: PlaylistReorderRequest
    ): Call<Void>

    @Multipart
    @POST("/api/v1/playlists/{id}/cover")
    fun uploadPlaylistCover(
        @Path("id") playlistId: Int,
        @Part file: MultipartBody.Part
    ): Call<Map<String, String>>

    @POST("/api/v1/playlists/{id}/like")
    fun togglePlaylistLike(@Path("id") playlistId: Int): Call<Map<String, String>>

    @PUT("/api/v1/playlists/{id}")
    fun updatePlaylist(@Path("id") id: Int, @Body request: PlaylistUpdateRequest): Call<Playlist>

    @POST("/api/v1/playlists/{id}/editors")
    fun addPlaylistEditor(@Path("id") id: Int, @Body request: EditorAddRequest): Call<Map<String, String>>

    @GET("/api/v1/playlists/{id}")
    fun getPlaylist(@Path("id") id: Int): Call<Playlist>
}

// ================= СЕТЕВЫЕ МОДЕЛИ =================

@Keep
data class LoginResponse(
    val access_token: String,
    val token_type: String
)

@Keep
data class Playlist(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("is_public") val isPublic: Boolean = false,
    @SerializedName("auto_covers") val autoCovers: List<String> = emptyList()
) : Serializable

@Keep
data class PlaylistCreateRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("is_public") val isPublic: Boolean = false
)

@Keep
data class PlaylistTrackAddRequest(
    @SerializedName("track_id") val trackId: Int
)

@Keep
data class TrackReorderItem(
    @SerializedName("track_id") val trackId: Int,
    @SerializedName("position") val position: Int
)

@Keep
data class PlaylistReorderRequest(
    @SerializedName("tracks") val tracks: List<TrackReorderItem>
)

@Keep
data class PlaylistUpdateRequest(
    @SerializedName("title") val title: String? = null,
    @SerializedName("is_public") val isPublic: Boolean? = null
)

@Keep
data class EditorAddRequest(
    @SerializedName("username") val username: String
)