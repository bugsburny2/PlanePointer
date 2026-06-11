package com.example.pointtoplane.calibration

import android.content.Context
import android.util.Log

/**
 * Persists a 3D unit vector representing the pointing direction of the user's arm
 * in the watch's local coordinate system.
 *
 * This completely compensates for:
 * 1. How the watch is worn/mounted on the wrist
 * 2. Wrist tilt/roll when the user raises their hand to point and look at the screen
 *
 * During calibration, the user points their arm straight at the sky (zenith, world [0, 0, 1]).
 * We extract this pointing direction in device coordinates as the third row of the
 * current rotation matrix (R[6], R[7], R[8]), and save it.
 *
 * During normal operation, we project the saved local vector back into world coordinates
 * using the current rotation matrix: p_world = R * p_local.
 */
class CalibrationManager(context: Context) {

    private val prefs = context.getSharedPreferences("ptp_prefs", Context.MODE_PRIVATE)

    var isCalibrated: Boolean
        get() = prefs.getBoolean(KEY_IS_CALIBRATED, false)
        private set(value) { prefs.edit().putBoolean(KEY_IS_CALIBRATED, value).apply() }

    private var calibX: Float
        get() = prefs.getFloat(KEY_CALIB_X, 0f)
        private set(value) { prefs.edit().putFloat(KEY_CALIB_X, value).apply() }

    private var calibY: Float
        get() = prefs.getFloat(KEY_CALIB_Y, 1f) // default forearm vector is local Y-axis
        private set(value) { prefs.edit().putFloat(KEY_CALIB_Y, value).apply() }

    private var calibZ: Float
        get() = prefs.getFloat(KEY_CALIB_Z, 0f)
        private set(value) { prefs.edit().putFloat(KEY_CALIB_Z, value).apply() }

    /**
     * Call when the user points their arm straight at the sky.
     * At this moment, the arm points straight UP (world direction [0, 0, 1]).
     * We record the world Z-axis in the device's local coordinates.
     * @param rotationMatrix The 9-element rotation matrix of the device at this moment.
     */
    fun calibrateWithRotationMatrix(rotationMatrix: FloatArray) {
        if (rotationMatrix.size >= 9) {
            val px = rotationMatrix[6]
            val py = rotationMatrix[7]
            val pz = rotationMatrix[8]

            val len = Math.sqrt((px * px + py * py + pz * pz).toDouble()).toFloat()
            if (len > 0.001f) {
                calibX = px / len
                calibY = py / len
                calibZ = pz / len
                isCalibrated = true
                Log.d("CalibrationManager", "Calibrated pointing vector: ($calibX, $calibY, $calibZ)")
            } else {
                Log.e("CalibrationManager", "Invalid rotation matrix during calibration")
            }
        }
    }

    /**
     * Calculates the world pointing direction from the current rotation matrix
     * and the calibrated pointing vector, returning azimuth and elevation in degrees.
     */
    fun calculateOrientation(rotationMatrix: FloatArray): Pair<Float, Float> {
        val px = calibX
        val py = calibY
        val pz = calibZ

        // p_world = R * p_local
        val wx = rotationMatrix[0] * px + rotationMatrix[1] * py + rotationMatrix[2] * pz
        val wy = rotationMatrix[3] * px + rotationMatrix[4] * py + rotationMatrix[5] * pz
        val wz = rotationMatrix[6] * px + rotationMatrix[7] * py + rotationMatrix[8] * pz

        // elevation = arcsin(wz)
        val clampedWz = wz.coerceIn(-1f, 1f)
        val elevationRad = Math.asin(clampedWz.toDouble()).toFloat()
        val elevationDeg = Math.toDegrees(elevationRad.toDouble()).toFloat()

        // azimuth = atan2(wx, wy)
        val azimuthRad = Math.atan2(wx.toDouble(), wy.toDouble()).toFloat()
        val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f

        return Pair(azimuthDeg, elevationDeg)
    }

    fun reset() {
        isCalibrated = false
        prefs.edit().putBoolean(KEY_IS_CALIBRATED, false).apply()
        calibX = 0f
        calibY = 1f
        calibZ = 0f
    }

    companion object {
        private const val KEY_IS_CALIBRATED = "is_calibrated"
        private const val KEY_CALIB_X = "calib_vector_x"
        private const val KEY_CALIB_Y = "calib_vector_y"
        private const val KEY_CALIB_Z = "calib_vector_z"
    }
}
