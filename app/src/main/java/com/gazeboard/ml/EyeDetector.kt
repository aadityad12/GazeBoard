package com.gazeboard.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.media.FaceDetector
import android.util.Log
import java.nio.FloatBuffer

/**
 * Detects eyes in a camera frame and produces a preprocessed eye crop for EyeGazeModel.
 *
 * PERSON A OWNS THIS FILE.
 *
 * Uses Android's built-in [android.media.FaceDetector] (CPU, ~20-40ms) to locate the
 * eye region in each frame, then crops, resizes, and converts to grayscale for the
 * EyeGaze model.
 *
 * Pipeline:
 *   ARGB_8888 bitmap → RGB_565 copy (required by FaceDetector) → FaceDetector.findFaces()
 *   → compute eye crop rect → crop from ARGB original → resize to 160×96
 *   → grayscale normalize → FloatBuffer[15,360]
 *
 * If FaceDetector finds no face, returns null. CameraManager will skip that frame.
 *
 * Fallback: If Android FaceDetector is consistently unreliable, replace [detectAndCrop]
 * with ML Kit face detection (see CLAUDE.md Option B). The output contract (FloatBuffer)
 * stays the same — only this file changes.
 */
class EyeDetector {

    companion object {
        private const val TAG = "GazeBoard"

        // EyeGaze model expects 160×96 (width×height)
        private const val OUT_WIDTH  = EyeGazeModel.INPUT_WIDTH   // 160
        private const val OUT_HEIGHT = EyeGazeModel.INPUT_HEIGHT  // 96

        // Padding around the detected eye region to include eyelids and brow
        private const val EYE_CROP_PADDING_FACTOR = 0.5f

        // Minimum eye distance (as fraction of frame width) to filter spurious detections.
        // S25 Ultra front camera delivers ~1088px wide frames; a seated user at ~50cm
        // gives eye distance ~35-40px → ratio ~0.034. Keep threshold just below that.
        private const val MIN_EYE_DISTANCE_FRACTION = 0.02f
    }

    // Reuse FaceDetector instances to avoid GC pressure per frame
    private var cachedDetector: FaceDetector? = null
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0

    /**
     * Detect face in [bitmap], crop the eye region, and convert to the EyeGaze input format.
     *
     * @param bitmap ARGB_8888 camera frame (any size — typically 640×480)
     * @return FloatBuffer of size 15,360 (96×160 grayscale [0,1]) ready for EyeGazeModel,
     *         or null if no face was detected.
     */
    fun detectAndCrop(bitmap: Bitmap): FloatBuffer? {
        val face = detectFace(bitmap) ?: return null
        val eyeRect = computeEyeCropRect(face, bitmap.width, bitmap.height) ?: return null
        return cropAndPreprocess(bitmap, eyeRect)
    }

    /**
     * Run Android FaceDetector on the frame.
     * FaceDetector requires an RGB_565 bitmap — we convert from ARGB_8888.
     */
    private fun detectFace(argbBitmap: Bitmap): FaceDetector.Face? {
        val w = argbBitmap.width
        val h = argbBitmap.height

        // FaceDetector is not thread-safe and holds state per size; cache by dimensions
        if (cachedDetector == null || cachedWidth != w || cachedHeight != h) {
            cachedDetector = FaceDetector(w, h, 1)
            cachedWidth = w
            cachedHeight = h
        }

        // FaceDetector requires RGB_565 input
        val bitmap565 = argbBitmap.copy(Bitmap.Config.RGB_565, false)
        val faces = arrayOfNulls<FaceDetector.Face>(1)

        return try {
            val found = cachedDetector!!.findFaces(bitmap565, faces)
            bitmap565.recycle()

            if (found > 0 && faces[0] != null) {
                val face = faces[0]!!
                val eyeDist = face.eyesDistance()
                // Filter out spurious detections (face too small = too far away)
                if (eyeDist / w < MIN_EYE_DISTANCE_FRACTION) {
                    Log.d(TAG, "EyeDetector: face too small (eyeDist=$eyeDist, frameW=$w)")
                    null
                } else {
                    face
                }
            } else {
                null
            }
        } catch (e: Exception) {
            bitmap565.recycle()
            Log.w(TAG, "FaceDetector error: ${e.message}")
            null
        }
    }

    /**
     * From the detected face, compute a bounding rect around the eye region.
     *
     * FaceDetector gives us:
     *   - getMidPoint(): midpoint between the two eyes
     *   - eyesDistance(): pixel distance between left and right eye centers
     *
     * We crop a region centered on the eye midpoint, wide enough to contain both eyes
     * plus padding, with height scaled to the 96×160 (h×w) aspect ratio.
     */
    private fun computeEyeCropRect(face: FaceDetector.Face, frameW: Int, frameH: Int): Rect? {
        val midPoint = PointF()
        face.getMidPoint(midPoint)
        val eyeDist = face.eyesDistance()

        if (eyeDist < 4f) return null

        // Crop width = eye distance × (1 + 2 × padding)
        val cropW = (eyeDist * (1f + 2f * EYE_CROP_PADDING_FACTOR)).toInt()
        // Crop height = cropW × (96/160) to match model aspect ratio
        val cropH = (cropW * OUT_HEIGHT.toFloat() / OUT_WIDTH).toInt()

        val left   = (midPoint.x - cropW / 2f).toInt().coerceAtLeast(0)
        val top    = (midPoint.y - cropH / 2f).toInt().coerceAtLeast(0)
        val right  = (left + cropW).coerceAtMost(frameW)
        val bottom = (top + cropH).coerceAtMost(frameH)

        if (right - left < 4 || bottom - top < 4) return null

        return Rect(left, top, right, bottom)
    }

    /**
     * Crop the eye region from the ARGB bitmap, resize to 160×96, convert to
     * normalized grayscale FloatBuffer.
     *
     * Grayscale conversion: gray = 0.299×R + 0.587×G + 0.114×B (ITU-R BT.601)
     * Normalization: [0, 255] → [0.0, 1.0]
     */
    private fun cropAndPreprocess(bitmap: Bitmap, eyeRect: Rect): FloatBuffer {
        // Crop from original ARGB bitmap
        val cropped = Bitmap.createBitmap(
            bitmap,
            eyeRect.left, eyeRect.top,
            eyeRect.width(), eyeRect.height()
        )

        // Resize to model input dimensions (width=160, height=96)
        val resized = Bitmap.createScaledBitmap(cropped, OUT_WIDTH, OUT_HEIGHT, true)
        cropped.recycle()

        // Convert to grayscale float [0, 1] — layout: row-major [height][width]
        val buffer = FloatBuffer.allocate(OUT_HEIGHT * OUT_WIDTH)  // 15,360
        val pixels = IntArray(OUT_WIDTH * OUT_HEIGHT)
        resized.getPixels(pixels, 0, OUT_WIDTH, 0, 0, OUT_WIDTH, OUT_HEIGHT)
        resized.recycle()

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            buffer.put(gray)
        }

        buffer.rewind()
        return buffer
    }

    fun reset() {
        cachedDetector = null
        cachedWidth = 0
        cachedHeight = 0
    }
}
