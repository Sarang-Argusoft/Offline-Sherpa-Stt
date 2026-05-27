# Sherpa Offline Voice Suite

An offline-first, high-performance Android application featuring fully on-device **Speech-to-Text (STT)** and **Text-to-Speech (TTS)** voice tools. Built in Kotlin, using Android Material 3 design, standard MVVM architecture, and powered by **Sherpa-ONNX** local inference.

---

## 📖 Table of Contents
1. [Overview](#-overview)
2. [Core Features](#-core-features)
   * [Speech-to-Text (STT)](#1-speech-to-text-stt)
   * [Text-to-Speech (TTS)](#2-text-to-speech-tts)
3. [How It Works (Technical Deep-Dive)](#-how-it-works-technical-deep-dive)
   * [Part 1: Speech-to-Text (STT) Engine](#part-1-speech-to-text-stt-engine)
     * [Audio Record Capture & Normalization](#a-audio-record-capture--normalization)
     * [Streaming Transducer Decoding](#b-streaming-transducer-decoding)
     * [Voice Activity & Endpointing](#c-voice-activity--endpointing)
     * [Hotword Contextual Boosting](#d-hotword-contextual-boosting)
     * [File Transcription Pipeline](#e-file-transcription-pipeline)
   * [Part 2: Text-to-Speech (TTS) Engine](#part-2-text-to-speech-tts-engine)
     * [Recursive Asset Deployment](#a-recursive-asset-deployment)
     * [Clinical Pronunciation Preprocessing](#b-clinical-pronunciation-preprocessing)
     * [Low-Latency Float Playback](#c-low-latency-float-playback)
     * [C++ Native Scope Memory Lifecycle](#d-c-native-scope-memory-lifecycle)
4. [Architecture (MVVM)](#-architecture-mvvm)
5. [Directory Layout](#-directory-layout)
6. [Asset Requirements](#-asset-requirements)
7. [Dependencies](#-dependencies)

---

## 🌟 Overview

This application serves as a completely private, offline medical/clinical voice processing suite. It is designed to work **100% on-device** without any cloud APIs, Firebase, or external network requests. All machine learning inference is performed locally via the ONNX Runtime under the Sherpa-ONNX SDK wrapper.

---

## 🎙️ Core Features

### 1. Speech-to-Text (STT)
*   **ASR Model**: Next-Gen Kaldi Zipformer Streaming Transducer.
*   **Action**: Captures live microphone input via Android's low-level recording hardware, processes audio frames on the fly, and displays real-time partial and final transcripts.
*   **Capability**: Custom hotwords configuration to boost recognition accuracy of rare clinical vocabulary. Supports importing local audio WAV files for offline transcription.

### 2. Text-to-Speech (TTS)
*   **TTS Model**: Kokoro English v0.19 (82M parameters).
*   **Action**: Converts raw text inputs into high-quality, natural-sounding offline audio speech.
*   **Capability**: Custom clinical pronunciation preprocessor, float PCM audio stream playback, and responsive immediate playback stopping (interruption).

---

## 🛠️ How It Works (Technical Deep-Dive)

---

### Part 1: Speech-to-Text (STT) Engine

The STT module acts as an asynchronous real-time speech transcriber powered by an on-device Zipformer model. It is managed by `SpeechRecognitionManager.kt`:

#### A. Audio Record Capture & Normalization
*   **Sampling Rate**: Live recording runs at **16,000 Hz Mono** (16-bit PCM), which is the exact acoustic standard expected by the Zipformer feature extractor.
*   **Buffering**: Audio frames are continuously captured via `AudioRecord` in 200ms frame chunks (3,200 short frames per chunk) inside a dedicated `Dispatchers.IO` coroutine.
*   **Normalization**: Raw 16-bit signed shorts (`-32768` to `32767`) are divided by `32768.0f` to normalize them into a `float[]` array ranging from `[-1.0f, 1.0f]` before feeding the model.

#### B. Streaming Transducer Decoding
*   **Recognizer & Stream**: On model load, an `OnlineRecognizer` is configured. Each recording session instantiates a lightweight `OnlineStream` using `recognizer.createStream()`.
*   **Continuous Injection**: The normalized float buffers are injected into the stream via `stream.acceptWaveform(floatSamples, sampleRate)`.
*   **modified_beam_search**: An active loop checks `recognizer.isReady(stream)`. When ready, frames are processed using a low-overhead *modified beam search* decoding algorithm, exposing streaming partial transcripts in real-time.

#### C. Voice Activity & Endpointing
*   **Endpoint Detection**: To segment text without manual button controls, the engine evaluates trailing silence thresholds (`EndpointRule`):
    *   **Rule 1**: 2.4 seconds of absolute trailing silence commits the text.
    *   **Rule 2**: 1.2 seconds of trailing silence after active speech commits the text.
    *   **Rule 3**: Auto-commits speech segments reaching 20 seconds to prevent buffer bloat.
*   **Session Reset**: On endpoint trigger, the partial transcript is committed to the final accumulated transcription block, and the current stream is reset using `recognizer.reset(stream)`.

#### D. Hotword Contextual Boosting
*   **Boosting Mechanism**: To handle highly specialized clinical lexicon, `OnlineRecognizerConfig` is supplied with a local `hotwords.txt` path containing boosted phrases.
*   **Score Penalty**: The FST (Finite State Transducer) search paths in the decoder are biased with a `hotwordsScore` of `20.0f` to raise detection probabilities for those terms.

#### E. File Transcription Pipeline
*   **WAV Parsing**: When a local file URI is uploaded, a custom binary reader extracts the PCM bytes, strips the standard 44-byte WAV header, and reconstructs 16-bit short PCM segments into normalized floats.
*   **Simulated Stream**: Processes files asynchronously in 100ms simulated block intervals (`1,600` float samples) to trigger streaming recognition and endpoint checks.

---

### Part 2: Text-to-Speech (TTS) Engine

The TTS module provides a natural clinical voice voice synthesizer managed under `com.example.sherpastt.tts`:

#### A. Recursive Asset Deployment
Sherpa-ONNX utilizes native C++ engines and requires models to be accessed directly from the system path.
*   **Recursive Copying**: On initial boot, `KokoroTtsEngine` reads the local APK assets and extracts the `kokoro/` folder.
*   **Directory Traversal**: Standard copy utils fail on directories. The engine uses `copyAssetFolder` which queries asset directory structures recursively, correctly building subfolders (such as the detailed `espeak-ng-data` phoneme index) in `context.filesDir` storage.

#### B. Clinical Pronunciation Preprocessing
Due to complex pharmacological and clinical vocabulary, standard Grapheme-to-Phoneme (G2P) mappings often mispronounce clinical words.
*   **Substitute Layer**: Before text is forwarded to the local synthesizer, it passes through the `MedicalPronunciationProcessor` layer.
*   **Word Boundaries**: Leverages word boundary markers (`\b${medicalTerm}\b`) in case-insensitive regular expressions.
*   **Respelled Phonetics**: Replaces medical vocabulary (e.g. *Hydrochlorothiazide*, *Atorvastatin*, *Myocardial infarction*) with phonetic, easily-synthesized spellings (*hydro-chloro-thia-zide*, *a-tore-va-statin*, *myo-cardial infarction*), yielding accurate clinical speech.

#### C. Low-Latency Float Playback
Synthesized wave outputs are mapped directly into physical speakers via Android's hardware audio buffers.
*   **Float Support**: Plays audio using `AudioTrack` configured with `AudioFormat.ENCODING_PCM_FLOAT` in `MODE_STREAM` mode.
*   **Chunked Interruptions**: Writes samples in small `4096` sample chunks. If the user commands a **Stop** action, the loop breaks instantly, flushes hardware queues, and releases the hardware track, bringing playback to an immediate halt.

#### D. C++ Native Scope Memory Lifecycle
Because the models operate in native C++ space, careful memory cleanup is executed to prevent silent RAM leaks on Android:
*   **Audio Disposal**: Immediately after a speech block finishes playing or gets cancelled, the JVM handles are cleaned, and the raw JNI memory buffer is freed via `GeneratedAudio.free()`.
*   **Engine Disposal**: The native engine lives as a reusable single-instance while on the TTS screen. When the user exits the screen, `onCleared()` in the `TtsViewModel` triggers `OfflineTts.release()` to cleanly unload native libraries from active RAM.

---

## 📐 Architecture (MVVM)

The project strictly follows clean architecture principles, ensuring high testability and separation of concerns:

```
+-----------------------------------+
|            UI / View              |   <--- MainActivity (STT) & TtsActivity (TTS)
+-----------------+-----------------+
                  | (observes StateFlow)
                  v
+-----------------+-----------------+
|            ViewModel              |   <--- MainViewModel (STT) & TtsViewModel (TTS)
+-----------------+-----------------+
                  | (dispatches actions)
                  v
+-----------------+-----------------+
|            Domain Layer           |   <--- TtsUseCase (TTS)
+-----------------+-----------------+
                  |
                  v
+-----------------+-----------------+
|            Data Layer             |   <--- SpeechRecognitionManager (STT) & KokoroTtsRepository (TTS)
+-----------------+-----------------+
                  |
         +--------+--------+
         |                 |
         v                 v
+--------+--------+ +------+---------+
| Pronunciation   | | Speech Engine  |  <--- MedicalPronunciationProcessor,
| Preprocessor    | | & Audio Track  |       KokoroTtsEngine & AudioPlayer
+-----------------+ +----------------+
```

---

## 📂 Directory Layout

The new TTS files are cleanly packaged in their own subdirectories, isolated from the existing STT files:

```
app/src/main/java/com/example/sherpastt/
│
├── MainActivity.kt               # Existing STT Activity (Unchanged)
├── SpeechRecognitionManager.kt   # Existing STT Manager (Unchanged)
├── AssetUtils.kt                 # Existing STT Asset Helper (Unchanged)
│
├── HomeActivity.kt               # NEW Launcher Landing Screen
│
└── tts/                          # NEW TTS Sub-package
    ├── ui/
    │   ├── TtsActivity.kt        # TTS Activity View Binding & Observers
    │   └── TtsViewModel.kt       # StateFlow owner & JNI Release executor
    │
    ├── domain/
    │   └── TtsUseCase.kt         # Decoupled Domain use-case
    │
    ├── data/
    │   └── KokoroTtsRepository.kt# Preprocessing, Synthesis & Playback Coordinator
    │
    ├── engine/
    │   ├── KokoroTtsEngine.kt    # OfflineTts wrapper & recursive copy installer
    │   ├── AudioPlayer.kt        # Float PCM streaming AudioTrack loop
    │   └── PronunciationProcessor.kt# Clinical phonetic map & regex mapper
    │
    └── model/
        └── TtsUiState.kt         # TTS view state representation
```

---

## 📦 Asset Requirements

The offline voice suite requires ASR and TTS model assets to be packaged inside the assets folder.

Make sure the following directory structure is set up:
```
app/src/main/assets/
│
├── hotwords.txt                  # Custom ASR hotwords list
│
├── sherpa-onnx-streaming-zipformer-en-2023-06-26/  # ASR Model Files
│   ├── encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx
│   ├── decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx
│   ├── joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx
│   └── tokens.txt
│
└── kokoro/                       # TTS Model Files
    ├── model.onnx                # Kokoro ONNX model
    ├── voices.bin                # Voice embeddings
    ├── tokens.txt                # Tokenizer mappings
    └── espeak-ng-data/           # Phonemization dictionaries
        ├── phontab
        ├── phonindex
        ├── phondata
        ├── intonation
        └── ... (rest of espeak dictionaries)
```

---

## ⚡ Dependencies

This project relies on the following central libraries defined in your `app/build.gradle.kts`:

```kotlin
dependencies {
    // Core Offline Speech SDK (incorporates both ASR & TTS native runtimes)
    implementation("com.github.k2-fsa:sherpa-onnx:1.13.1")

    // Coroutines (Background execution & non-blocking streams)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Jetpack Lifecycle & KTX Extensions
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-ktx:1.9.0")
}
```
