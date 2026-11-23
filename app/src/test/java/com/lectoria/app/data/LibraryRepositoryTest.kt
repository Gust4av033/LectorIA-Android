package com.lectoria.app.data

import android.content.Context
import androidx.room.Room
import com.lectoria.app.data.importing.ImportSourceReader
import com.lectoria.app.data.importing.LibraryFiles
import com.lectoria.app.data.importing.SourceDescriptor
import com.lectoria.app.data.local.BookEntity
import com.lectoria.app.data.local.ImportState
import com.lectoria.app.data.local.LibraryDatabase
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ImportOutcome
import com.lectoria.app.domain.model.LibraryException
import com.lectoria.app.domain.model.LibraryFailure
import com.lectoria.app.domain.repository.DocumentSource
import com.lectoria.app.engine.document.AndroidDocumentInspector
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LibraryRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var database: LibraryDatabase
    private lateinit var databaseName: String
    private lateinit var files: FaultFiles
    private lateinit var sources: Sources
    private lateinit var repository: RoomBookRepository

    class Sources : ImportSourceReader {
        val contents = mutableMapOf<String, ByteArray>()
        var sizeHint: Long? = null
        var opens = 0
        var interrupted = false
        var cancelled = false
        override fun describe(source: DocumentSource) = SourceDescriptor(source.value, "text/plain", sizeHint)
        override fun open(source: DocumentSource): InputStream {
            opens++
            if (interrupted || cancelled) return object : InputStream() {
                override fun read(): Int {
                    if (cancelled) throw CancellationException("Synthetic interruption")
                    throw IOException("Synthetic provider interruption")
                }
            }
            return checkNotNull(contents[source.value]).inputStream()
        }
    }

    class FaultFiles(root: File, limit: Long = 64L * 1024 * 1024) : LibraryFiles(root, limit) {
        var beforePublish = false
        var afterPublish = false
        var deleteFails = false
        override fun publish(id: String, format: BookFormat) {
            if (beforePublish) throw IOException("Synthetic failure before rename")
            super.publish(id, format)
            if (afterPublish) throw IOException("Synthetic failure after rename")
        }
        override fun delete(id: String) {
            if (deleteFails) throw IOException("Synthetic deletion failure")
            super.delete(id)
        }
    }

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        databaseName = "test-${UUID.randomUUID()}.db"
        database = openDatabase()
        files = FaultFiles(temp.newFolder("library"))
        sources = Sources()
        repository = newRepository()
    }

    private fun openDatabase() = Room.databaseBuilder(context, LibraryDatabase::class.java, databaseName).build()
    private fun newRepository(storage: LibraryFiles = files) = RoomBookRepository(database, storage, sources, AndroidDocumentInspector())
    private fun source(name: String, text: String): DocumentSource {
        sources.contents[name] = text.toByteArray()
        return DocumentSource(name)
    }
    private suspend fun addBook(name: String, text: String) =
        (repository.importDocument(source(name, text)).getOrThrow() as ImportOutcome.Imported).book

    @After fun close() { database.close() }

    @Test fun `import privately copies bytes and persists metadata after closing and reopening Room`() = runBlocking {
        val original = "Contenido sintético con ñ y tildes."
        val book = addBook("Lectura ágil.txt", original)
        assertEquals("Lectura ágil", book.title)
        assertEquals(original, File(book.localPath).readText())
        assertEquals(original.toByteArray().size.toLong(), book.sizeBytes)
        assertEquals("UTF-8", book.textEncoding)
        database.close()
        database = openDatabase()
        repository = newRepository()
        repository.reconcile().getOrThrow()
        val restored = repository.observeBooks().first().single()
        assertEquals(book, restored)
        assertTrue(restored.available)
        assertArrayEquals(original.toByteArray(), sources.contents["Lectura ágil.txt"])
    }

    @Test fun `same bytes under another name create no duplicate record or private copy`() = runBlocking {
        val first = addBook("Uno.txt", "Documento único.")
        val outcome = repository.importDocument(source("Otro nombre.txt", "Documento único.")).getOrThrow()
        assertEquals(first.id, (outcome as ImportOutcome.AlreadyPresent).book.id)
        assertEquals(1, database.books().all().size)
        assertEquals(1, files.root.listFiles()!!.size)
        assertEquals("Documento único.", File(first.localPath).readText())
    }

    @Test fun `duplicate bytes do not bypass validation of a contradictory extension`() = runBlocking {
        val first = addBook("Real.txt", "Texto que ya está guardado.")
        val failure = repository.importDocument(source("Falso.pdf", "Texto que ya está guardado.")).exceptionOrNull() as LibraryException
        assertEquals(LibraryFailure.FORMAT_MISMATCH, failure.reason)
        assertEquals(first.id, repository.observeBooks().first().single().id)
        assertTrue(File(first.localPath).isFile)
    }

    @Test fun `concurrent duplicate requests serialize and produce one READY book`() = runBlocking {
        val source = source("Mismo.txt", "Contenido único concurrente.")
        val outcomes = coroutineScope { (1..3).map { async { repository.importDocument(source).getOrThrow() } }.awaitAll() }
        assertEquals(1, outcomes.count { it is ImportOutcome.Imported })
        assertEquals(2, outcomes.count { it is ImportOutcome.AlreadyPresent })
        assertEquals(1, repository.observeBooks().first().size)
    }

    @Test fun `size hint and streamed size limits do not damage existing books`() = runBlocking {
        val good = addBook("Conservar.txt", "Libro que debe permanecer.")
        val opens = sources.opens
        sources.sizeHint = files.maxBytes + 1
        assertEquals(LibraryFailure.TOO_LARGE, (repository.importDocument(source("Grande.txt", "x")).exceptionOrNull() as LibraryException).reason)
        assertEquals(opens, sources.opens)
        sources.sizeHint = null
        val limited = newRepository(FaultFiles(files.root, 32))
        val failure = limited.importDocument(source("Grande.txt", "a".repeat(1024))).exceptionOrNull() as LibraryException
        assertEquals(LibraryFailure.TOO_LARGE, failure.reason)
        assertEquals("Libro que debe permanecer.", File(good.localPath).readText())
        assertEquals(1, repository.observeBooks().first().size)
    }

    @Test fun `invalid and empty files become discardable failures without losing another book`() = runBlocking {
        val good = addBook("Bueno.txt", "Original conservado.")
        repository.importDocument(source("Vacío.txt", "")).exceptionOrNull().let { assertNotNull(it) }
        repository.importDocument(source("Binario.txt", "\u0000\u0001")).exceptionOrNull().let { assertNotNull(it) }
        val content = repository.observeLibrary().first()
        assertEquals(1, content.books.size)
        assertEquals(2, content.issues.size)
        assertTrue(content.issues.all { it.canDiscard })
        content.issues.forEach { repository.discardFailedImport(it.id).getOrThrow() }
        assertEquals(1, database.books().all().size)
        assertTrue(File(good.localPath).isFile)
    }

    @Test fun `a provider interruption preserves originals and reports a failed import`() = runBlocking {
        val good = addBook("Bueno.txt", "No perder.")
        sources.interrupted = true
        assertTrue(repository.importDocument(source("Cortado.txt", "Otro documento")).isFailure)
        sources.interrupted = false
        newRepository().reconcile().getOrThrow()
        assertEquals(1, repository.observeLibrary().first().issues.size)
        assertEquals("No perder.", File(good.localPath).readText())
    }

    @Test fun `coroutine cancellation is propagated and its staging remains recoverable`() = runBlocking {
        sources.cancelled = true
        try {
            repository.importDocument(source("Cancelado.txt", "x"))
            fail("Cancellation must not be converted into a Result failure")
        } catch (_: CancellationException) { }
        sources.cancelled = false
        val issue = repository.observeLibrary().first().issues.single()
        assertEquals(LibraryFailure.INTERRUPTED, issue.reason)
        assertTrue(issue.canDiscard)
    }

    @Test fun `restart completes VERIFIED imports both before and after atomic publication`() = runBlocking {
        files.beforePublish = true
        assertTrue(repository.importDocument(source("Antes.txt", "Validado antes de publicar.")).isFailure)
        assertEquals(ImportState.VERIFIED.name, database.books().all().single().state)
        files.beforePublish = false
        newRepository().reconcile().getOrThrow()
        assertEquals(1, repository.observeBooks().first().size)
        files.afterPublish = true
        assertTrue(repository.importDocument(source("Después.txt", "Validado después de publicar.")).isFailure)
        files.afterPublish = false
        database.close()
        database = openDatabase()
        repository = newRepository()
        repository.reconcile().getOrThrow()
        assertEquals(2, repository.observeBooks().first().size)
        assertTrue(database.books().all().all { it.state == ImportState.READY.name })
    }

    @Test fun `startup retains interrupted partials and marks missing originals unavailable`() = runBlocking {
        val book = addBook("Registrado.txt", "Copia conocida.")
        val id = UUID.randomUUID().toString()
        database.books().insert(BookEntity(id, "Interrumpido.txt", createdAt = 0))
        files.copy(id, "parcial".byteInputStream()) {}
        File(book.localPath).delete()
        repository.reconcile().getOrThrow()
        val content = repository.observeLibrary().first()
        assertEquals(book.id, content.books.single().id)
        assertFalse(content.books.single().available)
        assertEquals(LibraryFailure.INTERRUPTED, content.issues.single().reason)
        assertEquals("parcial", files.staged(id).readText())
    }

    @Test fun `orphan original is adopted while unknown directories and partial copies are preserved`() = runBlocking {
        val id = UUID.randomUUID().toString()
        files.copy(id, "Original recuperable.".byteInputStream()) {}
        files.publish(id, BookFormat.TXT)
        val unknown = File(files.root, "unknown").apply { mkdirs() }
        File(unknown, "keep.txt").writeText("Conservar")
        val partialId = UUID.randomUUID().toString()
        files.copy(partialId, "parcial".byteInputStream()) {}
        repository.reconcile().getOrThrow()
        assertEquals(BookId(id), repository.observeBooks().first().single().id)
        assertEquals("Conservar", File(unknown, "keep.txt").readText())
        assertEquals("parcial", files.staged(partialId).readText())
        assertEquals(BookId(partialId), repository.observeLibrary().first().issues.single().id)
    }

    @Test fun `confirmed deletion can resume after storage failure and never removes another book`() = runBlocking {
        val first = addBook("Eliminar.txt", "Solo esta copia.")
        val second = addBook("Guardar.txt", "Este otro libro se conserva.")
        files.deleteFails = true
        assertTrue(repository.deleteBook(first.id).isFailure)
        assertEquals(ImportState.DELETING.name, database.books().find(first.id.value)!!.state)
        assertTrue(File(first.localPath).isFile)
        files.deleteFails = false
        repository.reconcile().getOrThrow()
        assertNull(database.books().find(first.id.value))
        assertFalse(File(first.localPath).exists())
        assertEquals(second.id, repository.observeBooks().first().single().id)
        assertEquals("Este otro libro se conserva.", File(second.localPath).readText())
        assertEquals("Solo esta copia.", sources.contents["Eliminar.txt"]!!.toString(Charsets.UTF_8))
    }

    @Test fun `storage paths reject non owned IDs`() {
        assertThrows(IllegalArgumentException::class.java) { files.directory("../outside") }
        assertThrows(IllegalArgumentException::class.java) { files.directory("1-1-1-1-1") }
    }

    @Test fun `missing migration rejects an incompatible database without deleting stored books`() = runBlocking {
        val book = addBook("Conservar.txt", "Debe sobrevivir a un esquema incompatible.")
        database.openHelper.writableDatabase.execSQL("PRAGMA user_version = 2")
        database.close()
        database = openDatabase()
        try {
            database.books().all()
            fail("A missing migration must fail rather than recreate the database")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("migration", ignoreCase = true))
        }
        assertEquals("Debe sobrevivir a un esquema incompatible.", File(book.localPath).readText())
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.rawQuery("SELECT COUNT(*) FROM books", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }
}
