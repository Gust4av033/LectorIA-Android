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

data class PlayerActions(
    val play: () -> Unit = {}, val pause: () -> Unit = {}, val previous: () -> Unit = {}, val next: () -> Unit = {},
    val stop: () -> Unit = {}, val library: () -> Unit = {}, val read: () -> Unit = {}, val refresh: () -> Unit = {},
    val test: () -> Unit = {}, val reconnect: () -> Unit = {}, val engine: (String) -> Unit = {}, val voice: (String) -> Unit = {}, val rate: (Float) -> Unit = {},
)

@Composable
fun PlayerRoute(onLibrary: () -> Unit, onReader: (String) -> Unit, settingsOnly: Boolean = false, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlayerScreen(state, PlayerActions(viewModel.client::play, viewModel.client::pause, viewModel.client::previous,
        viewModel.client::next, viewModel.client::stop, onLibrary, { viewModel.read(onReader) }, viewModel::refresh,
        viewModel.client::testVoice, viewModel.client::reconnectVoice, viewModel::engine, viewModel::voice, viewModel::rate), settingsOnly)
}

@Composable
fun PlayerScreen(state: NarrationState, actions: PlayerActions, settingsOnly: Boolean = false) {
    CompositionLocalProvider(LocalContentColor provides Midnight.Text) {
    var voiceDialog by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        .testTag(if (settingsOnly) "screen_settings" else "screen_player"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(if (settingsOnly) "Ajustes de voz" else "Reproductor", style = MaterialTheme.typography.headlineLarge)
        if (!settingsOnly) {
            com.lectoria.app.presentation.screens.GlassCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(com.lectoria.app.R.drawable.ic_reader), null,
                        Modifier.size(40.dp), tint = Midnight.Highlight)
                    Text(state.book?.title ?: "Tu próxima lectura", style = MaterialTheme.typography.headlineSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3)
                    state.book?.author?.let { Text(it, color = Midnight.MutedText) }
                    Text(playerStatus(state), Modifier.testTag("speech_status"), color = Midnight.Highlight)
                    if (state.book != null) {
                        if (state.sectionCount > 0) Text("Sección ${state.sectionIndex + 1} de ${state.sectionCount} · Parte ${state.fragmentIndex + 1} de ${state.fragmentCount}",
                            style = MaterialTheme.typography.bodySmall, color = Midnight.MutedText)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            Transport(com.lectoria.app.R.drawable.ic_speech_back, "Parte anterior", state.hasPrevious && !state.loadingVoices, actions.previous)
                            LectorButton(onClick = if (state.active) actions.pause else actions.play,
                                enabled = state.active || (state.voice != null && !state.loadingVoices),
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("speech_play")) {
                                Icon(androidx.compose.ui.res.painterResource(if (state.active) com.lectoria.app.R.drawable.ic_speech_pause else com.lectoria.app.R.drawable.ic_speech_play), null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.active) "Pausar" else if (state.phase == SpeechPhase.COMPLETED) "Repetir libro" else "Escuchar")
                            }
                            Transport(com.lectoria.app.R.drawable.ic_speech_next, "Parte siguiente", state.hasNext && !state.loadingVoices, actions.next)
                        }
                        Text("Anterior y siguiente cambian de parte del texto.", style = MaterialTheme.typography.bodySmall, color = Midnight.MutedText)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LectorTextButton(onClick = actions.stop, enabled = state.active || state.phase == SpeechPhase.PAUSED) { Text("Finalizar escucha") }
                            LectorTextButton(onClick = actions.read) { Text("Ver libro") }
                        }
                    } else {
                        Text("Abre un libro para empezar a escucharlo.", color = Midnight.MutedText)
                        LectorButton(onClick = actions.library) { Text("Elegir un libro") }
                    }
                }
            }
        }
        state.failure?.let { failure ->
            com.lectoria.app.presentation.screens.GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Lectura interrumpida", style = MaterialTheme.typography.titleMedium)
                    Text(engineFailureText(failure, state.engineErrorCode), Modifier.testTag("speech_error"))
                    Row {
                        if (failure in listOf(SpeechFailure.ENGINE, SpeechFailure.INIT, SpeechFailure.SERVICE, SpeechFailure.TIMEOUT)) {
                            LectorButton(onClick = actions.reconnect, enabled = !state.active) { Text("Reconectar voz") }
                        } else if (state.voice != null && state.book != null) LectorTextButton(onClick = actions.play) { Text("Reintentar") }
                        LectorTextButton(onClick = { voiceDialog = true }) { Text("Cambiar voz") }
                    }
                    LectorTextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(voiceDiagnostic(state))) }) { Text("Copiar diagnóstico") }
                    state.catalog.voices.firstOrNull { it.id == state.catalog.defaultVoiceId && it.offline && it.installed }?.let { default ->
                        LectorTextButton(onClick = { actions.voice(default.id) }) { Text("Usar voz de Android") }
                    }
                }
            }
        }
        if (state.saveFailed) Text("El avance aún no pudo guardarse. Pausa la lectura para reintentar.", color = MaterialTheme.colorScheme.error)
        com.lectoria.app.presentation.screens.GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Voz de lectura", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    state.reconnectingVoice -> "Reconectando con el motor…"
                    state.loadingVoices || state.phase == SpeechPhase.PREPARING -> "Conectando…"
                    state.failure != null -> "La voz necesita atención"
                    state.active && state.speechConfirmed -> "Voz iniciada"
                    state.active -> "Esperando respuesta del motor…"
                    state.voice != null -> "Voz preparada · sin Internet"
                    else -> "Falta seleccionar una voz"
                }, color = if (state.failure != null) MaterialTheme.colorScheme.error else Midnight.Highlight,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("voice_connection"))
                Text(state.voice?.let { friendlyVoice(it, state.catalog.voices) } ?: "Elige una voz en español", style = MaterialTheme.typography.titleLarge)
                Text(state.catalog.engines.firstOrNull { it.id == state.settings.engineId }?.label ?: "Configura el motor de voz", color = Midnight.MutedText)
                Row {
                    LectorTextButton(onClick = { voiceDialog = true }, modifier = Modifier.testTag("change_voice")) { Text("Cambiar voz") }
                    LectorTextButton(onClick = actions.test, enabled = state.voice != null && !state.active && !state.loadingVoices) { Text("Probar voz") }
                }
                if (state.loadingVoices) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Midnight.Highlight)
                HorizontalDivider(color = Midnight.MutedText.copy(alpha = 0.2f))
                Text("Velocidad", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    LectorTextButton(onClick = { actions.rate((state.settings.rate - 0.1f).coerceAtLeast(0.5f)) }, enabled = state.settings.rate > 0.5f) { Text("Más lenta") }
                    Text(String.format(Locale.getDefault(), "%.1f×", state.settings.rate), style = MaterialTheme.typography.titleLarge)
                    LectorTextButton(onClick = { actions.rate((state.settings.rate + 0.1f).coerceAtMost(2f)) }, enabled = state.settings.rate < 2f) { Text("Más rápida") }
                }
            }
        }
        if (state.preview.isNotBlank() && !settingsOnly) {
            LectorTextButton(onClick = { preview = !preview }) { Text(if (preview) "Ocultar texto" else "Mostrar texto de esta parte") }
            if (preview) Text(state.preview, style = MaterialTheme.typography.bodyLarge)
        }
        LectorTextButton(onClick = { help = !help }) { Text(if (help) "Cerrar ayuda" else "Ayuda con la voz") }
        if (help || settingsOnly) {
            Text("Cambiar la voz o velocidad pausa la escucha. Pulsa Escuchar para continuar. La pausa retoma la última palabra indicada por el motor; algunos motores repiten parte del texto.", color = Midnight.MutedText)
            LectorTextButton(onClick = actions.refresh, enabled = !state.active && !state.loadingVoices) { Text("Actualizar voces instaladas") }
            LectorTextButton(onClick = {
                try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            }) { Text("Abrir ajustes de voz de Android") }
        }
    }
    if (voiceDialog) AlertDialog(onDismissRequest = { voiceDialog = false }, title = { Text("Elige cómo escuchar") }, text = {
        androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Motor de voz", style = MaterialTheme.typography.labelLarge) }
            items(state.catalog.engines.size) { index -> val engine = state.catalog.engines[index]
                LectorTextButton(onClick = { actions.engine(engine.id) }, enabled = !state.loadingVoices, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = state.settings.engineId == engine.id, onClick = null)
                    Text(engine.label, Modifier.weight(1f))
                }
            }
            item { Text("Voces instaladas · sin Internet", style = MaterialTheme.typography.labelLarge) }
            val voices = state.catalog.voices.filter { it.offline && it.installed }
            if (state.loadingVoices) item { Text("Cargando voces…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (voices.isEmpty() && !state.loadingVoices) item { Text("No hay voces listas. Abre los ajustes de Android para instalar una voz y después actualiza la lista.") }
            items(voices.size) { index -> val voice = voices[index]
                LectorTextButton(onClick = { actions.voice(voice.id); voiceDialog = false }, enabled = !state.loadingVoices, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = state.settings.voiceId == voice.id, onClick = null)
                    Text(friendlyVoice(voice, state.catalog.voices), Modifier.weight(1f))
                }
            }
        }
    }, confirmButton = { LectorTextButton(onClick = { voiceDialog = false }) { Text("Listo") } })
    }
}

