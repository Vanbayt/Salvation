package org.akanework.gramophone.logic.api

data class SearchResponse(
    val artists: List<Artist>? = emptyList(),
    val albums: List<Album>? = emptyList(),
    val tracks: List<Track>? = emptyList()
)