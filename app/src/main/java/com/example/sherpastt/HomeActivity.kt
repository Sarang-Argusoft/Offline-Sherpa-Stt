package com.example.sherpastt

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.sherpastt.databinding.ActivityHomeBinding
import com.example.sherpastt.tts.ui.TtsActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardStt.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        binding.cardTts.setOnClickListener {
            val intent = Intent(this, TtsActivity::class.java)
            startActivity(intent)
        }
    }
}
