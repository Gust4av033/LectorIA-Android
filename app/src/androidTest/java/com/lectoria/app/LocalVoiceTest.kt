package com.lectoria.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.TextLocator
import com.lectoria.app.domain.narration.SpeechRequest
import com.lectoria.app.engine.narration.AndroidLocalNarrationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Produces one short, synthetic utterance on the device if a Spanish offline voice is installed. */
@RunWith(AndroidJUnit4::class)
class LocalVoiceTest {
    @Test fun installedSpanishVoiceSpeaksWithoutCreatingAudioFiles() = runBlocking {
        val engine = AndroidLocalNarrationEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val catalog = engine.configure(null)
            val voice = catalog.voices.firstOrNull { it.offline && it.installed && it.languageTag.startsWith("es") }
            assumeTrue("Requires an installed offline Spanish voice", voice != null)
            val text = "Prueba de lectura local: niño y canción."
            withTimeout(30_000) { engine.narrate(SpeechRequest("device-synthetic", TextChunk(text, TextLocator("test", 0), TextLocator("test", text.length.toLong())), voice!!.id, 1f)).getOrThrow() }
        } finally { withContext(Dispatchers.Main) { engine.shutdown() } }
    }
}
