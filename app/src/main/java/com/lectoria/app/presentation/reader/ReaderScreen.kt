package com.lectoria.app.presentation.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lectoria.app.R
import com.lectoria.app.core.designsystem.LectorButton
import com.lectoria.app.core.designsystem.LectorTextButton
import com.lectoria.app.core.designsystem.Midnight
import com.lectoria.app.domain.document.ReaderFailure
import com.lectoria.app.domain.model.BookFormat

@Composable
fun ReaderRoute(onLibrary: () -> Unit, onListen: () -> Unit = {}, viewModel: ReaderViewModel = hiltViewModel()) {
    DisposableEffect(viewModel) { viewModel.reload(); onDispose { viewModel.release() } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player: com.lectoria.app.presentation.player.PlayerViewModel = hiltViewModel()
    val listen = { state.book?.let { book -> state.page?.let { page -> player.client.listen(book.id, page.locator); onListen() } }; Unit }
    ReaderScreen(state, viewModel::go, viewModel::retry, viewModel::retrySave, onLibrary, listen)
}

@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    onGo: (com.lectoria.app.domain.model.ReadingLocator) -> Unit,
    onReload: () -> Unit,
    onRetrySave: () -> Unit,
    onLibrary: () -> Unit,
    onListen: () -> Unit,
) {
    var contents by remember { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableIntStateOf(20) }
    var showPdfText by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val page = state.page
    val smallerLabel = stringResource(R.string.reader_smaller)
    val largerLabel = stringResource(R.string.reader_larger)
    LaunchedEffect(page?.locator, showPdfText) { scroll.scrollTo(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("screen_reader"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LectorTextButton(onClick = onLibrary) { Text(stringResource(R.string.library)) }
            if (state.sections.isNotEmpty()) LectorTextButton(onClick = { contents = true }, enabled = !state.busy,
                modifier = Modifier.testTag("reader_sections")) { Text(stringResource(R.string.reader_sections)) }
        }
        Text(state.book?.title ?: stringResource(R.string.reader), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (state.busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Midnight.Highlight, strokeWidth = 2.dp)
            Text(stringResource(R.string.reader_loading))
        }
        state.failure?.let { failure ->
            Text(stringResource(when (failure) {
                ReaderFailure.OPEN -> R.string.reader_error
                ReaderFailure.LOCATION -> R.string.reader_location_error
                ReaderFailure.TOO_LARGE -> R.string.reader_section_large
                ReaderFailure.PROTECTED -> R.string.error_protected
            }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("reader_error"))
            if (page == null) LectorButton(onClick = onReload, enabled = !state.busy) { Text(stringResource(R.string.retry)) }
        }
        if (state.saveFailed) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reader_save_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            LectorTextButton(onClick = onRetrySave, enabled = !state.busy) { Text(stringResource(R.string.retry)) }
        }
        if (page == null && !state.busy && state.failure == null) {
            Text(stringResource(R.string.reader_empty), color = Midnight.MutedText)
            LectorButton(onClick = onLibrary) { Text(stringResource(R.string.reader_choose)) }
        }
        if (page != null) {
            val isPdf = state.book?.format == BookFormat.PDF
            val previous = if (isPdf && !showPdfText) state.sections.getOrNull(page.sectionIndex - 1)?.start else page.previous
            val next = if (isPdf && !showPdfText) state.sections.getOrNull(page.sectionIndex + 1)?.start else page.next
            val location = sectionLabel(state, page.sectionIndex)
            Text(if (!isPdf || showPdfText) "$location · ${stringResource(R.string.reader_fragment_number, page.fragmentIndex + 1, page.fragmentCount)}" else location,
                color = Midnight.Highlight, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("reader_position"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (isPdf) LectorTextButton(onClick = { showPdfText = !showPdfText }, modifier = Modifier.testTag("reader_toggle_text")) {
                    Text(stringResource(if (showPdfText) R.string.reader_show_page else R.string.reader_show_text))
                } else Text(stringResource(R.string.reader_text_mode), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Midnight.MutedText)
                if (!isPdf || showPdfText) {
                    LectorTextButton(onClick = { fontSize -= 2 }, enabled = fontSize > 16,
                        modifier = Modifier.testTag("reader_smaller").semantics { contentDescription = smallerLabel }) { Text("A−") }
                    LectorTextButton(onClick = { fontSize += 2 }, enabled = fontSize < 32,
                        modifier = Modifier.testTag("reader_larger").semantics { contentDescription = largerLabel }) { Text("A+") }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).testTag("reader_content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isPdf && page.text.isBlank()) Text(stringResource(if (state.extractionAllowed) R.string.reader_no_pdf_text else R.string.reader_extraction_denied), color = Midnight.Highlight)
                if (isPdf && !showPdfText) {
                    val bitmap = remember(state.image) { state.image?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.reader_pdf_image, page.sectionIndex + 1), modifier = Modifier.fillMaxWidth())
                    if (state.imageFailed) Text(stringResource(R.string.reader_image_error), color = MaterialTheme.colorScheme.error)
                } else if (page.text.isNotBlank()) SelectionContainer {
                    Text(page.text.removePrefix("\uFEFF"), fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp, modifier = Modifier.testTag("reader_text"))
                } else if (!isPdf) Text(stringResource(R.string.reader_no_section_text), color = Midnight.MutedText)
            }
            if (page.chunk != null) LectorButton(onClick = onListen, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("listen_book")) { Text("Escuchar desde aquí") }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                LectorButton(onClick = { previous?.let(onGo) }, enabled = previous != null && !state.busy,
                    modifier = Modifier.testTag("reader_previous")) { Text(stringResource(R.string.reader_previous)) }
                LectorButton(onClick = { next?.let(onGo) }, enabled = next != null && !state.busy,
                    modifier = Modifier.testTag("reader_next")) { Text(stringResource(R.string.reader_next)) }
            }
        } else Box(Modifier.weight(1f))
    }
    if (contents) AlertDialog(onDismissRequest = { contents = false }, title = { Text(stringResource(R.string.reader_sections)) },
        text = {
            LazyColumn { itemsIndexed(state.sections, key = { _, section -> section.id }) { index, section ->
                LectorTextButton(onClick = { contents = false; onGo(section.start) }, modifier = Modifier.fillMaxWidth().testTag("reader_section_$index")) {
                    Text(sectionLabel(state, index), Modifier.fillMaxWidth())
                }
            } }
        }, confirmButton = { LectorTextButton(onClick = { contents = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun sectionLabel(state: ReaderUiState, index: Int): String = when (state.book?.format) {
    BookFormat.PDF -> stringResource(R.string.reader_page_number, index + 1, state.sections.size)
    else -> state.sections.getOrNull(index)?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.reader_section_number, index + 1, state.sections.size)
}
