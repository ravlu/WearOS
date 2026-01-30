package com.pradyu.talkingtom.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

enum class TomState {
    IDLE,
    LISTENING,
    TALKING
}

class AudioEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var mainLoopJob: Job? = null

    private val _state = MutableStateFlow(TomState.IDLE)
    val state: StateFlow<TomState> = _state

    private val sampleRate = 16000
    private val playbackRate = (sampleRate * 1.5).toInt() // Chipmunk effect: 1.5x speed/pitch
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)

    // Thresholds
    private val silenceThreshold = 1000 // Adjusted threshold
    private val maxSilenceDuration = 1000L // 1 second of silence triggers playback
    private val maxRecordingDuration = 10000L // Max 10 seconds recording

    private val recordedData = ByteArrayOutputStream()

    fun start() {
        if (mainLoopJob?.isActive == true) return

        mainLoopJob = scope.launch {
            while (isActive) {
                // 1. Listen
                if (!recordAudio()) {
                    // If recording failed (e.g. permission), wait a bit and retry or exit
                    delay(1000)
                    continue
                }

                // 2. Playback if we have data
                if (recordedData.size() > 0) {
                    playAudio()
                }

                // Reset buffer
                recordedData.reset()
            }
        }
    }

    fun stop() {
        mainLoopJob?.cancel()
        _state.value = TomState.IDLE
    }

    private suspend fun recordAudio(): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        _state.value = TomState.LISTENING
        recordedData.reset()

        return try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfigIn,
                audioFormat,
                bufferSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) return false

            val buffer = ShortArray(bufferSize)
            recorder.startRecording()

            var lastSoundTime = System.currentTimeMillis()
            var startTime = System.currentTimeMillis()
            var hasSpoken = false

            while (true) {
                val read = recorder.read(buffer, 0, bufferSize)
                if (read < 0) break // Error

                // Calculate RMS
                var sum = 0L
                for (i in 0 until read) {
                    sum += buffer[i] * buffer[i]
                }
                val rms = if (read > 0) sqrt((sum / read).toDouble()) else 0.0

                // Write to stream
                val bytes = ByteArray(read * 2)
                for (i in 0 until read) {
                    val s = buffer[i].toInt()
                    bytes[i * 2] = (s and 0x00FF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0x00FF).toByte()
                }
                recordedData.write(bytes)

                val currentTime = System.currentTimeMillis()

                if (rms > silenceThreshold) {
                    lastSoundTime = currentTime
                    hasSpoken = true
                }

                // Check silence
                if (hasSpoken && (currentTime - lastSoundTime > maxSilenceDuration)) {
                    break
                }

                // Check max duration
                if (currentTime - startTime > maxRecordingDuration) {
                    break
                }
            }

            recorder.stop()
            recorder.release()
            hasSpoken
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun playAudio() {
        _state.value = TomState.TALKING
        try {
            val audioData = recordedData.toByteArray()
            if (audioData.isEmpty()) return

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(playbackRate)
                        .setChannelMask(channelConfigOut)
                        .build()
                )
                .setBufferSizeInBytes(audioData.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(audioData, 0, audioData.size)
            track.play()

            // Calculate duration: (bytes / 2 bytes per sample) / sampleRate
            // Note: Use playbackRate for calculation
            val durationMs = ((audioData.size / 2.0) / playbackRate * 1000).toLong()
            delay(durationMs)

            track.stop()
            track.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
