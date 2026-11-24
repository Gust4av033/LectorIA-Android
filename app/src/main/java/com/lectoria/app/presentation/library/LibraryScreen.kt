package com.lectoria.app.presentation.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lectoria.app.R
import com.lectoria.app.core.designsystem.Midnight
import com.lectoria.app.core.designsystem.LectorButton
import com.lectoria.app.core.designsystem.LectorTextButton
import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.LibraryFailure
import com.lectoria.app.domain.model.LibraryOrder
import com.lectoria.app.domain.repository.DocumentSource
import com.lectoria.app.presentation.screens.BrandHeader
import com.lectoria.app.presentation.screens.Eyebrow
import com.lectoria.app.presentation.screens.GlassCard
import com.lectoria.app.presentation.screens.IconBadge
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryRoute(viewModel: LibraryViewModel = hiltViewModel(), onOpenBook: (String) -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contract = remember { OpenLibraryDocument() }
    val picker = rememberLauncherForActivityResult(contract) { uri ->
        uri?.let { viewModel.importDocument(DocumentSource(it.toString())) }
    }
    LibraryScreen(state, viewModel, onOpenBook = onOpenBook, onImport = {
        try {
            picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/octet-stream", "application/zip"))
        } catch (_: ActivityNotFoundException) { viewModel.pickerUnavailable() }
        catch (_: SecurityException) { viewModel.pickerUnavailable() }
    })
}

@Composable
private fun LibraryScreen(state: LibraryUiState, viewModel: LibraryViewModel, onImport: () -> Unit, onOpenBook: (String) -> Unit) {
    Box(Modifier.fillMaxSize().testTag("screen_library")) {
        LazyColumn(
            modifier = Modifier.testTag("library_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "brand") { BrandHeader() }
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow(R.string.your_library)
                    Text(stringResource(if (state.books.isEmpty()) R.string.library_headline else R.string.library_filled_headline),
                        style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.library_intro), style = MaterialTheme.typography.bodyLarge, color = Midnight.MutedText)
                }
            }
            item(key = "import") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LectorButton(onClick = onImport, enabled = !state.loading && !state.busy && !state.loadFailed,
                        modifier = Modifier.fillMaxWidth().testTag("import_book")) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.padding(end = 10.dp).size(20.dp))
                        Text(stringResource(R.string.import_book), Modifier.padding(vertical = 8.dp))
                    }
                    Text(stringResource(R.string.import_formats), style = MaterialTheme.typography.bodySmall, color = Midnight.MutedText)
                }
            }
            if (state.loading || state.busy) item(key = "progress") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp).testTag("library_loading"), color = Midnight.Highlight, strokeWidth = 2.dp)
                    Text(stringResource(when {
                        state.loading -> R.string.loading_library
                        state.operation == LibraryOperation.DELETING -> R.string.delete_working
                        else -> R.string.import_working
                    }), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.message?.let { message -> item(key = "message") {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(messageText(message), Modifier.testTag("library_notice"), color = if (message is LibraryMessage.Problem) MaterialTheme.colorScheme.error else Midnight.Highlight)
                        LectorTextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.close)) }
                    }
                }
            } }
            if (state.loadFailed) item(key = "retry") {
                LectorButton(onClick = viewModel::reload, modifier = Modifier.testTag("retry_library")) { Text(stringResource(R.string.retry)) }
            }
            items(state.issues, key = { "issue_${it.id.value}" }) { issue ->
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(issue.title, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(failureText(issue.reason)), color = Midnight.MutedText)
                        LectorTextButton(enabled = !state.busy, onClick = {
                            if (issue.canDiscard) viewModel.requestDiscard(issue) else viewModel.reload()
                        }) { Text(stringResource(if (issue.canDiscard) R.string.discard_import else R.string.retry)) }
                    }
                }
            }
            if (!state.loading && !state.loadFailed && state.books.isEmpty()) item(key = "empty") {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        IconBadge(R.drawable.ic_library)
                        Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.library_empty_description), style = MaterialTheme.typography.bodyMedium, color = Midnight.MutedText)
                    }
                }
            }
            if (state.books.isNotEmpty()) {
                item(key = "search") {
                    OutlinedTextField(state.query, viewModel::query, modifier = Modifier.fillMaxWidth().testTag("search_books"),
                        label = { Text(stringResource(R.string.search_books)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Midnight.Highlight, cursorColor = Midnight.Highlight, focusedBorderColor = Midnight.Highlight))
                }
                item(key = "order") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (state.query.isBlank()) pluralStringResource(R.plurals.book_count, state.books.size, state.books.size)
                            else pluralStringResource(R.plurals.book_filtered_count, state.books.size, state.visibleBooks.size, state.books.size),
                            modifier = Modifier.testTag("book_count"), style = MaterialTheme.typography.labelLarge)
                        OrderMenu(state.order, viewModel::order)
                    }
                }
                if (state.visibleBooks.isEmpty()) item(key = "no_results") { Text(stringResource(R.string.no_results), color = Midnight.MutedText) }
                if (state.visibleBooks.isNotEmpty()) item(key = "collection_label") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("TU COLECCIÓN", style = MaterialTheme.typography.labelSmall, color = Midnight.Highlight)
                    }
                }
                items(state.visibleBooks, key = { it.id.value }) { book ->
                    Surface(onClick = { viewModel.select(book.id) }, enabled = !state.busy, shape = RoundedCornerShape(20.dp),
                        color = Midnight.Surface,
                        border = BorderStroke(1.dp, Midnight.Highlight.copy(alpha = 0.14f)),
                        modifier = Modifier.fillMaxWidth().testTag("book_${book.id.value}")) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BookCover(book)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                book.author?.let { Text(it, color = Midnight.MutedText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Surface(shape = RoundedCornerShape(50), color = Midnight.Container.copy(alpha = 0.45f)) {
                                    Text("${book.format.name} · ${sizeLabel(book.sizeBytes)}",
                                        Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = Midnight.Highlight, style = MaterialTheme.typography.labelSmall)
                                }
                                if (!book.available) Text(stringResource(R.string.book_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item(key = "privacy") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_device), contentDescription = null, tint = Midnight.MutedText, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.private_library), style = MaterialTheme.typography.bodySmall, color = Midnight.MutedText)
                    }
                }
            }
        }
    }
    if (state.deletion != null) {
        val deletion = state.deletion
        AlertDialog(onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(if (deletion.failedImport) R.string.discard_import else R.string.delete_book)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(deletion.title, fontWeight = FontWeight.SemiBold)
                Text(stringResource(if (deletion.failedImport) R.string.discard_confirmation else R.string.delete_confirmation))
            } },
            confirmButton = { LectorTextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm_delete")) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { LectorTextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.cancel)) } },
        )
    } else state.selectedBook?.let { book ->
        AlertDialog(onDismissRequest = { viewModel.select(null) },
            title = { Text(book.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BookCover(book)
                book.author?.let { Text(stringResource(R.string.book_author, it)) }
                Text(stringResource(R.string.book_format, book.format.name))
                Text(stringResource(R.string.book_size, sizeLabel(book.sizeBytes)))
                book.pageCount?.let { Text(pluralStringResource(R.plurals.page_count, it, it)) }
                book.textEncoding?.let { Text(stringResource(R.string.book_encoding, it)) }
                Text(stringResource(R.string.book_date, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(book.createdAt)))
                Text(stringResource(if (book.available) R.string.book_saved else R.string.book_missing_detail), color = if (book.available) Midnight.Highlight else MaterialTheme.colorScheme.error)
                LectorButton(onClick = { viewModel.select(null); onOpenBook(book.id.value) }, enabled = book.available && !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("open_book")) { Text(stringResource(R.string.reader_open)) }
            } },
            confirmButton = { LectorTextButton(onClick = { viewModel.select(null) }, modifier = Modifier.testTag("close_book_details")) { Text(stringResource(R.string.close)) } },
            dismissButton = { LectorTextButton(onClick = { viewModel.requestDelete(book) }, modifier = Modifier.testTag("request_delete")) { Text(stringResource(R.string.delete_book), color = MaterialTheme.colorScheme.error) } },
        )
    }
}

