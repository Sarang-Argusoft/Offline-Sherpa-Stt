package com.example.sherpastt.tts.model

sealed class TtsUiState {
    object Idle : TtsUiState()
    object Initializing : TtsUiState()
    object Ready : TtsUiState()
    object Generating : TtsUiState()
    object Playing : TtsUiState()
    object Completed : TtsUiState()
    data class Error(val message: String) : TtsUiState()
}
