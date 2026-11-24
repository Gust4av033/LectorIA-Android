package com.lectoria.app.domain.narration

import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.TextLocator
import kotlinx.coroutines.flow.Flow

enum class NarrationOutput { LiveSpeech, AudioFile }

/** Deliberately has no millisecond seek/pause API: live TTS resumes at a text boundary. */
interface NarrationEngine {
    val output: NarrationOutput
    val events: Flow<NarrationEvent>
    /** Returns after completion/error, not merely after enqueueing speech. Cancellation stops it. */
    suspend fun narrate(request: SpeechRequest): Result<Unit>
    suspend fun cancel()
}

sealed interface NarrationEvent {
    data class Progress(val requestId: String, val locator: TextLocator) : NarrationEvent
    data class AudioReady(val requestId: String, val localPath: String) : NarrationEvent
    data class Completed(val requestId: String) : NarrationEvent
    data class Failed(val requestId: String, val reason: String) : NarrationEvent
}

data class SpeechRequest(val id: String, val chunk: TextChunk, val voiceId: String, val rate: Float) {
    init { require(id.isNotBlank() && voiceId.isNotBlank() && rate.isFinite() && rate in 0.5f..2f) }
}

data class SpeechEngineInfo(val id: String, val label: String)
data class SpeechVoice(val id: String, val label: String, val languageTag: String, val offline: Boolean, val installed: Boolean, val quality: Int)
data class VoiceCatalog(val engineId: String?, val engines: List<SpeechEngineInfo>, val voices: List<SpeechVoice>)

interface LocalNarrationEngine : NarrationEngine {
    suspend fun configure(engineId: String?): VoiceCatalog
    /** Synchronous main-thread emergency stop for service destruction. */
    fun stopImmediately()
    fun shutdown()
}

enum class SpeechFailure { NO_ENGINE, NO_OFFLINE_VOICE, INIT, ENGINE, DOCUMENT, LOCATION, NO_TEXT, AUDIO_FOCUS, STORAGE, SERVICE }
class SpeechException(val reason: SpeechFailure, val engineCode: Int? = null) : Exception(reason.name)
