package com.example.sherpastt.tts.engine

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class KokoroTtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "KokoroTtsEngine"
        private const val KOKORO_ASSET_DIR = "kokora"
    }

    private var tts: OfflineTts? = null
    private val mutex = Mutex()

    /**
     * Checks if all required Kokoro assets exist in application files directory.
     */
    fun isModelReady(): Boolean {
        val destDir = File(context.filesDir, KOKORO_ASSET_DIR)
        val modelFile = File(destDir, "model.onnx")
        val voicesFile = File(destDir, "voices.bin")
        val tokensFile = File(destDir, "tokens.txt")
        val espeakDir = File(destDir, "espeak-ng-data")
        return modelFile.exists() && voicesFile.exists() && tokensFile.exists() && espeakDir.exists() && espeakDir.isDirectory
    }

    /**
     * Initializes ASR, copying assets if needed, and building the OfflineTts class.
     * This is a thread-safe singleton initialization.
     */
    suspend fun initialize() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (tts != null) {
                Log.d(TAG, "KokoroTtsEngine already initialized. Reusing model.")
                return@withContext
            }

            try {
            val destDir = File(context.filesDir, KOKORO_ASSET_DIR)
            if (!isModelReady()) {
                Log.d(TAG, "Copying Kokoro assets recursively...")
                copyAssetFolder(context, KOKORO_ASSET_DIR, destDir)
            } else {
                Log.d(TAG, "Kokoro assets already present in storage.")
            }

            val modelPath = File(destDir, "model.onnx").absolutePath
            val voicesPath = File(destDir, "voices.bin").absolutePath
            val tokensPath = File(destDir, "tokens.txt").absolutePath
            val espeakDataPath = File(destDir, "espeak-ng-data").absolutePath

            Log.d(TAG, "Initializing OfflineTts with Kokoro model...")
            
            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model = modelPath,
                voices = voicesPath,
                tokens = tokensPath,
                dataDir = espeakDataPath,
                lengthScale = 1.0f
            )

            val modelConfig = OfflineTtsModelConfig(
                kokoro = kokoroConfig,
                numThreads = 2,
                debug = false
            )

            val config = OfflineTtsConfig(
                model = modelConfig
            )

            tts = OfflineTts(config = config)
            Log.d(TAG, "OfflineTts initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS engine", e)
            throw e
        }
    } }

    /**
     * Synthesizes the input text into GeneratedAudio.
     */
    suspend fun generateSpeech(text: String, speed: Float = 1.0f): GeneratedAudio = withContext(Dispatchers.Default) {
        val engine = tts ?: throw IllegalStateException("TTS Engine is not initialized")
        Log.d(TAG, "Generating speech for text: \"$text\" with speed: $speed")
        // Kokoro speaker id 0 is default English voice
        engine.generate(text, 0, speed)
    }

    /**
     * Helper to recursively copy files and folders from Android assets to external filesDir.
     */
    private fun copyAssetFolder(context: Context, assetPath: String, destDir: File) {
        val assetManager = context.assets
        val list = assetManager.list(assetPath)
        if (list == null || list.isEmpty()) {
            // Copy file
            if (!destDir.parentFile.exists()) {
                destDir.parentFile.mkdirs()
            }
            if (!destDir.exists()) {
                assetManager.open(assetPath).use { input ->
                    destDir.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied file: $assetPath -> ${destDir.absolutePath}")
            }
        } else {
            // Create directory and recurse
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            for (file in list) {
                copyAssetFolder(context, "$assetPath/$file", File(destDir, file))
            }
        }
    }

    /**
     * Cleans up the native TTS instance.
     */
    fun release() {
        tts?.release()
        tts = null
        Log.d(TAG, "KokoroTtsEngine resources released")
    }
}
