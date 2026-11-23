package com.lectoria.app.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ImportOutcome
import com.lectoria.app.domain.model.LibraryException
import com.lectoria.app.domain.model.LibraryFailure
import com.lectoria.app.domain.model.LibraryIssue
import com.lectoria.app.domain.model.LibraryOrder
import com.lectoria.app.domain.repository.DocumentSource
import com.lectoria.app.domain.usecase.DeleteBook
import com.lectoria.app.domain.usecase.DiscardFailedImport
import com.lectoria.app.domain.usecase.ImportBook
import com.lectoria.app.domain.usecase.ObserveLibrary
import com.lectoria.app.domain.usecase.ReconcileLibrary
import com.lectoria.app.domain.usecase.SetLibraryOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryMessage {
    data object Imported : LibraryMessage
    data object Duplicate : LibraryMessage
    data object Deleted : LibraryMessage
    data class Problem(val reason: LibraryFailure) : LibraryMessage
}

data class DeletionRequest(val id: BookId, val title: String, val failedImport: Boolean)
enum class LibraryOperation { IMPORTING, DELETING }

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val issues: List<LibraryIssue> = emptyList(),
    val loading: Boolean = true,
    val operation: LibraryOperation? = null,
    val loadFailed: Boolean = false,
    val query: String = "",
    val order: LibraryOrder = LibraryOrder.RECENT,
    val selected: BookId? = null,
    val deletion: DeletionRequest? = null,
    val message: LibraryMessage? = null,
) {
    val busy get() = operation != null
    val visibleBooks get() = books.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.author.orEmpty().contains(query, ignoreCase = true) }
    val selectedBook get() = books.firstOrNull { it.id == selected }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val observe: ObserveLibrary,
    private val importBook: ImportBook,
    private val deleteBook: DeleteBook,
    private val reconcile: ReconcileLibrary,
    private val discard: DiscardFailedImport,
    private val setOrder: SetLibraryOrder,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()
    private var observer: Job? = null

    init { reload() }

    fun reload() {
        if (state.value.busy) return
        observer?.cancel()
        mutableState.update { it.copy(loading = true, loadFailed = false) }
        observer = viewModelScope.launch {
            val result = reconcile()
            if (result.isFailure) {
                mutableState.update { it.copy(loading = false, loadFailed = true, message = problem(result.exceptionOrNull())) }
                return@launch
            }
            observe().catch { error ->
                mutableState.update { it.copy(loading = false, loadFailed = true, message = problem(error)) }
            }.collect { (content, order) ->
                mutableState.update { it.copy(books = content.books, issues = content.issues, order = order, loading = false, loadFailed = false) }
            }
        }
    }

    fun importDocument(source: DocumentSource) {
        if (state.value.loading || state.value.busy || state.value.loadFailed) return
        mutableState.update { it.copy(operation = LibraryOperation.IMPORTING, message = null) }
        viewModelScope.launch {
            val result = importBook(source)
            mutableState.update { it.copy(operation = null, message = result.fold(
                onSuccess = { outcome -> if (outcome is ImportOutcome.Imported) LibraryMessage.Imported else LibraryMessage.Duplicate },
                onFailure = ::problem,
            )) }
        }
    }

    fun query(value: String) { mutableState.update { it.copy(query = value) } }
    fun select(id: BookId?) { mutableState.update { it.copy(selected = id) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null) } }
    fun pickerUnavailable() { mutableState.update { it.copy(message = LibraryMessage.Problem(LibraryFailure.ACCESS)) } }

    fun order(value: LibraryOrder) {
        viewModelScope.launch {
            runCatching { setOrder(value) }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                mutableState.update { it.copy(message = problem(error)) }
            }
        }
    }

    fun requestDelete(book: Book) {
        if (!state.value.busy) mutableState.update { it.copy(deletion = DeletionRequest(book.id, book.title, false)) }
    }

    fun requestDiscard(issue: LibraryIssue) {
        if (!state.value.busy && issue.canDiscard) mutableState.update { it.copy(deletion = DeletionRequest(issue.id, issue.title, true)) }
    }

    fun cancelDelete() { mutableState.update { it.copy(deletion = null) } }

    fun confirmDelete() {
        val request = state.value.deletion ?: return
        if (state.value.busy) return
        mutableState.update { it.copy(operation = LibraryOperation.DELETING, deletion = null, selected = null) }
        viewModelScope.launch {
            val result = if (request.failedImport) discard(request.id) else deleteBook(request.id)
            mutableState.update { it.copy(operation = null, message = result.fold({ LibraryMessage.Deleted }, ::problem)) }
        }
    }

    private fun problem(error: Throwable?) = LibraryMessage.Problem((error as? LibraryException)?.reason ?: LibraryFailure.STORAGE)
}
