package org.akanework.gramophone.logic.utils.exoplayer

import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.BundledExtractorsAdapter
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ExternalLoader
import androidx.media3.exoplayer.source.ExternallyLoadedMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap.Unseekable
import androidx.media3.extractor.text.SubtitleParser
import com.google.common.base.Supplier
import com.google.common.primitives.Ints
import java.io.IOException

@Suppress("unused")
class GramophoneMediaSourceFactory(
    private val context: android.content.Context,
    private var dataSourceFactory: DataSource.Factory,
    extractorsFactory: ExtractorsFactory
) : MediaSource.Factory {
    private val delegateFactoryLoader: DelegateFactoryLoader =
        DelegateFactoryLoader(extractorsFactory, SubtitleParser.Factory.UNSUPPORTED)
    private var serverSideAdInsertionMediaSourceFactory: MediaSource.Factory? = null
    private var externalImageLoader: ExternalLoader? = null
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null
    private var liveTargetOffsetMs: Long
    private var liveMinOffsetMs: Long
    private var liveMaxOffsetMs: Long
    private var liveMinSpeed: Float
    private var liveMaxSpeed: Float

    init {
        // === ДИСКОВЫЙ КЭШ (500 МБ LRU) + СТЕК АВТОРИЗАЦИИ И ТАЙМ-АУТОВ ===
        val upstreamFactory = AuthenticatedDataSourceFactory(context)
        this.dataSourceFactory = MediaCacheManager.createCacheDataSourceFactory(context, upstreamFactory)

        delegateFactoryLoader.setDataSourceFactory(this.dataSourceFactory)

        this.liveTargetOffsetMs = -9223372036854775807L
        this.liveMinOffsetMs = -9223372036854775807L
        this.liveMaxOffsetMs = -9223372036854775807L
        this.liveMinSpeed = -3.4028235E38f
        this.liveMaxSpeed = -3.4028235E38f
    }

    fun setExternalImageLoader(externalImageLoader: ExternalLoader?): GramophoneMediaSourceFactory {
        this.externalImageLoader = externalImageLoader
        return this
    }

    fun setLiveTargetOffsetMs(liveTargetOffsetMs: Long): GramophoneMediaSourceFactory {
        this.liveTargetOffsetMs = liveTargetOffsetMs
        return this
    }

    fun setLiveMinOffsetMs(liveMinOffsetMs: Long): GramophoneMediaSourceFactory {
        this.liveMinOffsetMs = liveMinOffsetMs
        return this
    }

    fun setLiveMaxOffsetMs(liveMaxOffsetMs: Long): GramophoneMediaSourceFactory {
        this.liveMaxOffsetMs = liveMaxOffsetMs
        return this
    }

    fun setLiveMinSpeed(minSpeed: Float): GramophoneMediaSourceFactory {
        this.liveMinSpeed = minSpeed
        return this
    }

    fun setLiveMaxSpeed(maxSpeed: Float): GramophoneMediaSourceFactory {
        this.liveMaxSpeed = maxSpeed
        return this
    }

    override fun setCmcdConfigurationFactory(cmcdConfigurationFactory: CmcdConfiguration.Factory): GramophoneMediaSourceFactory {
        delegateFactoryLoader.setCmcdConfigurationFactory(
            Assertions.checkNotNull(cmcdConfigurationFactory)
        )
        return this
    }

    override fun setDrmSessionManagerProvider(param: DrmSessionManagerProvider): MediaSource.Factory {
        throw UnsupportedOperationException("drm is not supported")
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): GramophoneMediaSourceFactory {
        this.loadErrorHandlingPolicy = Assertions.checkNotNull(
            loadErrorHandlingPolicy,
            "MediaSource.Factory#setLoadErrorHandlingPolicy no longer handles null..."
        )
        delegateFactoryLoader.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray {
        return delegateFactoryLoader.getSupportedTypes()
    }

    override fun createMediaSource(inMediaItem: MediaItem): MediaSource {
        var mediaItem = inMediaItem
        Assertions.checkNotNull(mediaItem.localConfiguration)
        val scheme = mediaItem.localConfiguration!!.uri.scheme
        if (scheme != null && (scheme == "ssai")) {
            return Assertions.checkNotNull(this.serverSideAdInsertionMediaSourceFactory)
                .createMediaSource(mediaItem)
        } else if ((mediaItem.localConfiguration!!.mimeType == "application/x-image-uri")) {
            return (ExternallyLoadedMediaSource.Factory(
                Util.msToUs(mediaItem.localConfiguration!!.imageDurationMs),
                Assertions.checkNotNull(this.externalImageLoader)
            )).createMediaSource(mediaItem)
        } else {
            val type = Util.inferContentTypeForUriAndMimeType(
                mediaItem.localConfiguration!!.uri, mediaItem.localConfiguration!!.mimeType
            )
            if (mediaItem.localConfiguration!!.imageDurationMs != -9223372036854775807L) {
                delegateFactoryLoader.setJpegExtractorFlags(1)
            }

            val mediaSourceFactory = delegateFactoryLoader.getMediaSourceFactory(type)
            Assertions.checkStateNotNull(
                mediaSourceFactory,
                "No suitable media source factory found for content type: $type"
            )
            val liveConfigurationBuilder = mediaItem.liveConfiguration.buildUpon()

            if (mediaItem.liveConfiguration.targetOffsetMs == -9223372036854775807L) {
                liveConfigurationBuilder.setTargetOffsetMs(this.liveTargetOffsetMs)
            }
            if (mediaItem.liveConfiguration.minPlaybackSpeed == -3.4028235E38f) {
                liveConfigurationBuilder.setMinPlaybackSpeed(this.liveMinSpeed)
            }
            if (mediaItem.liveConfiguration.maxPlaybackSpeed == -3.4028235E38f) {
                liveConfigurationBuilder.setMaxPlaybackSpeed(this.liveMaxSpeed)
            }
            if (mediaItem.liveConfiguration.minOffsetMs == -9223372036854775807L) {
                liveConfigurationBuilder.setMinOffsetMs(this.liveMinOffsetMs)
            }
            if (mediaItem.liveConfiguration.maxOffsetMs == -9223372036854775807L) {
                liveConfigurationBuilder.setMaxOffsetMs(this.liveMaxOffsetMs)
            }

            val liveConfiguration = liveConfigurationBuilder.build()
            if (liveConfiguration != mediaItem.liveConfiguration) {
                mediaItem = mediaItem.buildUpon().setLiveConfiguration(liveConfiguration).build()
            }

            var mediaSource: MediaSource = mediaSourceFactory!!.createMediaSource(mediaItem)
            val subtitleConfigurations: List<SubtitleConfiguration> =
                Util.castNonNull(mediaItem.localConfiguration).subtitleConfigurations

            if (subtitleConfigurations.isNotEmpty()) {
                val mediaSources = ArrayList<MediaSource>(subtitleConfigurations.size + 1)
                mediaSources.add(mediaSource)

                for (i in subtitleConfigurations.indices) {
                    val config = subtitleConfigurations[i]
                    val format = (Format.Builder())
                        .setSampleMimeType(config.mimeType)
                        .setLanguage(config.language)
                        .setSelectionFlags(config.selectionFlags)
                        .setRoleFlags(config.roleFlags)
                        .setLabel(config.label)
                        .setId(config.id)
                        .build()

                    val extractorsFactory = ExtractorsFactory { arrayOf(UnknownSubtitlesExtractor(format)) }
                    val progressiveMediaSourceFactory = ProgressiveMediaSource.Factory(
                        this.dataSourceFactory,
                        { BundledExtractorsAdapter(extractorsFactory) },
                        { DrmSessionManager.DRM_UNSUPPORTED },
                        DefaultLoadErrorHandlingPolicy(),
                        1048576
                    )
                    if (this.loadErrorHandlingPolicy != null) {
                        progressiveMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy!!)
                    }

                    mediaSources.add(progressiveMediaSourceFactory.createMediaSource(
                        MediaItem.fromUri(config.uri.toString())
                    ))
                }
                mediaSource = MergingMediaSource(*mediaSources.toTypedArray())
            }

            return maybeClipMediaSource(mediaItem, mediaSource)
        }
    }

    private class DelegateFactoryLoader(
        private val extractorsFactory: ExtractorsFactory,
        private var subtitleParserFactory: SubtitleParser.Factory
    ) {
        private val mediaSourceFactorySuppliers: MutableMap<Int?, Supplier<MediaSource.Factory>?> = hashMapOf()
        private val supportedTypes: MutableSet<Int?> = hashSetOf()
        private val mediaSourceFactories: MutableMap<Int?, MediaSource.Factory?> = hashMapOf()
        private var dataSourceFactory: DataSource.Factory? = null
        private var cmcdConfigurationFactory: CmcdConfiguration.Factory? = null
        private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
        private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null

        fun getSupportedTypes(): IntArray {
            this.ensureAllSuppliersAreLoaded()
            return Ints.toArray(this.supportedTypes.filterNotNull())
        }

        fun getMediaSourceFactory(contentType: Int): MediaSource.Factory? {
            var mediaSourceFactory = mediaSourceFactories[contentType]
            if (mediaSourceFactory != null) {
                return mediaSourceFactory
            } else {
                val supplier = this.maybeLoadSupplier(contentType) ?: return null
                mediaSourceFactory = supplier.get() as MediaSource.Factory

                cmcdConfigurationFactory?.let { mediaSourceFactory.setCmcdConfigurationFactory(it) }
                drmSessionManagerProvider?.let { mediaSourceFactory.setDrmSessionManagerProvider(it) }
                loadErrorHandlingPolicy?.let { mediaSourceFactory.setLoadErrorHandlingPolicy(it) }

                mediaSourceFactory.setSubtitleParserFactory(this.subtitleParserFactory)
                mediaSourceFactories[contentType] = mediaSourceFactory
                return mediaSourceFactory
            }
        }

        fun setDataSourceFactory(dataSourceFactory: DataSource.Factory) {
            if (dataSourceFactory !== this.dataSourceFactory) {
                this.dataSourceFactory = dataSourceFactory
                mediaSourceFactorySuppliers.clear()
                mediaSourceFactories.clear()
            }
        }

        fun setCmcdConfigurationFactory(cmcdConfigurationFactory: CmcdConfiguration.Factory?) {
            this.cmcdConfigurationFactory = cmcdConfigurationFactory
            mediaSourceFactories.values.forEach {
                (it as? MediaSource.Factory)?.setCmcdConfigurationFactory(cmcdConfigurationFactory!!)
            }
        }

        fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy?) {
            this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
            mediaSourceFactories.values.forEach {
                (it as? MediaSource.Factory)?.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy!!)
            }
        }

        fun setJpegExtractorFlags(flags: Int) {
            if (extractorsFactory is DefaultExtractorsFactory) {
                extractorsFactory.setJpegExtractorFlags(flags)
            }
        }

        private fun ensureAllSuppliersAreLoaded() {
            for (i in 0..4) this.maybeLoadSupplier(i)
        }

        private fun maybeLoadSupplier(contentType: Int): Supplier<MediaSource.Factory>? {
            if (mediaSourceFactorySuppliers.containsKey(contentType)) {
                return mediaSourceFactorySuppliers[contentType]
            } else {
                var supplier: Supplier<MediaSource.Factory>? = null
                val dsFactory = Assertions.checkNotNull<DataSource.Factory?>(this.dataSourceFactory)

                try {
                    when (contentType) {
                        0 -> {
                            val clazz = Class.forName("androidx.media3.exoplayer.dash.DashMediaSource\$Factory").asSubclass(MediaSource.Factory::class.java)
                            supplier = Supplier { newInstance(clazz, dsFactory) }
                        }
                        1 -> {
                            val clazz = Class.forName("androidx.media3.exoplayer.smoothstreaming.SsMediaSource\$Factory").asSubclass(MediaSource.Factory::class.java)
                            supplier = Supplier { newInstance(clazz, dsFactory) }
                        }
                        2 -> {
                            val clazz = Class.forName("androidx.media3.exoplayer.hls.HlsMediaSource\$Factory").asSubclass(MediaSource.Factory::class.java)
                            supplier = Supplier { newInstance(clazz, dsFactory) }
                        }
                        3 -> {
                            val clazz = Class.forName("androidx.media3.exoplayer.rtsp.RtspMediaSource\$Factory").asSubclass(MediaSource.Factory::class.java)
                            supplier = Supplier { newInstance(clazz) }
                        }
                        4 -> {
                            supplier = Supplier {
                                ProgressiveMediaSource.Factory(
                                    dsFactory,
                                    { BundledExtractorsAdapter(extractorsFactory) },
                                    { DrmSessionManager.DRM_UNSUPPORTED },
                                    DefaultLoadErrorHandlingPolicy(),
                                    1048576
                                )
                            }
                        }
                    }
                } catch (_: ClassNotFoundException) {}

                mediaSourceFactorySuppliers[contentType] = supplier
                if (supplier != null) {
                    supportedTypes.add(contentType)
                }
                return supplier
            }
        }
    }

    private class UnknownSubtitlesExtractor(private val format: Format) : Extractor {
        override fun sniff(input: ExtractorInput) = true
        override fun init(output: ExtractorOutput) {
            val trackOutput = output.track(0, 3)
            output.seekMap(Unseekable(-9223372036854775807L))
            output.endTracks()
            trackOutput.format(
                format.buildUpon().setSampleMimeType("text/x-unknown").setCodecs(format.sampleMimeType).build()
            )
        }
        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
            return if (input.skip(Int.MAX_VALUE) == -1) Extractor.RESULT_END_OF_INPUT else Extractor.RESULT_CONTINUE
        }
        override fun seek(position: Long, timeUs: Long) {}
        override fun release() {}
    }

    companion object {
        private fun maybeClipMediaSource(mediaItem: MediaItem, mediaSource: MediaSource): MediaSource {
            return if ((mediaItem.clippingConfiguration.startPositionUs == 0L) &&
                (mediaItem.clippingConfiguration.endPositionUs == Long.MIN_VALUE) &&
                !mediaItem.clippingConfiguration.relativeToDefaultPosition) {
                mediaSource
            } else {
                ClippingMediaSource(
                    mediaSource,
                    mediaItem.clippingConfiguration.startPositionUs,
                    mediaItem.clippingConfiguration.endPositionUs,
                    !mediaItem.clippingConfiguration.startsAtKeyFrame,
                    mediaItem.clippingConfiguration.relativeToLiveWindow,
                    mediaItem.clippingConfiguration.relativeToDefaultPosition
                )
            }
        }

        private fun newInstance(clazz: Class<out MediaSource.Factory>, dataSourceFactory: DataSource.Factory): MediaSource.Factory {
            return clazz.getConstructor(DataSource.Factory::class.java).newInstance(dataSourceFactory)
        }

        private fun newInstance(clazz: Class<out MediaSource.Factory>): MediaSource.Factory {
            return clazz.getConstructor().newInstance()
        }
    }
}

