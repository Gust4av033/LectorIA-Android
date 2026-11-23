package com.lectoria.app.engine.document

import java.io.PushbackReader
import java.io.Reader

/** Bounded units shared by visual reading and future narration. Offsets are UTF-16 characters. */
internal object TextPagination {
    const val CHARS = 1600
    const val MAX_SECTION_CHARS = 2_000_000

    fun read(reader: PushbackReader): String? {
        val buffer = CharArray(CHARS + 1)
        var count = 0
        while (count < buffer.size) {
            val read = reader.read(buffer, count, buffer.size - count)
            if (read < 0) break
            count += read
        }
        if (count == 0) return null
        var end = minOf(count, CHARS)
        if (count > CHARS) {
            val whitespace = (end - 1 downTo CHARS / 2).firstOrNull { buffer[it].isWhitespace() }
            if (whitespace != null) end = whitespace + 1
            if (Character.isHighSurrogate(buffer[end - 1])) end--
        }
        if (end < count) reader.unread(buffer, end, count - end)
        return String(buffer, 0, end)
    }

    fun offsets(text: String): List<Int> {
        val starts = mutableListOf(0)
        PushbackReader(text.reader(), CHARS + 1).use { reader ->
            var offset = 0
            while (true) {
                val chunk = read(reader) ?: break
                offset += chunk.length
                if (offset < text.length) starts += offset
            }
        }
        return starts
    }

    fun index(starts: List<Int>, offset: Int): Int = starts.binarySearch(offset).let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }

    fun skip(reader: Reader, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = reader.skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (reader.read() == -1) throw java.io.EOFException() else remaining--
        }
    }
}
