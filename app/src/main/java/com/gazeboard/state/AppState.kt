package com.gazeboard.state

sealed class AppState {
    /** 4-point corner calibration in progress */
    object Calibrating : AppState()

    /** Calibrated; actively tracking gaze and mapping to cells */
    object Tracking : AppState()

    /** A cell was selected — TTS is speaking; UI shows selection highlight */
    data class Selected(val cellIndex: Int) : AppState()

    /** Brief post-selection lockout to prevent double-trigger */
    object Cooldown : AppState()
}
