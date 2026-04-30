package com.gazeboard.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.nio.FloatBuffer
import kotlin.math.hypot

/**
 * Detects a face with ML Kit, crops the left eye, and converts it to the
 * 96x160 grayscale input expected by EyeGazeModel.
 */
class EyeDetector {

    companion object {
        private const val TAG = "GazeBoard"
        private const val OUT_WIDTH = 160
        private const val OUT_HEIGHT = 96
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    fun detectAndCrop(bitmap: Bitmap): FloatBuffer? {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces = try {
            Tasks.await(detector.process(inputImage))
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit face detection failed: ${e.message}")
            return null
        }

        if (faces.isEmpty()) return null
        val face = faces[0]

        val leftEyePos = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEyePos = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null

        val interEyeDist = hypot(
            (rightEyePos.x - leftEyePos.x).toDouble(),
            (rightEyePos.y - leftEyePos.y).toDouble()
        ).toFloat()

        if (interEyeDist < 8f) return null

        val cropW = (interEyeDist * 0.75f).toInt().coerceAtLeast(16)
        val cropH = (cropW * OUT_HEIGHT.toFloat() / OUT_WIDTH).toInt().coerceAtLeast(10)

        val cx = leftEyePos.x.toInt()
        val cy = leftEyePos.y.toInt()

        val left = (cx - cropW / 2).coerceAtLeast(0)
        val top = (cy - cropH / 2).coerceAtLeast(0)
        val right = (left + cropW).coerceAtMost(bitmap.width)
        val bottom = (top + cropH).coerceAtMost(bitmap.height)

        if (right - left < 4 || bottom - top < 4) return null

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val resized = Bitmap.createScaledBitmap(cropped, OUT_WIDTH, OUT_HEIGHT, true)
        cropped.recycle()

        val buffer = FloatBuffer.allocate(OUT_HEIGHT * OUT_WIDTH)
        val pixels = IntArray(OUT_WIDTH * OUT_HEIGHT)
        resized.getPixels(pixels, 0, OUT_WIDTH, 0, 0, OUT_WIDTH, OUT_HEIGHT)
        resized.recycle()

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            buffer.put((0.299f * r + 0.587f * g + 0.114f * b) / 255f)
        }

        buffer.rewind()
        return buffer
    }

    fun reset() {
        // Stateless: detector is reused across frames.
    }

    fun close() {
        detector.close()
    }
}
