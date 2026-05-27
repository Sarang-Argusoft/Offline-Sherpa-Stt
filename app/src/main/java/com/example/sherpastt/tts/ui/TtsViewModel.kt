package com.example.sherpastt.tts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sherpastt.tts.data.KokoroTtsRepository
import com.example.sherpastt.tts.domain.TtsUseCase
import com.example.sherpastt.tts.model.TtsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TtsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KokoroTtsRepository(application.applicationContext)
    private val useCase = TtsUseCase(repository)

    private val _uiState = MutableStateFlow<TtsUiState>(TtsUiState.Initializing)
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        viewModelScope.launch {
            _uiState.value = TtsUiState.Initializing
            try {
                useCase.initialize()
                _uiState.value = TtsUiState.Ready
            } catch (e: Exception) {
                _uiState.value = TtsUiState.Error("Failed to load Kokoro TTS: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Start synthesizing and playing the input text.
     */
    fun speak(text: String) {
        if (text.trim().isEmpty()) {
            _uiState.value = TtsUiState.Error("Please enter some text")
            return
        }

        viewModelScope.launch {
            useCase.speak(text) { newState ->
                _uiState.value = newState
            }
        }
    }

    /**
     * Stop active audio track.
     */
    fun stop() {
        useCase.stop()
        _uiState.value = TtsUiState.Ready
    }

    /**
     * Free JNI assets.
     */
    override fun onCleared() {
        super.onCleared()
        useCase.release()
    }
}
