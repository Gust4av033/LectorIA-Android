package com.lectoria.app.presentation.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lectoria.app.core.designsystem.Midnight

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NarratedText(text: String, fontSize: Int, highlight: ReaderHighlight?, follow: Boolean) {
    var layout by remember(text, fontSize) { mutableStateOf<TextLayoutResult?>(null) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(layout, highlight, follow) {
        val measured = layout ?: return@LaunchedEffect
        if (follow && highlight != null) {
            requester.bringIntoView(measured.getPathForRange(highlight.start, highlight.end).getBounds())
        }
    }
    Text(text, fontSize = fontSize.sp, lineHeight = (fontSize * 1.65f).sp,
        onTextLayout = { layout = it },
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 5.dp).bringIntoViewRequester(requester)
            .testTag("reader_text").semantics {
                if (highlight != null) contentDescription = "Texto narrado: ${text.substring(highlight.start, highlight.end)}"
            }.drawBehind {
                val measured = layout
                if (highlight != null && measured != null) {
                    val first = measured.getLineForOffset(highlight.start)
                    val last = measured.getLineForOffset(highlight.end - 1)
                    for (line in first..last) {
                        val start = maxOf(highlight.start, measured.getLineStart(line))
                        val end = minOf(highlight.end, measured.getLineEnd(line))
                        if (start < end) {
                            val bounds = measured.getPathForRange(start, end).getBounds()
                            val topLeft = Offset(bounds.left - 3.dp.toPx(), bounds.top)
                            val size = Size(bounds.width + 6.dp.toPx(), bounds.height)
                            drawRoundRect(Midnight.Highlight.copy(alpha = 0.12f), topLeft, size, CornerRadius(4.dp.toPx()))
                            drawRoundRect(Midnight.Highlight.copy(alpha = 0.7f), topLeft, size, CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
                        }
                    }
                }
            })
}