// =========================================================================
// КЛАССЫ-ОБЕРТКИ ДЛЯ АВТОРИЗАЦИИ И ТАЙМ-АУТОВ
// =========================================================================

private val streamingOkHttpClient: okhttp3.OkHttpClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .addNetworkInterceptor { chain ->
            val orig = chain.request()
            val host = orig.url.host
            if (host.contains("googlevideo.com")) {
                val ua = ClientTrackResolver.getUserAgentForUrl(orig.url.toString())
                val builder = orig.newBuilder()
                    .removeHeader("Authorization")
                    .removeHeader("Referer")
                    .removeHeader("Origin")
                    .removeHeader("Cookie")
                    .header("User-Agent", ua)

                // 🔥 CRITICAL CDN RULE: Google Video CDN returns 403 on open-ended ranges or ranges > 1MB.
                // Must always provide a strictly bounded Range: bytes=start-end with max chunk size 512 KB (524287 bytes).
                val existingRange = orig.header("Range")
                if (existingRange == null) {
                    builder.header("Range", "bytes=0-524287")
                } else if (existingRange.startsWith("bytes=")) {
                    val rangeBody = existingRange.removePrefix("bytes=").trim()
                    val dashIdx = rangeBody.indexOf('-')
                    if (dashIdx != -1) {
                        val startStr = rangeBody.substring(0, dashIdx).trim()
                        val endStr = rangeBody.substring(dashIdx + 1).trim()
                        val start = startStr.toLongOrNull() ?: 0L
                        val end = endStr.toLongOrNull()
                        val clampedEnd = if (end == null || (end - start) >= 524288L) {
                            start + 524287L
                        } else {
                            end
                        }
                        builder.header("Range", "bytes=$start-$clampedEnd")
                    } else {
                        builder.header("Range", "bytes=0-524287")
                    }
                }
                chain.proceed(builder.build())
            } else {
                chain.proceed(orig)
            }
        }
        .connectionPool(okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

internal class AuthenticatedDataSourceFactory(
    private val context: android.content.Context
) : DataSource.Factory {

    private val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
        context,
        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamingOkHttpClient)
    )

    override fun createDataSource(): DataSource {
        return AuthenticatedDataSource(context, defaultDataSourceFactory)
    }
}

