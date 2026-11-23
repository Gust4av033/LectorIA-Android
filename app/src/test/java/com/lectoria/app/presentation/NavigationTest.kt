package com.lectoria.app.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lectoria.app.MainActivity
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Runs the real Hilt Application/Activity and Compose navigation without a hardware device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "es-rGT-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `starts with an empty persistent library and an enabled import action`() {
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag("import_book").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithTag("screen_library").assertIsDisplayed()
        compose.onNodeWithTag("nav_library").assertIsSelected()
        compose.onNodeWithText("Tu primera historia\nestá por llegar").assertExists()
        compose.onNodeWithTag("import_book").assertIsEnabled()
        val output = File("build/reports/screenshots/library.png")
        checkNotNull(output.parentFile).mkdirs()
        // Draw the native Robolectric view directly; PixelCopy's redraw callback
        // used by captureToImage is not dispatched reliably by its virtual window.
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        output.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
    }

    @Test
    fun `all destinations open and repeated selection returns safely to library`() {
        listOf("reader", "player", "settings", "settings", "library").forEach { route ->
            compose.onNodeWithTag("nav_$route").performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
            compose.onNodeWithTag("nav_$route").assertIsSelected()
            if (route != "library") {
                compose.onNodeWithText("Pendiente de desarrollo").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `system back from a secondary destination returns to library`() {
        compose.onNodeWithTag("nav_player").performClick()
        compose.onNodeWithTag("screen_player").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("screen_library").assertIsDisplayed()
        compose.onNodeWithTag("nav_library").assertIsSelected()
    }

    @Test
    fun `selected destination survives activity recreation`() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").assertIsSelected()
    }
}
