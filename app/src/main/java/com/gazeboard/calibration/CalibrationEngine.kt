package com.gazeboard.calibration

/**
 * Simple four-point calibration from raw EyeGaze pitch/yaw to screen pixels.
 */
class CalibrationEngine {

    data class CalibPoint(val screenX: Float, val screenY: Float)
    data class RawSample(val pitch: Float, val yaw: Float)

    private val calibTargets = mutableListOf<CalibPoint>()
    private val rawSamples = mutableListOf<RawSample>()

    private var pitchMin = 0f
    private var pitchMax = 1f
    private var yawMin = 0f
    private var yawMax = 1f
    private var screenW = 1080f
    private var screenH = 2400f

    var isCalibrated = false
        private set

    private val accumPitch = mutableListOf<Float>()
    private val accumYaw = mutableListOf<Float>()

    fun setScreenSize(w: Float, h: Float) {
        screenW = w
        screenH = h
    }

    fun setCalibrationTargets(targets: List<CalibPoint>) {
        calibTargets.clear()
        calibTargets.addAll(targets)
        rawSamples.clear()
        accumPitch.clear()
        accumYaw.clear()
        isCalibrated = false
    }

    fun accumulateSample(pitch: Float, yaw: Float) {
        accumPitch.add(pitch)
        accumYaw.add(yaw)
    }

    fun commitCurrentTarget(): Boolean {
        if (accumPitch.isEmpty()) return false

        rawSamples.add(
            RawSample(
                pitch = accumPitch.average().toFloat(),
                yaw = accumYaw.average().toFloat()
            )
        )
        accumPitch.clear()
        accumYaw.clear()

        if (rawSamples.size >= 4) {
            computeMapping()
            isCalibrated = true
            return true
        }
        return false
    }

    private fun computeMapping() {
        pitchMin = (rawSamples[0].pitch + rawSamples[1].pitch) / 2f
        pitchMax = (rawSamples[2].pitch + rawSamples[3].pitch) / 2f
        yawMin = (rawSamples[0].yaw + rawSamples[2].yaw) / 2f
        yawMax = (rawSamples[1].yaw + rawSamples[3].yaw) / 2f
    }

    fun toScreenPoint(pitch: Float, yaw: Float): Pair<Float, Float>? {
        if (!isCalibrated) return null

        val yawRange = (yawMax - yawMin).coerceAtLeast(0.01f)
        val pitchRange = (pitchMax - pitchMin).coerceAtLeast(0.01f)
        val sx = ((yaw - yawMin) / yawRange) * screenW
        val sy = ((pitch - pitchMin) / pitchRange) * screenH
        return Pair(sx.coerceIn(0f, screenW), sy.coerceIn(0f, screenH))
    }

    val currentTargetIndex: Int get() = rawSamples.size
    val totalTargets: Int get() = calibTargets.size

    fun getTarget(index: Int): CalibPoint? = calibTargets.getOrNull(index)

    fun reset() {
        rawSamples.clear()
        accumPitch.clear()
        accumYaw.clear()
        isCalibrated = false
    }
}
