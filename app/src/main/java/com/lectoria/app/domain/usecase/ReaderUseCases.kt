package com.lectoria.app.domain.usecase

import com.lectoria.app.domain.document.DocumentReader
import com.lectoria.app.domain.document.DocumentSession
import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingProgress
import com.lectoria.app.domain.repository.BookRepository
import com.lectoria.app.domain.repository.ReadingProgressRepository
import java.io.IOException

class OpenReader(private val books: BookRepository, private val progress: ReadingProgressRepository, private val reader: DocumentReader) {
    suspend operator fun invoke(id: BookId?): OpenedBook? {
        val selected = id ?: progress.lastBook() ?: return null
        val book = books.find(selected)?.takeIf { it.available } ?: throw IOException("Book unavailable")
        // Load the saved locator first. A failure must not silently overwrite it with the beginning.
        val saved = progress.load(selected)
        return OpenedBook(book, reader.open(book).getOrThrow(), saved)
    }
}

data class OpenedBook(val book: Book, val session: DocumentSession, val progress: ReadingProgress?)

class SaveReadingProgress(private val repository: ReadingProgressRepository) {
    suspend operator fun invoke(progress: ReadingProgress) = repository.save(progress)
}
