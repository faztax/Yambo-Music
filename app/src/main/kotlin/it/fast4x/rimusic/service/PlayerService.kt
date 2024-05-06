package it.fast4x.rimusic.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.SQLException
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.bodies.PlayerBody
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.player
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.ProxyPreferences
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.R
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import it.fast4x.rimusic.enums.ExoPlayerMinTimeForEvent
import it.fast4x.rimusic.models.Event
import it.fast4x.rimusic.models.PersistentQueue
import it.fast4x.rimusic.models.PersistentSong
import it.fast4x.rimusic.models.QueuedMediaItem
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.models.asMediaItem
import it.fast4x.rimusic.query
import it.fast4x.rimusic.transaction
import it.fast4x.rimusic.utils.ConditionalCacheDataSourceFactory
import it.fast4x.rimusic.utils.InvincibleService
import it.fast4x.rimusic.utils.RingBuffer
import it.fast4x.rimusic.utils.TimerJob
import it.fast4x.rimusic.utils.YouTubeRadio
import it.fast4x.rimusic.utils.activityPendingIntent
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.audioQualityFormatKey
import it.fast4x.rimusic.utils.broadCastPendingIntent
import it.fast4x.rimusic.utils.closebackgroundPlayerKey
import it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import it.fast4x.rimusic.utils.exoPlayerMinTimeForEventKey
import it.fast4x.rimusic.utils.findNextMediaItemById
import it.fast4x.rimusic.utils.forcePlayFromBeginning
import it.fast4x.rimusic.utils.forceSeekToNext
import it.fast4x.rimusic.utils.forceSeekToPrevious
import it.fast4x.rimusic.utils.getEnum
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.isAtLeastAndroid10
import it.fast4x.rimusic.utils.isAtLeastAndroid12
import it.fast4x.rimusic.utils.isAtLeastAndroid13
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isAtLeastAndroid8
import it.fast4x.rimusic.utils.isInvincibilityEnabledKey
import it.fast4x.rimusic.utils.isShowingThumbnailInLockscreenKey
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.persistentQueueKey
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.queueLoopEnabledKey
import it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey
import it.fast4x.rimusic.utils.shouldBePlaying
import it.fast4x.rimusic.utils.showDownloadButtonBackgroundPlayerKey
import it.fast4x.rimusic.utils.showLikeButtonBackgroundPlayerKey
import it.fast4x.rimusic.utils.skipSilenceKey
import it.fast4x.rimusic.utils.timer
import it.fast4x.rimusic.utils.trackLoopEnabledKey
import it.fast4x.rimusic.utils.volumeNormalizationKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import android.os.Binder as AndroidBinder

const val LOCAL_KEY_PREFIX = "local:"

@get:OptIn(UnstableApi::class)
val DataSpec.isLocal get() = key?.startsWith(LOCAL_KEY_PREFIX) == true

val MediaItem.isLocal get() = mediaId.startsWith(LOCAL_KEY_PREFIX)
val Song.isLocal get() = id.startsWith(LOCAL_KEY_PREFIX)

