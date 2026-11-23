package com.lectoria.app.data

import androidx.room.withTransaction
import com.lectoria.app.data.local.ImportState
import com.lectoria.app.data.local.LibraryDatabase
import com.lectoria.app.data.local.ReadingProgressEntity
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.ReadingProgress
import com.lectoria.app.domain.repository.ReadingProgressRepository
import java.io.IOException
import java.time.Instant
import org.json.JSONObject

class RoomReadingProgressRepository(private val database: LibraryDatabase) : ReadingProgressRepository {
    override suspend fun load(bookId: BookId): ReadingProgress? = database.progress().find(bookId.value)?.let {
        ReadingProgress(bookId, LocatorCodec.decode(it.locator), Instant.ofEpochMilli(it.updatedAt))
    }

    override suspend fun lastBook(): BookId? = database.progress().lastBook()?.let(::BookId)

    override suspend fun save(progress: ReadingProgress) = database.withTransaction {
        val book = database.books().find(progress.bookId.value) ?: throw IOException("Book no longer exists")
        if (book.state != ImportState.READY.name) throw IOException("Book unavailable")
        val format = when (progress.locator) {
            is ReadingLocator.Epub -> BookFormat.EPUB
            is ReadingLocator.Pdf -> BookFormat.PDF
            is ReadingLocator.Text -> BookFormat.TXT
        }
        require(book.format == format.name)
        val previous = database.progress().find(progress.bookId.value)
        if (previous != null && progress.updatedAt.toEpochMilli() < previous.updatedAt) throw IOException("Outdated progress update")
        database.progress().save(ReadingProgressEntity(book.id, LocatorCodec.encode(progress.locator), progress.updatedAt.toEpochMilli()))
    }
}

internal object LocatorCodec {
    fun encode(locator: ReadingLocator): String = JSONObject().apply {
        put("version", 1)
        when (locator) {
            is ReadingLocator.Epub -> { JSONObject(locator.locatorJson); put("type", "epub"); put("value", locator.locatorJson) }
            is ReadingLocator.Pdf -> { put("type", "pdf"); put("page", locator.pageIndex); put("offset", locator.characterOffset) }
            is ReadingLocator.Text -> { put("type", "txt"); put("offset", locator.characterOffset) }
        }
    }.toString().also { require(it.length <= 65536) }

    fun decode(value: String): ReadingLocator {
        require(value.length <= 65536)
        val json = JSONObject(value)
        require(json.getInt("version") == 1)
        return when (json.getString("type")) {
            "epub" -> ReadingLocator.Epub(json.getString("value").also { JSONObject(it) })
            "pdf" -> ReadingLocator.Pdf(json.getInt("page"), json.getInt("offset"))
            "txt" -> ReadingLocator.Text(json.getLong("offset"))
            else -> throw IOException("Unsupported locator")
        }
    }
}
