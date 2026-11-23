package com.lectoria.app.domain.model

import java.time.Instant

/** Preserve format-specific locations; a page number cannot stand in for an EPUB locator. */
sealed interface ReadingLocator {
    data class Epub(val locatorJson: String) : ReadingLocator {
        init { require(locatorJson.isNotBlank()) }
    }

    data class Pdf(val pageIndex: Int) : ReadingLocator {
        init { require(pageIndex >= 0) }
    }

    data class Text(val characterOffset: Long) : ReadingLocator {
        init { require(characterOffset >= 0) }
    }
}

/** Offset in the extracted section text, not a time in generated audio. */
data class TextLocator(val sectionId: String, val characterOffset: Long) {
    init {
        require(sectionId.isNotBlank())
        require(characterOffset >= 0)
    }
}

@JvmInline
value class MediaPositionMs(val value: Long) {
    init { require(value >= 0) }
}

data class ReadingProgress(
    val bookId: BookId,
    val locator: ReadingLocator,
    val updatedAt: Instant,
)
