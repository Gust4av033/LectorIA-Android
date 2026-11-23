package com.lectoria.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.engine.document.AndroidDocumentReader
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a real Android PDF implementation; all content is generated and removed by the test. */
@RunWith(AndroidJUnit4::class)
class PdfReaderTest {
    @Test fun textAndScannedPagesHaveSeparateExtractionAndRendering() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("reader-synthetic-", ".pdf", context.cacheDir)
        try {
            val pdf = PdfDocument()
            try {
                val text = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
                text.canvas.drawText("Español: niño y canción.", 40f, 100f, Paint().apply { color = Color.BLACK; textSize = 24f })
                pdf.finishPage(text)
                val scanned = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, 2).create())
                val image = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
                try {
                    image.eraseColor(Color.WHITE)
                    android.graphics.Canvas(image).drawText("Texto dentro de una imagen", 10f, 100f, Paint().apply { textSize = 22f; color = Color.BLACK })
                    scanned.canvas.drawBitmap(image, 40f, 100f, null)
                    pdf.finishPage(scanned)
                    file.outputStream().use(pdf::writeTo)
                } finally { image.recycle() }
            } finally { pdf.close() }
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            val book = Book(BookId("synthetic"), "Prueba", null, BookFormat.PDF, hash, file.path, Instant.EPOCH, file.length())
            AndroidDocumentReader(context).open(book).getOrThrow().use { session ->
                assertEquals(2, session.sections().size)
                val first = session.page(ReadingLocator.Pdf(0))
                assertTrue(first.text.contains("niño"))
                assertTrue(first.text.contains("canción"))
                assertNotNull(first.chunk)
                val bytes = checkNotNull(session.image(first.locator))
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                assertNotNull(bitmap)
                assertTrue(bitmap.width <= 1080 && bitmap.height <= 1440)
                bitmap.recycle()
                val second = session.page(ReadingLocator.Pdf(1))
                assertTrue(second.text.isBlank())
                assertNull(second.chunk)
                assertTrue(session.textFrom(second.locator).toList().isEmpty())
                assertNotNull(session.image(second.locator))
                assertEquals(first.locator, second.previous)
            }
        } finally { file.delete() }
    }
}
