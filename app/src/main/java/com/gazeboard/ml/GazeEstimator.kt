package com.gazeboard.ml

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Derives a normalized gaze point and blink state from a FaceMesh landmark array.
 *
 * PERSON B OWNS THIS FILE.
 *
 * Landmark index reference (from 478-point MediaPipe FaceMesh with iris):
 *   468: left iris center       473: right iris center
 *   33:  left eye outer corner  133: left eye inner corner
 *   159: left eye upper lid     145: left eye lower lid
 *   362: right eye outer corner 263: right eye inner corner
 *   386: right eye upper lid    374: right eye lower lid
 *   1:   nose tip               199: chin
 *   33/263: eye corners (for head pose fallback)
 *
 * All landmark coordinates are in [0,1], normalized to the 192×192 model input.
 */
class GazeEstimator {

    // EMA smoothing factor — higher = more responsive, lower = smoother
    // Tunable: try 0.2 (smooth) or 0.4 (responsive) if 0.3 is unsatisfactory
    private val alpha = 0.3f
    private var smoothedGaze = PointF(0.5f, 0.5f)

    // Blink state: require 2 consecutive blink frames to avoid false positives
    private var blinkFrameCount = 0
    private val BLINK_FRAME_THRESHOLD = 2
    private val EAR_BLINK_THRESHOLD = 0.2f

    companion object {
        // Landmark indices — must match 478-point iris-enabled FaceMesh
        const val IDX_LEFT_IRIS    = 468
        const val IDX_RIGHT_IRIS   = 473
        const val IDX_LEFT_OUTER   = 33
        const val IDX_LEFT_INNER   = 133
        const val IDX_LEFT_UPPER   = 159
        const val IDX_LEFT_LOWER   = 145
        const val IDX_RIGHT_OUTER  = 362
        const val IDX_RIGHT_INNER  = 263
        const val IDX_RIGHT_UPPER  = 386
        const val IDX_RIGHT_LOWER  = 374
        const val IDX_NOSE_TIP     = 1
        const val IDX_CHIN         = 199

        // Iris distance proxy for face-distance estimation
        const val IPD_TOO_CLOSE = 0.35f   // iris span > 35% of image width
        const val IPD_TOO_FAR   = 0.10f   // iris span < 10% of image width
    }

    /**
     * Extract a smoothed gaze estimate from raw landmark output.
     *
     * @param landmarks FloatArray[1434] from FaceLandmarkModel.runInference()
     * @return GazeResult containing smoothed gaze PointF, blink state, and distance warning.
     *         Returns null if iris landmarks are invalid or face confidence is too low.
     *
     * TODO(Person B): Add face confidence check from model's second output tensor
     * (if available) to gate inference on low-confidence frames.
     */
    fun estimate(landmarks: FloatArray): GazeResult? {
        if (landmarks.size != 478 * 3) return null

        val blinking = isBlinking(landmarks)
        val distanceWarning = checkDistance(landmarks)

        // Iris tracking mode (primary)
        val rawGaze = extractGaze(landmarks) ?: return GazeResult(
            gaze = smoothedGaze,
            isBlinking = blinking,
            distanceWarning = distanceWarning
        )

        smooth(rawGaze)

        return GazeResult(
            gaze = PointF(smoothedGaze.x, smoothedGaze.y),
            isBlinking = blinking,
            distanceWarning = distanceWarning
        )
    }

    /**
     * Normalize iris center position relative to eye corner landmarks.
     *
     * Formula:
     *   gazeX = (irisCenter.x - outerCorner.x) / (innerCorner.x - outerCorner.x)
     *   gazeY = (irisCenter.y - upperLid.y) / (lowerLid.y - upperLid.y)
     *
     * Average left and right eyes for stability.
     */
    private fun extractGaze(landmarks: FloatArray): PointF? {
        val irisL  = lm(landmarks, IDX_LEFT_IRIS)
        val irisR  = lm(landmarks, IDX_RIGHT_IRIS)
        val outerL = lm(landmarks, IDX_LEFT_OUTER)
        val innerL = lm(landmarks, IDX_LEFT_INNER)
        val upperL = lm(landmarks, IDX_LEFT_UPPER)
        val lowerL = lm(landmarks, IDX_LEFT_LOWER)
        val outerR = lm(landmarks, IDX_RIGHT_OUTER)
        val innerR = lm(landmarks, IDX_RIGHT_INNER)

        val eyeWidthL = abs(innerL.x - outerL.x)
        val eyeWidthR = abs(outerR.x - innerR.x)
        val eyeHeightL = abs(lowerL.y - upperL.y)

        // Guard against degenerate eye measurements (face too oblique or too close)
        if (eyeWidthL < 0.01f || eyeWidthR < 0.01f || eyeHeightL < 0.005f) return null

        val gazeLx = (irisL.x - outerL.x) / eyeWidthL
        val gazeRx = (irisR.x - innerR.x) / eyeWidthR

        // Y: use left eye only (right eye Y is mirrored and less reliable)
        val gazeY = (irisL.y - upperL.y) / eyeHeightL

        val rawGazeX = ((gazeLx + gazeRx) / 2f).coerceIn(0f, 1f)
        val rawGazeY = gazeY.coerceIn(0f, 1f)

        return PointF(rawGazeX, rawGazeY)
    }

