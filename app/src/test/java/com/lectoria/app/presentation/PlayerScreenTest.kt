package com.lectoria.app.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lectoria.app.MainActivity
import com.lectoria.app.core.designsystem.LectorIaTheme
import com.lectoria.app.domain.model.*
import com.lectoria.app.domain.narration.*
import com.lectoria.app.presentation.player.*
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
class PlayerScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val voice = SpeechVoice("private-technical-id", "private-technical-id", "es-MX", true, true, 200)
    private fun state() = NarrationState(phase = SpeechPhase.PAUSED,
        book = Book(BookId("synthetic"), "Lectura y memoria", "Autora sintética", BookFormat.TXT, "0".repeat(64), "/synthetic", Instant.EPOCH, 20),
        sectionCount = 12, fragmentCount = 8, hasNext = true, hasPrevious = true,
        catalog = VoiceCatalog("engine", listOf(SpeechEngineInfo("engine", "Voz del teléfono")), listOf(voice)),
        settings = SpeechSettings("engine", voice.id))

    @Test fun `main controls have explicit labels and trigger their respective actions`() {
        var played = false
        var next = false
        compose.activity.setContent { LectorIaTheme { PlayerScreen(state(), PlayerActions(play = { played = true }, next = { next = true })) } }
        compose.onNodeWithTag("speech_play").assertIsDisplayed().performClick()
        assertTrue(played)
        compose.onNodeWithContentDescription("Parte siguiente").performClick()
        assertTrue(next)
        compose.onNodeWithText("Lectura y memoria").assertIsDisplayed()
        val output = File("build/reports/screenshots/player.png").apply { parentFile!!.mkdirs() }
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try { output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
    }

    @Test fun `voice labels explain language and country while hiding technical engine IDs`() {
        var selected: String? = null
        compose.activity.setContent { LectorIaTheme { PlayerScreen(state(), PlayerActions(voice = { selected = it })) } }
        compose.onNodeWithTag("change_voice").performScrollTo().performClick()
        compose.onAllNodesWithText("Español · México").onLast().performClick()
        assertEquals(voice.id, selected)
        compose.onNodeWithText(voice.id).assertDoesNotExist()
        assertTrue(engineFailureText(SpeechFailure.ENGINE, -5).contains("salida de audio"))
    }
}
