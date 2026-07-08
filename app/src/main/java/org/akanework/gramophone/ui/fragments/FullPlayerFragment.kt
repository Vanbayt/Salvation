package org.akanework.gramophone.ui.fragments

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.LikeCache
import org.akanework.gramophone.logic.api.GramophoneApi
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.CookiePlayButton
import org.akanework.gramophone.ui.components.SquigglySlider

class FullPlayerFragment : BottomSheetDialogFragment() {

    private var tvTitle: TextView? = null
    private var tvArtist: TextView? = null
    private var ivCover: ImageView? = null
    private var albumCoverFrame: MaterialCardView? = null
    private var btnNext: MaterialButton? = null
    private var btnPrev: MaterialButton? = null
    private var btnClose: MaterialButton? = null

    private var btnFavorite: MaterialButton? = null
    private var btnShuffle: MaterialButton? = null
    private var btnLoop: MaterialButton? = null
    private var btnQueue: MaterialButton? = null

    private var tvPosition: TextView? = null
    private var tvDuration: TextView? = null

    private var isUIPausedState = false
    private var currentTrackIdForAnimation: String? = null

    private val composePosition = mutableFloatStateOf(0f)
    private val composeDuration = mutableFloatStateOf(100f)
    private val composeIsPlaying = mutableStateOf(false)

    private val progressHandler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private lateinit var api: GramophoneApi

