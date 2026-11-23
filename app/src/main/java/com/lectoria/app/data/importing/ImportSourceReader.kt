package com.lectoria.app.data.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.lectoria.app.domain.model.LibraryException
import com.lectoria.app.domain.model.LibraryFailure
import com.lectoria.app.domain.repository.DocumentSource
import java.io.InputStream

data class SourceDescriptor(val name: String, val mimeType: String?, val sizeHint: Long?)

interface ImportSourceReader {
    fun describe(source: DocumentSource): SourceDescriptor
    fun open(source: DocumentSource): InputStream
}

class SafSourceReader(private val resolver: ContentResolver) : ImportSourceReader {
    private fun uri(source: DocumentSource): Uri = source.value.toUri().also {
        if (it.scheme != ContentResolver.SCHEME_CONTENT) throw LibraryException(LibraryFailure.ACCESS)
    }

    override fun describe(source: DocumentSource): SourceDescriptor {
        val uri = uri(source)
        var name = "Documento"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
            }
        }
        return SourceDescriptor(name.substringAfterLast('/').substringAfterLast('\\').take(256), resolver.getType(uri), size)
    }

    override fun open(source: DocumentSource): InputStream = resolver.openInputStream(uri(source))
        ?: throw LibraryException(LibraryFailure.ACCESS)
}
