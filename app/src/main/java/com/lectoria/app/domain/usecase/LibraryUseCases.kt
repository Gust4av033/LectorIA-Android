package com.lectoria.app.domain.usecase

import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.LibraryOrder
import com.lectoria.app.domain.repository.BookRepository
import com.lectoria.app.domain.repository.DocumentSource
import com.lectoria.app.domain.repository.LibraryPreferencesRepository
import java.text.Collator
import kotlinx.coroutines.flow.combine

class ObserveLibrary(private val books: BookRepository, private val preferences: LibraryPreferencesRepository) {
    operator fun invoke() = combine(books.observeLibrary(), preferences.order) { content, order ->
        val sorted = when (order) {
            LibraryOrder.RECENT -> content.books.sortedByDescending { it.createdAt }
            LibraryOrder.TITLE -> {
                val collator = Collator.getInstance()
                content.books.sortedWith { a, b -> collator.compare(a.title, b.title) }
            }
        }
        content.copy(books = sorted) to order
    }
}

class ImportBook(private val repository: BookRepository) {
    suspend operator fun invoke(source: DocumentSource) = repository.importDocument(source)
}

class DeleteBook(private val repository: BookRepository) {
    suspend operator fun invoke(id: BookId) = repository.deleteBook(id)
}

class ReconcileLibrary(private val repository: BookRepository) {
    suspend operator fun invoke() = repository.reconcile()
}

class DiscardFailedImport(private val repository: BookRepository) {
    suspend operator fun invoke(id: BookId) = repository.discardFailedImport(id)
}

class SetLibraryOrder(private val preferences: LibraryPreferencesRepository) {
    suspend operator fun invoke(order: LibraryOrder) = preferences.setOrder(order)
}
