package org.akanework.gramophone.logic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.akanework.gramophone.logic.api.Album

class AlbumViewModel : ViewModel() {
    private val _album = MutableLiveData<Album>()
    val album: LiveData<Album> get() = _album

    fun setAlbum(data: Album) {
        _album.value = data
    }
}