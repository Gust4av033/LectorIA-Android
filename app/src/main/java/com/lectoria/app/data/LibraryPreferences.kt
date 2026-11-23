package com.lectoria.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lectoria.app.domain.model.LibraryOrder
import com.lectoria.app.domain.repository.LibraryPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class LibraryPreferences(private val store: DataStore<Preferences>) : LibraryPreferencesRepository {
    private val key = stringPreferencesKey("library_order")
    override val order = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        preferences[key]?.let { runCatching { LibraryOrder.valueOf(it) }.getOrNull() } ?: LibraryOrder.RECENT
    }

    override suspend fun setOrder(order: LibraryOrder) { store.edit { it[key] = order.name } }
}
