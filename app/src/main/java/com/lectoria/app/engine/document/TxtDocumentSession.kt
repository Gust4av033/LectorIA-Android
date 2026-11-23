package com.lectoria.app.engine.document

import com.lectoria.app.domain.document.DocumentPage
import com.lectoria.app.domain.document.DocumentSection
import com.lectoria.app.domain.document.DocumentSession
import com.lectoria.app.domain.document.ReaderException
import com.lectoria.app.domain.document.ReaderFailure
import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.TextLocator
import java.io.File
import java.io.PushbackReader
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

internal class TxtDocumentSession private constructor(
    private val file: File,
    private val charset: Charset,
    private val starts: List<Long>,
    private val length: Long,
) : DocumentSession {
    private var closed = false
    override val canExtractText = true

    companion object {
        suspend fun open(file: File, encoding: String): TxtDocumentSession = withContext(Dispatchers.IO) {
            val charset = Charset.forName(encoding)
            val starts = mutableListOf<Long>()
            var length = 0L
            PushbackReader(file.bufferedReader(charset), TextPagination.CHARS + 1).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val text = TextPagination.read(reader) ?: break
                    starts += length
                    length += text.length
                }
            }
            if (starts.isEmpty()) throw ReaderException(ReaderFailure.OPEN)
            TxtDocumentSession(file, charset, starts, length)
        }
    }

    override suspend fun sections() = starts.indices.step(20).map { index ->
        DocumentSection("txt_$index", null, ReadingLocator.Text(starts[index]))
    }

    override suspend fun page(locator: ReadingLocator): DocumentPage = withContext(Dispatchers.IO) {
        check(!closed)
        val offset = (locator as? ReadingLocator.Text)?.characterOffset ?: throw ReaderException(ReaderFailure.LOCATION)
        if (offset >= length) throw ReaderException(ReaderFailure.LOCATION)
        val index = starts.binarySearch(offset).let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }
        val start = starts[index]
        val end = starts.getOrElse(index + 1) { length }
        val text = file.bufferedReader(charset).use { reader ->
            TextPagination.skip(reader, start)
            val chars = CharArray((end - start).toInt())
            var count = 0
            while (count < chars.size) {
                val read = reader.read(chars, count, chars.size - count)
                if (read < 0) throw ReaderException(ReaderFailure.OPEN)
                count += read
            }
            String(chars)
        }
        DocumentPage(ReadingLocator.Text(start), index / 20, text,
            starts.getOrNull(index - 1)?.let(ReadingLocator::Text), starts.getOrNull(index + 1)?.let(ReadingLocator::Text),
            TextChunk(text, TextLocator("text", start), TextLocator("text", end)), index % 20, minOf(20, starts.size - (index / 20) * 20))
    }

    override fun textFrom(locator: ReadingLocator) = flow {
        var next: ReadingLocator? = locator
        while (next != null) {
            currentCoroutineContext().ensureActive()
            val page = page(next)
            page.chunk?.let { emit(it) }
            next = page.next
        }
    }.flowOn(Dispatchers.IO)

    override fun close() { closed = true }
}
