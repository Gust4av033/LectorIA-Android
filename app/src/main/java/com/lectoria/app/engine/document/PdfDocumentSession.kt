package com.lectoria.app.engine.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.lectoria.app.domain.document.DocumentPage
import com.lectoria.app.domain.document.DocumentSection
import com.lectoria.app.domain.document.DocumentSession
import com.lectoria.app.domain.document.ReaderException
import com.lectoria.app.domain.document.ReaderFailure
import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.TextLocator
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Native rendering and PDFBox text extraction are deliberately separate paths. No OCR. */
internal class PdfDocumentSession(private val file: File) : DocumentSession {
    private val document = PDDocument.load(file, MemoryUsageSetting.setupMainMemoryOnly(32L * 1024 * 1024))
    override val canExtractText = document.currentAccessPermission.canExtractContent()
    private var cachedIndex = -1
    private var cachedText = ""
    private var cachedStarts = listOf(0)
    private var closed = false

    init {
        if (document.isEncrypted || document.numberOfPages < 1) {
            document.close()
            throw ReaderException(ReaderFailure.PROTECTED)
        }
    }

    override suspend fun sections() = (0 until document.numberOfPages).map { index ->
        DocumentSection("pdf_$index", null, ReadingLocator.Pdf(index))
    }

    private suspend fun load(index: Int) {
        if (cachedIndex == index) return
        currentCoroutineContext().ensureActive()
        val text = StringBuilder()
        if (canExtractText) {
            val writer = object : Writer() {
                override fun write(chars: CharArray, offset: Int, length: Int) {
                    if (text.length + length > TextPagination.MAX_SECTION_CHARS) throw ReaderException(ReaderFailure.TOO_LARGE)
                    text.append(chars, offset, length)
                }
                override fun flush() = Unit
                override fun close() = Unit
            }
            PDFTextStripper().apply { startPage = index + 1; endPage = index + 1; sortByPosition = true }.writeText(document, writer)
        }
        currentCoroutineContext().ensureActive()
        cachedIndex = index
        cachedText = text.toString().trim()
        cachedStarts = TextPagination.offsets(cachedText)
    }

    override suspend fun page(locator: ReadingLocator): DocumentPage = withContext(Dispatchers.IO) {
        check(!closed)
        val pdf = locator as? ReadingLocator.Pdf ?: throw ReaderException(ReaderFailure.LOCATION)
        if (pdf.pageIndex >= document.numberOfPages) throw ReaderException(ReaderFailure.LOCATION)
        load(pdf.pageIndex)
        if (pdf.characterOffset > cachedText.length) throw ReaderException(ReaderFailure.LOCATION)
        val chunkIndex = TextPagination.index(cachedStarts, pdf.characterOffset)
        val start = cachedStarts[chunkIndex]
        val end = cachedStarts.getOrElse(chunkIndex + 1) { cachedText.length }
        val text = cachedText.substring(start, end)
        val previous = if (chunkIndex > 0) ReadingLocator.Pdf(pdf.pageIndex, cachedStarts[chunkIndex - 1])
            else if (pdf.pageIndex > 0) previousPage(pdf.pageIndex - 1) else null
        DocumentPage(ReadingLocator.Pdf(pdf.pageIndex, start), pdf.pageIndex, text, previous,
            if (chunkIndex + 1 < cachedStarts.size) ReadingLocator.Pdf(pdf.pageIndex, cachedStarts[chunkIndex + 1])
            else if (pdf.pageIndex + 1 < document.numberOfPages) ReadingLocator.Pdf(pdf.pageIndex + 1) else null,
            text.takeIf { it.isNotBlank() }?.let { TextChunk(it, TextLocator("pdf_${pdf.pageIndex}", start.toLong()), TextLocator("pdf_${pdf.pageIndex}", end.toLong())) }, chunkIndex, cachedStarts.size, pdf.characterOffset - start)
    }

    override fun locateText(locator: TextLocator): ReadingLocator {
        val index = locator.sectionId.removePrefix("pdf_").toIntOrNull()
        require(locator.sectionId.startsWith("pdf_") && index != null && index in 0 until document.numberOfPages)
        require(locator.characterOffset <= TextPagination.MAX_SECTION_CHARS)
        return ReadingLocator.Pdf(index, locator.characterOffset.toInt())
    }

    private suspend fun previousPage(index: Int): ReadingLocator.Pdf {
        val currentIndex = cachedIndex
        val currentText = cachedText
        val currentStarts = cachedStarts
        return try {
            load(index)
            ReadingLocator.Pdf(index, cachedStarts.last())
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { ReadingLocator.Pdf(index) }
        finally {
            cachedIndex = currentIndex
            cachedText = currentText
            cachedStarts = currentStarts
        }
    }

    override suspend fun image(locator: ReadingLocator): ByteArray = withContext(Dispatchers.IO) {
        check(!closed)
        val index = (locator as? ReadingLocator.Pdf)?.pageIndex ?: throw ReaderException(ReaderFailure.LOCATION)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(index).use { page ->
                    val scale = minOf(1080f / page.width, 1440f / page.height)
                    val bitmap = createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()))
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ByteArrayOutputStream().use { output ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                            output.toByteArray()
                        }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    override fun textFrom(locator: ReadingLocator) = flow {
        var next: ReadingLocator? = locator
        while (next != null) {
            val page = page(next)
            page.chunk?.let { emit(it) }
            next = page.next
        }
    }.flowOn(Dispatchers.IO)

    override fun close() { if (!closed) { closed = true; cachedText = ""; document.close() } }
}
