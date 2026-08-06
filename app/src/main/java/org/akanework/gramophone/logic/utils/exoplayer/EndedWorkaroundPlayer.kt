package org.akanework.gramophone.logic.utils.exoplayer

import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.ExoPlayer
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.utils.CircularShuffleOrder


/**
 * If player in STATE_ENDED is resumed, state will be STATE_READY, on play button press it will
 * update to STATE_ENDED and only then media3 will wrap around playlist for us. This is a workaround
 * to restore STATE_ENDED as well and fake it for media3 until it indeed wraps around playlist.
 */
class EndedWorkaroundPlayer(exoPlayer: ExoPlayer) : ForwardingSimpleBasePlayer(exoPlayer),
    Player.Listener {

    companion object {
        private const val TAG = "EndedWorkaroundPlayer"
    }

    private val remoteDeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()

    init {
        player.addListener(this)
    }

    val exoPlayer
        get() = player as ExoPlayer

    // TODO: can't we do this in a cleaner way?
    var nextShuffleOrder:
            ((firstIndex: Int, mediaItemCount: Int, EndedWorkaroundPlayer) -> CircularShuffleOrder)? =
        null
    var isEnded = false
        set(value) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "isEnded set to $value (was $field)")
            }
            field = value
        }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == DISCONTINUITY_REASON_SEEK) {
            isEnded = false
        }
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    override fun getState(): State {
        var baseState = super.getState()
        if (isEnded) {
            if (baseState.playerError != null) {
                isEnded = false
            } else {
                baseState = baseState.buildUpon().setPlaybackState(STATE_ENDED).setIsLoading(false).build()
            }
        }
        if (player.currentTimeline.isEmpty) {
            return baseState.buildUpon().setDeviceInfo(remoteDeviceInfo).build()
        }

        val currentId = player.currentMediaItem?.mediaId
        val trackDur = currentId?.let { org.akanework.gramophone.logic.GramophonePlaybackService.getTrackDuration(it) }
            ?: androidx.media3.common.C.TIME_UNSET
        val dur = if (player.duration > 0) player.duration else trackDur

        val updatedCommands = baseState.availableCommands.buildUpon()
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

        return baseState.buildUpon()
            .setAvailableCommands(updatedCommands)
            .build()
    }
}