package com.lectoria.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lectoria.app.domain.document.TextChunk
import com.lectoria.app.domain.model.TextLocator
import com.lectoria.app.domain.narration.SpeechRequest
import com.lectoria.app.domain.narration.preferredOfflineSpanishVoice
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
            val requestedEngine = InstrumentationRegistry.getArguments().getString("ttsEngine")
            val catalog = engine.configure(requestedEngine)
            val voice = preferredOfflineSpanishVoice(catalog.voices, catalog.defaultVoiceId)
            assumeTrue("Requires an installed offline Spanish voice", voice != null)
            val selectedVoice = checkNotNull(voice)
            val engineLabel = catalog.engines.firstOrNull { it.id == catalog.engineId }?.label
            android.util.Log.i(
                "LocalVoiceTest",
                "Engine=${engineLabel ?: catalog.engineId} locale=${selectedVoice.languageTag} AndroidQuality=${selectedVoice.quality}",
            )
            val text = "Prueba de lectura local: niño y canción."
            withTimeout(30_000) { engine.narrate(SpeechRequest("device-synthetic", TextChunk(text, TextLocator("test", 0), TextLocator("test", text.length.toLong())), selectedVoice.id, 1f)).getOrThrow() }
        } finally { withContext(Dispatchers.Main) { engine.shutdown() } }
    }
}
