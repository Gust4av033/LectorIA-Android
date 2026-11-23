package com.lectoria.app.domain.narration

import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.TextLocator
import kotlinx.coroutines.flow.Flow

enum class NarrationOutput { LiveSpeech, AudioFile }

/** Deliberately has no millisecond seek/pause API: live TTS resumes at a text boundary. */
interface NarrationEngine {
    val output: NarrationOutput
    val events: Flow<NarrationEvent>
    suspend fun narrate(chunk: TextChunk, voiceId: String): Result<Unit>
    suspend fun cancel()
}

sealed interface NarrationEvent {
    data class Progress(val locator: TextLocator) : NarrationEvent
    data class AudioReady(val localPath: String) : NarrationEvent
    data object Completed : NarrationEvent
    data class Failed(val reason: String) : NarrationEvent
}
