package com.lectoria.app.engine.narration

import com.lectoria.app.domain.narration.SpeechDelivery
import java.text.BreakIterator
import java.util.Locale

internal data class SpeechProsody(val pitch: Float, val rate: Float, val pauseMs: Long)

internal fun speechProsody(text: String, rate: Float, delivery: SpeechDelivery): SpeechProsody {
    val trimmed = text.trimEnd()
    val paragraphBreak = text.endsWith("\n\n")
    val ending = trimmed.trimEnd { it in "\"'»”’)]}" }.lastOrNull()
    val pause = when (ending) {
        '?' -> 170L
        '!' -> 210L
        '.', '…' -> 145L
        ';', ':' -> 105L
        ',' -> 45L
        else -> 0L
    }.let { if (paragraphBreak) maxOf(it, 320L) else it }
    return when (delivery) {
        SpeechDelivery.STANDARD -> SpeechProsody(1f, rate, 0)
        SpeechDelivery.NARRATIVE -> SpeechProsody(
            pitch = when (ending) { '?' -> 1.025f; '!' -> 0.99f; else -> 0.98f },
            rate = (rate * 0.94f).coerceIn(0.5f, 2f),
            pauseMs = pause,
        )
        SpeechDelivery.LIVELY -> SpeechProsody(
            pitch = when (ending) { '?' -> 1.055f; '!' -> 1.045f; else -> 1.01f },
            rate = (rate * 1.02f).coerceIn(0.5f, 2f),
            pauseMs = (pause * 0.72f).toLong(),
        )
    }
}

/** Splits by locale and punctuation without altering spoken text or source offsets. */
internal fun deliveryParts(text: String, delivery: SpeechDelivery, languageTag: String = "es"): List<String> {
    if (delivery == SpeechDelivery.STANDARD) return speechParts(text)
    val locale = Locale.forLanguageTag(languageTag).takeUnless { it.language.isBlank() } ?: Locale.forLanguageTag("es")
    val boundaries = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
    val result = mutableListOf<String>()
    var start = boundaries.first()
    var end = boundaries.next()
    while (end != BreakIterator.DONE) {
        result += prosodicParts(text.substring(start, end))
        start = end
        end = boundaries.next()
    }
    return result
}

private fun prosodicParts(text: String): List<String> {
    val result = mutableListOf<String>()
    var offset = 0
    while (offset < text.length) {
        val pauseBoundary = (offset + 28 until minOf(offset + 350, text.length))
            .firstOrNull { text[it] in ",;:" }
        val end = if (pauseBoundary != null) {
            pauseBoundary + 1
        } else {
            offset + speechParts(text.substring(offset)).first().length
        }
        result += text.substring(offset, end)
        offset = end
    }
    return result
}
