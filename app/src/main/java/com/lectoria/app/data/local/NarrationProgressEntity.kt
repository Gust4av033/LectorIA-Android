package com.lectoria.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "narration_progress", foreignKeys = [ForeignKey(
    entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE,
)])
data class NarrationProgressEntity(@PrimaryKey val bookId: String, val locator: String, val updatedAt: Long, val completed: Boolean)

@Dao
interface NarrationProgressDao {
    @Query("SELECT * FROM narration_progress WHERE bookId = :id")
    suspend fun find(id: String): NarrationProgressEntity?
    @Query("SELECT narration_progress.bookId FROM narration_progress JOIN books ON books.id = narration_progress.bookId WHERE books.state = 'READY' ORDER BY narration_progress.updatedAt DESC, narration_progress.bookId LIMIT 1")
    suspend fun lastBook(): String?
    @Upsert suspend fun save(progress: NarrationProgressEntity)
}
