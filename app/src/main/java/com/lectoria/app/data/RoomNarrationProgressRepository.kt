package com.lectoria.app.data

import androidx.room.withTransaction
import com.lectoria.app.data.local.LibraryDatabase
import com.lectoria.app.data.local.NarrationProgressEntity
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.narration.NarrationBookmark
import com.lectoria.app.domain.narration.NarrationProgressRepository
import java.io.IOException
import java.time.Instant

class RoomNarrationProgressRepository(private val database: LibraryDatabase) : NarrationProgressRepository {
    override suspend fun load(bookId: BookId) = database.narration().find(bookId.value)?.let {
        NarrationBookmark(bookId, LocatorCodec.decode(it.locator), Instant.ofEpochMilli(it.updatedAt), it.completed)
    }
    override suspend fun lastBook() = database.narration().lastBook()?.let(::BookId)
    override suspend fun save(bookmark: NarrationBookmark) = database.withTransaction {
        val book = database.books().find(bookmark.bookId.value) ?: throw IOException("Book unavailable")
        if (book.state != "READY") throw IOException("Book unavailable")
        val format = when (bookmark.locator) {
            is ReadingLocator.Text -> BookFormat.TXT
            is ReadingLocator.Pdf -> BookFormat.PDF
            is ReadingLocator.Epub -> BookFormat.EPUB
        }
        require(book.format == format.name)
        val old = database.narration().find(book.id)
        if (old != null && old.updatedAt > bookmark.updatedAt.toEpochMilli()) throw IOException("Outdated bookmark")
        database.narration().save(NarrationProgressEntity(book.id, LocatorCodec.encode(bookmark.locator), bookmark.updatedAt.toEpochMilli(), bookmark.completed))
    }
}
