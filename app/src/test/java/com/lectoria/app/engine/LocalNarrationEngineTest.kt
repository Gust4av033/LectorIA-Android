@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.lectoria.app.engine

import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.TextLocator
import com.lectoria.app.domain.narration.*
import com.lectoria.app.engine.narration.*
import kotlinx.coroutines.async
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
        override fun engines() = listOf(SpeechEngineInfo("engine", "Motor"))
        override fun defaultEngine() = "engine"
        override fun create(engine: String, initialized: (Boolean) -> Unit, listener: SpeechPlatform.Listener): SpeechPlatform {
            this.listener = listener; initialized(true); return this
        }
        override fun voices() = listOf(SpeechVoice("local", "Español", "es", true, true, 200), SpeechVoice("network", "Red", "es", false, true, 500))
        override fun selectVoice(id: String): Boolean { selected = id; return true }
        override fun setRate(rate: Float) = true
        override fun speak(text: String, id: String): Boolean { speaks++; return accept }
        override fun stop() { stopped++ }
        override fun shutdown() = Unit
    }
    private fun request(id: String, voice: String = "local") = SpeechRequest(id,
        TextChunk("Niño y canción.", TextLocator("chapter", 100), TextLocator("chapter", 115)), voice, 1f)

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
