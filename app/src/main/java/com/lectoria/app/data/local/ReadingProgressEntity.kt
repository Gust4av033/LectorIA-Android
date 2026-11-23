package com.lectoria.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "reading_progress", foreignKeys = [ForeignKey(
    entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE,
)])
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val locator: String,
    val updatedAt: Long,
)

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun find(bookId: String): ReadingProgressEntity?

    @Query("SELECT reading_progress.bookId FROM reading_progress JOIN books ON books.id = reading_progress.bookId WHERE books.state = 'READY' ORDER BY reading_progress.updatedAt DESC, reading_progress.bookId LIMIT 1")
    suspend fun lastBook(): String?

    @Upsert suspend fun save(progress: ReadingProgressEntity)
}
