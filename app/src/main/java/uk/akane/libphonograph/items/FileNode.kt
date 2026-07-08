package uk.akane.libphonograph.items

import androidx.media3.common.MediaItem
import kotlin.math.min

interface FileNode {
    val folderName: String
    val folderList: Map<String, FileNode>
    val songList: List<MediaItem>
    val albumId: Long?
    val addDate: Long?
        get() = min(songList.minOfOrNull { it.mediaMetadata.addDate ?: Long.MAX_VALUE }
            ?: Long.MAX_VALUE,
            folderList.minOfOrNull { it.value.addDate ?: Long.MAX_VALUE } ?: Long.MAX_VALUE).let {
            if (it == Long.MAX_VALUE) null else it
        }
    val modifiedDate: Long?
        get() = min(songList.maxOfOrNull { it.mediaMetadata.modifiedDate ?: Long.MIN_VALUE }
            ?: Long.MIN_VALUE,
            folderList.maxOfOrNull { it.value.modifiedDate ?: Long.MIN_VALUE }
                ?: Long.MIN_VALUE).let {
            if (it == Long.MIN_VALUE) null else it
        }
}

object EmptyFileNode : FileNode {
    override val folderName: String
        get() = ""
    override val folderList = mapOf<String, FileNode>()
    override val songList = listOf<MediaItem>()
    override val albumId = null
}