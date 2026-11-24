package com.lectoria.app.presentation.reader

import com.lectoria.app.domain.document.DocumentPage
import com.lectoria.app.domain.model.TextLocator
import com.lectoria.app.domain.narration.SpokenRange

/** UTF-16 positions in the displayed text, excluding its optional leading BOM. */
data class ReaderHighlight(val start: Int, val end: Int)

internal fun containsSpokenStart(page: DocumentPage?, start: TextLocator): Boolean {
    val chunk = page?.chunk ?: return false
    return start.sectionId == chunk.start.sectionId && start.characterOffset >= chunk.start.characterOffset &&
        start.characterOffset < chunk.end.characterOffset
}

internal fun readerHighlight(page: DocumentPage?, range: SpokenRange?): ReaderHighlight? {
    val chunk = page?.chunk ?: return null
    if (range == null || range.start.sectionId != chunk.start.sectionId || range.end.sectionId != chunk.start.sectionId) return null
    val bom = if (page.text.startsWith('\uFEFF')) 1 else 0
    val text = page.text.removePrefix("\uFEFF")
    var start = (range.start.characterOffset - chunk.start.characterOffset - bom).coerceIn(0, text.length.toLong()).toInt()
    var end = (range.end.characterOffset - chunk.start.characterOffset - bom).coerceIn(0, text.length.toLong()).toInt()
    if (start >= end) return null
    if (start > 0 && Character.isLowSurrogate(text[start])) start--
    if (end < text.length && Character.isLowSurrogate(text[end])) end++
    return ReaderHighlight(start, end)
}
