package com.gazeboard

import android.app.Application
import com.gazeboard.audio.TtsManager

class GazeBoardApplication : Application() {

    lateinit var ttsManager: TtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager(this)
        ttsManager.init()
    }

    override fun onTerminate() {
        ttsManager.shutdown()
        super.onTerminate()
    }
}
