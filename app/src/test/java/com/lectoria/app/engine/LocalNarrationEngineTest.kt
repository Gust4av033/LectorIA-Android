@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.lectoria.app.engine

import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.TextLocator
import com.lectoria.app.domain.narration.*
import com.lectoria.app.engine.narration.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [36])
class LocalNarrationEngineTest {
    private class Platform : SpeechPlatform, SpeechPlatform.Factory {
        lateinit var listener: SpeechPlatform.Listener
        var selected: String? = null
        var speaks = 0
        var stopped = 0
        var accept = true
        var ready = false
        val ids = mutableListOf<String>()
        val texts = mutableListOf<String>()
        override fun ready() { ready = true }
        override fun engines() = listOf(SpeechEngineInfo("engine", "Motor"))
        override fun defaultEngine() = "engine"
        override fun create(engine: String, initialized: (Boolean) -> Unit, listener: SpeechPlatform.Listener): SpeechPlatform {
            this.listener = listener; initialized(true); return this
        }
        override fun voices(): List<SpeechVoice> { check(ready); return listOf(SpeechVoice("local", "Español", "es", true, true, 200), SpeechVoice("network", "Red", "es", false, true, 500)) }
        override fun selectVoice(id: String): Boolean { selected = id; return true }
        override fun setRate(rate: Float) = true
        override fun speak(text: String, id: String): Boolean { speaks++; ids += id; texts += text; return accept }
        override fun stop() { stopped++ }
        override fun shutdown() = Unit
    }
    private fun request(id: String, voice: String = "local") = SpeechRequest(id,
        TextChunk("Niño y canción.", TextLocator("chapter", 100), TextLocator("chapter", 115)), voice, 1f)

    @Test fun `vendor error code is retained instead of a generic speech failure`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        val pending = async { engine.narrate(request("error")) }; runCurrent()
        platform.listener.error("error", -5); runCurrent()
        assertEquals(-5, (pending.await().exceptionOrNull() as SpeechException).engineCode)
        engine.shutdown()
    }

    @Test fun `vendor utterances stay short without losing Unicode or text boundaries`() {
        val text = "Canción, niño y lectura. 😀 ".repeat(100)
        val parts = speechParts(text)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.all { it.length <= 350 && !Character.isHighSurrogate(it.last()) })
    }

    @Test fun `multiple native utterances produce a single completion and preserve parent progress identity`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        val events = mutableListOf<NarrationEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.collect { events += it } }
        engine.configure(null)
        val text = "Niño y canción. ".repeat(100)
        val request = request("parent").copy(chunk = TextChunk(text, TextLocator("chapter", 100), TextLocator("chapter", 100L + text.length)))
        val result = async { engine.narrate(request) }; runCurrent()
        while (!result.isCompleted) {
            val id = platform.ids.last()
            platform.listener.started(id)
            platform.listener.done(id)
            runCurrent()
        }
        assertTrue(result.await().isSuccess)
        assertEquals(text, platform.texts.joinToString(""))
        assertEquals(listOf(NarrationEvent.Completed("parent")), events.filterIsInstance<NarrationEvent.Completed>())
        assertTrue(events.filterIsInstance<NarrationEvent.Progress>().all { it.requestId == "parent" })
        engine.shutdown()
    }

    @Test fun `completion waits for the matching callback and a cancelled callback cannot finish the next request`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        val first = async { engine.narrate(request("first")) }; runCurrent()
        assertFalse(first.isCompleted)
        engine.cancel(); runCurrent()
        assertTrue(first.isCancelled)
        val second = async { engine.narrate(request("second")) }; runCurrent()
        platform.listener.done("first"); runCurrent()
        assertFalse(second.isCompleted)
        platform.listener.done("second"); runCurrent()
        assertTrue(second.await().isSuccess)
        assertEquals("local", platform.selected)
        engine.shutdown()
    }

    @Test fun `network voice is refused before speaking and immediate engine errors are returned`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        assertTrue(engine.narrate(request("network-test", "network")).isFailure)
        assertEquals(0, platform.speaks)
        platform.accept = false
        assertTrue(engine.narrate(request("rejected")).isFailure)
        assertEquals(1, platform.speaks)
        engine.shutdown()
    }
}
