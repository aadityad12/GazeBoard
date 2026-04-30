package com.gazeboard.calibration

import android.graphics.PointF
import android.util.Log
import kotlin.math.abs

/**
 * 4-point affine calibration: maps raw normalized gaze coordinates to screen space.
 *
 * PERSON B OWNS THIS FILE.
 *
 * Protocol:
 *   1. Show calibration target at each of 4 screen corners.
 *   2. User dwells on each target — capture raw gaze at that moment.
 *   3. computeAffineTransform() fits a 2×3 affine matrix using least squares.
 *   4. applyCalibration() transforms every subsequent raw gaze point.
 *
 * Affine model:
 *   [sx]   [a00 a01 a02]   [gx]
 *   [sy] = [a10 a11 a12] * [gy]
 *                          [ 1]
 *
 * With 4+ point pairs we have an overdetermined system — solve via pseudo-inverse.
 */
class CalibrationEngine {

    // Parallel lists: index i pairs screenPoints[i] ↔ gazePoints[i]
    private val screenPoints = mutableListOf<PointF>()
    private val gazePoints   = mutableListOf<PointF>()

    // 2×3 affine matrix stored flat: [a00, a01, a02, a10, a11, a12]
    private var affineMatrix: FloatArray? = null

    companion object {
        private const val TAG = "GazeBoard"
        private const val MIN_POINTS = 3  // minimum for affine; 4 preferred
    }

    /**
     * Record one calibration observation.
     *
     * @param screenPoint Known target position in screen coordinates (pixels or [0,1] — be consistent)
     * @param gazePoint   Raw gaze PointF from GazeEstimator at the moment of capture
     */
    fun addCalibrationPoint(screenPoint: PointF, gazePoint: PointF) {
        screenPoints.add(screenPoint)
        gazePoints.add(gazePoint)
        Log.d(TAG, "Calibration point ${screenPoints.size}: screen=$screenPoint gaze=$gazePoint")
    }

    /**
     * Fit the affine transform from collected point pairs.
     *
     * @return true if transform was computed successfully, false if insufficient points.
     *
     * TODO(Person B): Implement least-squares pseudo-inverse below.
     * The math is in docs/ARCHITECTURE.md — build matrix G (Nx3) and S (Nx2),
     * then A = (G^T G)^{-1} G^T S.
     */
    fun computeAffineTransform(): Boolean {
        val n = gazePoints.size
        if (n < MIN_POINTS) {
            Log.w(TAG, "Need at least $MIN_POINTS calibration points, have $n")
            return false
        }

        // Build G matrix (N×3): each row is [gx, gy, 1]
        // Build S matrix (N×2): each row is [sx, sy]
        // Solve: A (2×3) = pseudoinverse(G) * S

        // TODO(Person B): Implement 3×3 matrix inversion and matrix multiply.
        // For a 24-hour hackathon, a direct 3×3 Cramer's rule inversion is acceptable.
        // Reference implementation in Python (for verification):
        //   import numpy as np
        //   G = np.array([[gx, gy, 1] for gx, gy in gazePoints])
        //   S = np.array([[sx, sy]    for sx, sy in screenPoints])
        //   A = np.linalg.lstsq(G, S, rcond=None)[0]   # shape (3, 2)

        affineMatrix = computeLeastSquares(gazePoints, screenPoints)

        return if (affineMatrix != null) {
            Log.i(TAG, "Affine transform computed from $n points: ${affineMatrix?.contentToString()}")
            true
        } else {
            Log.e(TAG, "Affine transform computation failed")
            false
        }
    }

    /**
     * Apply the calibration affine transform to a raw gaze point.
     *
     * @return Calibrated gaze point, or the raw input if calibration is not yet computed.
     */
    fun applyCalibration(rawGaze: PointF): PointF {
        val A = affineMatrix ?: return rawGaze  // pass-through before calibration

        val sx = A[0] * rawGaze.x + A[1] * rawGaze.y + A[2]
        val sy = A[3] * rawGaze.x + A[4] * rawGaze.y + A[5]
        return PointF(sx, sy)
    }

