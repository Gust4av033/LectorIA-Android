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
    /** Capability of this adapter; an individual page can still have no extractable text. */
    val canExtractText: Boolean
    suspend fun sections(): List<DocumentSection>
    suspend fun page(locator: ReadingLocator): DocumentPage
    /** Optional PNG preview, generated in memory. Text extraction does not depend on rendering. */
    suspend fun image(locator: ReadingLocator): ByteArray? = null
    fun textFrom(locator: ReadingLocator): Flow<TextChunk>
    override fun close()
}

data class DocumentSection(val id: String, val title: String?, val start: ReadingLocator)

data class TextChunk(val text: String, val start: TextLocator, val end: TextLocator)

data class DocumentPage(
    val locator: ReadingLocator,
    val sectionIndex: Int,
    val text: String,
    val previous: ReadingLocator?,
    val next: ReadingLocator?,
    val chunk: TextChunk?,
    val fragmentIndex: Int = 0,
    val fragmentCount: Int = 1,
)

enum class ReaderFailure { OPEN, LOCATION, TOO_LARGE, PROTECTED }
class ReaderException(val reason: ReaderFailure) : Exception(reason.name)
