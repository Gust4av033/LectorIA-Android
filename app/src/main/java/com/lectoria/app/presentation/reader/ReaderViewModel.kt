package com.lectoria.app.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lectoria.app.domain.document.DocumentPage
import com.lectoria.app.domain.document.DocumentSection
import com.lectoria.app.domain.document.DocumentSession
import com.lectoria.app.domain.document.ReaderException
import com.lectoria.app.domain.document.ReaderFailure
import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.ReadingProgress
import com.lectoria.app.domain.usecase.OpenReader
import com.lectoria.app.domain.usecase.SaveReadingProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val book: Book? = null,
    val sections: List<DocumentSection> = emptyList(),
    val page: DocumentPage? = null,
    val image: ByteArray? = null,
    val busy: Boolean = true,
    val failure: ReaderFailure? = null,
    val saveFailed: Boolean = false,
    val imageFailed: Boolean = false,
    val extractionAllowed: Boolean = true,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val openReader: OpenReader,
    private val saveProgress: SaveReadingProgress,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var session: DocumentSession? = null
    private var lifetime: Job? = null
    private var action: Job? = null
    private var lastRequested: ReadingLocator? = null

    fun reload() {
        if (lifetime?.isActive == true) return
        val previous = lifetime
        mutableState.value = ReaderUiState()
        lifetime = viewModelScope.launch {
            previous?.cancelAndJoin()
            try {
                val opened = openReader(savedState.get<String>("bookId")?.takeIf { it.isNotBlank() }?.let(::BookId))
                if (opened == null) { mutableState.update { it.copy(busy = false) }; return@launch }
                session = opened.session
                val sections = opened.session.sections()
                if (sections.isEmpty()) throw ReaderException(ReaderFailure.OPEN)
                mutableState.update { it.copy(book = opened.book, sections = sections, extractionAllowed = opened.session.canExtractText) }
                mutex.withLock {
                    try { display(opened.progress?.locator ?: sections.first().start) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { showFailure(error) }
                }
                awaitCancellation()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showFailure(error) }
            finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    mutex.withLock { runCatching { session?.close() }; session = null }
                }
            }
        }
    }

    fun release() { action?.cancel(); lifetime?.cancel() }

    fun retry() { if (session == null) reload() else lastRequested?.let(::go) }

    private fun showFailure(error: Exception) {
        mutableState.update { it.copy(busy = false, failure = (error as? ReaderException)?.reason ?: ReaderFailure.OPEN) }
    }

    fun go(locator: ReadingLocator) {
        if (state.value.busy || session == null) return
        mutableState.update { it.copy(busy = true, failure = null) }
        action = viewModelScope.launch {
            mutex.withLock {
                try { display(locator) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { showFailure(error) }
            }
        }
    }

    fun retrySave() {
        val locator = state.value.page?.locator ?: return
        go(locator)
    }

    private suspend fun display(locator: ReadingLocator) {
        lastRequested = locator
        val active = checkNotNull(session)
        val page = active.page(locator)
        val book = checkNotNull(state.value.book)
        var saveFailed = false
        try { saveProgress(ReadingProgress(book.id, page.locator, Instant.now())) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { saveFailed = true }
        var imageFailed = false
        val image = try { active.image(page.locator) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { imageFailed = true; null }
        mutableState.update { it.copy(page = page, image = image, busy = false, failure = null, saveFailed = saveFailed, imageFailed = imageFailed) }
    }
}
