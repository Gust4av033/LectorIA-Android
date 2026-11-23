package com.lectoria.app.engine.document

import android.content.Context
import com.lectoria.app.domain.document.DocumentReader
import com.lectoria.app.domain.document.DocumentSession
import com.lectoria.app.domain.document.ReaderException
import com.lectoria.app.domain.document.ReaderFailure
import com.lectoria.app.domain.model.Book
import com.lectoria.app.domain.model.BookFormat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class AndroidDocumentReader(context: Context) : DocumentReader {
    // Imported EPUBs resolve only their private archive resources. Remote requests are rejected.
    private val httpClient = object : HttpClient {
        override suspend fun stream(request: org.readium.r2.shared.util.http.HttpRequest) =
            Try.failure(HttpError.IO(java.io.IOException("Offline reading only")))
    }
    private val retriever = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(DefaultPublicationParser(context, httpClient, retriever, pdfFactory = null))

    init { PDFBoxResourceLoader.init(context.applicationContext) }

    override suspend fun open(book: Book): Result<DocumentSession> {
        var opened: DocumentSession? = null
        return try { withContext(Dispatchers.IO) {
            val file = File(book.localPath)
            if (!book.available || !file.isFile || file.length() != book.sizeBytes) throw ReaderException(ReaderFailure.OPEN)
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            if (digest.digest().joinToString("") { "%02x".format(it) } != book.contentHash) throw ReaderException(ReaderFailure.OPEN)
            val session = when (book.format) {
                BookFormat.TXT -> TxtDocumentSession.open(file, book.textEncoding ?: "UTF-8")
                BookFormat.PDF -> PdfDocumentSession(file)
                BookFormat.EPUB -> {
                    val asset = retriever.retrieve(file).getOrElse { throw ReaderException(ReaderFailure.OPEN) }
                    try {
                        val publication = opener.open(asset, allowUserInteraction = false).getOrElse { throw ReaderException(ReaderFailure.OPEN) }
                        try { EpubDocumentSession(publication) } catch (error: Exception) { publication.close(); throw error }
                    } catch (error: Exception) { asset.close(); throw error }
                }
            }
            opened = session
            Result.success(session)
        } } catch (cancelled: CancellationException) { opened?.close(); throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
}
