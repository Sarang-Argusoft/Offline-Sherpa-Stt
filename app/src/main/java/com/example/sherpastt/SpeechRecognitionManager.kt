package com.example.sherpastt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the full lifecycle of offline STT using Sherpa-ONNX Zipformer.
 *
 * Usage:
 *  1. Call [initialize] once (suspends while copying model files).
 *  2. Observe [partialResult] and [finalResult] StateFlows.
 *  3. Call [startListening] / [stopListening] to control recording.
 *  4. Call [release] in onDestroy.
 */
class SpeechRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "SherpaSTT"
    }

    // ── Public state ─────────────────────────────────────────────────────────

    sealed class State {
        object Idle        : State()
        object Initializing: State()
        object Ready       : State()
        object Listening   : State()
        object Processing  : State()
        data class Error(val message: String) : State()
    }

    private val _state          = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Streaming partial transcript (updates while speaking) */
    private val _partialResult          = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    /** Committed final transcript segments */
    private val _finalResult          = MutableStateFlow("")
    val finalResult: StateFlow<String> = _finalResult.asStateFlow()

    // ── Private fields ───────────────────────────────────────────────────────

    private var recognizer    : OnlineRecognizer? = null
    private var stream        : OnlineStream?     = null
    private var audioRecord   : AudioRecord?      = null
    private var recordingJob  : Job?              = null
    private var processingJob : Job?              = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val fullTranscript = StringBuilder()

    // ── Initialization ───────────────────────────────────────────────────────

    /**
     * Copies model from assets and creates the Sherpa-ONNX recognizer.
     * Call this from a coroutine (e.g. viewModelScope.launch).
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        _state.value = State.Initializing
        try {
            // 1. Copy model files from assets → internal storage
            val modelDir = AssetUtils.copyModelToStorage(
                context,
                SherpaOnnxConfig.MODEL_DIR
            )

            // ── NEW — copy hotwords.txt to internal storage ───────────────────────
            val hotwordsPath = copyHotwordsToStorage()

            // 2. Build Zipformer transducer config
            val transducer = OnlineTransducerModelConfig(
                encoder = "$modelDir/${SherpaOnnxConfig.ENCODER}",
                decoder = "$modelDir/${SherpaOnnxConfig.DECODER}",
                joiner  = "$modelDir/${SherpaOnnxConfig.JOINER}"
            )

            val modelConfig = OnlineModelConfig(
                transducer  = transducer,
                tokens      = "$modelDir/${SherpaOnnxConfig.TOKENS}",
                numThreads  = SherpaOnnxConfig.NUM_THREADS,
                debug       = false,
                modelType   = "zipformer2"
            )

            // 3. Endpoint detection rules
            val endpointConfig = EndpointConfig(
                rule1 = EndpointRule(
                    mustContainNonSilence = false,
                    minTrailingSilence    = SherpaOnnxConfig.RULE1_MIN_TRAILING_SILENCE,
                    minUtteranceLength    = 0.0f
                ),
                rule2 = EndpointRule(
                    mustContainNonSilence = true,
                    minTrailingSilence    = SherpaOnnxConfig.RULE2_MIN_TRAILING_SILENCE,
                    minUtteranceLength    = 0.0f
                ),
                rule3 = EndpointRule(
                    mustContainNonSilence = true,
                    minTrailingSilence    = 0.0f,
                    minUtteranceLength    = SherpaOnnxConfig.RULE3_MIN_UTTERANCE_LENGTH
                )
            )

            // 4. Assemble recognizer config
            val config = OnlineRecognizerConfig(
                featConfig      = FeatureConfig(sampleRate = SherpaOnnxConfig.SAMPLE_RATE),
                modelConfig     = modelConfig,
                endpointConfig  = endpointConfig,
                enableEndpoint  = SherpaOnnxConfig.ENABLE_ENDPOINT,
                decodingMethod  = "modified_beam_search",
                hotwordsFile   = hotwordsPath,
                hotwordsScore  = SherpaOnnxConfig.HOTWORDS_SCORE
            )

            // 5. Create the recognizer
            recognizer = OnlineRecognizer(config = config)
            stream     = recognizer!!.createStream()

            _state.value = State.Ready
            Log.d(TAG, "Sherpa-ONNX initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            _state.value = State.Error("Initialization failed: ${e.message}")
        }
    }

    // ── Recording control ────────────────────────────────────────────────────

    fun startListening() {
        if (_state.value != State.Ready) {
            Log.w(TAG, "Cannot start: not in Ready state (${_state.value})")
            return
        }
        if (!hasAudioPermission()) {
            _state.value = State.Error("RECORD_AUDIO permission not granted")
            return
        }

        _state.value = State.Listening
        // Reset transcript for this session
        fullTranscript.clear()
        _partialResult.value = ""
        _finalResult.value   = ""

        // Reset stream for new recognition session
        stream?.release()
        stream = recognizer!!.createStream()

        recordingJob = scope.launch { captureAndRecognize() }
    }

    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        flushStream()

        _state.value = State.Ready
        Log.d(TAG, "Stopped listening")
    }

    private fun flushStream() {
        stream?.let { s ->
            recognizer?.decode(s)
            val lastText = recognizer?.getResult(s)?.text?.trim() ?: ""
            if (lastText.isNotEmpty()) {
                fullTranscript.append(" $lastText")
                _finalResult.value = fullTranscript.toString().trim()
            }
        }
    }

    // ── File processing ──────────────────────────────────────────────────────

    fun processAudioFile(uri: android.net.Uri) {
        if (_state.value != State.Ready) {
            Log.w(TAG, "Cannot process file: not in Ready state (${_state.value})")
            return
        }

        // Cancel any existing jobs
        recordingJob?.cancel()
        processingJob?.cancel()

        _state.value = State.Processing
        fullTranscript.clear()
        _partialResult.value = ""
        _finalResult.value   = ""

        stream?.release()
        stream = recognizer!!.createStream()

        processingJob = scope.launch {
            try {
                val samples = readAudioSamplesFromUri(uri)
                if (samples != null && isActive) {
                    processSamples(samples)
                } else if (isActive) {
                    _state.value = State.Error("Failed to read audio file (unsupported format or empty)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "File processing error", e)
                if (isActive) _state.value = State.Error("File error: ${e.message}")
            } finally {
                if (isActive) _state.value = State.Ready
            }
        }
    }

    private suspend fun readAudioSamplesFromUri(uri: android.net.Uri): FloatArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // This is a simplified WAV reader.
                val header = ByteArray(44)
                if (inputStream.read(header) != 44) return@withContext null
                
                // Optional: Check if it's a valid WAV (RIFF...WAVE)
                // if (String(header.sliceArray(0..3)) != "RIFF") ...

                val bytes = inputStream.readBytes()
                val shortCount = bytes.size / 2
                val samples = FloatArray(shortCount)
                for (i in 0 until shortCount) {
                    val b1 = bytes[i * 2].toInt() and 0xFF
                    val b2 = bytes[i * 2 + 1].toInt()
                    val shortVal = ((b2 shl 8) or b1).toShort()
                    samples[i] = shortVal / 32768.0f
                }
                return@withContext samples
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading URI", e)
        }
        null
    }

    private suspend fun processSamples(samples: FloatArray) = withContext(Dispatchers.Default) {
        val chunkSize = 1600 // 100ms at 16kHz
        var offset = 0
        
        // Ensure stream is fresh and only one processing job is active
        stream?.inputFinished() 

        while (offset < samples.size && isActive) {
            val count = minOf(chunkSize, samples.size - offset)
            val chunk = samples.sliceArray(offset until offset + count)
            
            stream!!.acceptWaveform(chunk, SherpaOnnxConfig.SAMPLE_RATE)
            while (recognizer!!.isReady(stream!!)) {
                recognizer!!.decode(stream!!)
            }

            val result = recognizer!!.getResult(stream!!)
            val partial = result.text.trim()
            if (partial.isNotEmpty()) {
                _partialResult.value = partial
            }

            if (recognizer!!.isEndpoint(stream!!)) {
                if (partial.isNotEmpty()) {
                    fullTranscript.append(if (fullTranscript.isEmpty()) partial else " $partial")
                    _finalResult.value = fullTranscript.toString().trim()
                    _partialResult.value = ""
                }
                recognizer!!.reset(stream!!)
            }
            
            offset += count
        }
        stream!!.inputFinished()
        while (recognizer!!.isReady(stream!!)) {
            recognizer!!.decode(stream!!)
        }
        flushStream()
    }

    // ── Core capture + decode loop ───────────────────────────────────────────

    private suspend fun captureAndRecognize() = withContext(Dispatchers.IO) {
        val minBufSize = AudioRecord.getMinBufferSize(
            SherpaOnnxConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, SherpaOnnxConfig.FRAMES_PER_READ * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SherpaOnnxConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            _state.value = State.Error("AudioRecord init failed")
            return@withContext
        }

        audioRecord!!.startRecording()
        Log.d(TAG, "AudioRecord started — sample rate: ${SherpaOnnxConfig.SAMPLE_RATE}Hz")

        val buf = ShortArray(SherpaOnnxConfig.FRAMES_PER_READ)

        try {
            while (isActive) {
                val read = audioRecord!!.read(buf, 0, buf.size)
                if (read <= 0) continue

                // Convert Short PCM → Float samples expected by Sherpa
                val floatSamples = FloatArray(read) { buf[it] / 32768.0f }

                stream!!.acceptWaveform(floatSamples, SherpaOnnxConfig.SAMPLE_RATE)

                // Decode all buffered audio
                while (recognizer!!.isReady(stream!!)) {
                    recognizer!!.decode(stream!!)
                }

                val result  = recognizer!!.getResult(stream!!)
                val partial = result.text.trim()

                // Update partial (live) transcript
                if (partial.isNotEmpty()) {
                    _partialResult.value = partial
                }

                // Endpoint detected → commit this segment as final
                if (recognizer!!.isEndpoint(stream!!)) {
                    val committed = partial
                    if (committed.isNotEmpty()) {
                        fullTranscript.append(if (fullTranscript.isEmpty()) committed else " $committed")
                        _finalResult.value   = fullTranscript.toString().trim()
                        _partialResult.value = ""
                        Log.d(TAG, "Endpoint — committed: \"$committed\"")
                    }
                    // Reset stream for next utterance
                    recognizer!!.reset(stream!!)
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Recording coroutine cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Recording error", e)
            _state.value = State.Error("Recording error: ${e.message}")
        } finally {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    // ── NEW function — copies hotwords.txt from assets → internal storage ────────
    private fun copyHotwordsToStorage(): String {
        val destFile = java.io.File(
            context.filesDir,
            SherpaOnnxConfig.HOTWORDS_FILE
        )
        if (!destFile.exists()) {
            context.assets.open(SherpaOnnxConfig.HOTWORDS_FILE).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Hotwords copied to: ${destFile.absolutePath}")
    }
    return destFile.absolutePath
}

    // ── Helpers ──────────────────────────────────────────────────────────────

    fun clearTranscript() {
        fullTranscript.clear()
        _partialResult.value = ""
        _finalResult.value   = ""
    }

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun release() {
        stopListening()
        stream?.release()
        stream = null
        recognizer?.release()
        recognizer = null
        scope.cancel()
        Log.d(TAG, "Resources released")
    }
}