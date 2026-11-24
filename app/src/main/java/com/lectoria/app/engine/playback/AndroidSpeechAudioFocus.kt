package com.lectoria.app.engine.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.lectoria.app.domain.narration.SpeechAudioFocus

class AndroidSpeechAudioFocus(context: Context) : SpeechAudioFocus {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    override fun acquire(onLoss: () -> Unit): Boolean {
        release()
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) onLoss()
            }, Handler(Looper.getMainLooper())).build()
        request = next
        return audio.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    override fun release() { request?.let(audio::abandonAudioFocusRequest); request = null }
}
