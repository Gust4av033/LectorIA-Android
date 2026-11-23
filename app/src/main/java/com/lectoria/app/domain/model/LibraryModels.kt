package com.lectoria.app.domain.model

enum class LibraryOrder { RECENT, TITLE }

enum class LibraryFailure {
    ACCESS, EMPTY, TOO_LARGE, UNSUPPORTED, FORMAT_MISMATCH, INVALID_DOCUMENT,
    PROTECTED_DOCUMENT, UNSUPPORTED_ENCODING, STORAGE, INTERRUPTED, MISSING_FILE,
}

class LibraryException(val reason: LibraryFailure, cause: Throwable? = null) : Exception(reason.name, cause)

sealed interface ImportOutcome {
    data class Imported(val book: Book) : ImportOutcome
    data class AlreadyPresent(val book: Book) : ImportOutcome
}

/** Interrupted staging/deletion is visible instead of being silently discarded. */
data class LibraryIssue(val id: BookId, val title: String, val reason: LibraryFailure, val canDiscard: Boolean)

data class LibraryContent(val books: List<Book>, val issues: List<LibraryIssue>)
