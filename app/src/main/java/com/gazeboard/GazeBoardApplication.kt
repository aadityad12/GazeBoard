package com.gazeboard

import android.app.Application
import android.util.Log
import com.gazeboard.audio.TtsManager

class GazeBoardApplication : Application() {

    // Singleton TTS manager — pre-warmed here so first selection has no delay
    lateinit var ttsManager: TtsManager
        private set

    override fun onCreate() {
        super.onCreate()

        ttsManager = TtsManager(this)
        ttsManager.initialize {
            Log.i(TAG, "TTS engine ready")
        }
    }

    override fun onTerminate() {
        ttsManager.shutdown()
        super.onTerminate()
    }

    companion object {
        private const val TAG = "GazeBoard"
    }
}
