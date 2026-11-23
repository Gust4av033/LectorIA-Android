package com.lectoria.app.domain.repository

import com.lectoria.app.domain.model.LibraryOrder
import kotlinx.coroutines.flow.Flow

interface LibraryPreferencesRepository {
    val order: Flow<LibraryOrder>
    suspend fun setOrder(order: LibraryOrder)
}
