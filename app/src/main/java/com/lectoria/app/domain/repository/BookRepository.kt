package com.lectoria.app.domain.repository

import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingProgress
import com.lectoria.app.domain.model.ImportOutcome
import com.lectoria.app.domain.model.LibraryContent
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeLibrary(): Flow<LibraryContent>
    suspend fun find(id: BookId): Book?

    /** The adapter resolves the opaque source (e.g. SAF URI), validates and privately copies it. */
    suspend fun importDocument(source: DocumentSource): Result<ImportOutcome>
    suspend fun reconcile(): Result<Unit>
    suspend fun discardFailedImport(id: BookId): Result<Unit>

    /** Caller must already have obtained explicit confirmation from the user. */
    suspend fun deleteBook(id: BookId): Result<Unit>
}

@JvmInline
value class DocumentSource(val value: String) {
    init { require(value.isNotBlank()) }
}

interface ReadingProgressRepository {
    suspend fun load(bookId: BookId): ReadingProgress?
    suspend fun save(progress: ReadingProgress)
    suspend fun lastBook(): BookId?
}
