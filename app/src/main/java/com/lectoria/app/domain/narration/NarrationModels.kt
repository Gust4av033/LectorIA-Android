package com.lectoria.app.domain.narration

import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

data class SpeechSettings(val engineId: String? = null, val voiceId: String? = null, val rate: Float = 1f) {
    init { require(rate.isFinite() && rate in 0.5f..2f) }
}

data class NarrationBookmark(val bookId: BookId, val locator: ReadingLocator, val updatedAt: Instant, val completed: Boolean = false)

interface SpeechPreferencesRepository {
    suspend fun load(): SpeechSettings
    suspend fun save(settings: SpeechSettings)
}

interface NarrationProgressRepository {
    suspend fun load(bookId: BookId): NarrationBookmark?
    suspend fun lastBook(): BookId?
    suspend fun save(bookmark: NarrationBookmark)
}

enum class SpeechPhase { IDLE, PREPARING, PLAYING, PAUSED, COMPLETED, FAILED }
enum class PauseReason { USER, FOCUS, HEADPHONES, SETTINGS, SERVICE }

data class NarrationState(
    val phase: SpeechPhase = SpeechPhase.IDLE,
    val book: Book? = null,
    val locator: ReadingLocator? = null,
    val sectionIndex: Int = 0,
    val sectionTitle: String? = null,
    val sectionCount: Int = 0,
    val fragmentIndex: Int = 0,
    val fragmentCount: Int = 1,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val preview: String = "",
    val catalog: VoiceCatalog = VoiceCatalog(null, emptyList(), emptyList()),
    val settings: SpeechSettings = SpeechSettings(),
    val loadingVoices: Boolean = false,
    val testingVoice: Boolean = false,
    val speechConfirmed: Boolean = false,
    val reconnectingVoice: Boolean = false,
    val failure: SpeechFailure? = null,
    val engineErrorCode: Int? = null,
    val saveFailed: Boolean = false,
    val pauseReason: PauseReason? = null,
) {
    val active: Boolean get() = phase == SpeechPhase.PLAYING || phase == SpeechPhase.PREPARING
    val voice: SpeechVoice? get() = catalog.voices.firstOrNull { it.id == settings.voiceId && it.offline && it.installed }
}

interface SpeechAudioFocus {
    fun acquire(onLoss: () -> Unit): Boolean
    fun release()
}

/** UI commands cross the Android service boundary; state and metadata remain platform independent. */
interface NarrationClient {
    val state: StateFlow<NarrationState>
    suspend fun prepare()
    suspend fun chooseEngine(id: String)
    suspend fun chooseVoice(id: String)
    suspend fun setRate(rate: Float)
    suspend fun readerPosition(): BookId?
    fun listen(bookId: BookId, locator: ReadingLocator)
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun stop()
    fun testVoice()
    fun reconnectVoice()
}
