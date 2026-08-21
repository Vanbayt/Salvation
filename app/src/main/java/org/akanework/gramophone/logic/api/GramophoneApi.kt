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

    @GET("/api/v1/search/artist")
    fun searchArtistFast(@Query("query") query: String): Call<ArtistLookupResponse>

    @GET("/api/v1/search/album")
    fun searchAlbumFast(
        @Query("query") query: String? = null,
        @Query("track_id") trackId: String? = null
    ): Call<AlbumLookupResponse>

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
        @Path("track_id") trackId: Any
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

    @POST("/api/v1/stats/event")
    fun sendStatsEvents(@Body events: List<PlaybackEventRequest>): Call<Map<String, Any>>

    @GET("/api/v1/stats/summary")
    fun getStatsSummary(@Query("period") period: String): Call<BackendStatsSummary>

    // ================= FLAC / LOSSLESS =================

    @POST("/api/v1/tracks/{track_id}/download_flac")
    fun downloadTrackFlac(@Path("track_id") trackId: String): Call<DownloadFlacResponse>

    @POST("/api/v1/albums/{album_id}/download_flac")
    fun downloadAlbumFlac(@Path("album_id") albumId: String): Call<DownloadFlacResponse>

    @POST("/api/v1/playlists/{playlist_id}/download_flac")
    fun downloadPlaylistFlac(@Path("playlist_id") playlistId: Int): Call<DownloadFlacResponse>

    @GET("/api/v1/albums/{album_id}/flac_status")
    fun getAlbumFlacStatus(@Path("album_id") albumId: String): Call<AlbumFlacStatusResponse>

    @GET("/api/v1/playlists/{playlist_id}/flac_status")
    fun getPlaylistFlacStatus(@Path("playlist_id") playlistId: Int): Call<PlaylistFlacStatusResponse>

    @GET("/api/v1/tracks/{track_id}/resolve_info")
    fun getTrackResolveInfo(@Path("track_id") trackId: String): Call<TrackResolveInfoResponse>
}

// ================= СЕТЕВЫЕ МОДЕЛИ =================

@Keep
data class PlaybackEventRequest(
    @SerializedName("track_id") val trackId: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("duration_ms") val durationMs: Long,
    @SerializedName("listened_ms") val listenedMs: Long,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("is_completed") val isCompleted: Boolean,
    @SerializedName("context_source") val contextSource: String? = null
)

@Keep
data class BackendStatsSummary(
    @SerializedName("period") val period: String,
    @SerializedName("total_listened_ms") val totalListenedMs: Long,
    @SerializedName("total_plays_count") val totalPlaysCount: Long,
    @SerializedName("unique_tracks_count") val uniqueTracksCount: Long,
    @SerializedName("unique_artists_count") val uniqueArtistsCount: Long,
    @SerializedName("top_tracks") val topTracks: List<BackendTopTrack>,
    @SerializedName("top_artists") val topArtists: List<BackendTopArtist>,
    @SerializedName("peak_hour") val peakHour: Int = 20,
    @SerializedName("favorite_day_of_week") val favoriteDayOfWeek: String = "Пятница"
)

@Keep
data class BackendTopTrack(
    @SerializedName("track_id") val trackId: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("play_count") val playCount: Long,
    @SerializedName("total_listened_ms") val totalListenedMs: Long
)

@Keep
data class BackendTopArtist(
    @SerializedName("artist") val artist: String,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("play_count") val playCount: Long,
    @SerializedName("total_listened_ms") val totalListenedMs: Long
)

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
    @SerializedName("track_id") val trackId: Any
)

@Keep
data class TrackReorderItem(
    @SerializedName("track_id") val trackId: Any,
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

@Keep
data class ArtistLookupResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("picture") val picture: String? = null
)

@Keep
data class AlbumLookupResponse(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist_name") val artistName: String? = null,
    @SerializedName("artist_id") val artistId: String? = null,
    @SerializedName("cover") val cover: String? = null
)

// ================= FLAC DATA MODELS =================

@Keep
data class DownloadFlacResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("track_id") val trackId: Any? = null,
    @SerializedName("album_id") val albumId: Any? = null,
    @SerializedName("album_title") val albumTitle: String? = null,
    @SerializedName("playlist_id") val playlistId: Any? = null,
    @SerializedName("detail") val detail: String? = null
)

@Keep
data class AlbumFlacStatusResponse(
    @SerializedName("album_id") val albumId: String? = null,
    @SerializedName("has_flac") val hasFlac: Boolean = false,
    @SerializedName("total_tracks") val totalTracks: Int = 0,
    @SerializedName("flac_tracks") val flacTracks: Int = 0,
    @SerializedName("percent") val percent: Float = 0f,
    @SerializedName("is_complete") val isComplete: Boolean = false
)

@Keep
data class PlaylistFlacStatusResponse(
    @SerializedName("playlist_id") val playlistId: Int? = null,
    @SerializedName("total_tracks") val totalTracks: Int = 0,
    @SerializedName("flac_tracks") val flacTracks: Int = 0,
    @SerializedName("percent") val percent: Float = 0f,
    @SerializedName("is_complete") val isComplete: Boolean = false
)

@Keep
data class TrackResolveInfoResponse(
    @SerializedName("track_id") val trackId: Any? = null,
    @SerializedName("is_lossless") val isLossless: Boolean = false,
    @SerializedName("has_local_flac") val hasLocalFlac: Boolean = false,
    @SerializedName("bitrate") val bitrate: Int? = null,
    @SerializedName("file_path") val filePath: String? = null
)