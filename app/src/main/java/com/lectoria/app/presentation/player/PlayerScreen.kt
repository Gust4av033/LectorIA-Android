package com.lectoria.app.presentation.player

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lectoria.app.core.designsystem.*
import com.lectoria.app.domain.narration.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.util.Locale

@HiltViewModel
class PlayerViewModel @Inject constructor(val client: NarrationClient) : ViewModel() {
    val state = client.state
    init { refresh() }
    fun refresh() { viewModelScope.launch { client.prepare() } }
    fun engine(id: String) { viewModelScope.launch { client.chooseEngine(id) } }
    fun voice(id: String) { viewModelScope.launch { client.chooseVoice(id) } }
    fun rate(rate: Float) { viewModelScope.launch { client.setRate(rate) } }
    fun read(onBook: (String) -> Unit) { viewModelScope.launch { client.readerPosition()?.let { onBook(it.value) } } }
}

@Composable
fun PlayerRoute(onLibrary: () -> Unit, onReader: (String) -> Unit, settingsOnly: Boolean = false, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var engineMenu by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        .testTag(if (settingsOnly) "screen_settings" else "screen_player"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (settingsOnly) "Ajustes de voz" else "Escucha tu libro", style = MaterialTheme.typography.headlineLarge)
        Text("Voz local · sin archivos de audio generados", color = Midnight.Highlight)
        if (!settingsOnly) {
            Text(state.book?.title ?: "Elige un libro de tu biblioteca y toca «Escuchar desde aquí» en el lector.", style = MaterialTheme.typography.titleLarge)
            if (state.book == null) LectorButton(onClick = onLibrary) { Text("Ir a biblioteca") }
            else {
                Text(when (state.phase) {
                    SpeechPhase.PREPARING -> "Preparando lectura…"
                    SpeechPhase.PLAYING -> "Leyendo · sección ${state.sectionIndex + 1} de ${state.sectionCount} · fragmento ${state.fragmentIndex + 1} de ${state.fragmentCount}"
                    SpeechPhase.COMPLETED -> "Lectura terminada"
                    SpeechPhase.FAILED -> "No se pudo continuar"
                    SpeechPhase.IDLE -> "Detenido"
                    SpeechPhase.PAUSED -> when (state.pauseReason) {
                        PauseReason.FOCUS -> "Pausado por otra aplicación o llamada. Reanuda cuando quieras."
                        PauseReason.HEADPHONES -> "Auriculares desconectados. Reanuda cuando estés listo."
                        PauseReason.SETTINGS -> "Voz o velocidad cambiada. Pulsa Reproducir."
                        else -> "En pausa"
                    }
                }, Modifier.testTag("speech_status"), color = Midnight.Highlight)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LectorButton(onClick = { if (state.active) viewModel.client.pause() else viewModel.client.play() },
                        enabled = state.active || state.voice != null, modifier = Modifier.testTag("speech_play")) {
                        Text(if (state.active) "Pausar" else if (state.phase == SpeechPhase.COMPLETED) "Volver a escuchar" else "Reproducir")
                    }
                    LectorTextButton(onClick = viewModel.client::stop) { Text("Detener") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LectorTextButton(onClick = viewModel.client::previous, enabled = state.hasPrevious) { Text("Anterior") }
                    LectorTextButton(onClick = viewModel.client::next, enabled = state.hasNext) { Text("Siguiente") }
                }
                if (state.preview.isNotBlank()) Text(state.preview, style = MaterialTheme.typography.bodyLarge)
                LectorTextButton(onClick = { viewModel.read(onReader) }) { Text("Abrir esta parte en el lector") }
            }
        }
        state.failure?.let { Text(speechFailureText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("speech_error")) }
        if (state.saveFailed) Text("No se pudo guardar el avance de escucha. Se mantiene en memoria; vuelve a pausar para reintentar.", color = MaterialTheme.colorScheme.error)
        if (state.loadingVoices) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Midnight.Highlight)
        Text("Motor y voz", style = MaterialTheme.typography.titleMedium)
        Box {
            LectorTextButton(onClick = { engineMenu = true }, enabled = !state.loadingVoices && state.catalog.engines.isNotEmpty()) {
                Text(state.catalog.engines.firstOrNull { it.id == state.settings.engineId }?.label ?: "Sin motor TTS disponible")
            }
            DropdownMenu(engineMenu, { engineMenu = false }) {
                state.catalog.engines.forEach { engine -> DropdownMenuItem(text = { Text(engine.label) }, onClick = { engineMenu = false; viewModel.engine(engine.id) }) }
            }
        }
        Box {
            LectorTextButton(onClick = { voiceMenu = true }, enabled = !state.loadingVoices && state.catalog.voices.any { it.offline && it.installed }) {
                Text(state.voice?.let { "${it.languageTag} · ${it.label}" } ?: "Elegir una voz instalada sin red")
            }
            DropdownMenu(voiceMenu, { voiceMenu = false }) {
                state.catalog.voices.filter { it.offline && it.installed }.forEach { voice ->
                    DropdownMenuItem(text = { Text("${voice.languageTag} · ${voice.label}") }, onClick = { voiceMenu = false; viewModel.voice(voice.id) })
                }
            }
        }
        Text("Solo se ofrecen voces que el motor identifica como instaladas y sin conexión. La calidad depende del motor y del idioma.", color = Midnight.MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LectorTextButton(onClick = { viewModel.rate((state.settings.rate - 0.1f).coerceAtLeast(0.5f)) }, enabled = state.settings.rate > 0.5f) { Text("Más lento") }
            Text(String.format(Locale.getDefault(), "%.1f×", state.settings.rate), Modifier.padding(top = 12.dp))
            LectorTextButton(onClick = { viewModel.rate((state.settings.rate + 0.1f).coerceAtMost(2f)) }, enabled = state.settings.rate < 2f) { Text("Más rápido") }
        }
        Text("La pausa guarda la última palabra informada por el motor. Si no ofrece esa información, al reanudar se repite el fragmento actual.", color = Midnight.MutedText)
        LectorTextButton(onClick = viewModel::refresh, enabled = !state.active && !state.loadingVoices) { Text("Actualizar voces") }
        LectorTextButton(onClick = {
            try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }) { Text("Configurar voces en Android") }
    }
}

fun speechFailureText(failure: SpeechFailure): String = when (failure) {
    SpeechFailure.NO_ENGINE -> "No hay un motor TTS instalado. Configura la salida de texto a voz en Android."
    SpeechFailure.NO_OFFLINE_VOICE -> "Elige una voz instalada sin red. Si falta español, descarga su voz desde los ajustes de Android y actualiza la lista."
    SpeechFailure.INIT -> "El motor de voz no respondió. Revisa sus ajustes e intenta de nuevo."
    SpeechFailure.ENGINE -> "El motor no pudo pronunciar este fragmento. Prueba otra voz instalada."
    SpeechFailure.DOCUMENT -> "El libro ya no está disponible o no se pudo abrir. Se conservan los datos guardados."
    SpeechFailure.LOCATION -> "No se pudo recuperar esta ubicación. Puedes iniciar desde otra parte en el lector."
    SpeechFailure.NO_TEXT -> "No se encontró texto narrable. Los documentos escaneados necesitan OCR."
    SpeechFailure.AUDIO_FOCUS -> "Otra aplicación controla el audio. Reanuda cuando esté disponible."
    SpeechFailure.STORAGE -> "No se pudieron recuperar o guardar los ajustes de voz. Intenta de nuevo."
    SpeechFailure.SERVICE -> "Android no permitió iniciar la lectura. Abre la app e intenta de nuevo."
}
