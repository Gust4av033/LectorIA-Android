package com.lectoria.app.domain.document

import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.TextLocator
import kotlinx.coroutines.flow.Flow

/** Opening/rendering a PDF does not establish that its text can be extracted. */
interface DocumentReader {
    suspend fun open(book: Book): Result<DocumentSession>
}

interface DocumentSession : AutoCloseable {
    val canExtractText: Boolean
    suspend fun sections(): List<DocumentSection>
    fun textFrom(locator: ReadingLocator): Flow<TextChunk>
    override fun close()
}

data class DocumentSection(val id: String, val title: String?, val start: ReadingLocator)

data class TextChunk(val text: String, val start: TextLocator, val end: TextLocator)
