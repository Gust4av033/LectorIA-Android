package com.lectoria.app.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lectoria.app.MainActivity
import com.lectoria.app.core.designsystem.LectorIaTheme
import com.lectoria.app.domain.document.*
import com.lectoria.app.domain.model.*
import com.lectoria.app.presentation.reader.*
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "es-rGT-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun `reader shows narrated text and offers pause without restarting the book`() {
        val text = "Afuera, la ciudad guardaba silencio.\n\n¿Quién llamaba a esas horas? La respuesta esperaba al otro lado de la puerta.\n\nRespiró despacio y dio un paso al frente."
        val start = text.indexOf("¿Quién")
        val book = Book(BookId("synthetic"), "Al otro lado de la puerta", "Autora de prueba", BookFormat.TXT, "0".repeat(64), "/synthetic", Instant.EPOCH, text.length.toLong())
        val page = DocumentPage(ReadingLocator.Text(0), 0, text, null, null,
            TextChunk(text, TextLocator("text", 0), TextLocator("text", text.length.toLong())))
        var paused = false
        var followed = false
        var restarted = false
        compose.activity.setContent { LectorIaTheme { ReaderScreen(ReaderUiState(book = book, sections = listOf(DocumentSection("text", "Capítulo 1", ReadingLocator.Text(0))),
            page = page, busy = false, highlight = ReaderHighlight(start, start + 26), hasNarration = true, voicePlaying = true),
            {}, {}, {}, {}, { restarted = true }, { followed = true }, { paused = true }) } }
        compose.onNodeWithTag("reader_text").assertIsDisplayed()
        compose.onNodeWithContentDescription("Texto narrado: ${text.substring(start, start + 26)}").assertExists()
        val output = File("build/reports/screenshots/reader-synchronized.png").apply { parentFile!!.mkdirs() }
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try { output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        compose.onNodeWithText("Pausar voz").performClick()
        assertTrue(paused)
        assertFalse(restarted)
        compose.onNodeWithTag("reader_follow_voice").performClick()
        assertTrue(followed)
        compose.waitForIdle()
    }
}
