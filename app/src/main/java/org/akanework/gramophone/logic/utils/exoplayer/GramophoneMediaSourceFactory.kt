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
        // === ИСПРАВЛЕНИЕ ТАЙМ-АУТОВ ===
        // Мы полностью заменяем dataSourceFactory на нашу, прокачанную!
        this.dataSourceFactory = AuthenticatedDataSourceFactory(context)

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

private val sharedOkHttpClient: okhttp3.OkHttpClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(10, 10, java.util.concurrent.TimeUnit.MINUTES))
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

private class AuthenticatedDataSourceFactory(
    private val context: android.content.Context
) : DataSource.Factory {

    private val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
        context,
        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(sharedOkHttpClient)
    )

    override fun createDataSource(): DataSource {
        return AuthenticatedDataSource(context, defaultDataSourceFactory.createDataSource())
    }
}

private class AuthenticatedDataSource(
    private val context: android.content.Context,
    private val upstream: DataSource
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val targetUri = ClientTrackResolver.resolveStreamUrl(context, dataSpec.uri)
        val specToUse = if (targetUri != dataSpec.uri) {
            dataSpec.buildUpon().setUri(targetUri).build()
        } else {
            dataSpec
        }

        val newHeaders = specToUse.httpRequestHeaders.toMutableMap()
        val token = org.akanework.gramophone.logic.api.AuthManager.getToken(context)

        // 1. Подставляем токен авторизации ТОЛЬКО для нашего сервера 185.196.41.31
        val targetHost = targetUri.host ?: ""
        val isOurBackend = targetHost == "185.196.41.31"

        if (isOurBackend) {
            if (token != null) {
                newHeaders["Authorization"] = "Bearer $token"
            }
        } else {
            newHeaders.remove("Authorization")
            newHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
            newHeaders["Referer"] = "https://music.youtube.com/"
            newHeaders["Origin"] = "https://music.youtube.com"
        }

        // 2. Формируем заголовок Range для перемотки по байтам
        if (specToUse.position > 0 || specToUse.length != androidx.media3.common.C.LENGTH_UNSET.toLong()) {
            val rangeHeader = buildString {
                append("bytes=")
                append(specToUse.position)
                append("-")
                if (specToUse.length != androidx.media3.common.C.LENGTH_UNSET.toLong()) {
                    append(specToUse.position + specToUse.length - 1)
                }
            }
            newHeaders["Range"] = rangeHeader
        }

        // 3. Собираем новый запрос с сохранением позиции и заголовками
        val newSpec = specToUse.buildUpon()
            .setHttpRequestHeaders(newHeaders)
            .build()

        val bytesRemaining = upstream.open(newSpec)

        // 4. ДИНАМИЧЕСКАЯ ОБРАБОТКА X-Content-Duration
        try {
            val trackId = dataSpec.uri.lastPathSegment ?: ""
            val respHeaders = upstream.responseHeaders
            val durationHeader = respHeaders.entries
                .find { it.key.equals("X-Content-Duration", ignoreCase = true) }
                ?.value?.firstOrNull()
            if (!durationHeader.isNullOrEmpty()) {
                val durationSec = durationHeader.toLongOrNull() ?: 0L
                if (durationSec > 0L) {
                    org.akanework.gramophone.logic.GramophonePlaybackService.updateTrackDuration(
                        trackId,
                        durationSec * 1000L
                    )
                }
            }
        } catch (_: Exception) {
        }

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? {
        return upstream.uri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return upstream.responseHeaders
    }

    override fun close() {
        upstream.close()
    }
}