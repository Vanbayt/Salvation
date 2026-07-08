package uk.akane.libphonograph.reader

import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.emitOrDie
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.flows.Invalidation
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.flows.conflateAndBlockWhenPaused
import org.akanework.gramophone.logic.utils.flows.provideReplayCacheInvalidationManager
import org.akanework.gramophone.logic.utils.flows.repeatUntilDoneWhenUnpaused
import org.akanework.gramophone.logic.utils.flows.requireReplayCacheInvalidationManager
import uk.akane.libphonograph.contentObserverVersioningFlow
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.items.Genre

@OptIn(ExperimentalCoroutinesApi::class)
class FlowReader(
    context: Context,
    minSongLengthSecondsFlow: SharedFlow<Long>,
    blackListSetFlow: SharedFlow<Set<String>>,
    shouldUseEnhancedCoverReadingFlow: SharedFlow<Boolean?>,
    recentlyAddedFilterSecondFlow: SharedFlow<Long?>,
    shouldIncludeExtraFormatFlow: SharedFlow<Boolean>,
    coverStubUri: String? = null
) {
    // --- ДОБАВЛЕНО: Глобальный доступ ---
    companion object {
        var INSTANCE: FlowReader? = null
    }
    // ------------------------------------

    private var awaitingRefresh = false
    var hadFirstRefresh = true
        private set
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineName("FlowReader"))
    private val finishRefreshTrigger = MutableSharedFlow<Unit>(replay = 0)
    private val manualRefreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    init {
        // Сохраняем ссылку на себя, чтобы AdapterFragment мог нас вызвать
        INSTANCE = this

        manualRefreshTrigger.emitOrDie(Unit)

        // Авто-обновление каждые 15 секунд (оставляем пока для теста)
        scope.launch {
            while (true) {
                delay(150_000L)
                try {
                    manualRefreshTrigger.emit(Unit)
                    android.util.Log.d("NAVIDROME_REFRESH", "Timer refresh tick")
                } catch (e: Exception) {
                    android.util.Log.e("NAVIDROME_REFRESH", "Timer failed", e)
                }
            }
        }
    }

    // Слушатели изменений
    private val rawPlaylistVersionFlow = contentObserverVersioningFlow(
        context, scope,
        @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true
    ).shareIn(scope, Eagerly, replay = 1)

    private val mediaVersionFlow = contentObserverVersioningFlow(
        context, scope, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true
    ).shareIn(scope, Eagerly, replay = 1)

    private val rawPlaylistFlow = rawPlaylistVersionFlow
        .onEach { requireReplayCacheInvalidationManager().invalidate() }
        .conflateAndBlockWhenPaused()
        .flatMapLatest {
            manualRefreshTrigger.mapLatest { _ ->
                if (context.hasAudioPermission())
                    Reader.fetchPlaylists(context).first
                else emptyList()
            }
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)

    private val readerFlow: Flow<ReaderResult> =
        shouldIncludeExtraFormatFlow.distinctUntilChanged()
            .flatMapLatest { shouldIncludeExtraFormat ->
                shouldUseEnhancedCoverReadingFlow.distinctUntilChanged()
                    .flatMapLatest { shouldUseEnhancedCoverReading ->
                        minSongLengthSecondsFlow.distinctUntilChanged()
                            .flatMapLatest { minSongLengthSeconds ->
                                blackListSetFlow.distinctUntilChanged()
                                    .flatMapLatest { blackListSet ->
                                        mediaVersionFlow
                                            .onEach { requireReplayCacheInvalidationManager().invalidate() }
                                            .conflateAndBlockWhenPaused()
                                            .flatMapLatest {
                                                manualRefreshTrigger.mapLatest { _ ->
                                                    repeatUntilDoneWhenUnpaused {
                                                        if (context.hasAudioPermission())
                                                            Reader.readFromMediaStore(
                                                                context,
                                                                minSongLengthSeconds,
                                                                blackListSet,
                                                                shouldUseEnhancedCoverReading,
                                                                shouldIncludeExtraFormat,
                                                                coverStubUri = coverStubUri
                                                            )
                                                        else ReaderResult.emptyReaderResult()
                                                    }
                                                }
                                            }
                                    }
                            }
                    }
            }
            .onEach {
                finishRefreshTrigger.emit(Unit)
                awaitingRefresh = true
                hadFirstRefresh = true
            }
            .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
            .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)

    val idMapFlow: Flow<Map<Long, MediaItem>> = readerFlow.map { it.idMap!! }
    private val idPathMapFlow = readerFlow.map { it.idMap!! to it.pathMap!! }
    val songListFlow: Flow<List<MediaItem>> = readerFlow.map { it.songList }

    private val recentlyAddedFlow = recentlyAddedFilterSecondFlow.distinctUntilChanged()
        .onEach { requireReplayCacheInvalidationManager().invalidate() }
        .combine(songListFlow) { recentlyAddedFilterSecond, songList ->
            if (recentlyAddedFilterSecond != null)
                RecentlyAdded(
                    (System.currentTimeMillis() / 1000L) - recentlyAddedFilterSecond,
                    songList
                )
            else
                null
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)

    private val favoriteFlow = songListFlow.map { songList ->
        Favorite(songList)
    }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)

    private val mappedPlaylistsFlow =
        idPathMapFlow.combine(rawPlaylistFlow) { idPathMap, rawPlaylists ->
            rawPlaylists.map { it.toPlaylist(idPathMap.first, idPathMap.second) }
        }

    val albumListFlow: Flow<List<Album>> = readerFlow.map { it.albumList!! }
    val albumArtistListFlow: Flow<List<Artist>> = readerFlow.map { it.albumArtistList!! }
    val artistListFlow: Flow<List<Artist>> = readerFlow.map { it.artistList!! }
    val genreListFlow: Flow<List<Genre>> = readerFlow.map { it.genreList!! }
    val dateListFlow: Flow<List<Date>> = readerFlow.map { it.dateList!! }

    val playlistListFlow = combine(mappedPlaylistsFlow, recentlyAddedFlow, favoriteFlow)
    { mappedPlaylists, recentlyAdded, favorite ->
        val base = if (Flags.FAVORITE_SONGS) mappedPlaylists + favorite else mappedPlaylists
        if (recentlyAdded != null) base + recentlyAdded else base
    }

    val folderStructureFlow: Flow<FileNode> = readerFlow.map { it.folderStructure!! }
    val shallowFolderFlow: Flow<FileNode> = readerFlow.map { it.shallowFolder!! }
    val foldersFlow: Flow<Set<String>> = readerFlow.map { it.folders!! }

    suspend fun refresh() {
        coroutineScope {
            if (!awaitingRefresh) {
                playlistListFlow.first()
            }
            val waiter = launch {
                finishRefreshTrigger.first()
            }
            manualRefreshTrigger.emit(Unit)
            waiter.join()
        }
    }
}