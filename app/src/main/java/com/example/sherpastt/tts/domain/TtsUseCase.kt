package com.example.sherpastt.tts.domain

import com.example.sherpastt.tts.data.KokoroTtsRepository
import com.example.sherpastt.tts.model.TtsUiState

class TtsUseCase(private val repository: KokoroTtsRepository) {

    suspend fun initialize() {
        repository.initialize()
    }

    suspend fun speak(text: String, onStateChange: (TtsUiState) -> Unit) {
        repository.speak(text, onStateChange)
    }

    fun stop() {
        repository.stop()
    }

    fun release() {
        repository.release()
    }
}