internal class AuthenticatedDataSource(
    private val context: android.content.Context,
    private val dataSourceFactory: DataSource.Factory
) : DataSource {

    private companion object {
        private const val CHUNK_SIZE_BYTES = 512 * 1024L // 512 KB HTTP Range Chunks
    }

    private val transferListeners = java.util.concurrent.CopyOnWriteArrayList<TransferListener>()
    private var activeDataSource: DataSource = createDelegate()

    private fun createDelegate(): DataSource {
        val ds = dataSourceFactory.createDataSource()
        for (listener in transferListeners) {
            ds.addTransferListener(listener)
        }
        return ds
    }

    private var isChunkedMode = false
    private var originalDataSpec: DataSpec? = null
    private var activeSpecToUse: DataSpec? = null
    private var currentStreamPosition = 0L
    private var totalLengthRequested = 0L
    private var totalBytesReadInStream = 0L
    private var knownTotalLength = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        if (!transferListeners.contains(transferListener)) {
            transferListeners.add(transferListener)
        }
        activeDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val startTime = System.currentTimeMillis()
        val targetUri = ClientTrackResolver.resolveStreamUrl(context, dataSpec.uri)
        var specToUse = if (targetUri != dataSpec.uri) {
            dataSpec.buildUpon().setUri(targetUri).build()
        } else {
            dataSpec
        }

        val newHeaders = specToUse.httpRequestHeaders.toMutableMap()
        val token = org.akanework.gramophone.logic.api.AuthManager.getToken(context)

        // 1. Подставляем токен авторизации ТОЛЬКО для нашего сервера 185.196.41.31
        val targetHost = targetUri.host ?: ""
        val isOurBackend = targetHost == "185.196.41.31"
        val isGoogleVideo = targetHost.contains("googlevideo.com")

        if (isGoogleVideo) {
            newHeaders.remove("Authorization")
            newHeaders.remove("Referer")
            newHeaders.remove("Origin")
            newHeaders.remove("Cookie")
            newHeaders["User-Agent"] = ClientTrackResolver.getUserAgentForUrl(targetUri.toString())
        } else if (isOurBackend) {
            if (token != null) {
                newHeaders["Authorization"] = "Bearer $token"
            }
        } else {
            newHeaders.remove("Authorization")
            newHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
            newHeaders["Referer"] = "https://music.youtube.com/"
            newHeaders["Origin"] = "https://music.youtube.com"
        }

        // 2. Continuous streaming mode
        isChunkedMode = false
        originalDataSpec = dataSpec
        currentStreamPosition = dataSpec.position
        totalLengthRequested = dataSpec.length

        // 3. Собираем новый запрос с сохранением позиции и заголовками
        val baseSpec = specToUse.buildUpon()
            .setHttpRequestHeaders(newHeaders)
            .build()
        activeSpecToUse = baseSpec
        val newSpec = baseSpec

        val trackIdStr = dataSpec.uri.lastPathSegment ?: ""
        val trackIdLong = trackIdStr.toLongOrNull()

        org.akanework.gramophone.logic.utils.PlaybackLogger.log(
            "STREAM_OPEN",
            "Opening stream for track '$trackIdStr' | Host: $targetHost | Position: ${dataSpec.position} | Length: ${dataSpec.length} | Chunked: $isChunkedMode"
        )

        var attempt = 0
        val maxRetries = 2
        var lastException: Exception? = null

        while (attempt <= maxRetries) {
            attempt++
            try {
                try {
                    activeDataSource.close()
                } catch (_: Exception) {}
                activeDataSource = createDelegate()

                val bytesRemaining = activeDataSource.open(newSpec)
                val elapsed = System.currentTimeMillis() - startTime

                // 🔍 ДЕТАЛЬНАЯ ДИАГНОСТИКА YOUTUBE CDN
                try {
                    val queryStr = targetUri.query ?: ""
                    val hasRatebypass = queryStr.contains("ratebypass=yes")
                    val cver = targetUri.getQueryParameter("cver") ?: "none"
                    val rn = targetUri.getQueryParameter("rn") ?: "none"
                    val expire = targetUri.getQueryParameter("expire")?.toLongOrNull() ?: 0L
                    val nowSec = System.currentTimeMillis() / 1000L
                    val expireDiffSec = expire - nowSec

                    val respHeaders = activeDataSource.responseHeaders
                    val serverHeader = respHeaders.entries.find { it.key.equals("Server", ignoreCase = true) }?.value?.firstOrNull() ?: "none"
                    val googStat = respHeaders.entries.find { it.key.equals("X-Goog-Stat", ignoreCase = true) }?.value?.firstOrNull() ?: "none"
                    val googBackoff = respHeaders.entries.find { it.key.equals("X-Goog-Backoff", ignoreCase = true) }?.value?.firstOrNull() ?: "none"
                    val contentType = respHeaders.entries.find { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull() ?: "none"

                    val crHeader = respHeaders.entries.find { it.key.equals("Content-Range", ignoreCase = true) }?.value?.firstOrNull() ?: ""
                    if (crHeader.isNotEmpty()) {
                        val m = Regex("/(\\d+)").find(crHeader)
                        if (m != null && m.groupValues.size > 1) {
                            knownTotalLength = m.groupValues[1].toLongOrNull() ?: 0L
                        }
                    }

                    val diagMsg = "Host: $targetHost | HasRatebypass: $hasRatebypass | Cver: $cver | Rn: $rn | ExpireSecLeft: ${expireDiffSec}s | OpenTime: ${elapsed}ms | TotalLen: ${knownTotalLength}b | Server: $serverHeader | ContentType: $contentType | GoogStat: $googStat | Backoff: $googBackoff"
                    org.akanework.gramophone.logic.utils.PlaybackLogger.log("STREAM_CDN_DIAG", diagMsg)
                    if (trackIdLong != null) {
                        ClientTrackResolver.sendTelemetryDirect("STREAM_CDN_DIAG", trackIdLong, message = diagMsg)
                    }
                } catch (_: Exception) {}

                org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                    "STREAM_SUCCESS",
                    "Stream opened in ${elapsed}ms for track '$trackIdStr' (Attempt $attempt) | Bytes remaining: $bytesRemaining"
                )

                // 🔥 ДИНАМИЧЕСКОЕ ПРОДЛЕНИЕ ТАЙМ-АУТА И СБРОС СЧЕТЧИКА СБОЕВ
                org.akanework.gramophone.logic.utils.SmartPlaybackManager.extendTimeoutOnStreamOpened()
                org.akanework.gramophone.logic.utils.SmartPlaybackManager.resetConsecutiveFailures()

                // 4. ДИНАМИЧЕСКАЯ ОБРАБОТКА X-Content-Duration
                try {
                    val respHeaders = activeDataSource.responseHeaders
                    val durationHeader = respHeaders.entries
                        .find { it.key.equals("X-Content-Duration", ignoreCase = true) }
                        ?.value?.firstOrNull()
                    if (!durationHeader.isNullOrEmpty()) {
                        val durationSec = durationHeader.toLongOrNull() ?: 0L
                        if (durationSec > 0L) {
                            org.akanework.gramophone.logic.GramophonePlaybackService.updateTrackDuration(
                                trackIdStr,
                                durationSec * 1000L
                            )
                        }
                    }
                } catch (_: Exception) {}

                val remainingToReturn = if (totalLengthRequested != androidx.media3.common.C.LENGTH_UNSET.toLong()) {
                    totalLengthRequested
                } else if (knownTotalLength > 0L) {
                    knownTotalLength - currentStreamPosition
                } else {
                    bytesRemaining
                }
                return remainingToReturn
            } catch (e: Exception) {
                lastException = e
                val elapsed = System.currentTimeMillis() - startTime

                // 🔥 INSTANT FALLBACK TO DIRECT CLIENT STREAM IF SERVER FAILED
                if (targetHost == "185.196.41.31" && trackIdLong != null) {
                    val directUrl = ClientTrackResolver.getDirectStreamUrl(trackIdLong)
                    if (!directUrl.isNullOrEmpty()) {
                        org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                            "STREAM_FAILOVER_DIRECT",
                            "Server stream failed on 185.196.41.31 (${e.javaClass.simpleName}: ${e.message}). Instant failover to direct client stream on phone!"
                        )
                        isChunkedMode = true
                        val chunkLen = if (totalLengthRequested != androidx.media3.common.C.LENGTH_UNSET.toLong() && totalLengthRequested < CHUNK_SIZE_BYTES) totalLengthRequested else CHUNK_SIZE_BYTES
                        val directSpec = baseSpec.buildUpon()
                            .setUri(Uri.parse(directUrl))
                            .setHttpRequestHeaders(emptyMap())
                            .setPosition(currentStreamPosition)
                            .setLength(chunkLen)
                            .build()
                        try {
                            try {
                                activeDataSource.close()
                            } catch (_: Exception) {}
                            activeDataSource = createDelegate()
                            activeSpecToUse = directSpec
                            originalDataSpec = directSpec.buildUpon().setLength(androidx.media3.common.C.LENGTH_UNSET.toLong()).build()
                            val freshBytes = activeDataSource.open(directSpec)
                            org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                                "STREAM_FAILOVER_DIRECT_SUCCESS",
                                "Direct client stream connected in failover! Playing directly from phone IP"
                            )
                            org.akanework.gramophone.logic.utils.SmartPlaybackManager.extendTimeoutOnStreamOpened()
                            org.akanework.gramophone.logic.utils.SmartPlaybackManager.resetConsecutiveFailures()
                            return freshBytes
                        } catch (directEx: Exception) {
                            org.akanework.gramophone.logic.utils.PlaybackLogger.log("STREAM_FAILOVER_DIRECT_ERR", "Direct fallback failed: ${directEx.message}")
                        }
                    }
                }

                if (attempt <= maxRetries && (e is java.io.IOException || e is androidx.media3.datasource.HttpDataSource.HttpDataSourceException)) {
                    org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                        "STREAM_RETRY",
                        "Retrying stream open (Attempt $attempt/$maxRetries) with fresh URL resolution after error: ${e.javaClass.simpleName} - ${e.message}"
                    )

                    val effectiveTrackId = ClientTrackResolver.findTrackIdForUri(dataSpec.uri) ?: trackIdStr
                    val freshUri = ClientTrackResolver.resolveStreamUrl(context, dataSpec.uri, forceRefresh = true)
                    val freshHost = freshUri.host ?: ""
                    val freshHeaders = specToUse.httpRequestHeaders.toMutableMap()
                    if (freshHost.contains("googlevideo.com")) {
                        freshHeaders.remove("Authorization")
                        freshHeaders.remove("Referer")
                        freshHeaders.remove("Origin")
                        freshHeaders.remove("Cookie")
                        freshHeaders["User-Agent"] = ClientTrackResolver.getUserAgentForUrl(freshUri.toString())
                    } else {
                        freshHeaders.remove("Authorization")
                        freshHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                        freshHeaders["Referer"] = "https://music.youtube.com/"
                        freshHeaders["Origin"] = "https://music.youtube.com"
                    }
                    val retrySpec = specToUse.buildUpon()
                        .setUri(freshUri)
                        .setHttpRequestHeaders(freshHeaders)
                        .setPosition(currentStreamPosition)
                        .setLength(if (totalLengthRequested != androidx.media3.common.C.LENGTH_UNSET.toLong()) totalLengthRequested else androidx.media3.common.C.LENGTH_UNSET.toLong())
                        .build()
                    activeSpecToUse = retrySpec

                    try {
                        try {
                            activeDataSource.close()
                        } catch (_: Exception) {}
                        activeDataSource = createDelegate()

                        val freshBytes = activeDataSource.open(retrySpec)
                        org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                            "STREAM_RETRY_SUCCESS",
                            "Retry $attempt succeeded! Stream opened for track '$effectiveTrackId' on host '$freshHost'"
                        )
                        org.akanework.gramophone.logic.utils.SmartPlaybackManager.extendTimeoutOnStreamOpened()
                        org.akanework.gramophone.logic.utils.SmartPlaybackManager.resetConsecutiveFailures()
                        return freshBytes
                    } catch (retryEx: Exception) {
                        lastException = retryEx
                    }
                } else {
                    org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                        "STREAM_ERR",
                        "Stream open FAILED in ${elapsed}ms for track '$trackIdStr' on host '$targetHost': ${e.javaClass.simpleName} - ${e.message}"
                    )
                    if (trackIdLong != null) {
                        ClientTrackResolver.invalidateCache(trackIdLong)
                    }
                    throw e
                }
            }
        }
        throw lastException ?: java.io.IOException("Stream open failed after retries")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val t0 = System.currentTimeMillis()
        try {
            var bytes = activeDataSource.read(buffer, offset, length)

            // 🔥 SEAMLESS CHUNK ADVANCE: When reaching end of current 512KB bounded chunk, open next chunk!
            if (bytes == androidx.media3.common.C.RESULT_END_OF_INPUT || bytes <= 0) {
                if (knownTotalLength <= 0L || currentStreamPosition < knownTotalLength) {
                    try {
                        activeDataSource.close()
                    } catch (_: Exception) {}
                    activeDataSource = createDelegate()

                    val base = activeSpecToUse ?: originalDataSpec
                    if (base != null) {
                        val nextSpec = base.buildUpon()
                            .setPosition(currentStreamPosition)
                            .setLength(androidx.media3.common.C.LENGTH_UNSET.toLong())
                            .build()
                        activeDataSource.open(nextSpec)
                        bytes = activeDataSource.read(buffer, offset, length)
                    }
                }
            }

            val readTime = System.currentTimeMillis() - t0

            if (bytes > 0) {
                totalBytesReadInStream += bytes
                currentStreamPosition += bytes
            }

            if (readTime > 1000L) {
                val host = activeDataSource.uri?.host ?: ""
                org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                    "STREAM_READ_BLOCK",
                    "Read delayed: ${readTime}ms for $bytes bytes on host '$host' (Total bytes read: $totalBytesReadInStream)"
                )
            }
            return bytes
        } catch (e: Exception) {
            val readTime = System.currentTimeMillis() - t0
            val host = activeDataSource.uri?.host ?: ""
            org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                "STREAM_READ_EXCEPTION",
                "Read exception after ${readTime}ms on host '$host': ${e.javaClass.simpleName} - ${e.message} at offset $currentStreamPosition"
            )

            // 🔥 AUTO-RECONNECT ON CDN DISCONNECT
            if (e is androidx.media3.datasource.HttpDataSource.HttpDataSourceException || e is java.io.IOException) {
                if (currentStreamPosition > 0L && (knownTotalLength <= 0L || currentStreamPosition < knownTotalLength - 32768L)) {
                    org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                        "STREAM_AUTO_RECONNECT",
                        "Auto-reconnecting continuous stream from offset $currentStreamPosition..."
                    )
                    try {
                        activeDataSource.close()
                    } catch (_: Exception) {}
                    activeDataSource = createDelegate()

                    val base = activeSpecToUse ?: originalDataSpec
                    if (base != null) {
                        val resumeSpec = base.buildUpon()
                            .setPosition(currentStreamPosition)
                            .setLength(androidx.media3.common.C.LENGTH_UNSET.toLong())
                            .build()
                        try {
                            val freshBytesRemaining = activeDataSource.open(resumeSpec)
                            val resumedBytes = activeDataSource.read(buffer, offset, length)
                            if (resumedBytes > 0) {
                                totalBytesReadInStream += resumedBytes
                                currentStreamPosition += resumedBytes
                            }
                            org.akanework.gramophone.logic.utils.PlaybackLogger.log(
                                "STREAM_AUTO_RECONNECT_SUCCESS",
                                "Auto-reconnect succeeded at offset $currentStreamPosition | Remaining: $freshBytesRemaining"
                            )
                            return resumedBytes
                        } catch (resEx: Exception) {
                            org.akanework.gramophone.logic.utils.PlaybackLogger.log("STREAM_RECONNECT_ERR", "Auto-reconnect failed: ${resEx.message}")
                        }
                    }
                }
            }
            throw e
        }
    }

    override fun getUri(): Uri? {
        return activeDataSource.uri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return activeDataSource.responseHeaders
    }

    override fun close() {
        isChunkedMode = false
        originalDataSpec = null
        activeSpecToUse = null
        try {
            activeDataSource.close()
        } catch (_: Exception) {}
    }
}

