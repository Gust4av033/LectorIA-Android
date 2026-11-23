package com.lectoria.app.presentation

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.lectoria.app.MainActivity
import com.lectoria.app.support.DocumentFixtures
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowContentResolver

/** Real Activity, Hilt, SAF contract, resolver, validators, Room and ViewModel; only the provider is synthetic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "es-rGT-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LibraryFlowTest {
    @get:Rule val temp = TemporaryFolder()
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var provider: FixtureProvider

    class FixtureProvider : ContentProvider() {
        data class Source(val file: File, val name: String, val mime: String)
        val entries = mutableMapOf<String, Source>()
        override fun onCreate() = true
        override fun getType(uri: Uri) = entries[uri.lastPathSegment]?.mime
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val source = entries[uri.lastPathSegment] ?: throw FileNotFoundException()
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(source.name, source.file.length())) }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (mode != "r") throw SecurityException("Read only fixture")
            return ParcelFileDescriptor.open(entries[uri.lastPathSegment]?.file ?: throw FileNotFoundException(), ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    }

    @Before fun ready() {
        val context = RuntimeEnvironment.getApplication()
        provider = FixtureProvider().apply { attachInfo(context, ProviderInfo().apply { authority = "lectoria.test.documents" }) }
        ShadowContentResolver.registerProviderInternal("lectoria.test.documents", provider)
        waitReady()
    }

    private fun waitReady() = compose.waitUntil(15_000) {
        runCatching { compose.onNodeWithTag("import_book").assertIsEnabled() }.isSuccess
    }

    private fun document(name: String, bytes: ByteArray, mime: String = "text/plain"): Uri {
        val file = temp.newFile().apply { writeBytes(bytes) }
        val id = provider.entries.size.toString()
        provider.entries[id] = FixtureProvider.Source(file, name, mime)
        return Uri.parse("content://lectoria.test.documents/$id")
    }

    private fun pick(uri: Uri?, expectedCount: Int? = null) {
        compose.onNodeWithTag("library_list").performScrollToIndex(0)
        waitReady()
        compose.onNodeWithTag("import_book").performClick()
        val request = checkNotNull(shadowOf(compose.activity).nextStartedActivityForResult)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
        assertTrue(request.intent.hasCategory(Intent.CATEGORY_OPENABLE))
        compose.runOnIdle {
            compose.activity.activityResultRegistry.dispatchResult(request.requestCode,
                if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                uri?.let { Intent().setData(it) })
        }
        waitReady()
        if (expectedCount != null) compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag("book_count").assertTextEquals(if (expectedCount == 1) "1 libro" else "$expectedCount libros") }.isSuccess
        }
    }

    @Test fun `TXT import deletion cancellation and explicit confirmation protect the provider original`() {
        val text = "Contenido sintético: niño y canción."
        val uri = document("Notas ñ.txt", text.toByteArray())
        pick(uri, 1)
        compose.onNodeWithTag("library_list").performScrollToNode(hasText("Notas ñ"))
        compose.onNodeWithText("Notas ñ").performClick()
        compose.onNodeWithText("Codificación: UTF-8").assertExists()
        compose.onNodeWithTag("request_delete").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("request_delete").assertExists()
        assertEquals(1, File(RuntimeEnvironment.getApplication().filesDir, "library").listFiles()!!.size)
        compose.onNodeWithTag("request_delete").performClick()
        compose.onNodeWithTag("confirm_delete").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Tu primera historia\nestá por llegar").assertExists() }.isSuccess }
        assertEquals(text, provider.entries[uri.lastPathSegment]!!.file.readText())
        assertEquals(0, File(RuntimeEnvironment.getApplication().filesDir, "library").listFiles()!!.size)
    }

    @Test fun `cancelled picker does nothing and selecting duplicate bytes keeps a single book`() {
        pick(null)
        compose.onNodeWithText("Tu primera historia\nestá por llegar").assertExists()
        val bytes = "Mismos bytes, un solo documento.".toByteArray()
        pick(document("Primero.txt", bytes), 1)
        pick(document("Otro nombre.txt", bytes), 1)
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("library_notice").assertTextContains("ya está en tu biblioteca", substring = true) }.isSuccess }
        assertEquals(1, File(RuntimeEnvironment.getApplication().filesDir, "library").listFiles()!!.size)
    }

    @Test fun `EPUB provider metadata reaches the persisted library and details`() {
        pick(document("archivo.epub", DocumentFixtures.epub("Lectura & memoria", "María Núñez"), "application/epub+zip"), 1)
        compose.onNodeWithTag("library_list").performScrollToNode(hasText("Lectura & memoria"))
        compose.onNodeWithText("Lectura & memoria").performClick()
        compose.onNodeWithText("Autor: María Núñez").assertExists()
        compose.onNodeWithText("Formato: EPUB").assertExists()
        compose.onNodeWithText("La lectura de páginas y la narración estarán disponibles en las siguientes etapas.").assertExists()
        screenshot("book-details")
        compose.onNodeWithTag("close_book_details").performClick()
        compose.onNodeWithTag("library_list").performScrollToIndex(0)
        screenshot("library-with-import")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("book_count").assertTextEquals("1 libro") }.isSuccess }
    }

    @Test fun `search filters real metadata and the selected order survives recreation`() {
        pick(document("Zeta.txt", "Texto Zeta.".toByteArray()), 1)
        pick(document("Árbol.txt", "Texto Árbol.".toByteArray()), 2)
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("library_order"))
        compose.onNodeWithTag("library_order").performClick()
        compose.onNodeWithTag("order_TITLE").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("library_order").assertTextContains("Título (A–Z)") }.isSuccess }
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("search_books"))
        compose.onNodeWithTag("search_books").performTextInput("Ár")
        compose.onNodeWithTag("book_count").assertTextEquals("1 de 2 libros")
        compose.onNodeWithTag("library_list").performScrollToNode(hasText("Árbol"))
        compose.onNodeWithText("Árbol").assertIsDisplayed()
        compose.onNodeWithText("Zeta").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("library_order").assertTextContains("Título (A–Z)") }.isSuccess }
    }

    private fun screenshot(name: String) {
        val bitmap = compose.runOnIdle {
            val view = if (name == "book-details") checkNotNull(ShadowDialog.getLatestDialog().window).decorView else compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val output = File("build/reports/screenshots/$name.png")
        checkNotNull(output.parentFile).mkdirs()
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
