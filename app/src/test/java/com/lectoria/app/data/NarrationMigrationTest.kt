package com.lectoria.app.data

import android.content.Context
import androidx.room.Room
import com.lectoria.app.data.local.LibraryDatabase
import com.lectoria.app.domain.model.*
import com.lectoria.app.domain.narration.NarrationBookmark
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [36]) @SQLiteMode(SQLiteMode.Mode.NATIVE)
class NarrationMigrationTest {
    @Test fun `v2 migration keeps books and reading positions and stores separate speech progress`() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val name = "narration-migration.db"
        val schema = JSONObject(File("schemas/com.lectoria.app.data.local.LibraryDatabase/2.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                fun sql(value: String) = value.replace("$" + "{TABLE_NAME}", entity.getString("tableName"))
                db.execSQL(sql(entity.getString("createSql")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) db.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
            }
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, ?)", arrayOf(schema.getString("identityHash")))
            db.execSQL("INSERT INTO books (id,title,sizeBytes,createdAt,state,hasCover,format) VALUES ('legacy','Mi libro',42,0,'READY',0,'TXT')")
            db.execSQL("INSERT INTO reading_progress VALUES ('legacy', ?, 12)", arrayOf(LocatorCodec.encode(ReadingLocator.Text(100))))
            db.version = 2
        }
        try {
            val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_2_3).build()
            try {
                val id = BookId("legacy")
                assertEquals("Mi libro", db.books().find(id.value)!!.title)
                val read = RoomReadingProgressRepository(db)
                val speech = RoomNarrationProgressRepository(db)
                speech.save(NarrationBookmark(id, ReadingLocator.Text(23), Instant.ofEpochMilli(30)))
                assertEquals(ReadingLocator.Text(100), read.load(id)!!.locator)
                assertEquals(ReadingLocator.Text(23), speech.load(id)!!.locator)
                assertEquals(id, speech.lastBook())
                db.books().delete(id.value)
                assertNull(speech.load(id))
                assertNull(read.load(id))
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