@Composable
private fun Transport(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        FilledTonalIconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.ui.res.painterResource(icon), contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

internal fun friendlyVoice(voice: SpeechVoice, all: List<SpeechVoice>): String {
    val locale = Locale.forLanguageTag(voice.languageTag)
    val language = locale.getDisplayLanguage(Locale.forLanguageTag("es")).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
    val country = locale.getDisplayCountry(Locale.forLanguageTag("es")).takeIf { it.isNotBlank() }
    val group = all.filter { it.offline && it.installed && it.languageTag == voice.languageTag }.sortedBy { it.id }
    val variant = if (group.size > 1) " · Voz ${group.indexOfFirst { it.id == voice.id } + 1}" else ""
    return language + (country?.let { " · $it" } ?: "") + variant
}

private fun playerStatus(state: NarrationState): String = if (state.reconnectingVoice) "Reconectando la voz…" else if (state.testingVoice) {
    if (state.speechConfirmed) "Prueba de voz en curso" else "Preparando la prueba de voz…"
} else when (state.phase) {
    SpeechPhase.IDLE -> "Listo para escuchar"
    SpeechPhase.PREPARING -> "Preparando voz…"
    SpeechPhase.PLAYING -> if (state.speechConfirmed) "Leyendo en voz alta" else "Esperando al motor de voz…"
    SpeechPhase.PAUSED -> when (state.pauseReason) {
        PauseReason.FOCUS -> "En pausa por otra aplicación o llamada"
        PauseReason.HEADPHONES -> "En pausa: auriculares desconectados"
        else -> "Lectura en pausa"
    }
    SpeechPhase.COMPLETED -> "Libro terminado"
    SpeechPhase.FAILED -> "La lectura necesita atención"
}

internal fun engineFailureText(failure: SpeechFailure, code: Int?): String = if (failure != SpeechFailure.ENGINE || code == null) speechFailureText(failure) else when(code) {
    -9 -> "Esta voz necesita instalarse. Abre los ajustes de Android y descarga sus datos."
    -8 -> "El motor rechazó el texto (código -8). Prueba la voz y vuelve a abrir esta parte del libro."
    -7, -6 -> "Esta voz intentó usar red (código $code). Elige otra voz instalada que funcione sin Internet."
    -5 -> "No se pudo abrir la salida de audio (código -5). Revisa el volumen multimedia y los auriculares."
    -4 -> "El servicio de voz falló (código -4). Reconecta el motor y prueba la voz antes de continuar el libro."
    -3 -> "El motor no pudo generar la voz (código -3). Usa Probar voz o elige otra voz instalada."
    else -> "El motor devolvió el código $code. Usa Probar voz para comprobarlo sin abrir el libro."
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
    SpeechFailure.TIMEOUT -> "El motor dejó de responder. La lectura se detuvo para conservar tu avance. Reconecta la voz."
}

internal fun voiceDiagnostic(state: NarrationState): String = """
    Lector IA 0.4.2 · Android ${android.os.Build.VERSION.SDK_INT}
    Motor: ${state.settings.engineId ?: "sin seleccionar"}
    Voz: ${state.settings.voiceId ?: "sin seleccionar"}
    Estado: ${state.phase.name}
    Fallo: ${state.failure?.name ?: "ninguno"}
    Código del motor: ${state.engineErrorCode ?: "sin código"}
    Inicio confirmado: ${state.speechConfirmed}
""".trimIndent()
