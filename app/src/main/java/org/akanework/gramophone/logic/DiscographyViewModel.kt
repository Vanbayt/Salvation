package org.akanework.gramophone.logic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.akanework.gramophone.logic.api.Album

class DiscographyViewModel : ViewModel() {
    // Приватная изменяемая переменная (чтобы никто снаружи не сломал данные)
    private val _albums = MutableLiveData<List<Album>>()
    // Публичная неизменяемая для наблюдения из фрагментов
    val albums: LiveData<List<Album>> get() = _albums

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    // Метод для загрузки данных с сервера в ViewModel
    fun setAlbums(list: List<Album>) {
        _albums.value = list
        _isLoading.value = false
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}