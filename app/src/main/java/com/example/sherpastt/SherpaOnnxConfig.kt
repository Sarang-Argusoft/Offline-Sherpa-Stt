package com.example.sherpastt

/**
 * Configuration for Sherpa-ONNX Zipformer English model.
 *
 * Model used: sherpa-onnx-streaming-zipformer-en-2023-06-26
 * Download from: https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
 *
 * Place the unzipped model folder inside:
 *   app/src/main/assets/sherpa-onnx-streaming-zipformer-en-2023-06-26/
 *
 * Required files inside that folder:
 *   - encoder-epoch-99-avg-1.int8.onnx
 *   - decoder-epoch-99-avg-1.int8.onnx
 *   - joiner-epoch-99-avg-1.int8.onnx
 *   - tokens.txt
 */
object SherpaOnnxConfig {

    // ----- Model folder name (inside assets/) -----
    const val MODEL_DIR = "sherpa-onnx-streaming-zipformer-en-2023-06-26"

    // ----- Model file names -----
    // Using int8 (quantized) versions → smaller size, nearly identical accuracy
    const val ENCODER  = "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
    const val DECODER  = "decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
    const val JOINER   = "joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
    const val TOKENS   = "tokens.txt"

    // ----- Audio settings -----
    const val SAMPLE_RATE      = 16000   // Zipformer expects 16kHz mono
    const val CHANNELS         = 1
    const val BITS_PER_SAMPLE  = 16

    // ----- Recognizer settings -----
    const val NUM_THREADS           = 2      // Increase to 4 on high-end devices
    const val ENABLE_ENDPOINT       = true   // Auto-detect end of speech
    const val RULE1_MIN_TRAILING_SILENCE = 2.4f  // seconds of silence → endpoint
    const val RULE2_MIN_TRAILING_SILENCE = 1.2f
    const val RULE3_MIN_UTTERANCE_LENGTH = 20.0f

    // Buffer size for AudioRecord (in frames read per chunk)
    const val FRAMES_PER_READ = 3200  // 200ms at 16kHz
}