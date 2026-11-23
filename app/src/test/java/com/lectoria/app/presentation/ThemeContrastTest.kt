package com.lectoria.app.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lectoria.app.core.designsystem.Midnight
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun `normal text and status colors meet WCAG AA on reading surfaces`() {
        assertTrue("Filled action text must reach 4.5 to 1", contrast(Midnight.Text, Midnight.Container) >= 4.5f)
        listOf(Midnight.Background, Midnight.Surface).forEach { surface ->
            listOf(Midnight.Text, Midnight.MutedText, Midnight.Highlight).forEach { text ->
                assertTrue("Text $text on $surface must reach 4.5 to 1", contrast(text, surface) >= 4.5f)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
