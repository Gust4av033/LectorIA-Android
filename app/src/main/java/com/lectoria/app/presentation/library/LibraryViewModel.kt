package com.lectoria.app.presentation.library

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Phase 0 has no storage or import engine. This state never pretends to load books. */
data class LibraryUiState(val importAvailable: Boolean = false)

@HiltViewModel
class LibraryViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()
}
