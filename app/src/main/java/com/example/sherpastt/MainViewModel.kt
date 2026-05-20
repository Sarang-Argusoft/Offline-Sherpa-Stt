package com.example.sherpastt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel that owns the [SpeechRecognitionManager] lifecycle.
 * Survives configuration changes (screen rotation, etc.).
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val manager = SpeechRecognitionManager(application)

    // Expose StateFlows directly to the UI
    val state         = manager.state
    val partialResult = manager.partialResult
    val finalResult   = manager.finalResult

    init {
        viewModelScope.launch {
            manager.initialize()
        }
    }

    fun startListening()  = manager.startListening()
    fun stopListening()   = manager.stopListening()
    fun clearTranscript() = manager.clearTranscript()

    fun processAudioFile(uri: android.net.Uri) {
        manager.processAudioFile(uri)
    }

    override fun onCleared() {
        super.onCleared()
        manager.release()
    }
}