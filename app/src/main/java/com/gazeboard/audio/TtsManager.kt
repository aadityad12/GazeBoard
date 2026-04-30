package com.gazeboard.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Android TextToSpeech wrapper with pre-warming support.
 *
 * PERSON C OWNS THIS FILE.
 *
 * Pre-warm by calling initialize() in Application.onCreate() so the TTS engine
 * is ready before the first phrase selection. The first speak() call after a cold
 * TTS init has ~200ms delay; all subsequent calls are instant.
 *
 * Uses QUEUE_FLUSH mode — new speech immediately interrupts any current speech.
 * This is intentional: if the user looks at a new cell before TTS finishes,
 * the new phrase should win.
 */
class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    companion object {
        private const val TAG = "GazeBoard"
    }

    /**
     * Initialize the TTS engine. Safe to call from any thread.
     * [onReady] is invoked on an internal TTS thread when the engine is available.
     *
     * TODO(Person C): Call this from GazeBoardApplication.onCreate().
     */
    fun initialize(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "TTS: US English not supported — using device default")
                    tts?.language = Locale.getDefault()
                }
                isReady = true
                onReady()
                Log.i(TAG, "TTS engine initialized")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    /**
     * Speak [phrase] immediately, interrupting any current speech.
     *
     * Safe to call from any thread. No-op if TTS is not yet ready.
     *
     * @param phrase The text to speak. Should be a short phrase (< 10 words).
     */
    fun speak(phrase: String) {
        if (!isReady) {
            Log.w(TAG, "speak() called before TTS is ready")
            return
        }
        // QUEUE_FLUSH interrupts current speech — intentional for responsiveness
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, phrase.hashCode().toString())
        Log.d(TAG, "TTS: speaking \"$phrase\"")
    }

    /**
     * Release TTS resources. Call from Application.onTerminate() or ViewModel.onCleared().
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.i(TAG, "TTS shutdown")
    }

    val ready: Boolean get() = isReady
}
