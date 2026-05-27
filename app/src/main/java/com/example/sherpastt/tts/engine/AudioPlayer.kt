package com.example.sherpastt.tts.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.jvm.Synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AudioPlayer {

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    /**
     * Plays the float PCM samples at the specified sample rate in a background thread.
     * Suspends until playback is finished or cancelled.
     */
    suspend fun play(samples: FloatArray, sampleRate: Int, onComplete: () -> Unit) {
        withContext(Dispatchers.IO) {
            try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            val bufferSize = maxOf(minBufferSize, samples.size * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            isPlaying = true
            track.play()

            // Write PCM float samples in smaller chunks so we can cancel playback instantly
            val chunkSize = 4096
            var offset = 0
            while (offset < samples.size && isPlaying && isActive) {
                val size = minOf(chunkSize, samples.size - offset)
                val written = track.write(samples, offset, size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                    break
                }
                offset += size
            }

            // Wait for the track to finish playing the written data
            if (isPlaying && isActive) {
                val remainingSamples = samples.size
                val playDurationMs = (remainingSamples.toFloat() / sampleRate * 1000).toLong()
                delay(playDurationMs + 100) // add a small buffer for hardware latency
            }
            Unit
        } catch (e: CancellationException) {
            Log.d(TAG, "Playback coroutine cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Playback error", e)
        } finally {
            stopAndRelease()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    } }

    /**
     * Instantly stops playback and frees resources.
     */
    fun stop() {
        isPlaying = false
        stopAndRelease()
    }

    @Synchronized
    private fun stopAndRelease() {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.flush()
                track.release()
                Log.d(TAG, "AudioTrack stopped and released successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
            isPlaying = false
        }
    }
}
