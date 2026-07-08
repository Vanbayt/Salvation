package org.akanework.gramophone.logic

// Синглтон, который хранит ID всех лайкнутых треков в оперативной памяти
object LikeCache {
    val likedTracks = mutableSetOf<String>()
}