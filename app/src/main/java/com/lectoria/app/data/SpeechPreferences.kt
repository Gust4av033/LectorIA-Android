package com.lectoria.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lectoria.app.domain.narration.SpeechPreferencesRepository
import com.lectoria.app.domain.narration.SpeechSettings
import com.lectoria.app.domain.narration.SpeechDelivery
import kotlinx.coroutines.flow.first

class SpeechPreferences(private val store: DataStore<Preferences>) : SpeechPreferencesRepository {
    private val engine = stringPreferencesKey("engine")
    private val voice = stringPreferencesKey("voice")
    private val rate = floatPreferencesKey("rate")
    private val delivery = stringPreferencesKey("delivery")
    override suspend fun load(): SpeechSettings = store.data.first().let { SpeechSettings(it[engine], it[voice], it[rate] ?: 1f,
        it[delivery]?.let { value -> runCatching { SpeechDelivery.valueOf(value) }.getOrNull() } ?: SpeechDelivery.STANDARD) }
    override suspend fun save(settings: SpeechSettings) { store.edit {
        settings.engineId?.let { value -> it[engine] = value } ?: it.remove(engine)
        settings.voiceId?.let { value -> it[voice] = value } ?: it.remove(voice)
        it[rate] = settings.rate
        it[delivery] = settings.delivery.name
    } }
}
