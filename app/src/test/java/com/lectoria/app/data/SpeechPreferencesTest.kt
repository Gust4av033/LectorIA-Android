package com.lectoria.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.lectoria.app.domain.narration.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SpeechPreferencesTest {
    @Test fun `old settings keep voice and speed and new delivery round trips`() = runTest {
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow<Preferences>(preferencesOf(stringPreferencesKey("engine") to "engine",
                stringPreferencesKey("voice") to "voice", floatPreferencesKey("rate") to 1.2f))
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
        }
        val preferences = SpeechPreferences(store)
        assertEquals(SpeechSettings("engine", "voice", 1.2f), preferences.load())
        val expressive = preferences.load().copy(delivery = SpeechDelivery.LIVELY)
        preferences.save(expressive)
        assertEquals(expressive, preferences.load())
        store.updateData { it.toMutablePreferences().apply { this[stringPreferencesKey("delivery")] = "future_value" } }
        assertEquals(SpeechDelivery.STANDARD, preferences.load().delivery)
    }
}