// =========================================================================
// КЛАСС ДЛЯ УПРАВЛЕНИЯ ДИСКОВЫМ КЭШЕМ И ОФЛАЙН-ЗАГРУЗКАМИ
// =========================================================================
/*
 * TODO: ИНТЕГРАЦИЯ С ОФЛАЙН-ЗАГРУЗКАМИ (SPOTIFY-STYLE OFFLINE DOWNLOADS):
 * 
 * Текущая реализация `MediaCacheManager` предоставляет потоковый LRU-кэш на 500 МБ для защиты от
 * плохого 4G и мгновенного воспроизведения. При реализации полноценного офлайн-режима
 * (скачивания треков/альбомов на постоянной основе) необходимо внести следующие изменения:
 *
 * 1. РАЗДЕЛЕНИЕ ХРАНИЛИЩА (или Защита Загрузок от LRU-вытеснения):
 *    - Вариант А: Создать отдельный экземпляр `SimpleCache` (или директорию `context.filesDir/salvation_downloads`)
 *      без `LeastRecentlyUsedCacheEvictor` (или с `NoOpCacheEvictor`), предназначенный ТОЛЬКО для постоянных офлайн-загрузок.
 *    - Вариант Б: Использовать единый `SimpleCache`, но реализовать кастомный `CacheKeyFactory` / `CacheEvictor`,
 *      который проверяет флаг `is_downloaded` из локальной базы данных (Room/SQLite) и НЕ удаляет закэшированные
 *      spans, помеченные как "Офлайн".
 *
 * 2. ПОДКЛЮЧЕНИЕ `DownloadManager` ИЗ MEDIA3:
 *    - Использовать `androidx.media3.exoplayer.offline.DownloadManager` и `ProgressiveDownloader` для
 *      фонового скачивания выбранных пользователем плейлистов/альбомов в кэш-директорию.
 *    - При нажатии кнопки "Скачать" передавать `MediaItem` в `DownloadManager`.
 *
 * 3. ВОСПРОИЗВЕДЕНИЕ В ОФЛАЙНЕ (Без сети):
 *    - При открытии раздела "Скачанные" или в офлайн-режиме `SmartPlaybackManager` должен брать локальный `Uri`
 *      из базы данных / `CacheDataSource` напрямую через `FileDataSource.Factory()`, полностью минуя
 *      сетевые вызовы `ClientTrackResolver` и обращения к бэкенду.
 */
object MediaCacheManager {
    private const val TAG = "MediaCacheManager"
    private const val MAX_CACHE_SIZE_BYTES = 500 * 1024 * 1024L // 500 МБ
    private const val CACHE_DIR_NAME = "salvation_media_cache"

    @Volatile
    private var simpleCache: androidx.media3.datasource.cache.SimpleCache? = null

    @Synchronized
    fun getCache(context: android.content.Context): androidx.media3.datasource.cache.SimpleCache {
        if (simpleCache == null) {
            val appCtx = context.applicationContext
            val cacheDir = java.io.File(appCtx.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val databaseProvider = androidx.media3.database.StandaloneDatabaseProvider(appCtx)
            simpleCache = androidx.media3.datasource.cache.SimpleCache(cacheDir, evictor, databaseProvider)
            org.akanework.gramophone.logic.utils.PlaybackLogger.log(TAG, "SimpleCache initialized at ${cacheDir.absolutePath} (Max 500 MB)")
        }
        return simpleCache!!
    }

    fun createCacheDataSourceFactory(
        context: android.content.Context,
        upstreamFactory: DataSource.Factory
    ): DataSource.Factory {
        val cache = getCache(context)
        return androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}