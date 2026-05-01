package com.gazeboard.calibration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Four-point gaze calibration.
 *
 * The user looks at each screen corner in order: TL → TR → BL → BR.
 * After all 4 points are collected, we compute the pitch/yaw midpoint and
 * use it as the threshold for quadrant mapping.
 *
 * mapToQuadrant() returns 1..4:
 *   1 = Top-Left    2 = Top-Right
 *   3 = Bottom-Left 4 = Bottom-Right
 *
 * Calibration data is persisted in SharedPreferences so the user does not
 * need to recalibrate after every app restart.
 */
class CalibrationEngine(private val context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Midpoints computed from 4-corner calibration
    private var pitchMid = 0f
    private var yawMid   = 0f

    var isCalibrated = false
        private set

    // Per-corner accumulation during calibration
    private val accumPitch = mutableListOf<Float>()
    private val accumYaw   = mutableListOf<Float>()

    // Committed corner samples: TL, TR, BL, BR (pitch, yaw)
    private val corners = mutableListOf<Pair<Float, Float>>()

    val currentStep: Int get() = corners.size
    val totalSteps: Int = 4

    init {
        restoreFromPrefs()
    }

    /** Accumulate a raw pitch/yaw sample for the current corner. */
    fun accumulateSample(pitch: Float, yaw: Float) {
        accumPitch.add(pitch)
        accumYaw.add(yaw)
    }

    /**
     * Commit the accumulated samples for the current corner.
     * Returns true when all 4 corners are done and calibration is complete.
     */
    fun commitCorner(): Boolean {
        if (accumPitch.isEmpty()) return false

        val avgPitch = accumPitch.average().toFloat()
        val avgYaw   = accumYaw.average().toFloat()
        corners.add(Pair(avgPitch, avgYaw))
        accumPitch.clear()
        accumYaw.clear()

        Log.d(TAG, "Corner ${corners.size} committed: pitch=$avgPitch, yaw=$avgYaw")

        if (corners.size >= 4) {
            computeMapping()
            isCalibrated = true
            saveToPrefs()
            return true
        }
        return false
    }

    private fun computeMapping() {
        // Corners: TL=0, TR=1, BL=2, BR=3
        pitchMid = corners.map { it.first }.average().toFloat()
        yawMid   = corners.map { it.second }.average().toFloat()
        Log.i(TAG, "Calibration complete: pitchMid=$pitchMid, yawMid=$yawMid")
    }

    /**
     * Map calibrated pitch/yaw to a quadrant (1-4).
     * Requires isCalibrated == true.
     */
    fun mapToQuadrant(pitch: Float, yaw: Float): Int {
        val isUp   = pitch < pitchMid
        val isLeft = yaw   < yawMid
        return when {
            isUp   && isLeft  -> 1
            isUp   && !isLeft -> 2
            !isUp  && isLeft  -> 3
            else              -> 4
        }
    }

    /**
     * Best-effort quadrant mapping without calibration, using model-native
     * zero-center. Used during calibration dwell so the cursor still moves.
     */
    fun mapToQuadrantUncalibrated(pitch: Float, yaw: Float): Int {
        val isUp   = pitch < 0f
        val isLeft = yaw   < 0f
        return when {
            isUp   && isLeft  -> 1
            isUp   && !isLeft -> 2
            !isUp  && isLeft  -> 3
            else              -> 4
        }
    }

    fun reset() {
        corners.clear()
        accumPitch.clear()
        accumYaw.clear()
        isCalibrated = false
        pitchMid = 0f
        yawMid   = 0f
    }

    private fun saveToPrefs() {
        prefs?.edit()
            ?.putFloat(KEY_PITCH_MID, pitchMid)
            ?.putFloat(KEY_YAW_MID, yawMid)
            ?.putBoolean(KEY_CALIBRATED, true)
            ?.apply()
    }

    private fun restoreFromPrefs() {
        prefs ?: return
        if (prefs.getBoolean(KEY_CALIBRATED, false)) {
            pitchMid     = prefs.getFloat(KEY_PITCH_MID, 0f)
            yawMid       = prefs.getFloat(KEY_YAW_MID, 0f)
            isCalibrated = true
            Log.i(TAG, "Calibration restored from prefs: pitchMid=$pitchMid, yawMid=$yawMid")
        }
    }

    companion object {
        private const val TAG        = "GazeBoard"
        private const val PREFS_NAME = "gazeboard_calib"
        private const val KEY_PITCH_MID  = "pitch_mid"
        private const val KEY_YAW_MID    = "yaw_mid"
        private const val KEY_CALIBRATED = "calibrated"
    }
}