private class OpenLibraryDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
}

@Composable
private fun BookCover(book: Book) {
    val bitmap by produceState<Bitmap?>(null, book.coverPath) {
        value = book.coverPath?.let { path -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } }
    }
    val image = bitmap
    if (image != null) Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 58.dp, height = 80.dp).clip(RoundedCornerShape(10.dp)))
    else Surface(modifier = Modifier.size(width = 58.dp, height = 80.dp),
        shape = RoundedCornerShape(10.dp), color = Midnight.Container.copy(alpha = 0.3f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_library), contentDescription = null, tint = Midnight.Highlight, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun OrderMenu(order: LibraryOrder, onSelect: (LibraryOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        LectorTextButton(onClick = { expanded = true }, modifier = Modifier.testTag("library_order")) {
            Text(stringResource(if (order == LibraryOrder.RECENT) R.string.order_recent else R.string.order_title))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            LibraryOrder.entries.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(if (option == LibraryOrder.RECENT) R.string.order_recent else R.string.order_title)) },
                    onClick = { expanded = false; onSelect(option) }, modifier = Modifier.testTag("order_${option.name}"))
            }
        }
    }
}

private fun sizeLabel(bytes: Long) = if (bytes < 1024 * 1024) String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))

@Composable
private fun messageText(message: LibraryMessage): String = stringResource(when (message) {
    LibraryMessage.Imported -> R.string.import_success
    LibraryMessage.Duplicate -> R.string.import_duplicate
    LibraryMessage.Deleted -> R.string.delete_success
    is LibraryMessage.Problem -> failureText(message.reason)
})

private fun failureText(reason: LibraryFailure): Int = when (reason) {
    LibraryFailure.ACCESS -> R.string.error_access
    LibraryFailure.EMPTY -> R.string.error_empty
    LibraryFailure.TOO_LARGE -> R.string.error_size
    LibraryFailure.UNSUPPORTED -> R.string.error_format
    LibraryFailure.FORMAT_MISMATCH -> R.string.error_mismatch
    LibraryFailure.INVALID_DOCUMENT -> R.string.error_invalid
    LibraryFailure.PROTECTED_DOCUMENT -> R.string.error_protected
    LibraryFailure.UNSUPPORTED_ENCODING -> R.string.error_encoding
    LibraryFailure.STORAGE -> R.string.error_storage
    LibraryFailure.INTERRUPTED -> R.string.error_interrupted
    LibraryFailure.MISSING_FILE -> R.string.book_missing_detail
}
