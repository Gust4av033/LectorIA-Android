package com.lectoria.app.data

import com.lectoria.app.data.importing.ImportSourceReader
import com.lectoria.app.data.importing.LibraryFiles
import com.lectoria.app.data.local.BookEntity
import com.lectoria.app.data.local.ImportState
import com.lectoria.app.data.local.LibraryDatabase
import com.lectoria.app.domain.document.DocumentInspector
import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ImportOutcome
import com.lectoria.app.domain.model.LibraryContent
import com.lectoria.app.domain.model.LibraryException
import com.lectoria.app.domain.model.LibraryFailure
import com.lectoria.app.domain.model.LibraryIssue
import com.lectoria.app.domain.repository.BookRepository
import com.lectoria.app.domain.repository.DocumentSource
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomBookRepository(
    database: LibraryDatabase,
    private val files: LibraryFiles,
    private val sources: ImportSourceReader,
    private val inspector: DocumentInspector,
) : BookRepository {
    private val dao = database.books()
    private val writes = Mutex()

    override fun observeBooks() = observeLibrary().map { it.books }

    override fun observeLibrary() = dao.observeAll().map { rows ->
        LibraryContent(
            books = rows.filter { it.state in setOf(ImportState.READY.name, ImportState.UNAVAILABLE.name) }.map(::book),
            issues = rows.filter { it.state == ImportState.FAILED.name ||
                (it.state in setOf(ImportState.VERIFIED.name, ImportState.DELETING.name) && it.failure != null)
            }.map { LibraryIssue(BookId(it.id), it.title,
                it.failure?.let(LibraryFailure::valueOf) ?: LibraryFailure.INTERRUPTED,
                canDiscard = it.state == ImportState.FAILED.name) },
        )
    }

    override suspend fun find(id: BookId): Book? = withContext(Dispatchers.IO) {
        dao.find(id.value)?.takeIf { it.state in setOf(ImportState.READY.name, ImportState.UNAVAILABLE.name) }?.let(::book)
    }

    override suspend fun importDocument(source: DocumentSource): Result<ImportOutcome> = operation {
        var staging: BookEntity? = null
        try {
            val descriptor = try { sources.describe(source) } catch (error: SecurityException) {
                throw LibraryException(LibraryFailure.ACCESS, error)
            } catch (error: IOException) { throw LibraryException(LibraryFailure.ACCESS, error) }
            if ((descriptor.sizeHint ?: 0) > files.maxBytes) throw LibraryException(LibraryFailure.TOO_LARGE)
            val id = UUID.randomUUID().toString()
            staging = BookEntity(id, descriptor.name.ifBlank { "Documento" }, createdAt = System.currentTimeMillis())
            dao.insert(staging)
            val context = currentCoroutineContext()
            val input = try { sources.open(source) } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw LibraryException(LibraryFailure.ACCESS, error)
            }
            val copied = input.use { files.copy(id, it) { context.ensureActive() } }
            val metadata = inspector.inspect(files.staged(id), descriptor.name, descriptor.mimeType) { context.ensureActive() }
            val existing = dao.findHash(copied.sha256)
            if (existing != null) {
                val format = existing.format?.let(BookFormat::valueOf)
                if (format != null && existing.state in setOf(ImportState.READY.name, ImportState.UNAVAILABLE.name) &&
                    files.matches(files.original(existing.id, format), existing.sizeBytes, copied.sha256) { context.ensureActive() }) {
                    val restored = existing.copy(state = ImportState.READY.name, failure = null)
                    dao.update(restored)
                    files.delete(id)
                    dao.delete(id)
                    return@operation ImportOutcome.AlreadyPresent(book(restored))
                }
                if (existing.state == ImportState.READY.name) dao.update(existing.copy(state = ImportState.UNAVAILABLE.name, failure = LibraryFailure.MISSING_FILE.name))
                throw LibraryException(LibraryFailure.MISSING_FILE)
            }
            files.saveCover(id, metadata.coverPng)
            val verified = staging.copy(title = metadata.title, author = metadata.author, format = metadata.format.name,
                contentHash = copied.sha256, sizeBytes = copied.bytes, state = ImportState.VERIFIED.name,
                hasCover = metadata.coverPng != null, textEncoding = metadata.textEncoding, pageCount = metadata.pageCount)
            dao.update(verified)
            // A crash from here onward can be completed from VERIFIED, before or after the rename.
            files.publish(id, metadata.format)
            val ready = verified.copy(state = ImportState.READY.name)
            dao.update(ready)
            ImportOutcome.Imported(book(ready))
        } catch (error: Exception) {
            staging?.let { record ->
                withContext(NonCancellable) {
                    // Preserve both file and record if the storage itself is failing. Next startup retries.
                    runCatching {
                        dao.find(record.id)?.let { stored ->
                            val reason = if (error is CancellationException) LibraryFailure.INTERRUPTED else reason(error)
                            dao.update(stored.copy(state = if (stored.state == ImportState.COPYING.name) ImportState.FAILED.name else stored.state,
                                failure = reason.name))
                        }
                    }
                }
            }
            throw error
        }
    }

    override suspend fun deleteBook(id: BookId): Result<Unit> = operation {
        val row = dao.find(id.value) ?: return@operation
        require(row.state in setOf(ImportState.READY.name, ImportState.UNAVAILABLE.name, ImportState.DELETING.name))
        val deleting = row.copy(state = ImportState.DELETING.name, failure = null)
        dao.update(deleting) // Durable, already confirmed intent; startup may finish this deletion.
        try {
            files.delete(row.id)
            dao.delete(row.id)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            dao.update(deleting.copy(failure = LibraryFailure.STORAGE.name))
            throw error
        }
    }

    override suspend fun discardFailedImport(id: BookId): Result<Unit> = operation {
        val row = dao.find(id.value) ?: return@operation
        require(row.state == ImportState.FAILED.name)
        files.delete(row.id)
        dao.delete(row.id)
    }

    override suspend fun reconcile(): Result<Unit> = operation {
        val context = currentCoroutineContext()
        dao.all().forEach { row ->
            context.ensureActive()
            try {
                when (ImportState.valueOf(row.state)) {
                    ImportState.COPYING -> dao.update(row.copy(state = ImportState.FAILED.name, failure = LibraryFailure.INTERRUPTED.name))
                    ImportState.VERIFIED -> {
                        val format = BookFormat.valueOf(checkNotNull(row.format))
                        val published = files.original(row.id, format)
                        val candidate = if (published.isFile) published else files.staged(row.id)
                        if (files.matches(candidate, row.sizeBytes, checkNotNull(row.contentHash)) { context.ensureActive() }) {
                            if (!published.isFile) files.publish(row.id, format)
                            dao.update(row.copy(state = ImportState.READY.name, failure = null))
                        } else dao.update(row.copy(state = ImportState.UNAVAILABLE.name, failure = LibraryFailure.MISSING_FILE.name))
                    }
                    ImportState.READY, ImportState.UNAVAILABLE -> {
                        val path = files.original(row.id, BookFormat.valueOf(checkNotNull(row.format)))
                        val available = path.isFile && path.canRead() && path.length() == row.sizeBytes &&
                            (row.state == ImportState.READY.name || files.matches(path, row.sizeBytes, checkNotNull(row.contentHash)) { context.ensureActive() })
                        val next = row.copy(state = if (available) ImportState.READY.name else ImportState.UNAVAILABLE.name,
                            failure = if (available) null else LibraryFailure.MISSING_FILE.name)
                        if (next != row) dao.update(next)
                    }
                    ImportState.DELETING -> { files.delete(row.id); dao.delete(row.id) }
                    ImportState.FAILED -> Unit
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                dao.update(row.copy(failure = reason(error).name))
            }
        }
        // Adopt valid orphan originals. Unknown/non-UUID paths are preserved, never recursively swept.
        files.root.listFiles()?.filter { it.isDirectory }?.forEach { directory ->
            context.ensureActive()
            val id = directory.name
            if (runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false) && dao.find(id) == null) {
                val original = BookFormat.entries.map { files.original(id, it) }.firstOrNull { it.isFile }
                val record = BookEntity(id, "Documento recuperado", createdAt = directory.lastModified())
                if (original == null) dao.insert(record.copy(state = ImportState.FAILED.name, failure = LibraryFailure.INTERRUPTED.name))
                else {
                    try {
                        if (original.length() > files.maxBytes) throw LibraryException(LibraryFailure.TOO_LARGE)
                        val metadata = inspector.inspect(original, "Documento recuperado.${original.extension}", null) { context.ensureActive() }
                        val fingerprint = files.fingerprint(original) { context.ensureActive() }
                        if (dao.findHash(fingerprint.sha256) != null) throw LibraryException(LibraryFailure.INTERRUPTED)
                        files.saveCover(id, metadata.coverPng)
                        dao.insert(record.copy(title = metadata.title, author = metadata.author, format = metadata.format.name,
                            contentHash = fingerprint.sha256, sizeBytes = fingerprint.bytes, state = ImportState.READY.name,
                            hasCover = metadata.coverPng != null, textEncoding = metadata.textEncoding, pageCount = metadata.pageCount))
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        dao.insert(record.copy(state = ImportState.FAILED.name, failure = reason(error).name))
                    }
                }
            }
        }
    }

    private fun book(row: BookEntity) = Book(
        id = BookId(row.id), title = row.title, author = row.author, format = BookFormat.valueOf(checkNotNull(row.format)),
        contentHash = checkNotNull(row.contentHash), localPath = files.original(row.id, BookFormat.valueOf(row.format)).absolutePath,
        createdAt = Instant.ofEpochMilli(row.createdAt), sizeBytes = row.sizeBytes,
        coverPath = files.cover(row.id).takeIf { row.hasCover && it.isFile }?.absolutePath,
        textEncoding = row.textEncoding, pageCount = row.pageCount, available = row.state == ImportState.READY.name,
    )

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(writes.withLock { block() }) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(if (error is LibraryException) error else LibraryException(reason(error), error)) }
    }

    private fun reason(error: Exception) = when (error) {
        is LibraryException -> error.reason
        is SecurityException -> LibraryFailure.ACCESS
        else -> LibraryFailure.STORAGE
    }
}
