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
    @Test fun `Android speak preserves queued voice loading instead of flushing the service queue`() {
        val factory = AndroidSpeechPlatformFactory(org.robolectric.RuntimeEnvironment.getApplication())
        val platform = factory.create("test.engine", {}, object : SpeechPlatform.Listener {
            override fun done(id: String) = Unit
            override fun range(id: String, start: Int, end: Int) = Unit
            override fun error(id: String, code: Int) = Unit
            override fun stopped(id: String) = Unit
        })
        try {
            platform.stop()
            assertTrue(platform.speak("Prueba sin libro", "queue-regression"))
            val sdk = org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance()
            val shadow = org.robolectric.Shadows.shadowOf(sdk)
            assertEquals(android.speech.tts.TextToSpeech.QUEUE_ADD, shadow.queueMode)
            assertEquals("Prueba sin libro", shadow.lastSpokenText)
        } finally { platform.shutdown() }
    }
    private class Platform : SpeechPlatform, SpeechPlatform.Factory {
        lateinit var listener: SpeechPlatform.Listener
        var selected: String? = null
        var speaks = 0
        var stopped = 0
        var accept = true
        var ready = false
        var creations = 0
        var failInventoryOnce = false
        val ids = mutableListOf<String>()
        val texts = mutableListOf<String>()
        val pitches = mutableListOf<Float>()
        val rates = mutableListOf<Float>()
        override fun ready() { ready = true }
        override fun engines() = listOf(SpeechEngineInfo("engine", "Motor"))
        override fun defaultEngine() = "engine"
        override fun create(engine: String, initialized: (Boolean) -> Unit, listener: SpeechPlatform.Listener): SpeechPlatform {
            creations++
            this.listener = listener; initialized(true); return this
        }
        override fun voices(): List<SpeechVoice> {
            check(ready)
            if (failInventoryOnce) { failInventoryOnce = false; throw SpeechException(SpeechFailure.ENGINE, -4) }
            return listOf(SpeechVoice("local", "Español", "es", true, true, 200), SpeechVoice("network", "Red", "es", false, true, 500))
        }
        override fun selectVoice(id: String): Boolean { selected = id; return true }
        override fun setRate(rate: Float): Boolean { rates += rate; return true }
        override fun setPitch(pitch: Float): Boolean { pitches += pitch; return true }
        override fun speak(text: String, id: String): Boolean { speaks++; ids += id; texts += text; return accept }
        override fun stop() { stopped++ }
        override fun shutdown() = Unit
    }
    private fun request(id: String, voice: String = "local") = SpeechRequest(id,
        TextChunk("Niño y canción.", TextLocator("chapter", 100), TextLocator("chapter", 115)), voice, 1f)

    @Test fun `expressive voice preserves source offsets and sends real prosody to the engine`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        val events = mutableListOf<NarrationEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.collect { events += it } }
        engine.configure(null)
        val text = "¿Vienes? ¡Vamos!"
        val result = async { engine.narrate(request("expressive").copy(delivery = SpeechDelivery.LIVELY,
            chunk = TextChunk(text, TextLocator("chapter", 100), TextLocator("chapter", 100L + text.length)))) }; runCurrent()
        platform.listener.started(platform.ids.last()); runCurrent()
        val fallback = events.filterIsInstance<NarrationEvent.Progress>().last()
        assertEquals(TextLocator("chapter", 100), fallback.locator)
        assertEquals(TextLocator("chapter", 100L + platform.texts.first().length), fallback.end)
        platform.listener.range(platform.ids.last(), 1, 7); runCurrent()
        val word = events.filterIsInstance<NarrationEvent.Progress>().last()
        assertEquals(TextLocator("chapter", 101), word.locator)
        assertEquals(TextLocator("chapter", 107), word.end)
        platform.listener.done(platform.ids.last()); runCurrent()
        advanceTimeBy(90); runCurrent()
        assertEquals(2, platform.speaks)
        platform.listener.done(platform.ids.last()); runCurrent()
        assertTrue(result.await().isSuccess)
        assertEquals(text, platform.texts.joinToString(""))
        assertEquals(listOf(1.09f, 1.06f), platform.pitches)
        assertEquals(1.03f, platform.rates.last())
        engine.shutdown()
    }

    @Test fun `delivery splitting preserves Unicode whitespace and all text`() {
        val text = "¿Cómo estás? ¡Bien! Niño 😀. ".repeat(90)
        SpeechDelivery.entries.forEach { delivery ->
            val parts = deliveryParts(text, delivery)
            assertEquals(text, parts.joinToString(""))
            assertTrue(parts.all { it.length <= 350 && !Character.isHighSurrogate(it.last()) })
        }
        assertEquals(SpeechProsody(1f, 1f, 0), speechProsody("Hola.", 1f, SpeechDelivery.STANDARD))
        assertEquals(1.09f, speechProsody("¿Vienes?»", 1f, SpeechDelivery.LIVELY).pitch)
        assertEquals(2f, speechProsody("Hola!", 2f, SpeechDelivery.LIVELY).rate)
        assertEquals(0.5f, speechProsody("Hola.", 0.5f, SpeechDelivery.NARRATIVE).rate)
    }

    @Test fun `service failure before speech reconnects once and ignores old connection callbacks`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        val result = async { engine.narrate(request("recover")) }; runCurrent()
        val old = platform.listener
        old.error("recover", -4); runCurrent()
        advanceTimeBy(300); runCurrent()
        assertEquals(2, platform.creations)
        assertEquals(2, platform.speaks)
        old.done("recover"); runCurrent()
        assertFalse(result.isCompleted)
        platform.listener.started(platform.ids.last())
        platform.listener.done(platform.ids.last()); runCurrent()
        assertTrue(result.await().isSuccess)
        engine.shutdown()
    }

    @Test fun `repeated service failure ends after a single recovery attempt`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        val result = async { engine.narrate(request("bounded")) }; runCurrent()
        platform.listener.error(platform.ids.last(), -4); runCurrent()
        advanceTimeBy(300); runCurrent()
        platform.listener.error(platform.ids.last(), -4); runCurrent()
        assertEquals(-4, (result.await().exceptionOrNull() as SpeechException).engineCode)
        assertEquals(2, platform.creations)
        engine.configure(null)
        assertEquals(3, platform.creations)
        engine.shutdown()
    }

    @Test fun `service failure after speech started never automatically repeats the phrase`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        val result = async { engine.narrate(request("started")) }; runCurrent()
        platform.listener.started("started")
        platform.listener.error("started", -4); runCurrent()
        assertTrue(result.await().isFailure)
        assertEquals(1, platform.speaks)
        assertEquals(1, platform.creations)
        engine.shutdown()
    }

    @Test fun `cancelling during recovery prevents a delayed new utterance`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        val result = async { engine.narrate(request("cancel")) }; runCurrent()
        platform.listener.error("cancel", -4); runCurrent()
        result.cancel(); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, platform.creations)
        assertEquals(1, platform.speaks)
        engine.shutdown()
    }

    @Test fun `unresponsive engine times out instead of keeping an utterance pending forever`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler), completionTimeoutMs = 1000) { it() }
        engine.configure(null)
        val result = async { engine.narrate(request("timeout")) }; runCurrent()
        advanceTimeBy(1001); runCurrent()
        assertEquals(SpeechFailure.TIMEOUT, (result.await().exceptionOrNull() as SpeechException).reason)
        assertTrue(platform.stopped > 0)
        engine.shutdown()
    }

    @Test fun `a stale cached binder is replaced while refreshing voices`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        platform.failInventoryOnce = true
        val catalog = engine.configure(null)
        assertTrue(catalog.voices.isNotEmpty())
        assertEquals(2, platform.creations)
        engine.shutdown()
    }

    @Test fun `a dead binder discovered just before speak is recovered before enqueueing text`() = runTest {
        val platform = Platform()
        val engine = AndroidLocalNarrationEngine(platform, StandardTestDispatcher(testScheduler)) { it() }
        engine.configure(null)
        platform.failInventoryOnce = true
        val result = async { engine.narrate(request("preflight")) }; runCurrent()
        assertEquals(2, platform.creations)
        assertEquals(1, platform.speaks)
        platform.listener.done(platform.ids.last()); runCurrent()
        assertTrue(result.await().isSuccess)
        engine.shutdown()
    }

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
