package com.lectoria.app.domain.playback

import com.lectoria.app.domain.model.MediaPositionMs
import kotlinx.coroutines.flow.StateFlow

/** Only already available audio files use a media timeline; live speech uses TextLocator. */
interface AudioPlaybackController {
    val state: StateFlow<AudioPlaybackState>
    suspend fun play(localPath: String): Result<Unit>
    suspend fun pause()
    suspend fun seekTo(position: MediaPositionMs)
    suspend fun stop()
}

sealed interface AudioPlaybackState {
    data object Idle : AudioPlaybackState
    data class Playing(val position: MediaPositionMs) : AudioPlaybackState
    data class Paused(val position: MediaPositionMs) : AudioPlaybackState
    data class Failed(val reason: String) : AudioPlaybackState
}
