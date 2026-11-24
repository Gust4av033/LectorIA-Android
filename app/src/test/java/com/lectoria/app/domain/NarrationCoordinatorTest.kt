@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.lectoria.app.domain

import com.lectoria.app.domain.document.*
import com.lectoria.app.domain.model.*
import com.lectoria.app.domain.narration.*
import com.lectoria.app.domain.repository.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class NarrationCoordinatorTest {
    @Test fun `reconnect replaces the engine without automatically speaking or moving the bookmark`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.engine.range(5); runCurrent()
        r.coordinator.pause()
        val saved = r.marks.entries[book().id]
        r.coordinator.reconnectVoice(); runCurrent()
        assertEquals(SpeechPhase.PAUSED, r.coordinator.state.value.phase)
        assertFalse(r.coordinator.state.value.reconnectingVoice)
        assertEquals(1, r.engine.requests.size)
        assertEquals(saved, r.marks.entries[book().id])
    }

    @Test fun `speaking is only confirmed by the active engine callback`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        assertFalse(r.coordinator.state.value.speechConfirmed)
        r.engine.events.tryEmit(NarrationEvent.Started("old")); runCurrent()
        assertFalse(r.coordinator.state.value.speechConfirmed)
        val id = r.engine.requests.single().id
        r.engine.events.tryEmit(NarrationEvent.Started(id)); runCurrent()
        assertTrue(r.coordinator.state.value.speechConfirmed)
        r.engine.events.tryEmit(NarrationEvent.Reconnecting(id)); runCurrent()
        assertFalse(r.coordinator.state.value.speechConfirmed)
        assertTrue(r.coordinator.state.value.reconnectingVoice)
        r.coordinator.pause(); runCurrent()
        assertFalse(r.coordinator.state.value.reconnectingVoice)
    }
    @Test fun `voice sample uses no book text and cannot advance the saved listening position`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.engine.range(5); runCurrent()
        r.coordinator.pause()
        val saved = r.marks.entries[book().id]
        r.coordinator.testVoice(); runCurrent()
        assertEquals("voice-test", r.engine.requests.last().chunk.start.sectionId)
        assertTrue(r.coordinator.state.value.testingVoice)
        r.engine.finish(); runCurrent()
        assertEquals(saved, r.marks.entries[book().id])
        assertEquals(SpeechPhase.PAUSED, r.coordinator.state.value.phase)
        assertFalse(r.coordinator.state.value.testingVoice)
    }
    private val spanish = SpeechVoice("es-local", "Español local", "es-GT", true, true, 200)
    private val english = SpeechVoice("en-local", "English local", "en-US", true, true, 300)
    private fun book(id: String = "one") = Book(BookId(id), "Libro $id", null, BookFormat.TXT, "0".repeat(64), "/synthetic/$id.txt", Instant.EPOCH, 200)

    private inner class Books : BookRepository {
        var lookupGate: CompletableDeferred<Unit>? = null
        val entries = MutableStateFlow(listOf(book(), book("two")))
        override fun observeBooks() = entries
        override fun observeLibrary() = flow<LibraryContent> { }
        override suspend fun find(id: BookId): Book? { lookupGate?.await(); return entries.value.firstOrNull { it.id == id } }
        override suspend fun importDocument(source: DocumentSource): Result<ImportOutcome> = Result.failure(IOException())
        override suspend fun reconcile() = Result.success(Unit)
        override suspend fun discardFailedImport(id: BookId) = Result.success(Unit)
        override suspend fun deleteBook(id: BookId): Result<Unit> { entries.value = entries.value.filterNot { it.id == id }; return Result.success(Unit) }
    }
    private class Reading : ReadingProgressRepository {
        val entries = mutableMapOf<BookId, ReadingProgress>()
        override suspend fun load(bookId: BookId) = entries[bookId]
        override suspend fun save(progress: ReadingProgress) { entries[progress.bookId] = progress }
        override suspend fun lastBook() = entries.values.maxByOrNull { it.updatedAt }?.bookId
    }
    private class Bookmarks : NarrationProgressRepository {
        val entries = mutableMapOf<BookId, NarrationBookmark>()
        var fails = false
        override suspend fun load(bookId: BookId) = entries[bookId]
        override suspend fun lastBook() = entries.values.maxByOrNull { it.updatedAt }?.bookId
        override suspend fun save(bookmark: NarrationBookmark) { if (fails) throw IOException(); entries[bookmark.bookId] = bookmark }
    }
    private class Preferences : SpeechPreferencesRepository {
        var settings = SpeechSettings()
        var fails = false
        override suspend fun load(): SpeechSettings { if (fails) throw IOException(); return settings }
        override suspend fun save(settings: SpeechSettings) { if (fails) throw IOException(); this.settings = settings }
    }
    private class Focus : SpeechAudioFocus {
        var granted = true
        var requests = 0
        var losses: (() -> Unit)? = null
        var held = false
        override fun acquire(onLoss: () -> Unit): Boolean { requests++; losses = onLoss; held = granted; return granted }
        override fun release() { held = false }
    }
    private inner class Engine : LocalNarrationEngine {
        override val output = NarrationOutput.LiveSpeech
        override val events = MutableSharedFlow<NarrationEvent>(extraBufferCapacity = 32)
        var voices = listOf(spanish, english, spanish.copy(id = "network", offline = false, quality = 500), spanish.copy(id = "missing", installed = false, quality = 600))
        val requests = mutableListOf<SpeechRequest>()
        var current: CompletableDeferred<Result<Unit>>? = null
        var initFailure: SpeechFailure? = null
        var defaultVoiceId: String? = null
        override suspend fun configure(engineId: String?): VoiceCatalog {
            initFailure?.let { throw SpeechException(it) }
            return VoiceCatalog(engineId ?: "engine", listOf(SpeechEngineInfo("engine", "Motor instalado")), voices, defaultVoiceId)
        }
        override suspend fun narrate(request: SpeechRequest): Result<Unit> {
            requests += request
            val done = CompletableDeferred<Result<Unit>>()
            current = done
            try { return done.await() } finally { if (current === done) current = null }
        }
        override fun stopImmediately() { current?.cancel(); current = null }
        override suspend fun cancel() { stopImmediately() }
        override fun shutdown() { stopImmediately() }
        fun finish() { checkNotNull(current).complete(Result.success(Unit)) }
        fun range(offset: Int, id: String = requests.last().id) {
            events.tryEmit(NarrationEvent.Progress(id, TextLocator("text", requests.last().chunk.start.characterOffset + offset)))
        }
    }
    private class Session(private val text: List<String>) : DocumentSession {
        val starts = text.indices.map { index -> text.take(index).sumOf { it.length }.toLong() }
        var closed = false
        override val canExtractText = true
        override suspend fun sections() = listOf(DocumentSection("text", "Capítulo", ReadingLocator.Text(0)))
        override suspend fun page(locator: ReadingLocator): DocumentPage {
            check(!closed)
            val offset = (locator as ReadingLocator.Text).characterOffset
            val index = starts.indexOfLast { it <= offset }.coerceAtLeast(0)
            val start = starts[index]
            val value = text[index]
            return DocumentPage(ReadingLocator.Text(start), 0, value, starts.getOrNull(index - 1)?.let(ReadingLocator::Text),
                starts.getOrNull(index + 1)?.let(ReadingLocator::Text), value.takeIf { it.isNotBlank() }?.let {
                    TextChunk(it, TextLocator("text", start), TextLocator("text", start + it.length))
                }, index, text.size, (offset - start).toInt())
        }
        override fun locateText(locator: TextLocator) = ReadingLocator.Text(locator.characterOffset)
        override fun textFrom(locator: ReadingLocator) = flow<TextChunk> { }
        override fun close() { closed = true }
    }
    private inner class Reader : DocumentReader {
        var texts = listOf("Primer fragmento: canción y niño. ", "Segundo fragmento: español. ", "Último fragmento, final.")
        val sessions = mutableListOf<Session>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun open(book: Book): Result<DocumentSession> {
            gate?.await()
            return Result.success(Session(texts).also { sessions += it })
        }
    }
    private inner class Rig(scope: TestScope) {
        val books = Books()
        val reading = Reading()
        val marks = Bookmarks()
        val prefs = Preferences()
        val engine = Engine()
        val focus = Focus()
        val reader = Reader()
        val coordinator = NarrationCoordinator(books, reader, reading, marks, prefs, engine, focus, scope.backgroundScope,
            now = { Instant.ofEpochMilli(scope.testScheduler.currentTime + 1000) }, elapsedMs = { scope.testScheduler.currentTime },
            closeDispatcher = StandardTestDispatcher(scope.testScheduler))
    }

    @Test fun `only an installed offline Spanish voice is automatically selected`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        assertEquals("es-local", r.engine.requests.single().voiceId)
        assertEquals(SpeechPhase.PLAYING, r.coordinator.state.value.phase)
        assertTrue(r.focus.held)
    }

    @Test fun `offline Spanish system default is preferred over an arbitrary higher quality voice`() = runTest {
        val r = Rig(this)
        r.engine.voices = listOf(spanish.copy(id = "system", quality = 100), spanish.copy(id = "other", quality = 500))
        r.engine.defaultVoiceId = "system"
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        assertEquals("system", r.engine.requests.single().voiceId)
    }

    @Test fun `network system default is never automatically selected`() = runTest {
        val r = Rig(this)
        r.engine.voices = listOf(spanish, spanish.copy(id = "network-default", offline = false, quality = 500))
        r.engine.defaultVoiceId = "network-default"
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        assertEquals(spanish.id, r.engine.requests.single().voiceId)
    }

    @Test fun `missing offline Spanish voice is explicit and a different language requires a selection`() = runTest {
        val r = Rig(this)
        r.engine.voices = listOf(english)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        assertEquals(SpeechFailure.NO_OFFLINE_VOICE, r.coordinator.state.value.failure)
        assertTrue(r.engine.requests.isEmpty())
        r.coordinator.chooseVoice(english.id)
        r.coordinator.start(); runCurrent()
        assertEquals(english.id, r.engine.requests.single().voiceId)
    }

    @Test fun `network and uninstalled voices cannot be selected or spoken`() = runTest {
        val r = Rig(this)
        r.coordinator.prepare()
        r.coordinator.chooseVoice("network")
        assertEquals(SpeechFailure.NO_OFFLINE_VOICE, r.coordinator.state.value.failure)
        assertEquals(spanish.id, r.coordinator.state.value.settings.voiceId)
        r.coordinator.chooseVoice("missing")
        assertEquals(SpeechFailure.NO_OFFLINE_VOICE, r.coordinator.state.value.failure)
        assertTrue(r.engine.requests.isEmpty())
    }

    @Test fun `pause persists the reported word and resumes the remainder without skipping text`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.engine.range(7); runCurrent()
        r.coordinator.pause(); runCurrent()
        assertEquals(ReadingLocator.Text(7), r.marks.entries[book().id]!!.locator)
        assertEquals(SpeechPhase.PAUSED, r.coordinator.state.value.phase)
        assertTrue(r.reader.sessions.all { it.closed })
        assertFalse(r.focus.held)
        r.coordinator.start(); runCurrent()
        assertEquals(r.reader.texts.first().substring(7), r.engine.requests.last().chunk.text)
        assertEquals(7L, r.engine.requests.last().chunk.start.characterOffset)
    }

    @Test fun `without range callbacks pause repeats the current fragment honestly`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.coordinator.pause(); r.coordinator.start(); runCurrent()
        assertEquals(r.engine.requests.first().chunk, r.engine.requests.last().chunk)
    }

    @Test fun `completed utterances advance exactly once and completion releases all resources`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        repeat(3) { r.engine.finish(); runCurrent() }
        assertEquals(r.reader.texts, r.engine.requests.map { it.chunk.text })
        assertEquals(SpeechPhase.COMPLETED, r.coordinator.state.value.phase)
        assertTrue(r.marks.entries[book().id]!!.completed)
        assertTrue(r.reader.sessions.all { it.closed })
        assertFalse(r.focus.held)
        r.coordinator.start(); runCurrent()
        assertEquals(r.reader.texts.first(), r.engine.requests.last().chunk.text)
    }

    @Test fun `late callback from an interrupted request cannot move the new cursor`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        val old = r.engine.requests.single().id
        r.coordinator.skip(true); runCurrent()
        val position = r.coordinator.state.value.locator
        r.engine.range(5, old); runCurrent()
        assertEquals(position, r.coordinator.state.value.locator)
        assertEquals(2, r.engine.requests.size)
    }

    @Test fun `skip while paused preserves silence and skipping the end does not replay the last fragment`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.coordinator.pause(); r.coordinator.skip(true); runCurrent()
        assertEquals(1, r.engine.requests.size)
        assertEquals(SpeechPhase.PAUSED, r.coordinator.state.value.phase)
        r.coordinator.skip(true); r.coordinator.skip(true); runCurrent()
        assertEquals(SpeechPhase.COMPLETED, r.coordinator.state.value.phase)
        assertEquals(1, r.engine.requests.size)
    }

    @Test fun `audio focus loss pauses and focus gain never starts speech by itself`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        checkNotNull(r.focus.losses).invoke(); runCurrent()
        assertEquals(SpeechPhase.PAUSED, r.coordinator.state.value.phase)
        assertEquals(PauseReason.FOCUS, r.coordinator.state.value.pauseReason)
        assertEquals(1, r.engine.requests.size)
        r.coordinator.start(); runCurrent()
        assertEquals(2, r.engine.requests.size)
    }

    @Test fun `denied focus never speaks and engine failure preserves the book`() = runTest {
        val r = Rig(this)
        r.focus.granted = false
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        assertEquals(SpeechFailure.AUDIO_FOCUS, r.coordinator.state.value.failure)
        assertTrue(r.engine.requests.isEmpty())
        assertNotNull(r.books.find(book().id))
        r.focus.granted = true
        r.engine.initFailure = SpeechFailure.INIT
        r.coordinator.start(); runCurrent()
        assertEquals(SpeechFailure.INIT, r.coordinator.state.value.failure)
        assertNotNull(r.books.find(book().id))
    }

    @Test fun `voice speed and delivery changes persist and pause until explicitly played`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.coordinator.chooseVoice(english.id)
        r.coordinator.setRate(1.3f)
        r.coordinator.setDelivery(SpeechDelivery.NARRATIVE); runCurrent()
        assertEquals(SpeechPhase.PAUSED, r.coordinator.state.value.phase)
        assertEquals(1, r.engine.requests.size)
        assertEquals(english.id, r.prefs.settings.voiceId)
        r.coordinator.start(); runCurrent()
        assertEquals(1.3f, r.engine.requests.last().rate)
        assertEquals(english.id, r.engine.requests.last().voiceId)
        assertEquals(SpeechDelivery.NARRATIVE, r.engine.requests.last().delivery)
    }

    @Test fun `pause during document preparation prevents delayed autoplay`() = runTest {
        val r = Rig(this)
        val gate = CompletableDeferred<Unit>()
        r.reader.gate = gate
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.coordinator.pause(); gate.complete(Unit); runCurrent()
        assertTrue(r.engine.requests.isEmpty())
        assertEquals(SpeechPhase.PAUSED, r.coordinator.state.value.phase)
    }

    @Test fun `play enters preparation before database lookup and respects pause during that lookup`() = runTest {
        val r = Rig(this)
        val gate = CompletableDeferred<Unit>()
        r.books.lookupGate = gate
        launch { r.coordinator.start(book().id, ReadingLocator.Text(0)) }
        runCurrent()
        assertEquals(SpeechPhase.PREPARING, r.coordinator.state.value.phase)
        launch { r.coordinator.pause() }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertTrue(r.engine.requests.isEmpty())
        assertFalse(r.coordinator.state.value.active)
    }

    @Test fun `rapid stop and book switch cannot cancel the newer speech job`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        launch { r.coordinator.stop() }
        launch { r.coordinator.start(book("two").id, ReadingLocator.Text(0)) }
        runCurrent()
        assertEquals(book("two").id, r.coordinator.state.value.book!!.id)
        assertEquals(SpeechPhase.PLAYING, r.coordinator.state.value.phase)
        assertTrue(r.focus.held)
    }

    @Test fun `storage errors are visible and in memory resume keeps the latest word`() = runTest {
        val r = Rig(this)
        r.marks.fails = true
        r.reading.entries[book().id] = ReadingProgress(book().id, ReadingLocator.Text(0), Instant.EPOCH)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.engine.range(9); runCurrent()
        r.coordinator.pause(); runCurrent()
        assertTrue(r.coordinator.state.value.saveFailed)
        r.coordinator.start(); runCurrent()
        assertEquals(r.reader.texts.first().substring(9), r.engine.requests.last().chunk.text)
        assertEquals(ReadingLocator.Text(0), r.reading.entries[book().id]!!.locator)
    }

    @Test fun `a document with no narratable text never fabricates speech or marks completion`() = runTest {
        val r = Rig(this)
        r.reader.texts = listOf(" ", " ", " ")
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        assertEquals(SpeechFailure.NO_TEXT, r.coordinator.state.value.failure)
        assertTrue(r.engine.requests.isEmpty())
        assertTrue(r.marks.entries.isEmpty())
        assertEquals(0, r.focus.requests)
    }

    @Test fun `deleting the active book stops speech without restoring its record`() = runTest {
        val r = Rig(this)
        r.coordinator.start(book().id, ReadingLocator.Text(0)); runCurrent()
        r.books.deleteBook(book().id); runCurrent()
        assertEquals(SpeechFailure.DOCUMENT, r.coordinator.state.value.failure)
        assertFalse(r.focus.held)
        assertTrue(r.reader.sessions.all { it.closed })
        assertNull(r.books.find(book().id))
    }
}
