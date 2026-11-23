package com.lectoria.app.engine.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import androidx.core.graphics.createBitmap
import com.lectoria.app.domain.document.DocumentInspector
import com.lectoria.app.domain.document.DocumentMetadata
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.LibraryException
import com.lectoria.app.domain.model.LibraryFailure
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import org.xmlpull.v1.XmlPullParser

/** Phase A only: bounded validation/metadata. Readium remains the planned EPUB reading adapter. */
class AndroidDocumentInspector : DocumentInspector {
    override fun inspect(file: File, name: String, mimeType: String?, checkActive: () -> Unit): DocumentMetadata {
        try {
            if (file.length() == 0L) fail(LibraryFailure.EMPTY)
            val prefix = file.inputStream().use { readPrefix(it, 8) }
            val extension = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "epub" -> BookFormat.EPUB
                "pdf" -> BookFormat.PDF
                "txt" -> BookFormat.TXT
                else -> null
            }
            val mime = when (mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
                "application/epub+zip" -> BookFormat.EPUB
                "application/pdf" -> BookFormat.PDF
                "text/plain" -> BookFormat.TXT
                else -> null
            }
            val format = when {
                prefix.take(5).toByteArray().contentEquals("%PDF-".toByteArray()) -> BookFormat.PDF
                prefix.take(4).toByteArray().contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04)) -> BookFormat.EPUB
                extension == BookFormat.TXT || mime == BookFormat.TXT -> BookFormat.TXT
                extension != null || mime != null -> fail(LibraryFailure.INVALID_DOCUMENT)
                else -> fail(LibraryFailure.UNSUPPORTED)
            }
            if ((extension != null && extension != format) || (mime != null && mime != format)) {
                fail(LibraryFailure.FORMAT_MISMATCH)
            }
            checkActive()
            val fallback = clean(name.substringBeforeLast('.', name)).ifBlank { "Documento sin título" }
            return when (format) {
                BookFormat.TXT -> inspectText(file, fallback, checkActive)
                BookFormat.PDF -> inspectPdf(file, fallback)
                BookFormat.EPUB -> inspectEpub(file, fallback, checkActive)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: LibraryException) {
            throw error
        } catch (error: Exception) {
            throw LibraryException(LibraryFailure.INVALID_DOCUMENT, error)
        }
    }

    private fun inspectText(file: File, title: String, checkActive: () -> Unit): DocumentMetadata {
        val prefix = file.inputStream().use { readPrefix(it, 3) }
        val encoding = when {
            prefix.take(3).toByteArray().contentEquals(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())) -> "UTF-8"
            prefix.take(2).toByteArray().contentEquals(byteArrayOf(0xff.toByte(), 0xfe.toByte())) -> "UTF-16LE"
            prefix.take(2).toByteArray().contentEquals(byteArrayOf(0xfe.toByte(), 0xff.toByte())) -> "UTF-16BE"
            else -> null
        }
        fun validate(charset: String) {
            val decoder = Charset.forName(charset).newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            var hasText = false
            InputStreamReader(file.inputStream(), decoder).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    checkActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    for (i in 0 until count) {
                        val c = buffer[i]
                        if (c == '\u0000' || (c.isISOControl() && c !in "\t\n\r\u000c")) fail(LibraryFailure.UNSUPPORTED_ENCODING)
                        if (!c.isWhitespace() && c != '\uFEFF') hasText = true
                    }
                }
            }
            if (!hasText) fail(LibraryFailure.EMPTY)
        }
        val selected = if (encoding != null) {
            try { validate(encoding) } catch (error: CharacterCodingException) { fail(LibraryFailure.UNSUPPORTED_ENCODING) }
            encoding
        } else {
            try {
                validate("UTF-8")
                "UTF-8"
            } catch (error: CharacterCodingException) {
                try { validate("windows-1252") } catch (error: CharacterCodingException) { fail(LibraryFailure.UNSUPPORTED_ENCODING) }
                "windows-1252"
            }
        }
        return DocumentMetadata(title, null, BookFormat.TXT, textEncoding = selected)
    }

    private fun inspectPdf(file: File, title: String): DocumentMetadata {
        // PdfRenderer validates native opening and supplies a preview; it does not extract text/author.
        val tail = java.io.RandomAccessFile(file, "r").use {
            it.seek(maxOf(0, it.length() - 2048))
            ByteArray((it.length() - it.filePointer).toInt()).also(it::readFully)
        }
        if (!tail.toString(Charsets.ISO_8859_1).contains("%%EOF")) fail(LibraryFailure.INVALID_DOCUMENT)
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount < 1) fail(LibraryFailure.INVALID_DOCUMENT)
                    val cover = renderer.openPage(0).use { page ->
                        if (page.width <= 0 || page.height <= 0) fail(LibraryFailure.INVALID_DOCUMENT)
                        val scale = minOf(384f / page.width, 512f / page.height)
                        val bitmap = createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()))
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            png(bitmap)
                        } finally { bitmap.recycle() }
                    }
                    return DocumentMetadata(title, null, BookFormat.PDF, coverPng = cover, pageCount = renderer.pageCount)
                }
            }
        } catch (error: SecurityException) {
            throw LibraryException(LibraryFailure.PROTECTED_DOCUMENT, error)
        }
    }

    private data class ManifestItem(val href: String, val type: String, val properties: String)

    private fun inspectEpub(file: File, fallback: String, checkActive: () -> Unit): DocumentMetadata = ZipFile(file).use { zip ->
        val entries = zip.entries().toList()
        if (entries.size > 2000) fail(LibraryFailure.TOO_LARGE)
        val names = mutableSetOf<String>()
        var expanded = 0L
        entries.forEach { entry ->
            checkActive()
            safeEntry(entry.name)
            if (!names.add(entry.name)) fail(LibraryFailure.INVALID_DOCUMENT)
            if (!entry.isDirectory) {
                if (entry.size > 16L * 1024 * 1024) fail(LibraryFailure.TOO_LARGE)
                if (entry.size > 1024 * 1024 && entry.size > maxOf(1, entry.compressedSize) * 200) fail(LibraryFailure.TOO_LARGE)
                val crc = CRC32()
                var actual = 0L
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        actual += count
                        expanded += count
                        if (actual > 16L * 1024 * 1024 || expanded > 200L * 1024 * 1024) fail(LibraryFailure.TOO_LARGE)
                        crc.update(buffer, 0, count)
                    }
                }
                if (actual != entry.size || crc.value != entry.crc) fail(LibraryFailure.INVALID_DOCUMENT)
            }
        }
        fun read(path: String, limit: Int): ByteArray {
            val entry = zip.getEntry(path) ?: fail(LibraryFailure.INVALID_DOCUMENT)
            if (entry.isDirectory || entry.size > limit) fail(LibraryFailure.INVALID_DOCUMENT)
            return zip.getInputStream(entry).use { limited(it, limit, checkActive) }
        }
        val mimetype = zip.getEntry("mimetype") ?: fail(LibraryFailure.INVALID_DOCUMENT)
        if (entries.firstOrNull()?.name != "mimetype" || mimetype.method != java.util.zip.ZipEntry.STORED ||
            read("mimetype", 128).toString(Charsets.US_ASCII) != "application/epub+zip") fail(LibraryFailure.INVALID_DOCUMENT)
        if ("META-INF/license.lcpl" in names) fail(LibraryFailure.PROTECTED_DOCUMENT)
        if ("META-INF/encryption.xml" in names) {
            xml(read("META-INF/encryption.xml", 1024 * 1024)) { parser ->
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "EncryptionMethod" &&
                    parser.getAttributeValue(null, "Algorithm") !in setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")) {
                    fail(LibraryFailure.PROTECTED_DOCUMENT)
                }
            }
        }
        var packagePath: String? = null
        xml(read("META-INF/container.xml", 1024 * 1024)) { parser ->
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile" &&
                parser.namespace == "urn:oasis:names:tc:opendocument:xmlns:container" &&
                parser.getAttributeValue(null, "media-type") == "application/oebps-package+xml" && packagePath == null) {
                packagePath = resolve("", parser.getAttributeValue(null, "full-path") ?: fail(LibraryFailure.INVALID_DOCUMENT))
            }
        }
        val opf = packagePath ?: fail(LibraryFailure.INVALID_DOCUMENT)
        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()
        val title = StringBuilder()
        val author = StringBuilder()
        var capture: String? = null
        var coverId: String? = null
        var isPackage = false
        xml(read(opf, 2 * 1024 * 1024)) { parser ->
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when {
                    parser.namespace == "http://purl.org/dc/elements/1.1/" && parser.name in setOf("title", "creator") -> {
                        if (parser.name == "creator" && author.isNotEmpty()) author.append("; ")
                        capture = if (parser.name == "title" && title.isNotEmpty()) null else parser.name
                    }
                    parser.namespace == "http://www.idpf.org/2007/opf" -> when (parser.name) {
                        "package" -> isPackage = true
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id") ?: fail(LibraryFailure.INVALID_DOCUMENT)
                            val item = ManifestItem(resolve(opf.substringBeforeLast('/', ""), parser.getAttributeValue(null, "href") ?: fail(LibraryFailure.INVALID_DOCUMENT)),
                                parser.getAttributeValue(null, "media-type") ?: "", parser.getAttributeValue(null, "properties") ?: "")
                            if (manifest.put(id, item) != null) fail(LibraryFailure.INVALID_DOCUMENT)
                        }
                        "itemref" -> spine += parser.getAttributeValue(null, "idref") ?: fail(LibraryFailure.INVALID_DOCUMENT)
                        "meta" -> if (parser.getAttributeValue(null, "name") == "cover") coverId = parser.getAttributeValue(null, "content")
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> when (capture) {
                    "title" -> if (title.length < 256) title.append(parser.text.orEmpty().take(256 - title.length))
                    "creator" -> if (author.length < 256) author.append(parser.text.orEmpty().take(256 - author.length))
                }
                XmlPullParser.END_TAG -> if (parser.name == capture) capture = null
            }
        }
        if (!isPackage || spine.isEmpty()) fail(LibraryFailure.INVALID_DOCUMENT)
        spine.forEach { id ->
            val item = manifest[id] ?: fail(LibraryFailure.INVALID_DOCUMENT)
            if (item.type !in setOf("application/xhtml+xml", "text/html", "image/svg+xml") || (zip.getEntry(item.href)?.size ?: 0) <= 0) {
                fail(LibraryFailure.INVALID_DOCUMENT)
            }
        }
        val coverItem = manifest.values.firstOrNull { "cover-image" in it.properties.split(' ') } ?: manifest[coverId]
        val cover = coverItem?.takeIf { it.type in setOf("image/jpeg", "image/png") }
            ?.let { item ->
                zip.getEntry(item.href)?.takeIf { it.size in 1..5L * 1024 * 1024 }?.let {
                    thumbnail(read(item.href, 5 * 1024 * 1024))
                }
            }
        DocumentMetadata(clean(title.toString()).ifBlank { fallback }, clean(author.toString()).ifBlank { null }, BookFormat.EPUB, coverPng = cover)
    }

    private fun xml(bytes: ByteArray, visit: (XmlPullParser) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(bytes.inputStream(), null)
        var tokens = 0
        while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
            if (++tokens > 100_000 || parser.depth > 64 || parser.attributeCount > 64) fail(LibraryFailure.INVALID_DOCUMENT)
            if (parser.eventType == XmlPullParser.DOCDECL || (parser.eventType == XmlPullParser.ENTITY_REF && parser.text == null)) {
                fail(LibraryFailure.INVALID_DOCUMENT)
            }
            visit(parser)
        }
    }

    private fun safeEntry(name: String) {
        if (name.isBlank() || name.length > 1024 || name.startsWith('/') || '\\' in name || ':' in name ||
            name.split('/').any { it == "." || it == ".." }) fail(LibraryFailure.INVALID_DOCUMENT)
    }

    private fun resolve(parent: String, href: String): String {
        val reference = URI(href)
        if (reference.isAbsolute || reference.rawAuthority != null || reference.rawQuery != null || reference.path.isNullOrBlank()) fail(LibraryFailure.INVALID_DOCUMENT)
        val path = URI(if (parent.isEmpty()) "" else "$parent/").resolve(reference).normalize().path
        safeEntry(path)
        return path
    }

    private fun limited(input: InputStream, limit: Int, checkActive: () -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) fail(LibraryFailure.TOO_LARGE)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun thumbnail(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 16_000_000) return null
        val options = BitmapFactory.Options().apply {
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > 512) inSampleSize = inSampleSize.coerceAtLeast(1) * 2
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try { png(bitmap) } finally { bitmap.recycle() }
    }

    private fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        it.toByteArray()
    }

    private fun readPrefix(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var size = 0
        while (size < length) {
            val count = input.read(bytes, size, length - size)
            if (count < 0) break
            size += count
        }
        return bytes.copyOf(size)
    }

    private fun clean(value: String) = value.replace(Regex("[\\p{Cc}\\s]+"), " ").trim().take(256)
    private fun fail(reason: LibraryFailure): Nothing = throw LibraryException(reason)
}
