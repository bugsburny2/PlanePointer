package com.example.pointtoplane.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.pointtoplane.model.OrientationState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * Manages watch orientation via sensor fusion.
 *
 * Prefers TYPE_ROTATION_VECTOR (handles sensor fusion internally) and falls
 * back to TYPE_ACCELEROMETER + TYPE_MAGNETIC_FIELD if unavailable.
 *
 * Applies a low-pass filter (α = 0.12) to reduce sensor jitter.
 * Emits raw azimuth (0–360°), raw elevation (-90°–+90°), and the raw 3x3 rotation matrix.
 */
class SensorOrientationManager(private val context: Context) {

    private val LPF_ALPHA = 0.12f

    val orientationFlow: Flow<OrientationState> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var smoothAzimuth = 0f
        var smoothElevation = 0f

        val accelerometerReading = FloatArray(3)
        val magnetometerReading = FloatArray(3)
        val rotationMatrix = FloatArray(9)

        fun azimuthDelta(target: Float, current: Float): Float {
            var diff = target - current
            while (diff > 180f) diff -= 360f
            while (diff < -180f) diff += 360f
            return diff
        }

        fun processRotationMatrix(r: FloatArray) {
            // Projected world vector for forearm (default pointing vector is local Y axis: [0, 1, 0])
            // wx = R[1], wy = R[4], wz = R[7]
            val wx = r[1]
            val wy = r[4]
            val wz = r[7]

            // elevation = arcsin(wz)
            val clampedWz = wz.coerceIn(-1f, 1f)
            val rawElevation = Math.toDegrees(Math.asin(clampedWz.toDouble())).toFloat()

            // azimuth = atan2(wx, wy)
            val azimuthRad = Math.atan2(wx.toDouble(), wy.toDouble()).toFloat()
            val rawAzimuth = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f

            // Low-pass filter
            val deltaAzimuth = azimuthDelta(rawAzimuth, smoothAzimuth)
            smoothAzimuth = ((smoothAzimuth + LPF_ALPHA * deltaAzimuth) + 360f) % 360f
            smoothElevation += LPF_ALPHA * (rawElevation - smoothElevation)

            trySend(OrientationState(smoothAzimuth, smoothElevation, r.clone()))
        }

        if (rotationVectorSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    processRotationMatrix(rotationMatrix)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
            awaitClose { sensorManager.unregisterListener(listener) }

        } else if (accelerometerSensor != null && magnetometerSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER ->
                            System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                        Sensor.TYPE_MAGNETIC_FIELD ->
                            System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                    }
                    val success = SensorManager.getRotationMatrix(
                        rotationMatrix, null, accelerometerReading, magnetometerReading
                    )
                    if (success) {
                        processRotationMatrix(rotationMatrix)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetometerSensor, SensorManager.SENSOR_DELAY_UI)
            awaitClose { sensorManager.unregisterListener(listener) }

        } else {
            close(IllegalStateException("No orientation sensors available on this device"))
        }
    }.distinctUntilChanged { old, new ->
        abs(old.azimuthDeg - new.azimuthDeg) < 0.5f &&
                abs(old.elevationDeg - new.elevationDeg) < 0.5f
    }
}