    /**
     * Head pose fallback — derive pitch/yaw from face geometry landmarks.
     *
     * Activate this instead of [extractGaze] if iris tracking fails the Hour 8 go/no-go.
     * No model change required — same landmark array, different math.
     *
     * TODO(Person B): Call this from estimate() when head pose mode is enabled.
     */
    fun extractHeadPose(landmarks: FloatArray): PointF {
        val noseTip  = lm(landmarks, IDX_NOSE_TIP)
        val leftEye  = lm(landmarks, IDX_LEFT_OUTER)
        val rightEye = lm(landmarks, IDX_RIGHT_OUTER)
        val chin     = lm(landmarks, IDX_CHIN)

        val faceX = (leftEye.x + rightEye.x) / 2f
        val faceY = (noseTip.y + chin.y) / 2f
        val eyeWidth = abs(rightEye.x - leftEye.x).coerceAtLeast(0.01f)
        val faceHeight = abs(chin.y - (leftEye.y + rightEye.y) / 2f).coerceAtLeast(0.01f)

        // Yaw: nose tip deviation from face center, normalized by eye span
        val yaw = ((noseTip.x - faceX) / eyeWidth)
            .coerceIn(-1f, 1f)
            .let { (it + 1f) / 2f }  // remap [-1,1] → [0,1]

        // Pitch: nose position relative to face vertical center
        val pitch = ((noseTip.y - faceY) / faceHeight)
            .coerceIn(-1f, 1f)
            .let { (it + 1f) / 2f }  // remap [-1,1] → [0,1]

        smooth(PointF(yaw, pitch))
        return PointF(smoothedGaze.x, smoothedGaze.y)
    }

    /**
     * Eye Aspect Ratio (EAR) blink detection.
     * EAR = vertical eye opening / horizontal eye width.
     * Blink is confirmed after [BLINK_FRAME_THRESHOLD] consecutive frames below threshold.
     */
    private fun isBlinking(landmarks: FloatArray): Boolean {
        val upper = lm(landmarks, IDX_LEFT_UPPER)
        val lower = lm(landmarks, IDX_LEFT_LOWER)
        val outer = lm(landmarks, IDX_LEFT_OUTER)
        val inner = lm(landmarks, IDX_LEFT_INNER)

        val vertDist  = abs(upper.y - lower.y)
        val horizDist = abs(inner.x - outer.x).coerceAtLeast(0.001f)
        val ear = vertDist / horizDist

        return if (ear < EAR_BLINK_THRESHOLD) {
            blinkFrameCount++
            blinkFrameCount >= BLINK_FRAME_THRESHOLD
        } else {
            blinkFrameCount = 0
            false
        }
    }

    /**
     * Estimate face distance via inter-pupillary distance (IPD) proxy.
     * IPD is the distance between iris centers in normalized [0,1] coordinates.
     * Small IPD = far away; large IPD = too close.
     */
    private fun checkDistance(landmarks: FloatArray): String? {
        val irisL = lm(landmarks, IDX_LEFT_IRIS)
        val irisR = lm(landmarks, IDX_RIGHT_IRIS)
        val ipd = sqrt(
            (irisR.x - irisL.x).pow(2) + (irisR.y - irisL.y).pow(2)
        )

        return when {
            ipd < IPD_TOO_FAR  -> "Move closer"
            ipd > IPD_TOO_CLOSE -> "Move back"
            else -> null
        }
    }

    /** Apply exponential moving average to the smoothed gaze accumulator. */
    private fun smooth(raw: PointF) {
        smoothedGaze = PointF(
            alpha * raw.x + (1f - alpha) * smoothedGaze.x,
            alpha * raw.y + (1f - alpha) * smoothedGaze.y
        )
    }

    /** Extract a landmark as PointF. Layout: [x0,y0,z0, x1,y1,z1, ...] */
    private fun lm(landmarks: FloatArray, idx: Int) =
        PointF(landmarks[idx * 3], landmarks[idx * 3 + 1])

    fun reset() {
        smoothedGaze = PointF(0.5f, 0.5f)
        blinkFrameCount = 0
    }

    data class GazeResult(
        val gaze: PointF,
        val isBlinking: Boolean,
        val distanceWarning: String?
    )
}
