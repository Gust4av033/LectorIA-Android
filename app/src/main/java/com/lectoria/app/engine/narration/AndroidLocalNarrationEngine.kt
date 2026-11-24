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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
    private val completionTimeoutMs: Long = 120_000,
    private val post: (() -> Unit) -> Unit,
) : LocalNarrationEngine {
    constructor(context: Context) : this(AndroidSpeechPlatformFactory(context), Dispatchers.Main.immediate,
        post = { block -> android.os.Handler(android.os.Looper.getMainLooper()).post(block) })

    override val output = NarrationOutput.LiveSpeech
    private val mutableEvents = MutableSharedFlow<NarrationEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events = mutableEvents.asSharedFlow()
    private var platform: SpeechPlatform? = null
    private var engineId: String? = null
    private var request: SpeechRequest? = null
    private var completion: CancellableContinuation<Result<Unit>>? = null
    private var platformGeneration = 0L
    private var parentRequestId: String? = null
    private var partStarted = false

    override suspend fun configure(engineId: String?): VoiceCatalog = withContext(dispatcher) {
        try { configureOnce(engineId) }
        catch (failure: SpeechException) {
            if (failure.engineCode != TextToSpeech.ERROR_SERVICE) throw failure
            shutdown()
            configureOnce(engineId)
        }
    }

    private suspend fun configureOnce(engineId: String?): VoiceCatalog {
        val engines = factory.engines()
        if (engines.isEmpty()) { shutdown(); return VoiceCatalog(null, engines, emptyList()) }
        val selected = engineId ?: factory.defaultEngine()?.takeIf { id -> engines.any { it.id == id } } ?: engines.first().id
        if (engines.none { it.id == selected }) throw SpeechException(SpeechFailure.NO_ENGINE)
        if (platform == null || this@AndroidLocalNarrationEngine.engineId != selected) {
            shutdown()
            val generation = ++platformGeneration
            val initialized = CompletableDeferred<Boolean>()
            val listener = object : SpeechPlatform.Listener {
                override fun started(id: String) = post {
                    val active = request
                    if (generation == platformGeneration && active?.id == id) {
                        partStarted = true
                        mutableEvents.tryEmit(NarrationEvent.Started(parentRequestId ?: id))
                        mutableEvents.tryEmit(NarrationEvent.Progress(parentRequestId ?: id, active.chunk.start))
                    }
                }
                override fun done(id: String) = post { if (generation == platformGeneration) finish(id, null) }
                override fun error(id: String, code: Int) = post { if (generation == platformGeneration) finish(id,
                    if (code == TextToSpeech.ERROR_NOT_INSTALLED_YET) SpeechFailure.NO_OFFLINE_VOICE else SpeechFailure.ENGINE, code) }
                override fun stopped(id: String) = post { if (generation == platformGeneration) finish(id, SpeechFailure.ENGINE) }
                override fun range(id: String, start: Int, end: Int) = post {
                    val active = request
                    if (generation == platformGeneration && active?.id == id && start >= 0 && start < end && end <= active.chunk.text.length) {
                        if (!partStarted) mutableEvents.tryEmit(NarrationEvent.Started(parentRequestId ?: id))
                        partStarted = true
                        var safeStart = start
                        if (safeStart > 0 && Character.isLowSurrogate(active.chunk.text[safeStart])) safeStart--
                        mutableEvents.tryEmit(NarrationEvent.Progress(parentRequestId ?: id, TextLocator(active.chunk.start.sectionId, active.chunk.start.characterOffset + safeStart)))
                    }
                }
            }
            val created = factory.create(selected, initialized::complete, listener)
            platform = created
            try {
                if (!withTimeout(10_000) { initialized.await() }) throw SpeechException(SpeechFailure.INIT)
                if (generation != platformGeneration || platform !== created) throw SpeechException(SpeechFailure.ENGINE, TextToSpeech.ERROR_SERVICE)
                created.ready()
                this@AndroidLocalNarrationEngine.engineId = selected
            } catch (_: TimeoutCancellationException) { shutdown(); throw SpeechException(SpeechFailure.INIT) }
            catch (cancelled: CancellationException) { shutdown(); throw cancelled }
            catch (failure: SpeechException) { shutdown(); throw failure }
            catch (_: Exception) { shutdown(); throw SpeechException(SpeechFailure.INIT) }
        }
        val active = checkNotNull(platform)
        return VoiceCatalog(selected, engines, active.voices(), active.defaultVoiceId())
    }

    override suspend fun narrate(request: SpeechRequest): Result<Unit> = withContext(dispatcher) {
        stopImmediately()
        var active = platform ?: return@withContext Result.failure(SpeechException(SpeechFailure.INIT))
        val selectedEngine = engineId
        var recovered = false
        var options = applyOptions(active, request)
        if (options?.engineCode == TextToSpeech.ERROR_SERVICE) {
            recovered = true
            mutableEvents.tryEmit(NarrationEvent.Reconnecting(request.id))
            shutdown()
            try {
                configure(selectedEngine)
                active = checkNotNull(platform)
                options = applyOptions(active, request)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { return@withContext Result.failure(error) }
        }
        if (options != null) {
            if (options.engineCode == TextToSpeech.ERROR_SERVICE) shutdown()
            return@withContext Result.failure(options)
        }
        if (request.chunk.text.isBlank() || request.chunk.text.length > TextToSpeech.getMaxSpeechInputLength()) {
            return@withContext Result.failure(SpeechException(SpeechFailure.ENGINE))
        }
        parentRequestId = request.id
        var offset = 0
        for ((index, part) in speechParts(request.chunk.text).withIndex()) {
            val chunk = request.chunk.copy(text = part,
                start = request.chunk.start.copy(characterOffset = request.chunk.start.characterOffset + offset),
                end = request.chunk.start.copy(characterOffset = request.chunk.start.characterOffset + offset + part.length))
            val piece = request.copy(id = if (index == 0) request.id else "${request.id}:$index", chunk = chunk)
            var result = speakPart(active, piece)
            if ((result.exceptionOrNull() as? SpeechException)?.engineCode == TextToSpeech.ERROR_SERVICE && !partStarted && !recovered) {
                recovered = true
                mutableEvents.tryEmit(NarrationEvent.Reconnecting(request.id))
                shutdown()
                delay(300)
                try {
                    configure(selectedEngine)
                    active = checkNotNull(platform)
                    val error = applyOptions(active, request)
                    result = if (error != null) Result.failure(error) else speakPart(active, piece.copy(id = "${piece.id}:retry"))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { result = Result.failure(error) }
            }
            if (result.isFailure) {
                if ((result.exceptionOrNull() as? SpeechException)?.engineCode == TextToSpeech.ERROR_SERVICE) shutdown()
                mutableEvents.tryEmit(NarrationEvent.Failed(request.id, (result.exceptionOrNull() as? SpeechException)?.reason?.name ?: SpeechFailure.ENGINE.name))
                return@withContext result
            }
            offset += part.length
        }
        mutableEvents.tryEmit(NarrationEvent.Completed(request.id))
        Result.success(Unit)
    }

    private fun applyOptions(active: SpeechPlatform, request: SpeechRequest): SpeechException? = try {
        val voice = active.voices().firstOrNull { it.id == request.voiceId && it.offline && it.installed }
        when {
            voice == null || !active.selectVoice(voice.id) -> SpeechException(SpeechFailure.NO_OFFLINE_VOICE)
            !active.setRate(request.rate) -> SpeechException(SpeechFailure.ENGINE)
            else -> null
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: SpeechException) { failure }
    catch (_: Exception) { SpeechException(SpeechFailure.ENGINE, TextToSpeech.ERROR) }

    private suspend fun speakPart(active: SpeechPlatform, request: SpeechRequest): Result<Unit> {
        partStarted = false
        val result = withTimeoutOrNull(completionTimeoutMs) { suspendCancellableCoroutine<Result<Unit>> { continuation ->
            this@AndroidLocalNarrationEngine.request = request
            completion = continuation
            continuation.invokeOnCancellation { post { if (this@AndroidLocalNarrationEngine.request?.id == request.id) stopImmediately() } }
            try {
                if (!active.speak(request.chunk.text, request.id)) finish(request.id, SpeechFailure.ENGINE, TextToSpeech.ERROR)
            } catch (_: Exception) { finish(request.id, SpeechFailure.ENGINE, TextToSpeech.ERROR) }
        } }
        if (result != null) return result
        stopImmediately()
        return Result.failure(SpeechException(SpeechFailure.TIMEOUT))
    }

    private fun finish(id: String, failure: SpeechFailure?, code: Int? = null) {
        if (request?.id != id) return
        request = null
        val pending = completion
        completion = null
        if (pending?.isActive == true) pending.resume(if (failure == null) Result.success(Unit) else Result.failure(SpeechException(failure, code)))
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

/** Small, contiguous utterances work with vendor limits and preserve exact source offsets. */
internal fun speechParts(text: String): List<String> {
    val result = mutableListOf<String>()
    var offset = 0
    while (offset < text.length) {
        var end = minOf(offset + 350, text.length)
        if (end < text.length) {
            val sentence = (end - 1 downTo offset + 150).firstOrNull { text[it] in ".!?\n" }
            val space = (end - 1 downTo offset + 150).firstOrNull { text[it].isWhitespace() }
            end = (sentence ?: space)?.plus(1) ?: end
            if (Character.isHighSurrogate(text[end - 1])) end--
        }
        result += text.substring(offset, end)
        offset = end
    }
    return result
}
