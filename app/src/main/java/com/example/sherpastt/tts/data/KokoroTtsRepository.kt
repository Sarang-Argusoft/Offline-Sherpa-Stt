package com.example.sherpastt.tts.data

import android.content.Context
import android.util.Log
import com.example.sherpastt.tts.engine.AudioPlayer
import com.example.sherpastt.tts.engine.KokoroTtsEngine
import com.example.sherpastt.tts.engine.MedicalPronunciationProcessor
import com.example.sherpastt.tts.engine.PronunciationProcessor
import com.example.sherpastt.tts.model.TtsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KokoroTtsRepository(context: Context) {

    companion object {
        private const val TAG = "KokoroTtsRepository"
    }

    private val engine = KokoroTtsEngine(context)
    private val player = AudioPlayer()
    private val pronunciationProcessor: PronunciationProcessor = MedicalPronunciationProcessor()

    suspend fun initialize() {
        engine.initialize()
    }

    /**
     * Preprocesses text, generates speech, and streams playback.
     */
    suspend fun speak(text: String, onStateChange: (TtsUiState) -> Unit) = withContext(Dispatchers.Default) {
        try {
            onStateChange(TtsUiState.Generating)
            
            // 1. Apply phonetic substitutions to clinical medical terminology
            val processedText = pronunciationProcessor.process(text)
            Log.d(TAG, "Original text: \"$text\" -> Pronounced text: \"$processedText\"")

            // 2. Local inference to generate speech float PCM samples
            val generatedAudio = engine.generateSpeech(processedText)
            val samples = generatedAudio.samples
            val sampleRate = generatedAudio.sampleRate

            onStateChange(TtsUiState.Playing)

            // 3. Play generated float audio PCM samples using AudioPlayer
            player.play(samples, sampleRate) {
                onStateChange(TtsUiState.Completed)
            }

        } catch (e: CancellationException) {
            Log.d(TAG, "Coroutine was cancelled during TTS generation or playback")
            onStateChange(TtsUiState.Completed)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak execution failed", e)
            onStateChange(TtsUiState.Error("Synthesis/Playback failed: ${e.message}"))
        }
    }

    /**
     * Stop active audio track playback.
     */
    fun stop() {
        player.stop()
    }

    /**
     * Free TTS resources.
     */
    fun release() {
        engine.release()
    }
}
