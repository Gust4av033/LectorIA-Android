package com.lectoria.app.domain.narration

import com.lectoria.app.domain.document.DocumentPage
import com.lectoria.app.domain.document.DocumentReader
import com.lectoria.app.domain.document.DocumentSession
import com.lectoria.app.domain.document.ReaderException
import com.lectoria.app.domain.document.ReaderFailure
import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.ReadingProgress
import com.lectoria.app.domain.model.TextLocator
import com.lectoria.app.domain.repository.BookRepository
import com.lectoria.app.domain.repository.ReadingProgressRepository
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Commands and callbacks are confined to the supplied scope's dispatcher (Main on Android). */
class NarrationCoordinator(
    private val books: BookRepository,
    private val reader: DocumentReader,
    private val reading: ReadingProgressRepository,
    private val bookmarks: NarrationProgressRepository,
    private val preferences: SpeechPreferencesRepository,
    private val engine: LocalNarrationEngine,
    private val focus: SpeechAudioFocus,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
    private val elapsedMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val closeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow(NarrationState())
    val state = mutableState.asStateFlow()
    private val actions = Mutex()
    private val writes = Mutex()
    private var settingsLoaded = false
    private var generation = 0L
    private var playback: Job? = null
    private var activeSession: DocumentSession? = null
    private var requestId: String? = null
    private var pending: NarrationBookmark? = null
    private var lastWriteTime = Instant.EPOCH
    private var lastRangeSave = Long.MIN_VALUE / 2
    private var serviceToken = 0L
    private val persisted = mutableMapOf<BookId, Instant>()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            engine.events.collect { event ->
                if (event is NarrationEvent.Progress && event.requestId == requestId && state.value.phase == SpeechPhase.PLAYING) {
                    val book = state.value.book ?: return@collect
                    val locator = runCatching { activeSession?.locateText(event.locator) }.getOrNull() ?: return@collect
                    val bookmark = mark(book.id, locator)
                    mutableState.update { it.copy(locator = locator) }
                    if (elapsedMs() - lastRangeSave >= 1000) {
                        lastRangeSave = elapsedMs()
                        scope.launch { save(bookmark) }
                    }
                }
            }
        }
        scope.launch {
            books.observeBooks().collect { library ->
                val selected = state.value.book?.id ?: return@collect
                if (state.value.active && library.none { it.id == selected && it.available }) {
                    // Confirm against storage so a queued, older library snapshot cannot stop a new book.
                    if (books.find(selected)?.available != true) {
                        stop()
                        fail(SpeechFailure.DOCUMENT)
                    }
                }
            }
        }
    }

    suspend fun prepare() = actions.withLock {
        if (state.value.active) return@withLock
        try {
            loadSettings()
            if (state.value.book == null) {
                val id = bookmarks.lastBook() ?: reading.lastBook()
                if (id != null) {
                    val book = books.find(id)?.takeIf { it.available }
                    val saved = bookmarks.load(id)
                    if (book != null) {
                        pending = saved
                        saved?.let { lastWriteTime = maxOf(lastWriteTime, it.updatedAt) }
                        mutableState.update { it.copy(book = book, locator = saved?.locator ?: reading.load(id)?.locator,
                            phase = if (saved?.completed == true) SpeechPhase.COMPLETED else SpeechPhase.PAUSED) }
                    }
                }
            }
            catalog()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { fail((error as? SpeechException)?.reason ?: SpeechFailure.STORAGE) }
    }

    private suspend fun loadSettings() {
        if (!settingsLoaded) {
            val settings = try { preferences.load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw SpeechException(SpeechFailure.STORAGE) }
            mutableState.update { it.copy(settings = settings) }
            settingsLoaded = true
        }
    }

    private suspend fun catalog() {
        mutableState.update { it.copy(loadingVoices = true) }
        try {
            val catalog = engine.configure(state.value.settings.engineId)
            val settings = state.value.settings
            val voice = if (settings.voiceId != null) catalog.voices.firstOrNull { it.id == settings.voiceId && it.offline && it.installed }
                else catalog.voices.filter { it.offline && it.installed && Locale.forLanguageTag(it.languageTag).language == "es" }.maxByOrNull { it.quality }
            mutableState.update { it.copy(catalog = catalog, loadingVoices = false,
                settings = settings.copy(engineId = catalog.engineId, voiceId = voice?.id),
                failure = when { catalog.engines.isEmpty() -> SpeechFailure.NO_ENGINE; voice == null -> SpeechFailure.NO_OFFLINE_VOICE; else -> null }) }
        } finally { mutableState.update { it.copy(loadingVoices = false) } }
    }

    suspend fun chooseEngine(id: String) {
        pause(PauseReason.SETTINGS)
        actions.withLock {
            try {
                loadSettings()
                val settings = state.value.settings.copy(engineId = id, voiceId = null)
                preferences.save(settings)
                mutableState.update { it.copy(settings = settings, catalog = VoiceCatalog(null, emptyList(), emptyList())) }
                catalog()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail((error as? SpeechException)?.reason ?: SpeechFailure.STORAGE) }
        }
    }

    suspend fun chooseVoice(id: String) = changeSettings { settings ->
        if (state.value.catalog.voices.none { it.id == id && it.offline && it.installed }) throw SpeechException(SpeechFailure.NO_OFFLINE_VOICE)
        settings.copy(voiceId = id)
    }

    suspend fun setRate(rate: Float) = changeSettings { it.copy(rate = rate) }

    private suspend fun changeSettings(change: (SpeechSettings) -> SpeechSettings) {
        pause(PauseReason.SETTINGS)
        actions.withLock {
            try {
                loadSettings()
                val settings = change(state.value.settings)
                preferences.save(settings)
                mutableState.update { it.copy(settings = settings, failure = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail((error as? SpeechException)?.reason ?: SpeechFailure.STORAGE) }
        }
    }

    suspend fun start(id: BookId? = null, locator: ReadingLocator? = null) {
        if (id == null && state.value.active) return
        val operation = ++generation
        cancelPlayback()
        actions.withLock {
            if (operation != generation) return@withLock
            try {
                val selected = id ?: state.value.book?.id ?: bookmarks.lastBook() ?: reading.lastBook()
                    ?: throw SpeechException(SpeechFailure.DOCUMENT)
                val book = books.find(selected)?.takeIf { it.available } ?: throw SpeechException(SpeechFailure.DOCUMENT)
                val saved = if (locator == null) pending?.takeIf { it.bookId == selected } ?: bookmarks.load(selected) else null
                val from = locator ?: saved?.locator ?: state.value.locator?.takeIf { state.value.book?.id == selected } ?: reading.load(selected)?.locator
                saved?.let { lastWriteTime = maxOf(lastWriteTime, it.updatedAt) }
                pending = saved
                mutableState.update { it.copy(book = book, locator = from, phase = SpeechPhase.PREPARING,
                    failure = null, saveFailed = false, pauseReason = null, preview = "") }
                playback = scope.launch { speakBook(operation, selected, from, saved?.completed == true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail((error as? SpeechException)?.reason ?: SpeechFailure.DOCUMENT) }
        }
    }

    private suspend fun speakBook(operation: Long, id: BookId, from: ReadingLocator?, restart: Boolean) {
        var session: DocumentSession? = null
        try {
            loadSettings()
            catalog()
            val settings = state.value.settings
            val voice = state.value.voice ?: throw SpeechException(state.value.failure ?: SpeechFailure.NO_OFFLINE_VOICE)
            val book = checkNotNull(state.value.book)
            session = reader.open(book).getOrThrow()
            activeSession = session
            val sections = session.sections()
            var cursor = if (restart || from == null) sections.firstOrNull()?.start else from
            if (cursor == null) throw SpeechException(SpeechFailure.NO_TEXT)
            var focused = false
            var spoken = false
            var lastPage: DocumentPage? = null
            while (cursor != null) {
                currentCoroutineContext().ensureActive()
                val page = session.page(cursor)
                val chunk = page.chunk
                if (chunk != null && chunk.text.isNotBlank()) {
                    val offset = page.offsetInFragment.coerceIn(0, chunk.text.length)
                    val text = chunk.text.substring(offset).removePrefix("\uFEFF")
                    val skipped = chunk.text.length - offset - text.length
                    if (text.isNotBlank()) {
                        if (!focused) {
                            if (!focus.acquire { scope.launch { pause(PauseReason.FOCUS) } }) throw SpeechException(SpeechFailure.AUDIO_FOCUS)
                            focused = true
                        }
                        val start = TextLocator(chunk.start.sectionId, chunk.start.characterOffset + offset + skipped)
                        val sliced = TextChunk(text, start, chunk.end)
                        val current = session.locateText(start)
                        mark(id, current)
                        savePending()
                        mutableState.update { it.copy(phase = SpeechPhase.PLAYING, locator = current, sectionIndex = page.sectionIndex,
                            sectionTitle = sections.getOrNull(page.sectionIndex)?.title, sectionCount = sections.size,
                            fragmentIndex = page.fragmentIndex, fragmentCount = page.fragmentCount, hasPrevious = page.previous != null,
                            hasNext = page.next != null, preview = text.take(500), failure = null) }
                        val request = SpeechRequest(UUID.randomUUID().toString(), sliced, voice.id, settings.rate)
                        requestId = request.id
                        engine.narrate(request).getOrThrow()
                        requestId = null
                        spoken = true
                        lastPage = page
                    }
                }
                cursor = page.next
                if (spoken && cursor != null) { mark(id, cursor); savePending() }
            }
            if (!spoken) throw SpeechException(SpeechFailure.NO_TEXT)
            mark(id, checkNotNull(lastPage).locator, completed = true)
            savePending()
            mutableState.update { it.copy(phase = SpeechPhase.COMPLETED, locator = pending?.locator, failure = null) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (operation == generation) fail(when (error) {
                is SpeechException -> error.reason
                is ReaderException -> if (error.reason == ReaderFailure.LOCATION) SpeechFailure.LOCATION else SpeechFailure.DOCUMENT
                else -> SpeechFailure.DOCUMENT
            })
        } finally {
            withContext(NonCancellable) {
                requestId = null
                engine.cancel()
                focus.release()
                activeSession = null
                withContext(closeDispatcher) { runCatching { session?.close() } }
            }
        }
    }

    suspend fun pause(reason: PauseReason = PauseReason.USER) {
        val operation = ++generation
        cancelPlayback()
        actions.withLock {
            if (operation != generation) return@withLock
            savePending()
            mutableState.update { it.copy(phase = when { it.book == null -> SpeechPhase.IDLE; it.phase == SpeechPhase.COMPLETED -> SpeechPhase.COMPLETED; else -> SpeechPhase.PAUSED }, pauseReason = reason) }
        }
    }

    suspend fun stop() {
        val operation = generation + 1
        pause()
        if (operation == generation) mutableState.update { it.copy(phase = SpeechPhase.IDLE, failure = null) }
    }

    suspend fun skip(forward: Boolean) {
        val wasActive = state.value.active
        val operation = generation + 1
        pause()
        if (operation != generation) return
        var target: ReadingLocator? = null
        val id = state.value.book?.id ?: return
        actions.withLock {
            try {
                val session = reader.open(checkNotNull(state.value.book)).getOrThrow()
                try {
                    if (operation != generation) return@withLock
                    val page = session.page(state.value.locator ?: session.sections().first().start)
                    target = if (forward) page.next else page.previous
                    target?.let { mark(id, it); savePending(); mutableState.update { state -> state.copy(locator = it, phase = SpeechPhase.PAUSED, failure = null) } }
                    if (forward && target == null) {
                        mark(id, page.locator, completed = true)
                        savePending()
                        mutableState.update { it.copy(phase = SpeechPhase.COMPLETED, locator = page.locator, failure = null) }
                    }
                } finally { withContext(NonCancellable + closeDispatcher) { session.close() } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(SpeechFailure.DOCUMENT) }
        }
        if (wasActive && operation == generation && target != null) start(id, target)
    }

    private suspend fun cancelPlayback() {
        val previous = playback
        engine.stopImmediately()
        previous?.cancelAndJoin()
        if (playback === previous) { playback = null; focus.release() }
    }

    private fun mark(id: BookId, locator: ReadingLocator, completed: Boolean = false): NarrationBookmark {
        val time = maxOf(now(), lastWriteTime.plusMillis(1))
        lastWriteTime = time
        return NarrationBookmark(id, locator, time, completed).also { pending = it }
    }

    private suspend fun savePending() { pending?.let { save(it) } }

    private suspend fun save(bookmark: NarrationBookmark) = writes.withLock {
        if (persisted[bookmark.bookId]?.let { it > bookmark.updatedAt } == true) return@withLock
        try {
            bookmarks.save(bookmark)
            persisted[bookmark.bookId] = bookmark.updatedAt
            if (state.value.book?.id == bookmark.bookId) mutableState.update { it.copy(saveFailed = false) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (state.value.book?.id == bookmark.bookId) mutableState.update { it.copy(saveFailed = true) } }
    }

    suspend fun readerPosition(): BookId? {
        val book = state.value.book ?: return null
        val locator = state.value.locator ?: return book.id
        try { reading.save(ReadingProgress(book.id, locator, now())) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(saveFailed = true) }; return null }
        return book.id
    }

    fun attachService(): Long = ++serviceToken
    suspend fun detachService(token: Long) {
        if (token != serviceToken) return
        val wasActive = state.value.active
        if (wasActive) pause(PauseReason.SERVICE)
        if (token == serviceToken) engine.shutdown()
    }
    fun reportServiceFailure() { scope.launch { pause(PauseReason.SERVICE); fail(SpeechFailure.SERVICE) } }

    private fun fail(reason: SpeechFailure) { mutableState.update { it.copy(phase = SpeechPhase.FAILED, failure = reason, loadingVoices = false) } }
}
