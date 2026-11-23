package com.lectoria.app.engine

import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.engine.document.AndroidDocumentReader
import com.lectoria.app.support.DocumentFixtures
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentReaderTest {
    @get:Rule val temp = TemporaryFolder()
    private fun book(format: BookFormat, bytes: ByteArray, encoding: String? = null): Book {
        val file = temp.newFile("book.${format.name.lowercase()}").apply { writeBytes(bytes) }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return Book(BookId("synthetic"), "Prueba", null, format, hash, file.path, Instant.EPOCH, file.length(), textEncoding = encoding)
    }
    private fun reader() = AndroidDocumentReader(RuntimeEnvironment.getApplication())

    @Test fun `long Unicode TXT streams bounded chunks without losing or repeating characters`() = runBlocking {
        val text = ("¡Niño, canción, pingüino! 😀 España y Guatemala.\n".repeat(1800))
        val book = book(BookFormat.TXT, text.toByteArray(), "UTF-8")
        reader().open(book).getOrThrow().use { session ->
            val chunks = session.textFrom(ReadingLocator.Text(0)).toList()
            assertTrue(chunks.size > 20)
            assertTrue(session.sections().size > 1)
            assertEquals(text, chunks.joinToString("") { it.text })
            assertTrue(chunks.all { it.text.length <= 1600 && !Character.isHighSurrogate(it.text.last()) })
            chunks.zipWithNext().forEach { (left, right) -> assertEquals(left.end, right.start) }
            val first = session.page(ReadingLocator.Text(0))
            val second = session.page(first.next!!)
            assertEquals(first.locator, second.previous)
            val restored = session.page(second.locator)
            assertEquals(second.text, restored.text)
            assertEquals(text.substring((restored.locator as ReadingLocator.Text).characterOffset.toInt()), session.textFrom(restored.locator).toList().joinToString("") { it.text })
        }
        // Reopening owns no extracted copies or persistent temporary files.
        reader().open(book).getOrThrow().use { assertEquals(text.take(100), it.page(ReadingLocator.Text(0)).text.take(100)) }
        assertEquals(1, temp.root.listFiles()!!.size)
    }

    @Test fun `TXT uses the imported UTF16 encoding and rejects out of range locations`() = runBlocking {
        val text = "\uFEFFEspañol: acción, corazón y ñ."
        reader().open(book(BookFormat.TXT, text.toByteArray(Charsets.UTF_16LE), "UTF-16LE")).getOrThrow().use { session ->
            assertEquals(text, session.page(ReadingLocator.Text(0)).text)
            try { session.page(ReadingLocator.Text(500)); fail("Invalid offset") } catch (_: com.lectoria.app.domain.document.ReaderException) { }
        }
    }

    @Test fun `Readium extracts EPUB text and restores its resource locator`() = runBlocking {
        val book = book(BookFormat.EPUB, DocumentFixtures.epub())
        val locator = reader().open(book).getOrThrow().use { session ->
            assertTrue(session.canExtractText)
            val sections = session.sections()
            assertEquals(1, sections.size)
            assertEquals("Prueba", sections.first().title)
            val page = session.page(sections.first().start)
            assertTrue(page.text.contains("Lectura sintética con tildes: canción."))
            assertNull(page.previous)
            assertNull(page.next)
            assertEquals(page.text, session.textFrom(page.locator).toList().joinToString("") { it.text })
            page.locator
        }
        reader().open(book).getOrThrow().use { assertTrue(it.page(locator).text.contains("canción")) }
    }

    @Test fun `EPUB without TOC falls back to reading order and paginates real chapter text`() = runBlocking {
        val paragraph = "Texto español de capítulo sin índice. "
        val chapter = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Capítulo</title></head><body><p>${paragraph.repeat(120)}</p></body></html>"""
        val bytes = DocumentFixtures.epub(extra = mapOf(
            "OEBPS/nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Sin índice</title></head><body/></html>""".toByteArray(),
            "OEBPS/chapter.xhtml" to chapter.toByteArray(),
        ))
        reader().open(book(BookFormat.EPUB, bytes)).getOrThrow().use { session ->
            val section = session.sections().single()
            val first = session.page(section.start)
            val second = session.page(first.next!!)
            assertEquals(first.locator, second.previous)
            assertEquals(second.text, session.page(second.locator).text)
            val text = session.textFrom(section.start).toList().joinToString("") { it.text }
            assertEquals(paragraph.repeat(120).trim(), text)
        }
    }

    @Test fun `PDFBox extracts actual PDF text independently from native rendering`() = runBlocking {
        reader().open(book(BookFormat.PDF, DocumentFixtures.pdf())).getOrThrow().use { session ->
            assertTrue(session.canExtractText)
            val first = session.page(session.sections().single().start)
            assertTrue(first.text.contains("Texto sintetico"))
            assertNotNull(first.chunk)
            assertEquals(first.text, session.textFrom(first.locator).toList().single().text)
            assertEquals(first, session.page(first.locator))
        }
    }

    @Test fun `EPUB moves between resources and backward returns to the final fragment of the previous resource`() = runBlocking {
        val first = "Primer capítulo, canción y memoria. ".repeat(140)
        val second = "Segundo capítulo, fin de la historia."
        fun html(text: String) = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Capítulo</title></head><body><p>$text</p></body></html>"""
        val bytes = DocumentFixtures.epub(extra = mapOf("OEBPS/chapter.xhtml" to html(first).toByteArray()), additionalChapter = html(second))
        reader().open(book(BookFormat.EPUB, bytes)).getOrThrow().use { session ->
            val sections = session.sections()
            assertEquals(2, sections.size)
            assertEquals(first.trim() + second, session.textFrom(sections.first().start).toList().joinToString("") { it.text })
            val page = session.page(sections[1].start)
            assertEquals(second, page.text)
            val previous = session.page(page.previous!!)
            assertEquals(0, previous.sectionIndex)
            assertTrue((previous.locator as ReadingLocator.Epub).locatorJson.contains("lectorTextOffset"))
            assertEquals(page.locator, previous.next)
            assertTrue(first.trim().endsWith(previous.text))
        }
    }

    @Test fun `PDF with no text produces no fabricated narration chunks`() = runBlocking {
        reader().open(book(BookFormat.PDF, DocumentFixtures.pdf(textual = false))).getOrThrow().use { session ->
            val first = session.page(ReadingLocator.Pdf(0))
            assertTrue(first.text.isBlank())
            assertNull(first.chunk)
            assertTrue(session.textFrom(first.locator).toList().isEmpty())
        }
    }

    @Test fun `missing private file is a recoverable open failure`() = runBlocking {
        val book = book(BookFormat.TXT, "Texto".toByteArray(), "UTF-8")
        File(book.localPath).delete()
        assertTrue(reader().open(book).isFailure)
    }

    @Test fun `same length changed content is rejected without overwriting its file`() = runBlocking {
        val book = book(BookFormat.TXT, "Original".toByteArray(), "UTF-8")
        File(book.localPath).writeText("Alterado")
        assertTrue(reader().open(book).isFailure)
        assertEquals("Alterado", File(book.localPath).readText())
    }
}
