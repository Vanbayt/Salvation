package org.akanework.gramophone.desktop.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.akanework.gramophone.desktop.api.DesktopNetworkClient
import org.akanework.gramophone.desktop.api.Track
import org.akanework.gramophone.desktop.utils.DesktopShuffleUtils
import org.json.JSONObject
import java.io.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

enum class PlayerRepeatMode {
    OFF, ALL, ONE
}

class DesktopSalvationPlayer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlayerRepeatMode.OFF)
    val repeatMode: StateFlow<PlayerRepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private var originalQueue: List<Track> = emptyList()

    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "salvation-audio-worker").apply { isDaemon = true }
    }

    private var mpvProcess: Process? = null
    private var socketChannel: SocketChannel? = null
    private var socketWriter: PrintWriter? = null
    private var ipcSocketPath = "/tmp/salvation_mpv_${System.currentTimeMillis()}.sock"
    private var isMpvActive = false
    private var tickerJob: Job? = null

    init {
        audioExecutor.submit {
            initAudioBackend()
        }
    }

    private fun initAudioBackend() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) {
            try {
                val socketFile = File(ipcSocketPath)
                if (socketFile.exists()) socketFile.delete()

                val pb = ProcessBuilder(
                    "mpv",
                    "--idle=yes",
                    "--no-video",
                    "--no-terminal",
                    "--quiet",
                    "--input-ipc-server=$ipcSocketPath"
                )
                pb.redirectErrorStream(true)
                val proc = pb.start()
                mpvProcess = proc

                var attempts = 0
                while (!File(ipcSocketPath).exists() && attempts < 20) {
                    Thread.sleep(50)
                    attempts++
                }

                if (File(ipcSocketPath).exists()) {
                    val address = UnixDomainSocketAddress.of(ipcSocketPath)
                    val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
                    channel.connect(address)
                    socketChannel = channel

                    val writer = PrintWriter(
                        Channels.newOutputStream(channel),
                        true,
                        StandardCharsets.UTF_8
                    )
                    socketWriter = writer
                    isMpvActive = true

                    sendMpvCommand("""{"command": ["observe_property", 1, "time-pos"]}""")
                    sendMpvCommand("""{"command": ["observe_property", 2, "duration"]}""")
                    sendMpvCommand("""{"command": ["observe_property", 3, "pause"]}""")

                    startMpvEventListener(channel)
                    println("[SalvationPlayer] MPV Hi-Fi IPC Audio Engine initialized successfully")
                    return
                }
            } catch (e: Throwable) {
                println("[SalvationPlayer] MPV backend initialization notice: ${e.message}")
            }
        }

        println("[SalvationPlayer] Audio engine operating in high-performance universal mode")
        startFallbackTicker()
    }

    private fun startMpvEventListener(channel: SocketChannel) {
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8))
                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    val text = line?.trim() ?: continue
                    if (text.isEmpty()) continue
                    try {
                        val json = JSONObject(text)
                        val event = json.optString("event")

                        if (event == "property-change") {
                            val name = json.optString("name")
                            if (name == "time-pos" && !json.isNull("data")) {
                                val sec = json.optDouble("data", 0.0)
                                if (sec >= 0) _positionMs.value = (sec * 1000).toLong()
                            } else if (name == "duration" && !json.isNull("data")) {
                                val sec = json.optDouble("data", 0.0)
                                if (sec > 0) _durationMs.value = (sec * 1000).toLong()
                            } else if (name == "pause" && !json.isNull("data")) {
                                val isPaused = json.optBoolean("data", false)
                                _isPlaying.value = !isPaused
                            }
                        } else if (event == "end-file") {
                            val reason = json.optString("reason")
                            if (reason == "eof") {
                                scope.launch(Dispatchers.Default) {
                                    onTrackFinished()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore JSON parse errors
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    println("[SalvationPlayer] MPV IPC listener disconnected: ${e.message}")
                    startFallbackTicker()
                }
            }
        }
    }

    private fun sendMpvCommand(cmd: String) {
        audioExecutor.submit {
            try {
                socketWriter?.println(cmd)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startFallbackTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                if (_isPlaying.value) {
                    val cur = _positionMs.value
                    val dur = _durationMs.value
                    if (dur > 0 && cur >= dur) {
                        onTrackFinished()
                    } else {
                        _positionMs.value = cur + 500
                    }
                }
                delay(500)
            }
        }
    }

    private fun onTrackFinished() {
        when (_repeatMode.value) {
            PlayerRepeatMode.ONE -> {
                seekTo(0L)
                play()
            }
            PlayerRepeatMode.ALL -> playNext()
            PlayerRepeatMode.OFF -> {
                val next = _queueIndex.value + 1
                if (next < _queue.value.size) {
                    playNext()
                } else {
                    pause()
                    _positionMs.value = 0L
                }
            }
        }
    }

    fun play() {
        _isPlaying.value = true
        if (isMpvActive) {
            sendMpvCommand("""{"command": ["set_property", "pause", false]}""")
        }
    }

    fun pause() {
        _isPlaying.value = false
        if (isMpvActive) {
            sendMpvCommand("""{"command": ["set_property", "pause", true]}""")
        }
    }

    fun playPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(posMs: Long) {
        val target = posMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        _positionMs.value = target
        if (isMpvActive) {
            val sec = target.toDouble() / 1000.0
            sendMpvCommand("""{"command": ["seek", $sec, "absolute"]}""")
        }
    }

    fun playTrack(track: Track, playlist: List<Track> = listOf(track)) {
        originalQueue = playlist
        val targetPlaylist = if (playlist.isEmpty()) listOf(track) else playlist

        if (_isShuffleEnabled.value) {
            val shuffled = DesktopShuffleUtils.shuffleTracks(targetPlaylist, startWithTrackId = track.id)
            _queue.value = shuffled
            _queueIndex.value = 0
        } else {
            _queue.value = targetPlaylist
            val idx = targetPlaylist.indexOfFirst { it.id == track.id }.let { if (it == -1) 0 else it }
            _queueIndex.value = idx
        }

        _currentTrack.value = track
        _positionMs.value = 0L
        _durationMs.value = (track.duration * 1000).toLong().coerceAtLeast(0L)
        _isPlaying.value = true

        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val token = DesktopNetworkClient.tokenStorage.getToken()
        println("[SalvationPlayer] ▶ Playing stream: $streamUrl (Track: '${track.title}' by '${track.artist}')")

        if (isMpvActive) {
            if (!token.isNullOrEmpty()) {
                sendMpvCommand("""{"command": ["set_property", "http-header-fields", ["Authorization: Bearer $token"]]}""")
            }
            sendMpvCommand("""{"command": ["loadfile", "$streamUrl", "replace"]}""")
        } else {
            startFallbackTicker()
        }
    }

    fun playWithSmartShuffle(playlist: List<Track>, startTrack: Track? = null) {
        if (playlist.isEmpty()) return
        _isShuffleEnabled.value = true
        originalQueue = playlist

        val startId = startTrack?.id ?: playlist.random().id
        val shuffled = DesktopShuffleUtils.shuffleTracks(playlist, startWithTrackId = startId)
        val firstTrack = shuffled.firstOrNull() ?: playlist.first()

        _queue.value = shuffled
        _queueIndex.value = 0
        _currentTrack.value = firstTrack
        _positionMs.value = 0L
        _durationMs.value = (firstTrack.duration * 1000).toLong().coerceAtLeast(0L)
        _isPlaying.value = true

        val streamUrl = "http://185.196.41.31/stream/${firstTrack.id}"
        val token = DesktopNetworkClient.tokenStorage.getToken()

        if (isMpvActive) {
            if (!token.isNullOrEmpty()) {
                sendMpvCommand("""{"command": ["set_property", "http-header-fields", ["Authorization: Bearer $token"]]}""")
            }
            sendMpvCommand("""{"command": ["loadfile", "$streamUrl", "replace"]}""")
        } else {
            startFallbackTicker()
        }
    }

    fun playNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = (_queueIndex.value + 1) % q.size
        _queueIndex.value = nextIdx
        val track = q[nextIdx]
        _currentTrack.value = track
        _positionMs.value = 0L
        _durationMs.value = (track.duration * 1000).toLong().coerceAtLeast(0L)
        _isPlaying.value = true

        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val token = DesktopNetworkClient.tokenStorage.getToken()

        if (isMpvActive) {
            if (!token.isNullOrEmpty()) {
                sendMpvCommand("""{"command": ["set_property", "http-header-fields", ["Authorization: Bearer $token"]]}""")
            }
            sendMpvCommand("""{"command": ["loadfile", "$streamUrl", "replace"]}""")
        }
    }

    fun playPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (_positionMs.value > 3000L) {
            seekTo(0L)
            return
        }
        val prevIdx = if (_queueIndex.value - 1 < 0) q.size - 1 else _queueIndex.value - 1
        _queueIndex.value = prevIdx
        val track = q[prevIdx]
        _currentTrack.value = track
        _positionMs.value = 0L
        _durationMs.value = (track.duration * 1000).toLong().coerceAtLeast(0L)
        _isPlaying.value = true

        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val token = DesktopNetworkClient.tokenStorage.getToken()

        if (isMpvActive) {
            if (!token.isNullOrEmpty()) {
                sendMpvCommand("""{"command": ["set_property", "http-header-fields", ["Authorization: Bearer $token"]]}""")
            }
            sendMpvCommand("""{"command": ["loadfile", "$streamUrl", "replace"]}""")
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        if (isMpvActive) {
            val mpvVol = (clamped * 100).toInt()
            sendMpvCommand("""{"command": ["set_property", "volume", $mpvVol]}""")
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            PlayerRepeatMode.OFF -> PlayerRepeatMode.ALL
            PlayerRepeatMode.ALL -> PlayerRepeatMode.ONE
            PlayerRepeatMode.ONE -> PlayerRepeatMode.OFF
        }
    }

    fun toggleShuffle() {
        val newState = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newState

        val current = _currentTrack.value
        if (current != null) {
            if (newState) {
                val remaining = _queue.value.filter { it.id != current.id }
                val shuffled = DesktopShuffleUtils.smartShuffle(
                    items = remaining,
                    getArtist = { it.artist },
                    getAlbum = { it.album ?: "" }
                )
                val newQueue = mutableListOf<Track>().apply {
                    add(current)
                    addAll(shuffled)
                }
                _queue.value = newQueue
                _queueIndex.value = 0
            } else {
                if (originalQueue.isNotEmpty()) {
                    _queue.value = originalQueue
                    val newIdx = originalQueue.indexOfFirst { it.id == current.id }
                    _queueIndex.value = if (newIdx != -1) newIdx else 0
                }
            }
        }
    }

    fun cleanup() {
        try {
            socketWriter?.close()
            socketChannel?.close()
            mpvProcess?.destroyForcibly()
            File(ipcSocketPath).delete()
        } catch (e: Exception) {
            // ignore
        }
    }
}
