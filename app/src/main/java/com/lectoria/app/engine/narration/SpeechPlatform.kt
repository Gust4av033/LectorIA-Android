package com.lectoria.app.engine.narration

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lectoria.app.domain.narration.SpeechEngineInfo
import com.lectoria.app.domain.narration.SpeechVoice

/** Small SDK seam for callback/race tests; the production factory always binds Android TTS. */
internal interface SpeechPlatform {
    fun voices(): List<SpeechVoice>
    fun selectVoice(id: String): Boolean
    fun setRate(rate: Float): Boolean
    fun speak(text: String, id: String): Boolean
    fun stop()
    fun shutdown()

    interface Listener {
        fun done(id: String)
        fun range(id: String, start: Int, end: Int)
        fun error(id: String, code: Int)
        fun stopped(id: String)
    }

    interface Factory {
        fun engines(): List<SpeechEngineInfo>
        fun defaultEngine(): String?
        fun create(engine: String, initialized: (Boolean) -> Unit, listener: Listener): SpeechPlatform
    }
}

internal class AndroidSpeechPlatformFactory(private val context: Context) : SpeechPlatform.Factory {
    override fun engines(): List<SpeechEngineInfo> = context.packageManager.queryIntentServices(
        Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0,
    ).map { SpeechEngineInfo(it.serviceInfo.packageName, it.loadLabel(context.packageManager).toString()) }.distinctBy { it.id }.sortedBy { it.label }

    override fun defaultEngine(): String? = Settings.Secure.getString(context.contentResolver, "tts_default_synth")

    override fun create(engine: String, initialized: (Boolean) -> Unit, listener: SpeechPlatform.Listener): SpeechPlatform =
        AndroidSpeechPlatform(context, engine, initialized, listener)
}

private class AndroidSpeechPlatform(context: Context, engine: String, initialized: (Boolean) -> Unit, listener: SpeechPlatform.Listener) : SpeechPlatform {
    private val tts = TextToSpeech(context, { initialized(it == TextToSpeech.SUCCESS) }, engine)

    init {
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = listener.done(utteranceId)
            @Deprecated("Android compatibility callback")
            override fun onError(utteranceId: String) = listener.error(utteranceId, TextToSpeech.ERROR)
            override fun onError(utteranceId: String, errorCode: Int) = listener.error(utteranceId, errorCode)
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) = listener.range(utteranceId, start, end)
            override fun onStop(utteranceId: String, interrupted: Boolean) = listener.stopped(utteranceId)
        })
    }

    override fun voices(): List<SpeechVoice> = tts.voices.orEmpty().map { voice ->
        SpeechVoice(voice.name, voice.name, voice.locale.toLanguageTag(), !voice.isNetworkConnectionRequired,
            !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED), voice.quality)
    }.sortedWith(compareBy<SpeechVoice> { it.languageTag }.thenByDescending { it.quality }.thenBy { it.id })

    override fun selectVoice(id: String): Boolean {
        val voice = tts.voices.orEmpty().firstOrNull {
            it.name == id && !it.isNetworkConnectionRequired && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        } ?: return false
        return tts.setVoice(voice) == TextToSpeech.SUCCESS
    }

    override fun setRate(rate: Float) = tts.setSpeechRate(rate) == TextToSpeech.SUCCESS
    @Suppress("DEPRECATION")
    override fun speak(text: String, id: String): Boolean {
        val options = Bundle().apply { putString(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS, "true") }
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, options, id) == TextToSpeech.SUCCESS
    }
    override fun stop() { tts.stop() }
    override fun shutdown() { tts.shutdown() }
}
