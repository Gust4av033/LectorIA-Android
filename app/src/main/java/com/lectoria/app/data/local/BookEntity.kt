package com.lectoria.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ImportState { COPYING, VERIFIED, READY, UNAVAILABLE, FAILED, DELETING }

@Entity(tableName = "books", indices = [Index(value = ["contentHash"], unique = true)])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val format: String? = null,
    val contentHash: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Long,
    val state: String = ImportState.COPYING.name,
    val hasCover: Boolean = false,
    val textEncoding: String? = null,
    val pageCount: Int? = null,
    val failure: String? = null,
)
