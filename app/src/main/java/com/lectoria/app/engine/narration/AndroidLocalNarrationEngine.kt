package com.lectoria.app.engine.narration

import android.content.Context
import android.speech.tts.TextToSpeech
import com.lectoria.app.domain.model.TextLocator
import com.lectoria.app.domain.narration.LocalNarrationEngine
import com.lectoria.app.domain.narration.NarrationEvent
import com.lectoria.app.domain.narration.NarrationOutput
import com.lectoria.app.domain.narration.SpeechException
import com.lectoria.app.domain.narration.SpeechFailure
import com.lectoria.app.domain.narration.SpeechRequest
import com.lectoria.app.domain.narration.VoiceCatalog
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class AndroidLocalNarrationEngine internal constructor(
    private val factory: SpeechPlatform.Factory,
    private val dispatcher: CoroutineDispatcher,
    private val post: (() -> Unit) -> Unit,
) : LocalNarrationEngine {
    constructor(context: Context) : this(AndroidSpeechPlatformFactory(context), Dispatchers.Main.immediate,
        { block -> android.os.Handler(android.os.Looper.getMainLooper()).post(block) })

    override val output = NarrationOutput.LiveSpeech
    private val mutableEvents = MutableSharedFlow<NarrationEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events = mutableEvents.asSharedFlow()
    private var platform: SpeechPlatform? = null
    private var engineId: String? = null
    private var request: SpeechRequest? = null
    private var completion: CancellableContinuation<Result<Unit>>? = null
    private var platformGeneration = 0L

    override suspend fun configure(engineId: String?): VoiceCatalog = withContext(dispatcher) {
        val engines = factory.engines()
        if (engines.isEmpty()) { shutdown(); return@withContext VoiceCatalog(null, engines, emptyList()) }
        val selected = engineId ?: factory.defaultEngine()?.takeIf { id -> engines.any { it.id == id } } ?: engines.first().id
        if (engines.none { it.id == selected }) throw SpeechException(SpeechFailure.NO_ENGINE)
        if (platform == null || this@AndroidLocalNarrationEngine.engineId != selected) {
            shutdown()
            val generation = ++platformGeneration
            val initialized = CompletableDeferred<Boolean>()
            val listener = object : SpeechPlatform.Listener {
                override fun done(id: String) = post { if (generation == platformGeneration) finish(id, null) }
                override fun error(id: String, code: Int) = post { if (generation == platformGeneration) finish(id,
                    if (code == TextToSpeech.ERROR_NOT_INSTALLED_YET) SpeechFailure.NO_OFFLINE_VOICE else SpeechFailure.ENGINE) }
                override fun stopped(id: String) = post { if (generation == platformGeneration) finish(id, SpeechFailure.ENGINE) }
                override fun range(id: String, start: Int, end: Int) = post {
                    val active = request
                    if (generation == platformGeneration && active?.id == id && start >= 0 && start < end && end <= active.chunk.text.length) {
                        var safeStart = start
                        if (safeStart > 0 && Character.isLowSurrogate(active.chunk.text[safeStart])) safeStart--
                        mutableEvents.tryEmit(NarrationEvent.Progress(id, TextLocator(active.chunk.start.sectionId, active.chunk.start.characterOffset + safeStart)))
                    }
                }
            }
            val created = factory.create(selected, initialized::complete, listener)
            platform = created
            try {
                if (!withTimeout(10_000) { initialized.await() }) throw SpeechException(SpeechFailure.INIT)
                this@AndroidLocalNarrationEngine.engineId = selected
            } catch (_: TimeoutCancellationException) { shutdown(); throw SpeechException(SpeechFailure.INIT) }
            catch (cancelled: CancellationException) { shutdown(); throw cancelled }
            catch (_: Exception) { shutdown(); throw SpeechException(SpeechFailure.INIT) }
        }
        VoiceCatalog(selected, engines, checkNotNull(platform).voices())
    }

    override suspend fun narrate(request: SpeechRequest): Result<Unit> = withContext(dispatcher) {
        stopImmediately()
        val active = platform ?: return@withContext Result.failure(SpeechException(SpeechFailure.INIT))
        val voice = active.voices().firstOrNull { it.id == request.voiceId && it.offline && it.installed }
        if (voice == null || !active.selectVoice(voice.id)) return@withContext Result.failure(SpeechException(SpeechFailure.NO_OFFLINE_VOICE))
        if (!active.setRate(request.rate) || request.chunk.text.isBlank() || request.chunk.text.length > TextToSpeech.getMaxSpeechInputLength()) {
            return@withContext Result.failure(SpeechException(SpeechFailure.ENGINE))
        }
        suspendCancellableCoroutine { continuation ->
            this@AndroidLocalNarrationEngine.request = request
            completion = continuation
            continuation.invokeOnCancellation { post { if (this@AndroidLocalNarrationEngine.request?.id == request.id) stopImmediately() } }
            try {
                if (!active.speak(request.chunk.text, request.id)) finish(request.id, SpeechFailure.ENGINE)
            } catch (_: Exception) { finish(request.id, SpeechFailure.ENGINE) }
        }
    }

    private fun finish(id: String, failure: SpeechFailure?) {
        if (request?.id != id) return
        request = null
        val pending = completion
        completion = null
        if (failure == null) mutableEvents.tryEmit(NarrationEvent.Completed(id))
        else mutableEvents.tryEmit(NarrationEvent.Failed(id, failure.name))
        if (pending?.isActive == true) pending.resume(if (failure == null) Result.success(Unit) else Result.failure(SpeechException(failure)))
    }

    override fun stopImmediately() {
        request = null
        val pending = completion
        completion = null
        runCatching { platform?.stop() }
        pending?.cancel()
    }

    override suspend fun cancel() = withContext(dispatcher) { stopImmediately() }
    override fun shutdown() {
        stopImmediately()
        platformGeneration++
        runCatching { platform?.shutdown() }
        platform = null
        engineId = null
    }
}
