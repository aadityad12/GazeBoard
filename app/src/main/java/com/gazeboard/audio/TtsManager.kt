package com.gazeboard.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    companion object {
        private const val TAG = "GazeBoard"
    }

    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                // Silent warmup eliminates cold-start delay on first real speak
                tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, "warmup")
                ready = true
                Log.i(TAG, "TTS engine ready")
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
    }

    /** Speak a phrase immediately, interrupting any current speech. */
    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
        Log.i(TAG, "TTS speak: $text")
    }

    /** Speak gesture audio feedback at reduced volume. */
    fun speakFeedback(text: String) {
        if (!ready || text.isBlank()) return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.5f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "feedback_$text")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
