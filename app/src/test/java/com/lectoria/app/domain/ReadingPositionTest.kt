package com.lectoria.app.domain

import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.MediaPositionMs
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.TextLocator
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingPositionTest {
    @Test
    fun `zero is a valid beginning for text pages and media`() {
        assertEquals(0L, ReadingLocator.Text(0).characterOffset)
        assertEquals(0, ReadingLocator.Pdf(0).pageIndex)
        assertEquals(0L, MediaPositionMs(0).value)
    }

    @Test
    fun `invalid negative positions cannot reach persistence or playback`() {
        assertThrows(IllegalArgumentException::class.java) { ReadingLocator.Text(-1) }
        assertThrows(IllegalArgumentException::class.java) { ReadingLocator.Pdf(-1) }
        assertThrows(IllegalArgumentException::class.java) { MediaPositionMs(-1) }
        assertThrows(IllegalArgumentException::class.java) { TextLocator("chapter", -1) }
    }

    @Test
    fun `locators must retain the document section or EPUB location`() {
        assertThrows(IllegalArgumentException::class.java) { TextLocator(" ", 0) }
        assertThrows(IllegalArgumentException::class.java) { ReadingLocator.Epub("") }
        val locator = """{"href":"chapter-2.xhtml","locations":{"progression":0.3}}"""
        assertEquals(locator, ReadingLocator.Epub(locator).locatorJson)
    }

    @Test
    fun `verified book requires identity a content hash and local file`() {
        assertThrows(IllegalArgumentException::class.java) { BookId(" ") }
        val book = Book(BookId("test-book"), "Texto de prueba", null, BookFormat.TXT,
            "a".repeat(64), "/private/test.txt", Instant.EPOCH)
        assertThrows(IllegalArgumentException::class.java) { book.copy(contentHash = "partial") }
        assertThrows(IllegalArgumentException::class.java) { book.copy(localPath = "") }
        assertThrows(IllegalArgumentException::class.java) { book.copy(title = " ") }
    }
}
