package com.lectoria.app.engine.narration

import com.lectoria.app.domain.narration.SpeechDelivery
import java.text.BreakIterator
import java.util.Locale

internal data class SpeechProsody(val pitch: Float, val rate: Float, val pauseMs: Long)

internal fun speechProsody(text: String, rate: Float, delivery: SpeechDelivery): SpeechProsody {
    val ending = text.trimEnd { it.isWhitespace() || it in "\"'»”’)]}" }.lastOrNull()
    return when (delivery) {
        SpeechDelivery.STANDARD -> SpeechProsody(1f, rate, 0)
        SpeechDelivery.NARRATIVE -> SpeechProsody(if (ending == '?') 1.04f else 0.98f, (rate * 0.95f).coerceIn(0.5f, 2f), if (ending in listOf('.', '!', '?')) 180 else 60)
        SpeechDelivery.LIVELY -> SpeechProsody(when (ending) { '?' -> 1.09f; '!' -> 1.06f; else -> 1.01f }, (rate * 1.03f).coerceIn(0.5f, 2f), if (ending in listOf('.', '!', '?')) 90 else 40)
    }
}

/** Spoken text remains unchanged; sentence splitting never changes locators. */
internal fun deliveryParts(text: String, delivery: SpeechDelivery): List<String> {
    if (delivery == SpeechDelivery.STANDARD) return speechParts(text)
    val boundaries = BreakIterator.getSentenceInstance(Locale.forLanguageTag("es")).apply { setText(text) }
    val result = mutableListOf<String>()
    var start = boundaries.first()
    var end = boundaries.next()
    while (end != BreakIterator.DONE) {
        result += speechParts(text.substring(start, end))
        start = end
        end = boundaries.next()
    }
    return result
}
