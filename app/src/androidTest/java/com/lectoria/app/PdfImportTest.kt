package com.lectoria.app

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.LibraryException
import com.lectoria.app.domain.model.LibraryFailure
import com.lectoria.app.engine.document.AndroidDocumentInspector
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Needs Android's real PDF native processor. Robolectric has no PdfProcessor shadow. */
@RunWith(AndroidJUnit4::class)
class PdfImportTest {
    @Test fun nativePdfOpeningCreatesAPreviewAndRejectsCorruption() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "synthetic-${UUID.randomUUID()}.pdf")
        try {
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
                page.canvas.drawText("Documento sintético", 20f, 40f, Paint().apply { textSize = 18f })
                document.finishPage(page)
                file.outputStream().use(document::writeTo)
            } finally { document.close() }
            val inspector = AndroidDocumentInspector()
            val metadata = inspector.inspect(file, "Documento.pdf", "application/pdf")
            assertEquals(BookFormat.PDF, metadata.format)
            assertEquals(1, metadata.pageCount)
            assertNull(metadata.author)
            assertNull(metadata.textEncoding)
            val bytes = checkNotNull(metadata.coverPng)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            bitmap.recycle()
            file.writeText("%PDF-1.4\nnot a document\n%%EOF")
            val failure = assertThrows(LibraryException::class.java) { inspector.inspect(file, "Corrupto.pdf", "application/pdf") }
            assertEquals(LibraryFailure.INVALID_DOCUMENT, failure.reason)
        } finally { file.delete() }
    }
}
