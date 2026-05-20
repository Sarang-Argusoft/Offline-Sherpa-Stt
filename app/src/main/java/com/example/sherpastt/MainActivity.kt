package com.example.sherpastt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sherpastt.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val pickAudioLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.processAudioFile(it) }
        }

    // ── Permission launcher ──────────────────────────────────────────────────
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.startListening()
            } else {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
            }
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

    // ── UI setup ─────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnMic.setOnClickListener {
            when (viewModel.state.value) {
                is SpeechRecognitionManager.State.Listening -> viewModel.stopListening()
                is SpeechRecognitionManager.State.Ready     -> checkPermissionAndStart()
                else -> { /* ignore taps during init / error */ }
            }
        }

        binding.btnClear.setOnClickListener {
            viewModel.clearTranscript()
        }

        binding.btnUpload.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observe state
                launch {
                    viewModel.state.collect { state -> updateUiForState(state) }
                }

                // Observe partial (streaming) result
                launch {
                    viewModel.partialResult.collect { partial ->
                        binding.tvPartial.text = if (partial.isEmpty()) "" else "…$partial"
                    }
                }

                // Observe committed final result
                launch {
                    viewModel.finalResult.collect { final ->
                        binding.tvTranscript.text = final.ifEmpty { getString(R.string.transcript_hint) }
                    }
                }
            }
        }
    }

    // ── State → UI ───────────────────────────────────────────────────────────

    private fun updateUiForState(state: SpeechRecognitionManager.State) {
        when (state) {
            is SpeechRecognitionManager.State.Idle -> {
                binding.progressBar.visibility  = View.VISIBLE
                binding.btnMic.isEnabled        = false
                binding.tvStatus.text           = getString(R.string.status_idle)
                binding.micWaveform.visibility  = View.INVISIBLE
            }

            is SpeechRecognitionManager.State.Initializing -> {
                binding.progressBar.visibility  = View.VISIBLE
                binding.btnMic.isEnabled        = false
                binding.tvStatus.text           = getString(R.string.status_loading)
                binding.micWaveform.visibility  = View.INVISIBLE
            }

            is SpeechRecognitionManager.State.Ready -> {
                binding.progressBar.visibility  = View.GONE
                binding.btnMic.isEnabled        = true
                binding.btnMic.setImageResource(R.drawable.ic_mic)
                binding.tvStatus.text           = getString(R.string.status_ready)
                binding.micWaveform.visibility  = View.INVISIBLE
                binding.btnMic.contentDescription = getString(R.string.btn_start)
                binding.btnUpload.isEnabled     = true
            }

            is SpeechRecognitionManager.State.Listening -> {
                binding.progressBar.visibility  = View.GONE
                binding.btnMic.isEnabled        = true
                binding.btnMic.setImageResource(R.drawable.ic_mic_active)
                binding.tvStatus.text           = getString(R.string.status_listening)
                binding.micWaveform.visibility  = View.VISIBLE
                binding.btnMic.contentDescription = getString(R.string.btn_stop)
                binding.btnUpload.isEnabled     = false
            }

            is SpeechRecognitionManager.State.Processing -> {
                binding.progressBar.visibility  = View.VISIBLE
                binding.btnMic.isEnabled        = false
                binding.tvStatus.text           = "Processing file..."
                binding.micWaveform.visibility  = View.INVISIBLE
                binding.btnUpload.isEnabled     = false
            }

            is SpeechRecognitionManager.State.Error -> {
                binding.progressBar.visibility  = View.GONE
                binding.btnMic.isEnabled        = false
                binding.tvStatus.text           = "Error: ${state.message}"
                binding.micWaveform.visibility  = View.INVISIBLE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Permission ───────────────────────────────────────────────────────────

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> {
                viewModel.startListening()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(
                    this,
                    "This app needs the microphone to transcribe speech.",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}