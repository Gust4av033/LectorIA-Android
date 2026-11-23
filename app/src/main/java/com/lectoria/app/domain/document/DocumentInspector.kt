package com.lectoria.app.domain.document

import com.lectoria.app.domain.model.BookFormat
import java.io.File

/** Inspects a private staged file; does not claim to implement the reading engine. */
interface DocumentInspector {
    fun inspect(file: File, name: String, mimeType: String?, checkActive: () -> Unit = {}): DocumentMetadata
}

data class DocumentMetadata(
    val title: String,
    val author: String?,
    val format: BookFormat,
    val coverPng: ByteArray? = null,
    val textEncoding: String? = null,
    val pageCount: Int? = null,
)
