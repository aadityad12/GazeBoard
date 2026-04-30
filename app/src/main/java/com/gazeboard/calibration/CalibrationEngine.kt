package com.gazeboard.calibration

import android.graphics.PointF
import android.util.Log
import kotlin.math.abs

/**
 * 4-point affine calibration: maps raw (pitch, yaw) angles to screen coordinates.
 *
 * PERSON B OWNS THIS FILE.
 *
 * Protocol:
 *   1. Show calibration target at each of 4 screen corners.
 *   2. User looks at each target — capture (pitch, yaw) from GazeEstimator at that moment.
 *   3. [computeAffineTransform] fits a 2×3 affine matrix: (pitch, yaw) → (screenX, screenY).
 *   4. [applyCalibration] transforms every subsequent (pitch, yaw) to screen coordinates.
 *
 * Gaze space: pitch ∈ [-0.5, 0.5] rad (up/down), yaw ∈ [-0.8, 0.8] rad (left/right)
 * Screen space: (0,0) = top-left, (screenW, screenH) = bottom-right, in pixels
 *
 * Affine model:
 *   [sx]   [a00 a01 a02]   [pitch]
 *   [sy] = [a10 a11 a12] * [yaw  ]
 *                          [  1  ]
 *
 * With 4 calibration points we have an overdetermined system — solve via least squares.
 */
class CalibrationEngine {

    // pitch/yaw recorded at each calibration target
    private val gazePoints   = mutableListOf<PointF>()   // x=pitch, y=yaw
    // corresponding screen pixel positions of the targets
    private val screenPoints = mutableListOf<PointF>()

    // Fitted 2×3 affine matrix stored flat: [a00, a01, a02, a10, a11, a12]
    private var affineMatrix: FloatArray? = null

    companion object {
        private const val TAG = "GazeBoard"
        private const val MIN_POINTS = 3
    }

    /**
     * Record one calibration observation.
     *
     * @param screenPoint Target position in screen pixels (e.g. top-left corner)
     * @param pitchYaw    GazeEstimator output at the moment the user dwelled on that target
     *                    (x = pitch, y = yaw)
     */
    fun addCalibrationPoint(screenPoint: PointF, pitchYaw: PointF) {
        screenPoints.add(screenPoint)
        gazePoints.add(pitchYaw)
        Log.d(TAG, "Calibration point ${gazePoints.size}: " +
                "screen=(${screenPoint.x}, ${screenPoint.y}), " +
                "pitch=${pitchYaw.x}, yaw=${pitchYaw.y}")
    }

    /**
     * Fit the affine transform from collected (pitch, yaw) → screen point pairs.
     *
     * @return true if the transform was computed successfully.
     */
    fun computeAffineTransform(): Boolean {
        val n = gazePoints.size
        if (n < MIN_POINTS) {
            Log.w(TAG, "Need at least $MIN_POINTS calibration points, have $n")
            return false
        }

        affineMatrix = computeLeastSquares(gazePoints, screenPoints)

        return if (affineMatrix != null) {
            Log.i(TAG, "Affine transform computed from $n pitch/yaw→screen pairs")
            true
        } else {
            Log.e(TAG, "Affine transform computation failed — points may be collinear")
            false
        }
    }

    /**
     * Transform a smoothed (pitch, yaw) pair into screen pixel coordinates.
     *
     * @param pitch Smoothed pitch in radians (from GazeEstimator)
     * @param yaw   Smoothed yaw in radians (from GazeEstimator)
     * @return Screen position in pixels, or center-screen if not yet calibrated.
     */
    fun applyCalibration(pitch: Float, yaw: Float): PointF {
        val A = affineMatrix ?: return PointF(0.5f, 0.5f)  // uncalibrated pass-through

        val sx = A[0] * pitch + A[1] * yaw + A[2]
        val sy = A[3] * pitch + A[4] * yaw + A[5]
        return PointF(sx, sy)
    }

    fun reset() {
        gazePoints.clear()
        screenPoints.clear()
        affineMatrix = null
        Log.i(TAG, "Calibration reset")
    }

    val pointCount: Int get() = gazePoints.size
    val isCalibrated: Boolean get() = affineMatrix != null

    /**
     * Least-squares affine fitting.
     * Solves A (2×3) such that A * [pitch, yaw, 1]^T ≈ [sx, sy]
     * using pseudo-inverse: A^T = (G^T G)^{-1} G^T S
     */
    private fun computeLeastSquares(
        gazeList: List<PointF>,   // x=pitch, y=yaw
        screenList: List<PointF>  // x=screenX, y=screenY
    ): FloatArray? {
        val n = gazeList.size

        // Accumulate G^T G (3×3) and G^T S (3×2)
        var g00 = 0f; var g01 = 0f; var g02 = 0f
        var g11 = 0f; var g12 = 0f; var g22 = 0f
        var gs00 = 0f; var gs01 = 0f
        var gs10 = 0f; var gs11 = 0f
        var gs20 = 0f; var gs21 = 0f

        for (i in 0 until n) {
            val pitch = gazeList[i].x   // pitch
            val yaw   = gazeList[i].y   // yaw
            val sx    = screenList[i].x
            val sy    = screenList[i].y

            g00 += pitch * pitch; g01 += pitch * yaw; g02 += pitch
            g11 += yaw   * yaw;   g12 += yaw;          g22 += 1f

            gs00 += pitch * sx; gs01 += pitch * sy
            gs10 += yaw   * sx; gs11 += yaw   * sy
            gs20 += sx;          gs21 += sy
        }

        val inv = invert3x3(
            g00, g01, g02,
            g01, g11, g12,
            g02, g12, g22
        ) ?: return null

        // A row 0: [a00, a01, a02] maps pitch/yaw/1 → screenX
        val a00 = inv[0]*gs00 + inv[1]*gs10 + inv[2]*gs20
        val a01 = inv[3]*gs00 + inv[4]*gs10 + inv[5]*gs20
        val a02 = inv[6]*gs00 + inv[7]*gs10 + inv[8]*gs20
        // A row 1: [a10, a11, a12] maps pitch/yaw/1 → screenY
        val a10 = inv[0]*gs01 + inv[1]*gs11 + inv[2]*gs21
        val a11 = inv[3]*gs01 + inv[4]*gs11 + inv[5]*gs21
        val a12 = inv[6]*gs01 + inv[7]*gs11 + inv[8]*gs21

        return floatArrayOf(a00, a01, a02, a10, a11, a12)
    }

    private fun invert3x3(
        m00: Float, m01: Float, m02: Float,
        m10: Float, m11: Float, m12: Float,
        m20: Float, m21: Float, m22: Float
    ): FloatArray? {
        val det = m00*(m11*m22 - m12*m21) -
                  m01*(m10*m22 - m12*m20) +
                  m02*(m10*m21 - m11*m20)

        if (abs(det) < 1e-10f) {
            Log.e(TAG, "Singular calibration matrix (det=$det) — check that calibration " +
                    "points cover a range of pitch/yaw values and are not all the same angle")
            return null
        }

        val inv = 1f / det
        return floatArrayOf(
            inv * (m11*m22 - m12*m21), inv * (m02*m21 - m01*m22), inv * (m01*m12 - m02*m11),
            inv * (m12*m20 - m10*m22), inv * (m00*m22 - m02*m20), inv * (m02*m10 - m00*m12),
            inv * (m10*m21 - m11*m20), inv * (m01*m20 - m00*m21), inv * (m00*m11 - m01*m10)
        )
    }
}
