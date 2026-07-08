package org.akanework.gramophone.logic.api

data class SearchResponse(
    val artists: List<Artist>? = emptyList(),
    val tracks: List<Track>? = emptyList()
)