    fun reset() {
        screenPoints.clear()
        gazePoints.clear()
        affineMatrix = null
        Log.i(TAG, "Calibration reset")
    }

    val pointCount: Int get() = gazePoints.size
    val isCalibrated: Boolean get() = affineMatrix != null

    /**
     * Least-squares affine fitting via pseudo-inverse.
     *
     * Solves A^T = (G^T G)^{-1} G^T S for A (3×2 → stored as 2×3).
     *
     * TODO(Person B): Implement this method. The pseudo-inverse of a N×3 matrix G
     * can be computed as (G^T G)^{-1} G^T using 3×3 matrix inversion.
     */
    private fun computeLeastSquares(
        gazeList: List<PointF>,
        screenList: List<PointF>
    ): FloatArray? {
        val n = gazeList.size

        // G^T G — 3×3 matrix
        var g00 = 0f; var g01 = 0f; var g02 = 0f
        var g11 = 0f; var g12 = 0f; var g22 = 0f

        // G^T S — 3×2 matrix
        var gs00 = 0f; var gs01 = 0f
        var gs10 = 0f; var gs11 = 0f
        var gs20 = 0f; var gs21 = 0f

        for (i in 0 until n) {
            val gx = gazeList[i].x
            val gy = gazeList[i].y
            val sx = screenList[i].x
            val sy = screenList[i].y

            g00 += gx * gx; g01 += gx * gy; g02 += gx
            g11 += gy * gy; g12 += gy;       g22 += 1f

            gs00 += gx * sx; gs01 += gx * sy
            gs10 += gy * sx; gs11 += gy * sy
            gs20 += sx;       gs21 += sy
        }

        // Invert 3×3 symmetric matrix (G^T G) using Cramer's rule
        // [g00 g01 g02]
        // [g01 g11 g12]
        // [g02 g12 g22]
        val inv = invert3x3(
            g00, g01, g02,
            g01, g11, g12,
            g02, g12, g22
        ) ?: return null

        // A^T (3×2) = inv * [gs00 gs01; gs10 gs11; gs20 gs21]
        // Row 0 of A: [a00, a01, a02]
        val a00 = inv[0]*gs00 + inv[1]*gs10 + inv[2]*gs20
        val a01 = inv[3]*gs00 + inv[4]*gs10 + inv[5]*gs20
        val a02 = inv[6]*gs00 + inv[7]*gs10 + inv[8]*gs20
        // Row 1 of A: [a10, a11, a12]
        val a10 = inv[0]*gs01 + inv[1]*gs11 + inv[2]*gs21
        val a11 = inv[3]*gs01 + inv[4]*gs11 + inv[5]*gs21
        val a12 = inv[6]*gs01 + inv[7]*gs11 + inv[8]*gs21

        return floatArrayOf(a00, a01, a02, a10, a11, a12)
    }

    /** Invert a 3×3 matrix via cofactor expansion. Returns null if singular. */
    private fun invert3x3(
        m00: Float, m01: Float, m02: Float,
        m10: Float, m11: Float, m12: Float,
        m20: Float, m21: Float, m22: Float
    ): FloatArray? {
        val det = m00*(m11*m22 - m12*m21) -
                  m01*(m10*m22 - m12*m20) +
                  m02*(m10*m21 - m11*m20)

        if (abs(det) < 1e-8f) {
            Log.e(TAG, "Singular calibration matrix (det=$det) — calibration points may be collinear")
            return null
        }

        val inv = 1f / det
        return floatArrayOf(
            inv * (m11*m22 - m12*m21),
            inv * (m02*m21 - m01*m22),
            inv * (m01*m12 - m02*m11),
            inv * (m12*m20 - m10*m22),
            inv * (m00*m22 - m02*m20),
            inv * (m02*m10 - m00*m12),
            inv * (m10*m21 - m11*m20),
            inv * (m01*m20 - m00*m21),
            inv * (m00*m11 - m01*m10)
        )
    }
}
