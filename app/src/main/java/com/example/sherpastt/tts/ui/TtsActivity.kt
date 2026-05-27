package com.example.sherpastt.tts.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sherpastt.R
import com.example.sherpastt.databinding.ActivityTtsBinding
import com.example.sherpastt.tts.model.TtsUiState
import kotlinx.coroutines.launch

class TtsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTtsBinding
    private val viewModel: TtsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTtsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSpeak.setOnClickListener {
            hideKeyboard()
            val text = binding.etTtsInput.text.toString()
            viewModel.speak(text)
        }

        binding.btnStop.setOnClickListener {
            viewModel.stop()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUiState(state)
                }
            }
        }
    }

    private fun updateUiState(state: TtsUiState) {
        when (state) {
            is TtsUiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.btnSpeak.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.tvStatus.text = "Status: Ready"
            }
            is TtsUiState.Initializing -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnSpeak.isEnabled = false
                binding.btnStop.isEnabled = false
                binding.tvStatus.text = getString(R.string.status_tts_initializing)
            }
            is TtsUiState.Ready -> {
                binding.progressBar.visibility = View.GONE
                binding.btnSpeak.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.tvStatus.text = "Status: Ready"
            }
            is TtsUiState.Generating -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnSpeak.isEnabled = false
                binding.btnStop.isEnabled = true
                binding.tvStatus.text = getString(R.string.status_tts_generating)
            }
            is TtsUiState.Playing -> {
                binding.progressBar.visibility = View.GONE
                binding.btnSpeak.isEnabled = false
                binding.btnStop.isEnabled = true
                binding.tvStatus.text = getString(R.string.status_tts_playing)
            }
            is TtsUiState.Completed -> {
                binding.progressBar.visibility = View.GONE
                binding.btnSpeak.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.tvStatus.text = "Status: Ready"
            }
            is TtsUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.btnSpeak.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.tvStatus.text = "Status: Error"
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etTtsInput.windowToken, 0)
    }
}
