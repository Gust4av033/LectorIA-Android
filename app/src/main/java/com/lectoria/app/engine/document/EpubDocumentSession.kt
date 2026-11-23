@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.lectoria.app.engine.document

import com.lectoria.app.domain.document.DocumentPage
import com.lectoria.app.domain.document.DocumentSection
import com.lectoria.app.domain.document.DocumentSession
import com.lectoria.app.domain.document.ReaderException
import com.lectoria.app.domain.document.ReaderFailure
import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.model.TextLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content

/** Readium owns the EPUB structure and text parser. Compose presents a reflowed text reading mode. */
internal class EpubDocumentSession(private val publication: Publication) : DocumentSession {
    private val links = publication.readingOrder
    private val locators = links.map { publication.locatorFromLink(it) ?: throw ReaderException(ReaderFailure.OPEN) }
    private var cachedIndex = -1
    private var cachedText = ""
    private var cachedStarts = listOf(0)
    private var closed = false
    override val canExtractText = publication.content() != null

    init { if (links.isEmpty()) throw ReaderException(ReaderFailure.OPEN) }

    private fun flatten(links: List<Link>): List<Link> = links.flatMap { listOf(it) + flatten(it.children) }

    override suspend fun sections(): List<DocumentSection> {
        val titles = flatten(publication.tableOfContents)
            .distinctBy { it.href.toString().substringBefore('#') }
            .associate { it.href.toString().substringBefore('#') to it.title }
        return links.mapIndexed { index, link ->
            DocumentSection(link.href.toString(), titles[link.href.toString().substringBefore('#')] ?: link.title, at(index, 0))
        }
    }

    private fun at(index: Int, offset: Int): ReadingLocator.Epub {
        val locator = locators[index]
        return ReadingLocator.Epub(locator.copy(locations = locator.locations.copy(
            otherLocations = locator.locations.otherLocations + ("lectorTextOffset" to offset),
        )).toJSON().toString())
    }

    private suspend fun load(index: Int) {
        if (cachedIndex == index) return
        val iterator = publication.content(locators[index])?.iterator() ?: throw ReaderException(ReaderFailure.OPEN)
        val text = StringBuilder()
        while (iterator.hasNext()) {
            currentCoroutineContext().ensureActive()
            val element = iterator.next()
            if (element.locator.href.toString().substringBefore('#') != links[index].href.toString().substringBefore('#')) break
            val value = (element as? Content.TextualElement)?.text?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (text.length + value.length + 2 > TextPagination.MAX_SECTION_CHARS) throw ReaderException(ReaderFailure.TOO_LARGE)
            if (text.isNotEmpty()) text.append("\n\n")
            text.append(value)
        }
        cachedText = text.toString()
        cachedStarts = TextPagination.offsets(cachedText)
        cachedIndex = index
    }

    override suspend fun page(locator: ReadingLocator): DocumentPage = withContext(Dispatchers.IO) {
        check(!closed)
        val json = (locator as? ReadingLocator.Epub)?.locatorJson ?: throw ReaderException(ReaderFailure.LOCATION)
        val parsed = Locator.fromJSON(JSONObject(json)) ?: throw ReaderException(ReaderFailure.LOCATION)
        val index = locators.indexOfFirst { it.href == parsed.href }
        if (index < 0) throw ReaderException(ReaderFailure.LOCATION)
        load(index)
        val offset = (parsed.locations.otherLocations["lectorTextOffset"] as? Number)?.toInt() ?: 0
        if (offset < 0 || offset > cachedText.length) throw ReaderException(ReaderFailure.LOCATION)
        val chunkIndex = TextPagination.index(cachedStarts, offset)
        val start = cachedStarts[chunkIndex]
        val end = cachedStarts.getOrElse(chunkIndex + 1) { cachedText.length }
        val text = cachedText.substring(start, end)
        DocumentPage(at(index, start), index, text,
            if (chunkIndex > 0) at(index, cachedStarts[chunkIndex - 1]) else if (index > 0) lastPage(index - 1) else null,
            if (chunkIndex + 1 < cachedStarts.size) at(index, cachedStarts[chunkIndex + 1]) else if (index + 1 < links.size) at(index + 1, 0) else null,
            text.takeIf { it.isNotBlank() }?.let { TextChunk(it, TextLocator(links[index].href.toString(), start.toLong()), TextLocator(links[index].href.toString(), end.toLong())) }, chunkIndex, cachedStarts.size)
    }

    private suspend fun lastPage(index: Int): ReadingLocator.Epub {
        // Read the adjacent resource only for backward navigation; retain a single resource cache.
        val currentIndex = cachedIndex
        val currentText = cachedText
        val currentStarts = cachedStarts
        return try {
            load(index)
            at(index, cachedStarts.last())
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { at(index, 0) }
        finally {
            cachedIndex = currentIndex
            cachedText = currentText
            cachedStarts = currentStarts
        }
    }

    override fun textFrom(locator: ReadingLocator) = flow {
        var next: ReadingLocator? = locator
        while (next != null) {
            val page = page(next)
            page.chunk?.let { emit(it) }
            next = page.next
        }
    }.flowOn(Dispatchers.IO)

    override fun close() { closed = true; cachedText = ""; publication.close() }
}
