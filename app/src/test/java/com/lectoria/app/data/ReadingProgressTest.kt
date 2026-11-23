package com.lectoria.app.data

import android.content.Context
import androidx.room.Room
import com.lectoria.app.data.local.BookEntity
import com.lectoria.app.data.local.LibraryDatabase
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.ReadingProgress
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ReadingProgressTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val name = "progress-${UUID.randomUUID()}.db"
    private var database: LibraryDatabase? = null
    private fun open(): LibraryDatabase = Room.databaseBuilder(context, LibraryDatabase::class.java, name)
        .addMigrations(LibraryDatabase.MIGRATION_1_2).build().also { database = it }
    @After fun cleanup() { database?.close(); context.deleteDatabase(name) }

    @Test fun `v1 migration preserves books and enables persistent format specific progress`() = runBlocking {
        val schema = JSONObject(File("schemas/com.lectoria.app.data.local.LibraryDatabase/1.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entity = schema.getJSONArray("entities").getJSONObject(0)
            sqlite.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", "books"))
            val indices = entity.getJSONArray("indices")
            for (index in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(index).getString("createSql").replace("$" + "{TABLE_NAME}", "books"))
            sqlite.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqlite.execSQL("INSERT INTO room_master_table VALUES(42, ?)", arrayOf(schema.getString("identityHash")))
            sqlite.execSQL("INSERT INTO books (id,title,sizeBytes,createdAt,state,hasCover,format) VALUES ('legacy','Mi libro',42,0,'READY',0,'TXT')")
            sqlite.version = 1
        }
        var db = open()
        assertEquals("Mi libro", db.books().find("legacy")!!.title)
        val saved = ReadingProgress(BookId("legacy"), ReadingLocator.Text(2345), Instant.ofEpochMilli(1234))
        RoomReadingProgressRepository(db).save(saved)
        db.close()
        db = open()
        assertEquals(saved, RoomReadingProgressRepository(db).load(BookId("legacy")))
        assertEquals(1, db.books().all().size)
    }

    @Test fun `all locators roundtrip and deleting one book only cascades its own progress`() = runBlocking {
        val db = open()
        val repository = RoomReadingProgressRepository(db)
        val locations = listOf(ReadingLocator.Text(987654), ReadingLocator.Pdf(12, 320), ReadingLocator.Epub("""{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"lectorTextOffset":123}}"""))
        val formats = listOf(BookFormat.TXT, BookFormat.PDF, BookFormat.EPUB)
        locations.forEachIndexed { index, locator ->
            val id = BookId("book_$index")
            db.books().insert(BookEntity(id.value, "Libro", format = formats[index].name, createdAt = 0, state = "READY"))
            val saved = ReadingProgress(id, locator, Instant.ofEpochMilli(index.toLong()))
            repository.save(saved)
            assertEquals(saved, repository.load(id))
        }
        assertEquals(BookId("book_2"), repository.lastBook())
        db.books().delete("book_1")
        assertNull(repository.load(BookId("book_1")))
        assertNotNull(repository.load(BookId("book_0")))
        assertNotNull(repository.load(BookId("book_2")))
    }

    @Test fun `stale updates cannot replace newer progress and unavailable books cannot be saved`() = runBlocking {
        val db = open()
        val repository = RoomReadingProgressRepository(db)
        val entity = BookEntity("book", "Libro", format = "TXT", createdAt = 0, state = "READY")
        db.books().insert(entity)
        val latest = ReadingProgress(BookId("book"), ReadingLocator.Text(500), Instant.ofEpochMilli(100))
        repository.save(latest)
        try { repository.save(latest.copy(locator = ReadingLocator.Text(0), updatedAt = Instant.EPOCH)); fail("Stale update") } catch (_: java.io.IOException) { }
        assertEquals(latest, repository.load(latest.bookId))
        db.books().update(entity.copy(state = "UNAVAILABLE"))
        assertNull(repository.lastBook())
        try { repository.save(latest); fail("Unavailable book") } catch (_: java.io.IOException) { }
        assertEquals(latest, repository.load(latest.bookId))
    }

    @Test fun `unknown or corrupt locators fail without silently replacing the saved value`() {
        listOf("{}", """{"version":2,"type":"txt","offset":0}""", """{"version":1,"type":"pdf","page":-1,"offset":0}""").forEach { value ->
            assertTrue(runCatching { LocatorCodec.decode(value) }.isFailure)
        }
    }
}
