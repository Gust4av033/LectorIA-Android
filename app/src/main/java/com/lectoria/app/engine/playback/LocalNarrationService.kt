package com.lectoria.app.engine.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lectoria.app.MainActivity
import com.lectoria.app.R
import com.lectoria.app.data.LocatorCodec
import com.lectoria.app.di.NarrationScope
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.narration.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChangedBy

@AndroidEntryPoint
class LocalNarrationService : Service() {
    @Inject lateinit var coordinator: NarrationCoordinator
    @Inject @NarrationScope lateinit var applicationScope: CoroutineScope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private lateinit var wake: PowerManager.WakeLock
    private var heartbeat: Job? = null
    private var token = 0L
    private var receivedCommand = false
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) serviceScope.launch { coordinator.pause(PauseReason.HEADPHONES) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        token = coordinator.attachService()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("local_speech", "Lectura en voz alta", NotificationManager.IMPORTANCE_LOW))
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LectorIA:Speech").apply { setReferenceCounted(false) }
        session = MediaSession(this, "LectorIA local speech").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = dispatch("play")
                override fun onPause() = dispatch("pause")
                override fun onStop() = dispatch("stop")
                override fun onSkipToNext() = dispatch("next")
                override fun onSkipToPrevious() = dispatch("previous")
            })
            setSessionActivity(PendingIntent.getActivity(this@LocalNarrationService, 0, Intent(this@LocalNarrationService, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        serviceScope.launch {
            coordinator.state.distinctUntilChangedBy { listOf(it.phase, it.book?.id, it.sectionIndex, it.settings, it.failure, it.hasNext, it.hasPrevious) }.collect { state ->
                if (receivedCommand) update(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        receivedCommand = true
        foreground(notification(coordinator.state.value))
        dispatch(intent?.action ?: "stop", intent)
        return START_NOT_STICKY
    }

    private fun foreground(notification: Notification) = ServiceCompat.startForeground(this, 41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

    private fun dispatch(action: String, intent: Intent? = null) {
        if (action == "play" || action == "listen") foreground(notification(coordinator.state.value))
        serviceScope.launch {
            try {
                when (action) {
                    "listen" -> coordinator.start(BookId(checkNotNull(intent?.getStringExtra("book"))), LocatorCodec.decode(checkNotNull(intent.getStringExtra("locator"))))
                    "play" -> coordinator.start()
                    "pause" -> coordinator.pause()
                    "next" -> coordinator.skip(true)
                    "previous" -> coordinator.skip(false)
                    else -> coordinator.stop()
                }
                update(coordinator.state.value)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { coordinator.reportServiceFailure(); update(coordinator.state.value) }
        }
    }

    private fun update(state: NarrationState) {
        session.isActive = state.book != null && state.phase != SpeechPhase.IDLE
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, state.book?.title ?: "Lector IA")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.book?.author ?: "Voz local").build())
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or
            (if (state.hasNext) PlaybackState.ACTION_SKIP_TO_NEXT else 0L) or (if (state.hasPrevious) PlaybackState.ACTION_SKIP_TO_PREVIOUS else 0L)
        session.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(when (state.phase) {
            SpeechPhase.PLAYING -> PlaybackState.STATE_PLAYING
            SpeechPhase.PREPARING -> PlaybackState.STATE_BUFFERING
            SpeechPhase.COMPLETED -> PlaybackState.STATE_STOPPED
            SpeechPhase.FAILED -> PlaybackState.STATE_ERROR
            else -> PlaybackState.STATE_PAUSED
        }, PlaybackState.PLAYBACK_POSITION_UNKNOWN, state.settings.rate).build())
        if (state.active) {
            foreground(notification(state))
            if (heartbeat == null) heartbeat = serviceScope.launch {
                while (isActive) { wake.acquire(120_000); delay(60_000) }
            }
        } else {
            heartbeat?.cancel(); heartbeat = null
            if (wake.isHeld) wake.release()
            stopForeground(STOP_FOREGROUND_DETACH)
            if (state.phase == SpeechPhase.IDLE) {
                getSystemService(NotificationManager::class.java).cancel(41)
                stopSelf()
            } else postMediaNotification(state)
        }
    }

    // Android 13 exempts media-session notifications from POST_NOTIFICATIONS.
    // notification() always supplies MediaStyle and this service's MediaSession token.
    @android.annotation.SuppressLint("NotificationPermission")
    private fun postMediaNotification(state: NarrationState) {
        getSystemService(NotificationManager::class.java).notify(41, notification(state))
    }

    private fun notification(state: NarrationState): Notification {
        fun command(action: String, code: Int) = PendingIntent.getForegroundService(this, code,
            Intent(this, LocalNarrationService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "local_speech").setSmallIcon(R.drawable.ic_reader)
            .setContentTitle(state.book?.title ?: "Lector IA")
            .setContentText(when (state.phase) {
                SpeechPhase.PLAYING -> "Voz local · sección ${state.sectionIndex + 1}"
                SpeechPhase.PREPARING -> "Preparando voz local…"
                SpeechPhase.COMPLETED -> "Lectura terminada"
                SpeechPhase.FAILED -> "Abre Lector IA para revisar la voz o el libro"
                else -> "Lectura en pausa"
            }).setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setOngoing(state.active)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Anterior", command("previous", 1)).build())
            .addAction(Notification.Action.Builder(if (state.active) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.active) "Pausar" else "Reproducir", command(if (state.active) "pause" else "play", 2)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Siguiente", command("next", 3)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Detener", command("stop", 4)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        serviceScope.cancel()
        unregisterReceiver(noisy)
        heartbeat?.cancel()
        if (wake.isHeld) wake.release()
        session.release()
        applicationScope.launch { coordinator.detachService(token) }
        super.onDestroy()
    }
}
