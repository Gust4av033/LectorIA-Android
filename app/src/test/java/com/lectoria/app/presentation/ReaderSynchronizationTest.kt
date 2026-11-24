@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.lectoria.app.presentation

import androidx.lifecycle.SavedStateHandle
import com.lectoria.app.domain.document.*
import com.lectoria.app.domain.model.*
import com.lectoria.app.domain.narration.*
import com.lectoria.app.domain.repository.*
import com.lectoria.app.domain.usecase.*
import com.lectoria.app.presentation.reader.*
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ReaderSynchronizationTest {
    private val book = Book(BookId("synthetic"), "Prueba", null, BookFormat.TXT, "0".repeat(64), "/synthetic", Instant.EPOCH, 100)
    private fun page(text: String, start: Long = 100) = DocumentPage(ReadingLocator.Text(start), 0, text, null, null,
        TextChunk(text, TextLocator("text", start), TextLocator("text", start + text.length)))
    private fun range(start: Long, end: Long, section: String = "text") = SpokenRange(TextLocator(section, start), TextLocator(section, end))

    @Test fun `highlight clips to the loaded fragment and rejects other sections`() {
        assertEquals(ReaderHighlight(0, 3), readerHighlight(page("abcdef"), range(98, 103)))
        assertEquals(ReaderHighlight(3, 6), readerHighlight(page("abcdef"), range(103, 110)))
        assertNull(readerHighlight(page("abcdef"), range(106, 110)))
        assertNull(readerHighlight(page("abcdef"), range(100, 103, "other")))
    }

    @Test fun `display offsets account for BOM and never split a surrogate pair`() {
        assertEquals(ReaderHighlight(0, 2), readerHighlight(page("\uFEFF😀hola"), range(102, 103)))
        assertEquals(ReaderHighlight(0, 2), readerHighlight(page("😀hola"), range(100, 101)))
        assertFalse(containsSpokenStart(page("abc"), TextLocator("text", 103)))
    }

    private class Client : NarrationClient {
        override val state = MutableStateFlow(NarrationState())
        var starts = 0
        override suspend fun prepare() = Unit
        override suspend fun chooseEngine(id: String) = Unit
        override suspend fun chooseVoice(id: String) = Unit
        override suspend fun setRate(rate: Float) = Unit
        override suspend fun setDelivery(delivery: SpeechDelivery) = Unit
        override suspend fun readerPosition(): BookId? = null
        override fun listen(bookId: BookId, locator: ReadingLocator) { starts++ }
        override fun play() { starts++ }
        override fun pause() = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun stop() = Unit
        override fun testVoice() = Unit
        override fun reconnectVoice() = Unit
    }

    @Test fun `reader follows across fragments without restarting voice and manual navigation suspends following`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var vm: ReaderViewModel? = null
        try {
            var loads = 0
            var failRead = false
            val document = object : DocumentSession {
                override val canExtractText = true
                override suspend fun sections() = listOf(DocumentSection("text", "Prueba", ReadingLocator.Text(100)))
                override suspend fun page(locator: ReadingLocator): DocumentPage {
                    loads++
                    if (failRead) throw ReaderException(ReaderFailure.OPEN)
                    val start = (locator as ReadingLocator.Text).characterOffset.let { it - it % 10 }
                    return page("abcdefghij", start)
                }
                override fun locateText(locator: TextLocator) = ReadingLocator.Text(locator.characterOffset)
                override fun textFrom(locator: ReadingLocator): Flow<TextChunk> = emptyFlow()
                override fun close() = Unit
            }
            val books = object : BookRepository {
                override fun observeBooks() = flowOf(listOf(book))
                override fun observeLibrary(): Flow<LibraryContent> = emptyFlow()
                override suspend fun find(id: BookId) = book.takeIf { it.id == id }
                override suspend fun importDocument(source: DocumentSource): Result<ImportOutcome> = error("unused")
                override suspend fun reconcile() = Result.success(Unit)
                override suspend fun discardFailedImport(id: BookId) = Result.success(Unit)
                override suspend fun deleteBook(id: BookId) = Result.success(Unit)
            }
            var saves = 0
            val progress = object : ReadingProgressRepository {
                override suspend fun load(bookId: BookId): ReadingProgress? = null
                override suspend fun lastBook() = book.id
                override suspend fun save(progress: ReadingProgress) { saves++ }
            }
            val client = Client()
            client.state.value = NarrationState(book = book, phase = SpeechPhase.PLAYING, spokenRange = range(114, 117))
            vm = ReaderViewModel(SavedStateHandle(mapOf("bookId" to book.id.value)), OpenReader(books, progress,
                object : DocumentReader { override suspend fun open(book: Book) = Result.success(document) }), SaveReadingProgress(progress), client)
            vm.reload(); runCurrent()
            assertEquals(ReadingLocator.Text(110), vm.state.value.page?.locator)
            assertEquals(ReaderHighlight(4, 7), vm.state.value.highlight)
            val saved = saves
            val loaded = loads
            client.state.value = client.state.value.copy(spokenRange = range(117, 119)); runCurrent()
            assertEquals(saved, saves)
            assertEquals(loaded, loads)
            vm.go(ReadingLocator.Text(100)); runCurrent()
            client.state.value = client.state.value.copy(spokenRange = range(124, 127)); runCurrent()
            assertEquals(ReadingLocator.Text(100), vm.state.value.page?.locator)
            assertFalse(vm.state.value.followingVoice)
            vm.followVoice(); runCurrent()
            assertEquals(ReadingLocator.Text(120), vm.state.value.page?.locator)
            assertEquals(0, client.starts)
            client.state.value = client.state.value.copy(book = book.copy(id = BookId("other"))); runCurrent()
            assertNull(vm.state.value.highlight)
            assertFalse(vm.state.value.hasNarration)
            client.state.value = client.state.value.copy(book = book, testingVoice = true); runCurrent()
            assertNull(vm.state.value.highlight)
            client.state.value = client.state.value.copy(testingVoice = false, phase = SpeechPhase.COMPLETED); runCurrent()
            vm.go(ReadingLocator.Text(120)); runCurrent()
            assertNull(vm.state.value.highlight)
            vm.followVoice(); runCurrent()
            failRead = true
            client.state.value = client.state.value.copy(phase = SpeechPhase.PLAYING, spokenRange = range(154, 157)); runCurrent()
            assertFalse(vm.state.value.followingVoice)
            assertEquals(ReaderFailure.OPEN, vm.state.value.failure)
            val failedLoads = loads
            client.state.value = client.state.value.copy(spokenRange = range(157, 159)); runCurrent()
            assertEquals(failedLoads, loads)
        } finally { vm?.release()?.join(); Dispatchers.resetMain() }
    }
}
