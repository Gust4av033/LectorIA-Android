package com.lectoria.app.engine.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lectoria.app.data.LocatorCodec
import com.lectoria.app.domain.model.BookId
import com.lectoria.app.domain.model.ReadingLocator
import com.lectoria.app.domain.narration.NarrationClient
import com.lectoria.app.domain.narration.NarrationCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidNarrationClient(private val context: Context, private val coordinator: NarrationCoordinator) : NarrationClient {
    override val state = coordinator.state
    override suspend fun prepare() = withContext(Dispatchers.Main.immediate) { coordinator.prepare() }
    override suspend fun chooseEngine(id: String) = withContext(Dispatchers.Main.immediate) { coordinator.chooseEngine(id) }
    override suspend fun chooseVoice(id: String) = withContext(Dispatchers.Main.immediate) { coordinator.chooseVoice(id) }
    override suspend fun setRate(rate: Float) = withContext(Dispatchers.Main.immediate) { coordinator.setRate(rate) }
    override suspend fun setDelivery(delivery: com.lectoria.app.domain.narration.SpeechDelivery) = withContext(Dispatchers.Main.immediate) { coordinator.setDelivery(delivery) }
    override suspend fun readerPosition() = withContext(Dispatchers.Main.immediate) { coordinator.readerPosition() }
    private fun send(action: String, id: BookId? = null, locator: ReadingLocator? = null) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, LocalNarrationService::class.java).apply {
                this.action = action
                id?.let { putExtra("book", it.value) }
                locator?.let { putExtra("locator", LocatorCodec.encode(it)) }
            })
        } catch (_: RuntimeException) { coordinator.reportServiceFailure() }
    }
    override fun listen(bookId: BookId, locator: ReadingLocator) = send("listen", bookId, locator)
    override fun play() = send("play")
    override fun pause() = send("pause")
    override fun next() = send("next")
    override fun previous() = send("previous")
    override fun stop() = send("stop")
    override fun testVoice() = send("test")
    override fun reconnectVoice() = send("reconnect")
}
