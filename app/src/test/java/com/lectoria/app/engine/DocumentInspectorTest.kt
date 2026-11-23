package com.lectoria.app.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.LibraryException
import com.lectoria.app.domain.model.LibraryFailure
import com.lectoria.app.engine.document.AndroidDocumentInspector
import com.lectoria.app.support.DocumentFixtures
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentInspectorTest {
    @get:Rule val temp = TemporaryFolder()
    private val inspector = AndroidDocumentInspector()

    private fun inspect(bytes: ByteArray, name: String, mime: String? = null) =
        inspector.inspect(temp.newFile().apply { writeBytes(bytes) }, name, mime)

    private fun fails(reason: LibraryFailure, block: () -> Unit) {
        assertEquals(reason, assertThrows(LibraryException::class.java, block).reason)
    }

    @Test fun `UTF8 UTF16 and Windows1252 preserve Spanish and record their actual encoding`() {
        val text = "Canción, niño y corazón.\nSegunda línea."
        assertEquals("UTF-8", inspect(text.toByteArray(), "Español.txt", "text/plain").textEncoding)
        assertEquals("UTF-16LE", inspect(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE), "Unicode.txt").textEncoding)
        assertEquals("UTF-16BE", inspect(byteArrayOf(0xfe.toByte(), 0xff.toByte()) + text.toByteArray(Charsets.UTF_16BE), "Unicode.txt").textEncoding)
        assertEquals("windows-1252", inspect(text.toByteArray(charset("windows-1252")), "Clásico.txt").textEncoding)
    }

    @Test fun `empty whitespace binary and truncated UTF16 are rejected`() {
        fails(LibraryFailure.EMPTY) { inspect(byteArrayOf(), "vacío.txt") }
        fails(LibraryFailure.EMPTY) { inspect(" \r\n\t".toByteArray(), "vacío.txt") }
        fails(LibraryFailure.UNSUPPORTED_ENCODING) { inspect(byteArrayOf(0, 1, 2, 3), "binary.txt") }
        fails(LibraryFailure.UNSUPPORTED_ENCODING) { inspect(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x41), "broken.txt") }
    }

    @Test fun `a TXT beginning with PK is not mistaken for a ZIP archive`() {
        val metadata = inspect("PK significa clave primaria.\nTexto válido.".toByteArray(), "Notas.txt", "text/plain")
        assertEquals(BookFormat.TXT, metadata.format)
        assertEquals("UTF-8", metadata.textEncoding)
    }

    @Test fun `extension and known MIME cannot misrepresent a PDF as text`() {
        fails(LibraryFailure.FORMAT_MISMATCH) { inspect(DocumentFixtures.pdf(), "fake.txt", "text/plain") }
        fails(LibraryFailure.FORMAT_MISMATCH) { inspect(DocumentFixtures.epub(), "fake.pdf", "application/pdf") }
        fails(LibraryFailure.UNSUPPORTED) { inspect("unknown".toByteArray(), "program.exe", "application/octet-stream") }
    }

    @Test fun `EPUB reads real namespaced title author entities and an optional raster cover`() {
        val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val cover = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val metadata = inspect(DocumentFixtures.epub("Canción & memoria", "María Núñez", cover = cover), "book.epub", "application/epub+zip")
        assertEquals(BookFormat.EPUB, metadata.format)
        assertEquals("Canción & memoria", metadata.title)
        assertEquals("María Núñez", metadata.author)
        val decoded = BitmapFactory.decodeByteArray(metadata.coverPng!!, 0, metadata.coverPng.size)
        assertEquals(20, decoded.width)
        assertEquals(30, decoded.height)
        decoded.recycle()
    }

    @Test fun `EPUB rejects traversal absolute names and encoded traversal references`() {
        listOf("../outside.txt", "/outside.txt", "C:\\outside.txt").forEach { name ->
            fails(LibraryFailure.INVALID_DOCUMENT) { inspect(DocumentFixtures.epub(extra = mapOf(name to "x".toByteArray())), "bad.epub") }
        }
        fails(LibraryFailure.INVALID_DOCUMENT) {
            inspect(DocumentFixtures.epub(containerXml = DocumentFixtures.container.replace("OEBPS/package.opf", "%2e%2e/package.opf")), "bad.epub")
        }
        assertEquals(0, temp.root.listFiles()!!.count { it.name == "outside.txt" })
    }

    @Test fun `EPUB rejects DTD entities unsupported encryption missing spine and oversized expansion`() {
        val entity = """<!DOCTYPE container [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>"""
        fails(LibraryFailure.INVALID_DOCUMENT) { inspect(DocumentFixtures.epub(containerXml = entity + DocumentFixtures.container.substringAfter("?>")), "xxe.epub") }
        fails(LibraryFailure.PROTECTED_DOCUMENT) { inspect(DocumentFixtures.epub(extra = mapOf("META-INF/license.lcpl" to "{}".toByteArray())), "locked.epub") }
        fails(LibraryFailure.PROTECTED_DOCUMENT) { inspect(DocumentFixtures.epub(extra = mapOf("META-INF/encryption.xml" to """<encryption><EncryptionMethod Algorithm="urn:unsupported:encryption"/></encryption>""".toByteArray())), "locked.epub") }
        fails(LibraryFailure.INVALID_DOCUMENT) { inspect(DocumentFixtures.epub(extra = mapOf("OEBPS/chapter.xhtml" to byteArrayOf())), "empty.epub") }
        fails(LibraryFailure.TOO_LARGE) { inspect(DocumentFixtures.epub(extra = mapOf("bomb.txt" to ByteArray(2 * 1024 * 1024) { 65 })), "large.epub") }
    }

    @Test fun `plain ZIP or broken archive is not accepted as EPUB`() {
        fails(LibraryFailure.INVALID_DOCUMENT) { inspect(DocumentFixtures.zip(mapOf("hello.txt" to "hello".toByteArray())), "plain.epub") }
        fails(LibraryFailure.INVALID_DOCUMENT) { inspect("PK broken zip".toByteArray(), "broken.epub") }
    }

    @Test fun `EPUB rejects a CRC mismatch even when ZIP XML and payload still look valid`() {
        val archive = DocumentFixtures.epub()
        var offset = 0
        var changed = false
        while (offset + 46 < archive.size) {
            if (archive[offset] == 0x50.toByte() && archive[offset + 1] == 0x4b.toByte() &&
                archive[offset + 2] == 1.toByte() && archive[offset + 3] == 2.toByte()) {
                val length = (archive[offset + 28].toInt() and 255) + ((archive[offset + 29].toInt() and 255) shl 8)
                if (offset + 46 + length <= archive.size && archive.copyOfRange(offset + 46, offset + 46 + length).toString(Charsets.UTF_8) == "OEBPS/chapter.xhtml") {
                    archive[offset + 16] = (archive[offset + 16].toInt() xor 1).toByte()
                    changed = true
                    break
                }
            }
            offset++
        }
        assertTrue(changed)
        fails(LibraryFailure.INVALID_DOCUMENT) { inspect(archive, "bad-crc.epub") }
    }

    @Test fun `truncated PDF is rejected before invoking the native renderer`() {
        fails(LibraryFailure.INVALID_DOCUMENT) { inspect("%PDF-1.4\ntruncated".toByteArray(), "broken.pdf") }
    }
}