@UnstableApi
@Suppress("DEPRECATION")
class PlayerService : InvincibleService(),
    Player.Listener,
    PlaybackStatsListener.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val coroutineScope = CoroutineScope(Dispatchers.IO) + Job()
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var cache: SimpleCache
    private lateinit var player: ExoPlayer
    private lateinit var downloadCache: SimpleCache

    private val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH



    @ExperimentalCoroutinesApi
    @FlowPreview
    private val stateBuilderWithoutCustomAction
        get() = PlaybackStateCompat.Builder().setActions(actions.let {
            if (isAtLeastAndroid12) it or PlaybackState.ACTION_SET_PLAYBACK_SPEED else it
        })

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val stateBuilder
        get() = PlaybackStateCompat.Builder().setActions(actions.let {
            if (isAtLeastAndroid12) it or PlaybackState.ACTION_SET_PLAYBACK_SPEED else it
        })
        .addCustomAction(
            "DOWNLOAD",
            "Download",
            if (isDownloadedState.value || isCachedState.value) R.drawable.downloaded else R.drawable.download

        )
        .addCustomAction(
            "LIKE",
            "Like",
            if (isLikedState.value) R.drawable.heart else R.drawable.heart_outline
        )
        .addCustomAction(
            "PLAYRADIO",
            "Play radio",
            R.drawable.radio
        )

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val stateBuilderWithDownloadOnly
        get() = PlaybackStateCompat.Builder().setActions(actions.let {
            if (isAtLeastAndroid12) it or PlaybackState.ACTION_SET_PLAYBACK_SPEED else it
        }).addCustomAction(
            "DOWNLOAD",
            "Download",
            if (isDownloadedState.value || isCachedState.value) R.drawable.downloaded else R.drawable.download

        )
        .addCustomAction(
            "PLAYRADIO",
            "Play radio",
            R.drawable.radio
        )

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val stateBuilderWithLikeOnly
        get() = PlaybackStateCompat.Builder().setActions(actions.let {
            if (isAtLeastAndroid12) it or PlaybackState.ACTION_SET_PLAYBACK_SPEED else it
        }).addCustomAction(
            "LIKE",
            "Like",
            if (isLikedState.value) R.drawable.heart else R.drawable.heart_outline
        )
        .addCustomAction(
            "PLAYRADIO",
            "Play radio",
            R.drawable.radio
        )

    private val playbackStateMutex = Mutex()

    private val metadataBuilder = MediaMetadataCompat.Builder()

    private var notificationManager: NotificationManager? = null

    private var timerJob: TimerJob? = null

    private var radio: YouTubeRadio? = null

    private lateinit var bitmapProvider: BitmapProvider

    private var volumeNormalizationJob: Job? = null

    private var isPersistentQueueEnabled = false
    private var isclosebackgroundPlayerEnabled = false
    private var isShowingThumbnailInLockscreen = true
    override var isInvincibilityEnabled = false

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val binder = Binder()

    private var isNotificationStarted = false

    private var exoPlayerAlternateCacheLocation = ""

    override val notificationId: Int
        get() = NotificationId

    private lateinit var notificationActionReceiver: NotificationActionReceiver
    private lateinit var audioQualityFormat: AudioQualityFormat

    /*
    private val media = MutableStateFlow<MediaItem?>(null)
    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private val currentMedia = media.flatMapLatest { mediaItem ->
        Database.song(mediaItem?.mediaId)
    }.stateIn(coroutineScope, SharingStarted.Lazily, null)

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private var currentMediaLiked = media.flatMapLatest { mediaItem ->
       if (mediaItem?.mediaId?.let { Database.songliked(it) } == 0) flowOf(false) else flowOf(true)
    }.stateIn(coroutineScope, SharingStarted.Lazily, false)

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private var currentMediaDownloaded = media.flatMapLatest { mediaItem ->
        val downloads = DownloadUtil.downloads.value
        flowOf(downloads[mediaItem?.mediaId]?.state == Download.STATE_COMPLETED)
    }.stateIn(coroutineScope, SharingStarted.Lazily, false)
     */

    private val mediaItemState = MutableStateFlow<MediaItem?>(null)

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val isLikedState = mediaItemState
        .flatMapMerge { item ->
            item?.mediaId?.let { Database.likedAt(it).distinctUntilChanged() } ?: flowOf(null)
        }
        .map { it != null }
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    //private val mediaDownloadedItemState = MutableStateFlow<MediaItem?>(null)

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val isDownloadedState = mediaItemState
        .flatMapMerge { item ->
            item?.mediaId?.let {
                val downloads = DownloadUtil.downloads.value
                flowOf(downloads[item.mediaId]?.state == Download.STATE_COMPLETED)
                //flowOf(downloadCache.isCached(it, 0, Database.formatContentLength(it)))
            } ?: flowOf(false)
        }
        //.map { true }
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    //private val mediaCachedItemState = MutableStateFlow<MediaItem?>(null)

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val isCachedState = mediaItemState
        .flatMapMerge { item ->
            item?.mediaId?.let {
                flowOf(
                    cache.isCached(it, 0, Database.formatContentLength(it))
                )
            } ?: flowOf(false)
        }
        .map { it }
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private var showLikeButton = true
    private var showDownloadButton = true

    override fun onBind(intent: Intent?): AndroidBinder {
        super.onBind(intent)
        return binder
    }


    @ExperimentalCoroutinesApi
    @FlowPreview
    @SuppressLint("Range")
    @UnstableApi
    override fun onCreate() {
        super.onCreate()

        bitmapProvider = BitmapProvider(
            bitmapSize = (256 * resources.displayMetrics.density).roundToInt(),
            colorProvider = { isSystemInDarkMode ->
                if (isSystemInDarkMode) Color.BLACK else Color.WHITE
            }
        )

        createNotificationChannel()

        preferences.registerOnSharedPreferenceChangeListener(this)

        val preferences = preferences
        isPersistentQueueEnabled = preferences.getBoolean(persistentQueueKey, false)
        isInvincibilityEnabled = preferences.getBoolean(isInvincibilityEnabledKey, false)
        isShowingThumbnailInLockscreen =
            preferences.getBoolean(isShowingThumbnailInLockscreenKey, false)

        audioQualityFormat = preferences.getEnum(audioQualityFormatKey, AudioQualityFormat.High)
        showLikeButton = preferences.getBoolean(showLikeButtonBackgroundPlayerKey, true)
        showDownloadButton = preferences.getBoolean(showDownloadButtonBackgroundPlayerKey, true)

        val exoPlayerCustomCache = preferences.getInt(exoPlayerCustomCacheKey, 32) * 1000 * 1000L

        val cacheEvictor = when (val size =
            preferences.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`32MB`)) {
            ExoPlayerDiskCacheMaxSize.Unlimited -> NoOpCacheEvictor()
            ExoPlayerDiskCacheMaxSize.Custom -> LeastRecentlyUsedCacheEvictor(exoPlayerCustomCache)
            else -> LeastRecentlyUsedCacheEvictor(size.bytes)
        }

        //val cacheEvictor = NoOpCacheEvictor()

        var directory = cacheDir
        //val downloadDirectory = getExternalFilesDir(null) ?: filesDir
        var cacheDirName = "rimusic_cache"

        val cacheSize =
            preferences.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`32MB`)

        if (cacheSize == ExoPlayerDiskCacheMaxSize.Disabled) cacheDirName = "rimusic_no_cache"


        if (exoPlayerAlternateCacheLocation == "") {
            directory = cacheDir.resolve(cacheDirName).also { dir ->
                if (dir.exists()) return@also

                dir.mkdir()

                cacheDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name.length == 1 && file.name.isDigitsOnly() || file.extension == "uid") {
                        if (!file.renameTo(dir.resolve(file.name))) {
                            file.deleteRecursively()
                        }
                    }
                }

                filesDir.resolve("coil").deleteRecursively()
            }

        } else {
            // Available before android 10
            val path = File(exoPlayerAlternateCacheLocation)
            directory = path.resolve(cacheDirName).also { dir ->
                if (dir.exists()) return@also

                dir.mkdir()

                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name.length == 1 && file.name.isDigitsOnly() || file.extension == "uid") {
                        if (!dir.resolve(file.name).let { file.renameTo(it) }) {
                            file.deleteRecursively()
                        }
                    }
                }

                dir.resolve("coil").deleteRecursively()
            }
        }


        cache = SimpleCache(directory, cacheEvictor, StandaloneDatabaseProvider(this))

        //downloadCache = SimpleCache(downloadDirectory, cacheEvictor, StandaloneDatabaseProvider(this))

        downloadCache = DownloadUtil.getDownloadSimpleCache(applicationContext) as SimpleCache

        player = ExoPlayer.Builder(this, createRendersFactory(), createMediaSourceFactory())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .setSeekForwardIncrementMs(5000)
            .setSeekBackIncrementMs(5000)
            .setUsePlatformDiagnostics(false)
            .build()

        player.repeatMode = when {
            preferences.getBoolean(trackLoopEnabledKey, false) -> Player.REPEAT_MODE_ONE
            preferences.getBoolean(queueLoopEnabledKey, true) -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }

        player.skipSilenceEnabled = preferences.getBoolean(skipSilenceKey, false)
        player.addListener(this)
        player.addAnalyticsListener(PlaybackStatsListener(false, this))

        // Build a PendingIntent that can be used to launch the UI.
        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        mediaSession = MediaSessionCompat(baseContext, "PlayerService")
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setRatingType(RatingCompat.RATING_NONE)
        mediaSession.setSessionActivity(sessionActivityPendingIntent)
        mediaSession.setCallback(SessionCallback(player))

        if (showLikeButton && showDownloadButton)
            mediaSession.setPlaybackState(stateBuilder.build())
        if (showLikeButton && !showDownloadButton)
            mediaSession.setPlaybackState(stateBuilderWithLikeOnly.build())
        if (showDownloadButton && !showLikeButton)
            mediaSession.setPlaybackState(stateBuilderWithDownloadOnly.build())
        if (!showLikeButton && !showDownloadButton)
            mediaSession.setPlaybackState(stateBuilderWithoutCustomAction.build())

        mediaSession.isActive = true

        /*
        coroutineScope.launch {
            currentMedia.collect {
                updateNotification()
            }
        }

        currentMediaLiked = media.flatMapLatest { mediaItem ->
            if (mediaItem?.mediaId?.let { Database.songliked(it) } == 0) flowOf(false) else flowOf(true)
        }.stateIn(coroutineScope, SharingStarted.Lazily, false)

        currentMediaDownloaded = media.flatMapLatest { mediaItem ->
            val downloads = DownloadUtil.downloads.value
            flowOf(downloads[mediaItem?.mediaId]?.state == Download.STATE_COMPLETED)
        }.stateIn(coroutineScope, SharingStarted.Lazily, false)
         */

        updatePlaybackState()


        coroutineScope.launch {
            var first = true
            mediaItemState.zip(isLikedState) { mediaItem, _ ->
                if (first) {
                    first = false
                    return@zip
                }
                if (mediaItem == null) return@zip
                withContext(Dispatchers.Main) {
                    updatePlaybackState()
                    handler.post {
                        runCatching {
                            applicationContext.getSystemService<NotificationManager>()
                                ?.notify(NotificationId, notification())
                        }
                    }
                }
            }.collect()
        }

        coroutineScope.launch {
            var first = true
            mediaItemState.zip(isDownloadedState) { mediaItem, _ ->
                if (first) {
                    first = false
                    return@zip
                }
                if (mediaItem == null) return@zip
                withContext(Dispatchers.Main) {
                    updatePlaybackState()
                    handler.post {
                        runCatching {
                            applicationContext.getSystemService<NotificationManager>()
                                ?.notify(NotificationId, notification())
                        }
                    }
                }
            }.collect()
        }

        coroutineScope.launch {
            var first = true
            mediaItemState.zip(isCachedState) { mediaItem, _ ->
                if (first) {
                    first = false
                    return@zip
                }
                if (mediaItem == null) return@zip
                withContext(Dispatchers.Main) {
                    updatePlaybackState()
                    handler.post {
                        runCatching {
                            applicationContext.getSystemService<NotificationManager>()
                                ?.notify(NotificationId, notification())
                        }
                    }
                }
            }.collect()
        }

        notificationActionReceiver = NotificationActionReceiver(player)


        val filter = IntentFilter().apply {
            addAction(Action.play.value)
            addAction(Action.pause.value)
            addAction(Action.next.value)
            addAction(Action.previous.value)
            addAction(Action.like.value)
            addAction(Action.download.value)
            addAction(Action.playradio.value)
        }

        ContextCompat.registerReceiver(
             this,
             notificationActionReceiver,
             filter,
             ContextCompat.RECEIVER_NOT_EXPORTED
        )

        maybeRestorePlayerQueue()
        //maybeRestoreFromDiskPlayerQueue()

        if (isPersistentQueueEnabled) {
            // Save persistent queue periodically
            coroutineScope.launch {
                while (isActive) {
                    delay(25.seconds)
                        withContext(Dispatchers.Main) {
                            maybeSavePlayerQueue()
                            //maybeSaveToDiskPlayerQueue()
                        }
                }
            }
        }

        maybeResumePlaybackWhenDeviceConnected()

    }

    /*
    @kotlin.OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun updateNotification() {
        coroutineScope.launch {
            playbackStateMutex.withLock {
                withContext(Dispatchers.Main) {
                    if (showLikeButton && showDownloadButton)
                        mediaSession.setPlaybackState(
                            PlaybackStateCompat.Builder().setActions(actions)
                                .addCustomAction(
                                    "DOWNLOAD",
                                    "Download",
                                    if (currentMediaDownloaded.value) R.drawable.downloaded_to else R.drawable.download_to

                                ).addCustomAction(
                                    "LIKE",
                                    "Like",
                                    if (currentMediaLiked.value) R.drawable.heart else R.drawable.heart_outline
                                )
                                .setState(player.androidPlaybackState, player.currentPosition, 1f)
                                .setBufferedPosition(player.bufferedPosition)
                                .build()
                        )
                    if (showLikeButton && !showDownloadButton)
                        mediaSession.setPlaybackState(
                            stateBuilderWithLikeOnly
                                .setState(player.androidPlaybackState, player.currentPosition, 1f)
                                .setBufferedPosition(player.bufferedPosition)
                                .build()
                        )
                    if (showDownloadButton && !showLikeButton)
                        mediaSession.setPlaybackState(
                            stateBuilderWithDownloadOnly
                                .setState(player.androidPlaybackState, player.currentPosition, 1f)
                                .setBufferedPosition(player.bufferedPosition)
                                .build()
                        )
                    if (!showDownloadButton && !showLikeButton)
                        mediaSession.setPlaybackState(
                            stateBuilderWithoutCustomAction
                                .setState(player.androidPlaybackState, player.currentPosition, 1f)
                                .setBufferedPosition(player.bufferedPosition)
                                .build()
                        )
                }
            }
        }
    }
     */

    override fun onTaskRemoved(rootIntent: Intent?) {

        isclosebackgroundPlayerEnabled = preferences.getBoolean(closebackgroundPlayerKey, false)
        if (isclosebackgroundPlayerEnabled) {
            //if (!player.shouldBePlaying) {
                broadCastPendingIntent<NotificationDismissReceiver>().send()
            //}
            this.stopService(this.intent<MyDownloadService>())
            this.stopService(this.intent<PlayerService>())
            //stopSelf()
            onDestroy()
        }
        super.onTaskRemoved(rootIntent)
    }

    @UnstableApi
    override fun onDestroy() {
        runCatching {
            maybeSavePlayerQueue()

            preferences.unregisterOnSharedPreferenceChangeListener(this)

            player.removeListener(this)
            player.stop()
            player.release()

            unregisterReceiver(notificationActionReceiver)

            mediaSession.isActive = false
            mediaSession.release()
            cache.release()
            //downloadCache.release()

            loudnessEnhancer?.release()
        }
        super.onDestroy()
    }

    override fun shouldBeInvincible(): Boolean {
        return !player.shouldBePlaying
    }


    @ExperimentalCoroutinesApi
    @FlowPreview
    override fun onConfigurationChanged(newConfig: Configuration) {
        handler.post {
            runCatching {
        if (bitmapProvider.setDefaultBitmap() && player.currentMediaItem != null) {
            notificationManager?.notify(NotificationId, notification())
        }
            }
        }
        super.onConfigurationChanged(newConfig)
    }

    @UnstableApi
    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {

        val mediaItem =
            eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

        val totalPlayTimeMs = playbackStats.totalPlayTimeMs

        if (totalPlayTimeMs > 5000) {
            query {
                Database.incrementTotalPlayTimeMs(mediaItem.mediaId, totalPlayTimeMs)
            }
        }


        val minTimeForEvent = preferences.getEnum(exoPlayerMinTimeForEventKey, ExoPlayerMinTimeForEvent.`20s`)

        if (totalPlayTimeMs > minTimeForEvent.ms) {
            query {
                try {
                    Database.insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = System.currentTimeMillis(),
                            playTime = totalPlayTimeMs
                        )
                    )
                } catch (_: SQLException) {
                }
            }

        }
    }

    @UnstableApi
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {

        mediaItemState.update { mediaItem }
        //mediaDownloadedItemState.update { mediaItem }
        //mediaCachedItemState.update { mediaItem }

        maybeRecoverPlaybackError()
        maybeNormalizeVolume()
        maybeProcessRadio()

        if (mediaItem == null) {
            bitmapProvider.listener?.invoke(null)
        } else if (mediaItem.mediaMetadata.artworkUri == bitmapProvider.lastUri) {
            bitmapProvider.listener?.invoke(bitmapProvider.lastBitmap)
        }

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            updateMediaSessionQueue(player.currentTimeline)
        }

    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            updateMediaSessionQueue(timeline)
        }
    }

    private fun updateMediaSessionQueue(timeline: Timeline) {
        val builder = MediaDescriptionCompat.Builder()

        val currentMediaItemIndex = player.currentMediaItemIndex
        val lastIndex = timeline.windowCount - 1
        var startIndex = currentMediaItemIndex - 7
        var endIndex = currentMediaItemIndex + 7

        if (startIndex < 0) {
            endIndex -= startIndex
        }

        if (endIndex > lastIndex) {
            startIndex -= (endIndex - lastIndex)
            endIndex = lastIndex
        }

        startIndex = startIndex.coerceAtLeast(0)

        mediaSession.setQueue(
            List(endIndex - startIndex + 1) { index ->
                val mediaItem = timeline.getWindow(index + startIndex, Timeline.Window()).mediaItem
                MediaSessionCompat.QueueItem(
                    builder
                        .setMediaId(mediaItem.mediaId)
                        .setTitle(mediaItem.mediaMetadata.title)
                        .setSubtitle(mediaItem.mediaMetadata.artist)
                        .setIconUri(mediaItem.mediaMetadata.artworkUri)
                        .build(),
                    (index + startIndex).toLong()
                )
            }
        )
    }

    private fun maybeRecoverPlaybackError() {
        if (player.playerError != null) {
            player.prepare()
        }
    }

    private fun maybeProcessRadio() {
        radio?.let { radio ->
            if (player.mediaItemCount - player.currentMediaItemIndex <= 3) {
                coroutineScope.launch(Dispatchers.Main) {
                    player.addMediaItems(radio.process())
                }
            }
        }
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    @UnstableApi
    private fun maybeRestoreFromDiskPlayerQueue() {
        if (!isPersistentQueueEnabled) return
        //Log.d("mediaItem", "QueuePersistentEnabled Restore Initial")

        runCatching {
            filesDir.resolve("persistentQueue.data").inputStream().use { fis ->
                ObjectInputStream(fis).use { oos ->
                    oos.readObject() as PersistentQueue
                }
            }
        }.onSuccess { queue ->
            //Log.d("mediaItem", "QueuePersistentEnabled Restored queue $queue")
            //Log.d("mediaItem", "QueuePersistentEnabled Restored ${queue.songMediaItems.size}")
            runBlocking (Dispatchers.Main) {
                player.setMediaItems(
                    queue.songMediaItems.map { song ->
                        song.asMediaItem.buildUpon()
                            .setUri(song.asMediaItem.mediaId)
                            .setCustomCacheKey(song.asMediaItem.mediaId)
                            .build().apply {
                                mediaMetadata.extras?.putBoolean("isFromPersistentQueue", true)
                            }
                    },
                    queue.mediaItemIndex,
                    queue.position
                )

                player.prepare()

                isNotificationStarted = true
                startForegroundService(this@PlayerService, intent<PlayerService>())
                startForeground(NotificationId, notification())
            }

        }.onFailure {
            it.printStackTrace()
        }

        //Log.d("mediaItem", "QueuePersistentEnabled Restored ${player.currentTimeline.mediaItems.size}")

    }

    private fun maybeSaveToDiskPlayerQueue() {

        if (!isPersistentQueueEnabled) return
        //Log.d("mediaItem", "QueuePersistentEnabled Save ${player.currentTimeline.mediaItems.size}")

        val persistentQueue = PersistentQueue(
            title = "title",
            songMediaItems = player.currentTimeline.mediaItems.map {
                PersistentSong(
                       id = it.mediaId,
                       title = it.mediaMetadata.title.toString(),
                       durationText = it.mediaMetadata.extras?.getString("durationText").toString(),
                       thumbnailUrl = it.mediaMetadata.artworkUri.toString()
                   )
            },
            mediaItemIndex = player.currentMediaItemIndex,
            position = player.currentPosition
        )

        runCatching {
            filesDir.resolve("persistentQueue.data").outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistentQueue)
                }
            }
        }.onFailure {
            it.printStackTrace()
        }.onSuccess {
            Log.d("mediaItem", "QueuePersistentEnabled Saved $persistentQueue")
        }

    }

    private fun maybeSavePlayerQueue() {
            if (!isPersistentQueueEnabled) return
        /*
        if (player.playbackState == Player.STATE_IDLE) {
            Log.d("mediaItem", "QueuePersistentEnabled playbackstate idle return")
            return
        }
         */
        //Log.d("mediaItem", "QueuePersistentEnabled Save ${player.currentTimeline.mediaItems.size}")
        //Log.d("mediaItem", "QueuePersistentEnabled Save initial")

            val mediaItems = player.currentTimeline.mediaItems
            val mediaItemIndex = player.currentMediaItemIndex
            val mediaItemPosition = player.currentPosition

            mediaItems.mapIndexed { index, mediaItem ->
                QueuedMediaItem(
                    mediaItem = mediaItem,
                    position = if (index == mediaItemIndex) mediaItemPosition else null
                )
            }.let { queuedMediaItems ->
                query {
                    Database.clearQueue()
                    Database.insert(queuedMediaItems)
                }
            }
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    @UnstableApi
    private fun maybeRestorePlayerQueue() {
        if (!isPersistentQueueEnabled) return

        query {
            val queuedSong = Database.queue()

            if (queuedSong.isEmpty()) return@query

            val index = queuedSong.indexOfFirst { it.position != null }.coerceAtLeast(0)

            runBlocking(Dispatchers.Main) {
                player.setMediaItems(
                    queuedSong.map { mediaItem ->
                        mediaItem.mediaItem.buildUpon()
                            .setUri(mediaItem.mediaItem.mediaId)
                            .setCustomCacheKey(mediaItem.mediaItem.mediaId)
                            .build().apply {
                                mediaMetadata.extras?.putBoolean("isFromPersistentQueue", true)
                            }
                    },
                    index,
                    queuedSong[index].position ?: C.TIME_UNSET
                )
                player.prepare()

                isNotificationStarted = true
                startForegroundService(this@PlayerService, intent<PlayerService>())
                startForeground(NotificationId, notification())
            }
        }

    }

    @UnstableApi
    private fun maybeNormalizeVolume() {
        if (!preferences.getBoolean(volumeNormalizationKey, false)) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob?.cancel()
            player.volume = 1f
            return
        }

        runCatching {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
            }
        }.onFailure { return }

        player.currentMediaItem?.mediaId?.let { songId ->
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob = coroutineScope.launch(Dispatchers.Main) {
                Database.loudnessDb(songId).cancellable().collectLatest { loudnessDb ->
                    try {
                        loudnessEnhancer?.setTargetGain(-((loudnessDb ?: 0f) * 100).toInt() + 500)
                        loudnessEnhancer?.enabled = true
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun maybeShowSongCoverInLockScreen() {
        val bitmap =
            if (isAtLeastAndroid13 || isShowingThumbnailInLockscreen) bitmapProvider.bitmap else null

        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)

        if (isAtLeastAndroid13 && player.currentMediaItemIndex == 0) {
            metadataBuilder.putText(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                "${player.mediaMetadata.title} "
            )
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    @SuppressLint("NewApi")
    private fun maybeResumePlaybackWhenDeviceConnected() {
        if (!isAtLeastAndroid6) return

        if (preferences.getBoolean(resumePlaybackWhenDeviceConnectedKey, false)) {
            if (audioManager == null) {
                audioManager = getSystemService(AUDIO_SERVICE) as AudioManager?
            }

            audioDeviceCallback = object : AudioDeviceCallback() {
                private fun canPlayMusic(audioDeviceInfo: AudioDeviceInfo): Boolean {
                    if (!audioDeviceInfo.isSink) return false

                    return audioDeviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }

                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    if (!player.isPlaying && addedDevices.any(::canPlayMusic)) {
                        player.play()
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = Unit
            }

            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, handler)

        } else {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
        }
    }

    @UnstableApi
    private fun sendOpenEqualizerIntent() {
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }


    @UnstableApi
    private fun sendCloseEqualizerIntent() {
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    private fun updatePlaybackState() = coroutineScope.launch {
        playbackStateMutex.withLock {
            withContext(Dispatchers.Main) {
                if (showLikeButton && showDownloadButton)
                    mediaSession.setPlaybackState(
                        stateBuilder
                            .setState(player.androidPlaybackState, player.currentPosition, 1f)
                            .setBufferedPosition(player.bufferedPosition)
                            .build()
                    )
                if (showLikeButton && !showDownloadButton)
                    mediaSession.setPlaybackState(
                        stateBuilderWithLikeOnly
                            .setState(player.androidPlaybackState, player.currentPosition, 1f)
                            .setBufferedPosition(player.bufferedPosition)
                            .build()
                    )
                if (showDownloadButton && !showLikeButton)
                    mediaSession.setPlaybackState(
                        stateBuilderWithDownloadOnly
                            .setState(player.androidPlaybackState, player.currentPosition, 1f)
                            .setBufferedPosition(player.bufferedPosition)
                            .build()
                    )
                if (!showDownloadButton && !showLikeButton)
                    mediaSession.setPlaybackState(
                        stateBuilderWithoutCustomAction
                            .setState(player.androidPlaybackState, player.currentPosition, 1f)
                            .setBufferedPosition(player.bufferedPosition)
                            .build()
                    )
            }
        }

    }

    private val Player.androidPlaybackState
        get() = when (playbackState) {
            Player.STATE_BUFFERING -> if (playWhenReady) PlaybackStateCompat.STATE_BUFFERING else PlaybackStateCompat.STATE_PAUSED
            Player.STATE_READY -> if (playWhenReady) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            Player.STATE_IDLE -> PlaybackStateCompat.STATE_NONE
            else -> PlaybackStateCompat.STATE_NONE
        }

    // legacy behavior may cause inconsistencies, but not available on sdk 24 or lower
    @ExperimentalCoroutinesApi
    @FlowPreview
    @Suppress("DEPRECATION")
    override fun onEvents(player: Player, events: Player.Events) {
        if (player.duration != C.TIME_UNSET) mediaSession.setMetadata(
            metadataBuilder
                .putText(MediaMetadataCompat.METADATA_KEY_TITLE, player.mediaMetadata.title)
                .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, player.mediaMetadata.artist)
                .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, player.mediaMetadata.albumTitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.duration)
                .build()
        )


        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                //Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED
            )
        ) {
            val notification = notification()

            if (notification == null) {
                isNotificationStarted = false
                makeInvincible(false)
                stopForeground(false)
                sendCloseEqualizerIntent()
                notificationManager?.cancel(NotificationId)
                return
            }

            if (player.shouldBePlaying && !isNotificationStarted) {
                isNotificationStarted = true
                startForegroundService(this@PlayerService, intent<PlayerService>())
                startForeground(NotificationId, notification)
                makeInvincible(false)
                sendOpenEqualizerIntent()
            } else {
                if (!player.shouldBePlaying) {
                    isNotificationStarted = false
                    stopForeground(false)
                    makeInvincible(true)
                    sendCloseEqualizerIntent()
                }
                runCatching {
                    notificationManager?.notify(NotificationId, notification)
                }
            }
        }
        //updateNotification()
        updatePlaybackState()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        //this.stopService(this.intent<MyDownloadService>())
        //this.stopService(this.intent<PlayerService>())
        //Log.d("mediaItem","onPlayerError ${error.errorCodeName}")
    }
    override fun onPlayerErrorChanged(error: PlaybackException?) {
        super.onPlayerErrorChanged(error)
        //this.stopService(this.intent<MyDownloadService>())
        //this.stopService(this.intent<PlayerService>())
        //Log.d("mediaItem","onPlayerErrorChanged ${error?.errorCodeName}")
    }
    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        super.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        //Log.d("mediaItem","onPlaybackSuppressionReasonChanged $playbackSuppressionReason")
    }

    @UnstableApi
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)

        //val totalPlayTimeMs = player.totalBufferedDuration.toString()
        //Log.d("mediaEvent","isPlaying "+isPlaying.toString() + " buffered duration "+totalPlayTimeMs)
        //Log.d("mediaItem","onIsPlayingChanged isPlaying $isPlaying audioSession ${player.audioSessionId}")
    }

    @UnstableApi
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            persistentQueueKey -> if (sharedPreferences != null) {
                isPersistentQueueEnabled =
                    sharedPreferences.getBoolean(key, isPersistentQueueEnabled)
            }

            volumeNormalizationKey -> maybeNormalizeVolume()

            resumePlaybackWhenDeviceConnectedKey -> maybeResumePlaybackWhenDeviceConnected()

            isInvincibilityEnabledKey -> if (sharedPreferences != null) {
                isInvincibilityEnabled =
                    sharedPreferences.getBoolean(key, isInvincibilityEnabled)
            }

            skipSilenceKey -> if (sharedPreferences != null) {
                player.skipSilenceEnabled = sharedPreferences.getBoolean(key, false)
            }

            isShowingThumbnailInLockscreenKey -> {
                if (sharedPreferences != null) {
                    isShowingThumbnailInLockscreen = sharedPreferences.getBoolean(key, true)
                }
                maybeShowSongCoverInLockScreen()
            }

            trackLoopEnabledKey, queueLoopEnabledKey -> {
                player.repeatMode = when {
                    preferences.getBoolean(trackLoopEnabledKey, false) -> Player.REPEAT_MODE_ONE
                    preferences.getBoolean(queueLoopEnabledKey, true) -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    override fun notification(): Notification? {
        if (player.currentMediaItem == null) return null

        val playIntent = Action.play.pendingIntent
        val pauseIntent = Action.pause.pendingIntent
        val nextIntent = Action.next.pendingIntent
        val prevIntent = Action.previous.pendingIntent
        val likeIntent = Action.like.pendingIntent
        val downloadIntent = Action.download.pendingIntent
        val playradioIntent = Action.playradio.pendingIntent

        val mediaMetadata = player.mediaMetadata

        val builder = if (isAtLeastAndroid8) {
            NotificationCompat.Builder(applicationContext, NotificationChannelId)
        } else {
            NotificationCompat.Builder(applicationContext)
        }
            .setContentTitle(mediaMetadata.title)
            .setContentText(mediaMetadata.artist)
            .setSubText(player.playerError?.message)
            .setLargeIcon(bitmapProvider.bitmap)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSmallIcon(player.playerError?.let { R.drawable.alert_circle }
                ?: R.drawable.app_icon)
            .setOngoing(false)
            .setContentIntent(activityPendingIntent<MainActivity>(
                flags = PendingIntent.FLAG_UPDATE_CURRENT
            ) {
                putExtra("expandPlayerBottomSheet", true)
            })
            .setDeleteIntent(broadCastPendingIntent<NotificationDismissReceiver>())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .addAction(R.drawable.play_skip_back, "Skip back", prevIntent)
            .addAction(
                if (player.shouldBePlaying) R.drawable.pause else R.drawable.play,
                if (player.shouldBePlaying) "Pause" else "Play",
                if (player.shouldBePlaying) pauseIntent else playIntent
            )
            .addAction(R.drawable.play_skip_forward, "Skip forward", nextIntent)

        if (showLikeButton && showDownloadButton) {
            //Prior Android 11
            builder
                .addAction(
                    if (isDownloadedState.value || isCachedState.value) R.drawable.downloaded else R.drawable.download,
                    //if (currentMediaDownloaded.value) R.drawable.downloaded_to else R.drawable.download_to,
                    "Download", downloadIntent
                )
                .addAction(
                    if (isLikedState.value) R.drawable.heart else R.drawable.heart_outline,
                    //if (currentMediaLiked.value) R.drawable.heart else R.drawable.heart_outline,
                    "Like",
                    likeIntent
                )
                .addAction(
                    R.drawable.radio,
                    "Play radio",
                    playradioIntent
                )
        }
        //Prior Android 11
        if (showLikeButton && !showDownloadButton) {
            builder
                .addAction(
                    if (isLikedState.value) R.drawable.heart else R.drawable.heart_outline,
                    "Like",
                    likeIntent
                )
                .addAction(
                    R.drawable.radio,
                    "Play radio",
                    playradioIntent
                )
        }
        //Prior Android 11
        if (!showLikeButton && showDownloadButton) {
            builder
                .addAction(
                    if (isDownloadedState.value || isCachedState.value) R.drawable.downloaded else R.drawable.download,
                    "Download", downloadIntent
                )
                .addAction(
                    R.drawable.radio,
                    "Play radio",
                    playradioIntent
                )
        }


        bitmapProvider.load(mediaMetadata.artworkUri) { bitmap ->
            maybeShowSongCoverInLockScreen()
            handler.post {
                runCatching {
                    notificationManager?.notify(NotificationId, builder.setLargeIcon(bitmap).build())
                }
            }
        }


        return builder.build()
    }


    private fun createNotificationChannel() {
        notificationManager = getSystemService()

        if (!isAtLeastAndroid8) return

        notificationManager?.run {
            if (getNotificationChannel(NotificationChannelId) == null) {
                createNotificationChannel(
                    NotificationChannel(
                        NotificationChannelId,
                        "Now playing",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        setSound(null, null)
                        enableLights(false)
                        enableVibration(false)
                    }
                )
            }

            if (getNotificationChannel(SleepTimerNotificationChannelId) == null) {
                createNotificationChannel(
                    NotificationChannel(
                        SleepTimerNotificationChannelId,
                        "Sleep timer",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        setSound(null, null)
                        enableLights(false)
                        enableVibration(false)
                    }
                )
            }
        }
    }


    private fun okHttpClient(): OkHttpClient {
        ProxyPreferences.preference?.let {
            return OkHttpClient.Builder()
                .proxy(
                    Proxy(
                        it.proxyMode,
                        InetSocketAddress(it.proxyHost, it.proxyPort)
                    )
                )
                .connectTimeout(Duration.ofSeconds(16))
                .readTimeout(Duration.ofSeconds(8))
                .build()
        }
        return OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(16))
            .readTimeout(Duration.ofSeconds(8))
            .build()
    }


    /** NEW METHOD **/
    private fun createMediaSourceFactory() = DefaultMediaSourceFactory(
        createDataSourceResolverFactory(
            findMediaItem = { videoId ->
                runBlocking(Dispatchers.Main) {
                    player.findNextMediaItemById(videoId)
                }
            },
            context = applicationContext,
            cache = cache
        ),
        DefaultExtractorsFactory()
    )

    private fun createRendersFactory(): RenderersFactory {
        //val minimumSilenceDuration = PlayerPreferences.minimumSilence.coerceIn(1000L..2_000_000L)
        val audioSink = DefaultAudioSink.Builder(applicationContext)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false)
            .setAudioOffloadSupportProvider(DefaultAudioOffloadSupportProvider(applicationContext))
            .setAudioProcessorChain(
                DefaultAudioProcessorChain(
                    arrayOf(),
                    SilenceSkippingAudioProcessor(
                        2_000_000, //minimumSilenceDuration,
                        20_000, //minimumSilenceDuration / 100L,
                         256
                    ),
                    SonicAudioProcessor()
                )
            )
            .build()
            .apply {
                if (isAtLeastAndroid10) setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED)
            }

        return RenderersFactory { handler: Handler?, _, audioListener: AudioRendererEventListener?, _, _ ->
            arrayOf(
                MediaCodecAudioRenderer(
                    this,
                    MediaCodecSelector.DEFAULT,
                    handler,
                    audioListener,
                    audioSink
                )
            )
        }
    }

    //@Suppress("CyclomaticComplexMethod")
    private fun createDataSourceResolverFactory(
        findMediaItem: (videoId: String) -> MediaItem?,
        context: Context,
        cache: Cache,
        chunkLength: Long? = 512 * 1024L
    ): ResolvingDataSource.Factory {
        val ringBuffer = RingBuffer<Pair<String, Uri>?>(2) { null }

        return ResolvingDataSource.Factory(
            ConditionalCacheDataSourceFactory(
                cacheDataSourceFactory = CacheDataSource.Factory().setCache(downloadCache)
                    .setCacheWriteDataSinkFactory(null),
                upstreamDataSourceFactory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(okHttpClient())
                                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0")
                        )
                    )
            ) { !it.isLocal }
        ) { dataSpec ->
            val videoId = dataSpec.key?.removePrefix("https://youtube.com/watch?v=")
                ?: error("A key must be set")

            when {
                dataSpec.isLocal ||
                        cache.isCached(videoId, dataSpec.position, chunkLength ?: (512 * 1024L)) ||
                        downloadCache.isCached(videoId, dataSpec.position, chunkLength ?: (512 * 1024L)) -> dataSpec

                videoId == ringBuffer.getOrNull(0)?.first ->
                    dataSpec.withUri(ringBuffer.getOrNull(0)!!.second)

                videoId == ringBuffer.getOrNull(1)?.first ->
                    dataSpec.withUri(ringBuffer.getOrNull(1)!!.second)

                else -> {
                    val body = runBlocking(Dispatchers.IO) {
                        Innertube.player(PlayerBody(videoId = videoId))
                    }?.getOrThrow()

                    if (body?.videoDetails?.videoId != videoId) throw VideoIdMismatchException()

                    //println("mediaItem adaptive ${body.streamingData?.adaptiveFormats}")
                    //val format = body.streamingData?.highestQualityFormat
                    val format = when (audioQualityFormat) {
                        AudioQualityFormat.Auto -> body.streamingData?.autoMaxQualityFormat
                        AudioQualityFormat.High -> body.streamingData?.highestQualityFormat
                        AudioQualityFormat.Medium -> body.streamingData?.mediumQualityFormat
                        AudioQualityFormat.Low -> body.streamingData?.lowestQualityFormat
                    }

                    val url = when (val status = body.playabilityStatus?.status) {
                        "OK" -> format?.let { formatIn ->
                            val mediaItem = findMediaItem(videoId)
                            if (mediaItem?.mediaMetadata?.extras?.getString("durationText") == null)
                                formatIn.approxDurationMs?.div(1000)
                                    ?.let(DateUtils::formatElapsedTime)?.removePrefix("0")
                                    ?.let { durationText ->
                                        mediaItem?.mediaMetadata?.extras?.putString(
                                            "durationText",
                                            durationText
                                        )
                                        Database.updateDurationText(videoId, durationText)
                                    }

                            query {
                                mediaItem?.let(Database::insert)

                                Database.insert(
                                    it.fast4x.rimusic.models.Format(
                                        songId = videoId,
                                        itag = formatIn.itag,
                                        mimeType = formatIn.mimeType,
                                        bitrate = formatIn.bitrate,
                                        loudnessDb = body.playerConfig?.audioConfig?.normalizedLoudnessDb,
                                        contentLength = formatIn.contentLength,
                                        lastModified = formatIn.lastModified
                                    )
                                )
                            }

                            formatIn.url
                        } ?: throw PlayableFormatNotFoundException()

                        "UNPLAYABLE" -> throw UnplayableException()
                        "LOGIN_REQUIRED" -> throw LoginRequiredException()

                        else -> throw PlaybackException(
                            status,
                            null,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )
                    }
                    ringBuffer.append(videoId to url.toUri())
                    //ringBuffer += videoId to url.toUri()
                    dataSpec.buildUpon()
                        .setKey(videoId)
                        .setUri(url.toUri())
                        .build()
                        .let { spec ->
                            (chunkLength ?: format.contentLength)?.let {
                                spec.subrange(dataSpec.uriPositionOffset, it)
                            } ?: spec
                        }
                }
            }
        }
    }

    /** NEW METHOD **/


    /** ORIGINAL START **/
    /*
    @UnstableApi
    private fun createMediaSourceFactory(): MediaSource.Factory {
        return DefaultMediaSourceFactory(createDataSourceFactory(), createExtractorsFactory())
    }

    @UnstableApi
    private fun createExtractorsFactory() = DefaultExtractorsFactory()

    @UnstableApi
    private fun createRendersFactory(): RenderersFactory {
        val audioSink = DefaultAudioSink.Builder(applicationContext)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false)
            .setAudioOffloadSupportProvider(DefaultAudioOffloadSupportProvider(applicationContext))
            .setAudioProcessorChain(
                DefaultAudioProcessorChain(
                    emptyArray(),
                    SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                    SonicAudioProcessor()
                )
            )
            .build()
            .apply {
                if (isAtLeastAndroid10) setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED)
            }

        return RenderersFactory { handler: Handler?, _, audioListener: AudioRendererEventListener?, _, _ ->
            arrayOf(
                MediaCodecAudioRenderer(
                    this,
                    MediaCodecSelector.DEFAULT,
                    handler,
                    audioListener,
                    audioSink
                )
            )
        }
    }


    @UnstableApi
    private fun createCacheDataSource() = ConditionalCacheDataSourceFactory(
        cacheDataSourceFactory = CacheDataSource.Factory().setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
        upstreamDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(
                    this,
                    OkHttpDataSource.Factory(okHttpClient())
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0")
                    /*
                    DefaultHttpDataSource.Factory()
                        .setConnectTimeoutMs(16000)
                        .setReadTimeoutMs(8000)
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0")

                     */
                )
            )
    ) { !it.isLocal }


    @UnstableApi
    private fun createDataSourceFactory(): DataSource.Factory {
        val chunkLength = 512 * 1024L
        val ringBuffer = RingBuffer<Pair<String, Uri>?>(2) { null }

        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val videoId = dataSpec.key ?: error("A key must be set")

            //if(dataSpec.isLocal) videoId = videoId.removePrefix("local:")
            //Log.d("mediaItem","dataSpec isLocal ${dataSpec.isLocal} key ${videoId} all ${dataSpec.toString()}")

            //Log.d("mediaItem","dataSpec " + dataSpec.toString())
            //Log.d("downloadMedia", downloadCache.isCached(videoId, dataSpec.position, chunkLength).toString())
            //Log.d("downloadMedia", downloadCache.getCachedBytes(videoId, 0, -1).toString())

            //Log.d("mediaItemDatasource","dataSpec ${dataSpec.toString()} islocal ${dataSpec.isLocal}")
            //Log.d("mediaItemDatasource","dataSpecUri ${dataSpec.uri}")

            when {
                dataSpec.isLocal ||
                        cache.isCached(videoId, dataSpec.position, chunkLength) ||
                        downloadCache.isCached(videoId, dataSpec.position, chunkLength) -> dataSpec

                videoId == ringBuffer.getOrNull(0)?.first ->
                    dataSpec.withUri(ringBuffer.getOrNull(0)!!.second)

                videoId == ringBuffer.getOrNull(1)?.first ->
                    dataSpec.withUri(ringBuffer.getOrNull(1)!!.second)

                else -> {
                    val body = runBlocking(Dispatchers.IO) {
                        Innertube.player(PlayerBody(videoId = videoId))
                    }?.getOrThrow()

                    //Log.d("mediaItemDatasource","bodyVideoId ${body?.videoDetails?.videoId} videoId $videoId")

                    if (body?.videoDetails?.videoId != videoId) throw VideoIdMismatchException()

                    val url = when (val status = body.playabilityStatus?.status) {
                        //"OK" -> body.streamingData?.highestQualityFormat?.let { format ->
                        "OK" -> when (audioQualityFormat) {
                            AudioQualityFormat.Auto -> body.streamingData?.autoMaxQualityFormat
                            AudioQualityFormat.High -> body.streamingData?.highestQualityFormat
                            AudioQualityFormat.Medium -> body.streamingData?.mediumQualityFormat
                            AudioQualityFormat.Low -> body.streamingData?.lowestQualityFormat
                        }?.let { format ->
                            //Log.d("formatAudioQuality",format.audioQuality.toString())
                            val mediaItem = runBlocking(Dispatchers.Main) {
                                player.findNextMediaItemById(videoId)
                            }

                            if (mediaItem?.mediaMetadata?.extras?.getString("durationText") == null)
                                format.approxDurationMs?.div(1000)
                                    ?.let(DateUtils::formatElapsedTime)?.removePrefix("0")
                                    ?.let { durationText ->
                                        mediaItem?.mediaMetadata?.extras?.putString(
                                            "durationText",
                                            durationText
                                        )
                                        Database.updateDurationText(videoId, durationText)
                                    }

                            query {
                                mediaItem?.let(Database::insert)

                                Database.insert(
                                    it.vfsfitvnm.vimusic.models.Format(
                                        songId = videoId,
                                        itag = format.itag,
                                        mimeType = format.mimeType,
                                        bitrate = format.bitrate,
                                        loudnessDb = body.playerConfig?.audioConfig?.normalizedLoudnessDb,
                                        contentLength = format.contentLength,
                                        lastModified = format.lastModified
                                    )
                                )
                            }

                            format.url
                        } ?: throw PlayableFormatNotFoundException()

                        "UNPLAYABLE" -> throw UnplayableException()
                        "LOGIN_REQUIRED" -> throw LoginRequiredException()

                        else -> throw PlaybackException(
                            status,
                            null,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )
                    }

                    ringBuffer.append(videoId to url.toUri())
                    dataSpec.withUri(url.toUri()).subrange(dataSpec.uriPositionOffset, chunkLength)
                }
            }
        }
    }

     */
    /** ORIGINAL END **/

    inner class Binder : AndroidBinder() {
        val player: ExoPlayer
            get() = this@PlayerService.player

        val cache: Cache
            get() = this@PlayerService.cache

        val downloadCache: Cache
            get() = this@PlayerService.downloadCache

        val mediaSession
            get() = this@PlayerService.mediaSession

        val sleepTimerMillisLeft: StateFlow<Long?>?
            get() = timerJob?.millisLeft

        private var radioJob: Job? = null

        var isLoadingRadio by mutableStateOf(false)
            private set

        fun setBitmapListener(listener: ((Bitmap?) -> Unit)?) {
            bitmapProvider.listener = listener
        }

        fun startSleepTimer(delayMillis: Long) {
            timerJob?.cancel()



            timerJob = coroutineScope.timer(delayMillis) {
                val notification = NotificationCompat
                    .Builder(this@PlayerService, SleepTimerNotificationChannelId)
                    .setContentTitle("Sleep timer ended")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(true)
                    .setSmallIcon(R.drawable.app_icon)
                    .build()

                notificationManager?.notify(SleepTimerNotificationId, notification)

                stopSelf()
                exitProcess(0)
            }
        }

        fun cancelSleepTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        @UnstableApi
        fun setupRadio(endpoint: NavigationEndpoint.Endpoint.Watch?) =
            startRadio(endpoint = endpoint, justAdd = true)

        @UnstableApi
        fun playRadio(endpoint: NavigationEndpoint.Endpoint.Watch?) =
            startRadio(endpoint = endpoint, justAdd = false)

        @UnstableApi
        fun getRadioMediaItems(endpoint: NavigationEndpoint.Endpoint.Watch?): List<MediaItem> {
            YouTubeRadio(
                endpoint?.videoId,
                endpoint?.playlistId,
                endpoint?.playlistSetVideoId,
                endpoint?.params
            ).let {
                var mediaItems = listOf<MediaItem>()
                runBlocking {
                    mediaItems =  it.process()
                    return@runBlocking mediaItems
                }
                return mediaItems
            }
        }

        @UnstableApi
        fun getRadioSongs(endpoint: NavigationEndpoint.Endpoint.Watch?): List<Song> {
            YouTubeRadio(
                endpoint?.videoId,
                endpoint?.playlistId,
                endpoint?.playlistSetVideoId,
                endpoint?.params
            ).let {
                var songs = listOf<Song>()
                runBlocking {
                    songs =  it.process().map ( MediaItem::asSong )
                    return@runBlocking songs
                }
                return songs
            }
        }

        @UnstableApi
        fun setRadioMediaItems(endpoint: NavigationEndpoint.Endpoint.Watch?) {
            radioJob?.cancel()
            radio = null
            YouTubeRadio(
                endpoint?.videoId,
                endpoint?.playlistId,
                endpoint?.playlistSetVideoId,
                endpoint?.params
            ).let {
                isLoadingRadio = true
                coroutineScope.launch(Dispatchers.Main) {
                    player.clearMediaItems()
                    player.addMediaItems(it.process())
                    //Log.d("mediaItem", "binder ${it.process()}")
                }
                radio = it
                isLoadingRadio = false
                //Log.d("mediaItem", "binder $mediaItems")
                //return mediaItems
                /*
                isLoadingRadio = true
                radioJob = coroutineScope.launch(Dispatchers.Main) {
                    if (justAdd) {
                        player.addMediaItems(it.process().drop(1))
                    } else {
                        player.forcePlayFromBeginning(it.process())
                    }
                    radio = it
                    isLoadingRadio = false
                }
                 */
            }
        }

        @UnstableApi
        private fun startRadio(endpoint: NavigationEndpoint.Endpoint.Watch?, justAdd: Boolean) {
            radioJob?.cancel()
            radio = null
            YouTubeRadio(
                endpoint?.videoId,
                endpoint?.playlistId,
                endpoint?.playlistSetVideoId,
                endpoint?.params
            ).let {
                isLoadingRadio = true
                radioJob = coroutineScope.launch(Dispatchers.Main) {

                    it.process().forEach {
                        transaction {
                            Database.insert(it)
                        }
                    }

                    if (justAdd) {
                        player.addMediaItems(it.process().drop(1))
                    } else {
                        player.forcePlayFromBeginning(it.process())
                    }
                    radio = it
                    isLoadingRadio = false
                }
            }
        }

        fun stopRadio() {
            isLoadingRadio = false
            radioJob?.cancel()
            radio = null
        }

        fun playFromSearch(query: String) {
            coroutineScope.launch {
                Innertube.searchPage(
                    body = SearchBody(
                        query = query,
                        params = Innertube.SearchFilter.Song.value
                    ),
                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                )?.getOrNull()?.items?.firstOrNull()?.info?.endpoint?.let { playRadio(it) }
            }
        }

        @ExperimentalCoroutinesApi
        @FlowPreview
        fun toggleLike() = mediaItemState.value?.let { mediaItem ->
            //mediaItemToggleLike(mediaItem)
            transaction {
                Database.like(
                    mediaItem.mediaId,
                    if (isLikedState.value) null else System.currentTimeMillis()
                )
            }
             updatePlaybackState()
        }



        @ExperimentalCoroutinesApi
        @FlowPreview
        fun toggleDownload() = mediaItemState.value?.let { mediaItem ->
                val downloads = DownloadUtil.downloads.value
                manageDownload(
                    context = this@PlayerService,
                    songId = mediaItem.mediaId,
                    songTitle = mediaItem.mediaMetadata.title.toString(),
                    downloadState = downloads[mediaItem.mediaId]?.state == Download.STATE_COMPLETED
                )
            updatePlaybackState()
        }

        fun refreshPlayer() {
            coroutineScope.launch {
                withContext(Dispatchers.Main) {
                    if (player.isPlaying) {
                        player.pause()
                        player.play()
                    } else {
                        player.play()
                        player.pause()
                    }
                }
            }
        }
    }

    /*
    @ExperimentalCoroutinesApi
    @FlowPreview
    private fun toggleLikeAction() = mediaItemState.value?.let { mediaItem ->
            transaction {
                Database.like(
                    mediaItem.mediaId,
                    if (isLikedState.value) null else System.currentTimeMillis()
                )
            }
        }.let {  }



    @ExperimentalCoroutinesApi
    @FlowPreview
    private fun toggleDownloadAction() = mediaDownloadedItemState.value?.let { mediaItem ->
        manageDownload(
            context = this,
            songId = mediaItem.mediaId,
            songTitle = mediaItem.mediaMetadata.title.toString(),
            downloadState = isDownloadedState.value
        )

    }.let { }

     */

    private inner class SessionCallback(private val player: Player) : MediaSessionCompat.Callback() {
        override fun onPlay() = player.play()
        override fun onPause() = player.pause()
        override fun onSkipToPrevious() = runCatching(player::forceSeekToPrevious).let { }
        override fun onSkipToNext() = runCatching(player::forceSeekToNext).let { }
        override fun onSeekTo(pos: Long) = player.seekTo(pos)
        override fun onStop() = player.pause()
        override fun onRewind() = player.seekToDefaultPosition()
        override fun onSkipToQueueItem(id: Long) =
            runCatching { player.seekToDefaultPosition(id.toInt()) }.let { }



        @ExperimentalCoroutinesApi
        @FlowPreview
        override fun onCustomAction(action: String, extras: Bundle?) {
            super.onCustomAction(action, extras)
            println("mediaItem $action")
            if (action == "LIKE") {
                binder.toggleLike()
                refreshPlayer()
            }
            if (action == "DOWNLOAD") {
                binder.toggleDownload()
                refreshPlayer()
            }
            if (action == "PLAYRADIO") {
                coroutineScope.launch {
                    binder.stopRadio()
                    binder.playRadio(NavigationEndpoint.Endpoint.Watch(videoId = binder.player.currentMediaItem?.mediaId))
                    //refreshPlayer()
                    println("mediaItem $action")
                }

            }
            updatePlaybackState()
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (query.isNullOrBlank()) return
            binder.playFromSearch(query)
        }

    }


    private fun refreshPlayer() {
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                if (player.isPlaying) {
                    player.pause()
                    player.play()
                } else {
                    player.play()
                    player.pause()
                }
            }
        }
    }

    inner class NotificationActionReceiver(private val player: Player) : BroadcastReceiver() {


        @ExperimentalCoroutinesApi
        @FlowPreview
        // Prior Android 11
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Action.pause.value -> player.pause()
                Action.play.value -> player.play()
                Action.next.value -> player.forceSeekToNext()
                Action.previous.value -> player.forceSeekToPrevious()
                Action.like.value -> {
                    binder.toggleLike()
                    refreshPlayer()
                }
                Action.download.value -> {
                    binder.toggleDownload()
                    refreshPlayer()
                }
                Action.playradio.value -> {
                    println("mediaItem playradio")
                    binder.stopRadio()
                    binder.playRadio(NavigationEndpoint.Endpoint.Watch(videoId = binder.player.currentMediaItem?.mediaId))
                }
            }

            updatePlaybackState()
        }

    }




    class NotificationDismissReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            context.stopService(context.intent<MyDownloadService>())
            context.stopService(context.intent<PlayerService>())
        }
    }


    @JvmInline
    private value class Action(val value: String) {
        context(Context)
        val pendingIntent: PendingIntent
            get() = PendingIntent.getBroadcast(
                this@Context,
                100,
                Intent(value).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT.or(if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0)
            )

        companion object {

            val pause = Action("it.fast4x.rimusic.pause")
            val play = Action("it.fast4x.rimusic.play")
            val next = Action("it.fast4x.rimusic.next")
            val previous = Action("it.fast4x.rimusic.previous")
            val like = Action("it.fast4x.rimusic.like")
            val download = Action("it.fast4x.rimusic.download")
            val playradio = Action("it.fast4x.rimusic.playradio")

        }
    }

    private companion object {
        const val NotificationId = 1001
        const val NotificationChannelId = "default_channel_id"

        const val SleepTimerNotificationId = 1002
        const val SleepTimerNotificationChannelId = "sleep_timer_channel_id"


    }


}


