package com.lectoria.app.data.importing

import com.lectoria.app.domain.model.BookFormat
import com.lectoria.app.domain.model.LibraryException
import com.lectoria.app.domain.model.LibraryFailure
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class CopiedDocument(val bytes: Long, val sha256: String)

/** All paths are owned UUID directories; provider names and DB paths never choose a deletion target. */
open class LibraryFiles(val root: File, val maxBytes: Long = 64L * 1024 * 1024) {
    fun directory(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(root, id).also { require(it.canonicalFile.parentFile == root.canonicalFile) }
    }

    fun staged(id: String) = File(directory(id), "original.part")
    fun original(id: String, format: BookFormat) = File(directory(id), "original.${format.name.lowercase()}")
    fun cover(id: String) = File(directory(id), "cover.png")

    fun copy(id: String, input: InputStream, checkActive: () -> Unit): CopiedDocument {
        val dir = directory(id)
        if (!dir.isDirectory && !dir.mkdirs()) throw LibraryException(LibraryFailure.STORAGE)
        val hash = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        FileOutputStream(staged(id)).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkActive()
                val count = try { input.read(buffer) } catch (error: IOException) {
                    throw LibraryException(LibraryFailure.ACCESS, error)
                }
                if (count < 0) break
                bytes += count
                if (bytes > maxBytes) throw LibraryException(LibraryFailure.TOO_LARGE)
                hash.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        }
        if (bytes == 0L) throw LibraryException(LibraryFailure.EMPTY)
        return CopiedDocument(bytes, hash.digest().joinToString("") { "%02x".format(it) })
    }

    fun saveCover(id: String, png: ByteArray?) {
        if (png == null) return
        FileOutputStream(cover(id)).use { it.write(png); it.fd.sync() }
    }

    open fun publish(id: String, format: BookFormat) {
        check(!original(id, format).exists())
        Files.move(staged(id).toPath(), original(id, format).toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    fun matches(file: File, bytes: Long, hash: String, checkActive: () -> Unit): Boolean {
        if (!file.isFile || file.length() != bytes) return false
        return fingerprint(file, checkActive).sha256 == hash
    }

    fun fingerprint(file: File, checkActive: () -> Unit): CopiedDocument {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                digest.update(buffer, 0, count)
            }
        }
        return CopiedDocument(bytes, digest.digest().joinToString("") { "%02x".format(it) })
    }

    open fun delete(id: String) {
        val dir = directory(id)
        if (dir.exists() && !dir.deleteRecursively()) throw LibraryException(LibraryFailure.STORAGE)
    }
}
