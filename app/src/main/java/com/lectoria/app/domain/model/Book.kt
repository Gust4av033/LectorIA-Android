package com.lectoria.app.domain.model

import java.time.Instant

@JvmInline
value class BookId(val value: String) {
    init { require(value.isNotBlank()) { "A book identifier cannot be blank" } }
}

enum class BookFormat { EPUB, PDF, TXT }

/** Metadata for a verified, privately stored book; import staging belongs to the data layer. */
data class Book(
    val id: BookId,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val contentHash: String,
    val localPath: String,
    val createdAt: Instant,
    val sizeBytes: Long,
    val coverPath: String? = null,
    val textEncoding: String? = null,
    val pageCount: Int? = null,
    val available: Boolean = true,
) {
    init {
        require(title.isNotBlank()) { "A book needs a title" }
        require(contentHash.matches(Regex("[a-f0-9]{64}"))) { "Expected a SHA-256 content hash" }
        require(localPath.isNotBlank()) { "A verified book needs a local path" }
        require(sizeBytes > 0)
        require(pageCount == null || pageCount > 0)
    }
}