    private val progressRunnable = object : Runnable {
        override fun run() {
            controller?.let { player ->
                if (player.isPlaying) {
                    updateSliderSafe(player.currentPosition, player.duration)
                    tvPosition?.text = formatTime(player.currentPosition)
                    progressHandler.postDelayed(this, 32)
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let { updateUI(it) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            controller?.let { updateUI(it) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            controller?.let {
                updateUI(it)
                if (isPlaying) {
                    progressHandler.removeCallbacks(progressRunnable)
                    progressHandler.post(progressRunnable)
                } else {
                    progressHandler.removeCallbacks(progressRunnable)
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            controller?.let { updateUI(it) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            btnShuffle?.isChecked = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            btnLoop?.isChecked = repeatMode == Player.REPEAT_MODE_ALL
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        api = NetworkClient.getApi(requireContext())
        val view = inflater.inflate(R.layout.fragment_full_player, container, false)

        tvTitle = view.findViewById(R.id.full_song_name)
        tvArtist = view.findViewById(R.id.full_song_artist)
        ivCover = view.findViewById(R.id.full_sheet_cover)
        albumCoverFrame = view.findViewById(R.id.album_cover_frame)
        btnNext = view.findViewById(R.id.sheet_next_song)
        btnPrev = view.findViewById(R.id.sheet_previous_song)
        btnClose = view.findViewById(R.id.slide_down)

        tvPosition = view.findViewById(R.id.position)
        tvDuration = view.findViewById(R.id.duration)

        btnFavorite = view.findViewById(R.id.favor)
        btnShuffle = view.findViewById(R.id.sheet_random)
        btnLoop = view.findViewById(R.id.sheet_loop)
        btnQueue = view.findViewById(R.id.playlist)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- ИНИЦИАЛИЗАЦИЯ COMPOSE СЛАЙДЕРА ---
        val composeSliderView = view.findViewById<ComposeView>(R.id.compose_slider_view)
        composeSliderView.setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val hasDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                hasDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
                hasDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                SquigglySlider(
                    position = composePosition.floatValue,
                    duration = composeDuration.floatValue,
                    isPlaying = composeIsPlaying.value,
                    onValueChange = { newValue ->
                        composePosition.floatValue = newValue
                        tvPosition?.text = formatTime(newValue.toLong())
                        progressHandler.removeCallbacks(progressRunnable)
                    },
                    onValueChangeFinished = {
                        controller?.seekTo(composePosition.floatValue.toLong())
                        if (controller?.playWhenReady == true) {
                            progressHandler.post(progressRunnable)
                        }
                    }
                )
            }
        }

        // --- ИНИЦИАЛИЗАЦИЯ COMPOSE КНОПКИ "ПЕЧЕНЬКИ" ---
        val composePlayButton = view.findViewById<ComposeView>(R.id.compose_play_button)
        composePlayButton.setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val hasDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                hasDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
                hasDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                CookiePlayButton(
                    isPlaying = composeIsPlaying.value,
                    onClick = {
                        val currentPlayer = controller ?: return@CookiePlayButton
                        if (currentPlayer.playWhenReady) currentPlayer.pause() else currentPlayer.play()
                    }
                )
            }
        }

        setupClickListeners()

        val activity = requireActivity() as MainActivity
        activity.controllerViewModel.addControllerCallback(viewLifecycleOwner.lifecycle) { player, _ ->
            controller = player
            player.removeListener(playerListener)
            player.addListener(playerListener)
            updateUI(player)

            if (player.isPlaying) {
                progressHandler.removeCallbacks(progressRunnable)
                progressHandler.post(progressRunnable)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller?.let { player ->
            updateUI(player)
            if (player.isPlaying) {
                progressHandler.removeCallbacks(progressRunnable)
                progressHandler.post(progressRunnable)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(progressRunnable)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout

        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            sheet.requestLayout()
        }
    }

    private fun setupClickListeners() {
        btnClose?.setOnClickListener { dismiss() }
        btnNext?.setOnClickListener { controller?.seekToNext() }
        btnPrev?.setOnClickListener { controller?.seekToPrevious() }

        btnShuffle?.setOnClickListener {
            val currentPlayer = controller ?: return@setOnClickListener
            val isShuffleEnabled = !currentPlayer.shuffleModeEnabled
            currentPlayer.shuffleModeEnabled = isShuffleEnabled
            btnShuffle?.isChecked = isShuffleEnabled
            if (isShuffleEnabled) applyPhysicalShuffle(currentPlayer)
        }

        btnLoop?.setOnClickListener {
            val currentPlayer = controller ?: return@setOnClickListener
            val isLooping = currentPlayer.repeatMode == Player.REPEAT_MODE_ALL
            currentPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
            btnLoop?.isChecked = !isLooping
        }

        btnQueue?.setOnClickListener {
            PlayerMenuBottomSheet().show(parentFragmentManager, "PLAYER_MENU_SHEET")
        }

        btnFavorite?.setOnClickListener {
            val currentPlayer = controller ?: return@setOnClickListener
            val currentTrackId = currentPlayer.currentMediaItem?.mediaId ?: return@setOnClickListener
            val isCurrentlyLiked = LikeCache.likedTracks.contains(currentTrackId)
            val newFavStatus = !isCurrentlyLiked

            fun updateMemory(liked: Boolean) {
                if (liked) LikeCache.likedTracks.add(currentTrackId)
                else LikeCache.likedTracks.remove(currentTrackId)
                btnFavorite?.isChecked = liked
                applyFavoriteColor(liked)
            }

            updateMemory(newFavStatus)

            if (newFavStatus) {
                api.likeTrack(currentTrackId).enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                    override fun onResponse(call: retrofit2.Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {
                        if (!response.isSuccessful) updateMemory(false)
                    }
                    override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) { updateMemory(false) }
                })
            } else {
                api.unlikeTrack(currentTrackId).enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                    override fun onResponse(call: retrofit2.Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {
                        if (!response.isSuccessful) updateMemory(true)
                    }
                    override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) { updateMemory(true) }
                })
            }
        }
    }

    private fun updateUI(player: MediaController) {
        val currentItem = player.currentMediaItem
        val metadata = currentItem?.mediaMetadata ?: player.mediaMetadata
        val newTrackId = currentItem?.mediaId ?: ""

        // Проверяем, действительно ли переключился трек (чтобы не анимировать при обычной паузе)
        val isTrackChanged = currentTrackIdForAnimation != newTrackId && currentTrackIdForAnimation != null
        currentTrackIdForAnimation = newTrackId

        tvTitle?.text = metadata.title ?: "Неизвестный трек"
        tvTitle?.isSelected = true

        tvArtist?.text = metadata.artist ?: "Неизвестный артист"
        tvArtist?.isSelected = true

        val trackId = currentItem?.mediaId ?: ""
        val isLiked = LikeCache.likedTracks.contains(trackId)

        btnFavorite?.isChecked = isLiked
        applyFavoriteColor(isLiked)

        btnShuffle?.isChecked = player.shuffleModeEnabled
        btnLoop?.isChecked = player.repeatMode == Player.REPEAT_MODE_ALL

        val originalUri = metadata.artworkUri?.toString() ?: ""
        val finalCoverUrl = if (originalUri.startsWith("/")) {
            "http://185.196.41.31$originalUri"
        } else {
            originalUri
        }

        // 🔥 АНИМАЦИЯ СМЕНЫ ОБЛОЖКИ (PULSE SWAP)
        if (isTrackChanged) {
            albumCoverFrame?.animate()
                ?.scaleX(0.8f)?.scaleY(0.8f)?.alpha(0.5f)
                ?.setDuration(150)
                ?.withEndAction {
                    // Картинка меняется в момент максимального сжатия
                    if (finalCoverUrl.isNotEmpty()) ivCover?.load(finalCoverUrl) else ivCover?.setImageResource(R.drawable.ic_library)

                    // Выпрыгивает обратно
                    albumCoverFrame?.animate()
                        ?.scaleX(1f)?.scaleY(1f)?.alpha(1f)
                        ?.setDuration(400)
                        ?.setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                        ?.start()
                }?.start()
        } else {
            // Обычная загрузка (при старте приложения)
            if (finalCoverUrl.isNotEmpty()) ivCover?.load(finalCoverUrl) else ivCover?.setImageResource(R.drawable.ic_library)
        }

        composeIsPlaying.value = player.isPlaying

        animateCoverUI(player.isPlaying)

        updateSliderSafe(player.currentPosition, player.duration)
        tvDuration?.text = formatTime(player.duration)
        tvPosition?.text = formatTime(player.currentPosition)
    }

    private fun animateCoverUI(isPlaying: Boolean) {
        if (isUIPausedState == !isPlaying) return
        isUIPausedState = !isPlaying

        val density = requireContext().resources.displayMetrics.density

        // Сжимаем до 0.95 (5% разницы - очень мягкий эффект)
        val scaleTarget = if (isPlaying) 1f else 0.95f
        val elevationTarget = if (isPlaying) 20f * density else 4f * density

        albumCoverFrame?.animate()
            ?.scaleX(scaleTarget)
            ?.scaleY(scaleTarget)
            ?.setDuration(500)
            ?.setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            ?.start()

        android.animation.ValueAnimator.ofFloat(albumCoverFrame?.cardElevation ?: 0f, elevationTarget).apply {
            duration = 400
            addUpdateListener { albumCoverFrame?.cardElevation = it.animatedValue as Float }
            start()
        }
    }

    private fun applyFavoriteColor(isLiked: Boolean) {
        val typedValue = TypedValue()
        val theme = requireContext().theme
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        val activeColor = typedValue.data
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        val inactiveColor = typedValue.data
        btnFavorite?.iconTint = ColorStateList.valueOf(if (isLiked) activeColor else inactiveColor)
    }

    private fun updateSliderSafe(position: Long, duration: Long) {
        val dur = if (duration > 0) duration.toFloat() else 1f
        val pos = position.toFloat().coerceIn(0f, dur)
        composeDuration.floatValue = dur
        composePosition.floatValue = pos
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacksAndMessages(null)
        controller?.removeListener(playerListener)
        controller = null

        tvTitle = null; tvArtist = null; ivCover = null; albumCoverFrame = null
        btnNext = null; btnPrev = null; btnClose = null
        btnFavorite = null; btnShuffle = null; btnLoop = null; btnQueue = null
        tvPosition = null; tvDuration = null

        super.onDestroyView()
    }

    private fun applyPhysicalShuffle(player: MediaController) {
        val currentItemIndex = player.currentMediaItemIndex
        if (currentItemIndex == -1 || player.mediaItemCount <= 1) return

        val allItems = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            allItems.add(player.getMediaItemAt(i))
        }

        val currentItem = allItems.removeAt(currentItemIndex)
        allItems.shuffle()
        allItems.add(0, currentItem)

        player.replaceMediaItems(0, player.mediaItemCount, allItems)
        player.seekToDefaultPosition(0)
    }
}