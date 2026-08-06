package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.akanework.gramophone.logic.api.Playlist
import org.akanework.gramophone.ui.MainActivity

class ComposeContainerFragment : Fragment() {

    private var playlist: Playlist? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlist = arguments?.getSerializable("PLAYLIST") as? Playlist
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val isDarkTheme = isSystemInDarkTheme()
                val dynamicColorScheme = when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                        if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    isDarkTheme -> darkColorScheme()
                    else -> lightColorScheme()
                }

                MaterialTheme(colorScheme = dynamicColorScheme) {
                    playlist?.let { currentPlaylist ->
                        PlaylistDetailScreen(
                            playlist = currentPlaylist,
                            onBackClick = { requireActivity().onBackPressed() },
                            onPlayClick = { tracksToPlay, startIndex ->
                                val player = (requireActivity() as MainActivity).getPlayer()
                                if (player != null && tracksToPlay.isNotEmpty()) {
                                    val mediaItems = tracksToPlay.map { track ->
                                        val extrasBundle = Bundle().apply {
                                            putFloat("replay_gain", track.replayGain ?: 0f)
                                            putString("ARTIST_ID", track.artistId ?: "")
                                            putString("ALBUM_ID", track.albumId ?: "")
                                            putString("PLAYING_FROM", "Плейлист: ${currentPlaylist.title}")
                                        }

                                        MediaItem.Builder()
                                            .setMediaId(track.id)
                                            .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                                            .setMediaMetadata(
                                                MediaMetadata.Builder()
                                                    .setTitle(track.title)
                                                    .setArtist(track.artist)
                                                    .setArtworkUri(track.cover?.toUri())
                                                    .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                                                    .setExtras(extrasBundle)
                                                    .build()
                                            )
                                            .build()
                                    }

                                    player.setMediaItems(mediaItems, startIndex, 0L)
                                    player.prepare()
                                    player.play()
                                } else {
                                    Toast.makeText(context, "Плеер еще загружается...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(playlist: Playlist): ComposeContainerFragment {
            return ComposeContainerFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("PLAYLIST", playlist)
                }
            }
        }
    }
}