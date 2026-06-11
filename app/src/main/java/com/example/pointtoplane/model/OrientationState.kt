package com.example.pointtoplane.model

/**
 * Represents the processed orientation of the watch in space.
 */
data class OrientationState(
    /** Compass bearing in degrees, 0–360°, clockwise from North */
    val azimuthDeg: Float = 0f,
    /** Elevation angle in degrees: 0° = horizontal, 90° = zenith, negative = down */
    val elevationDeg: Float = 0f,
    /** Raw 3x3 rotation matrix (9 elements) representing device local to world coords */
    val rotationMatrix: FloatArray = FloatArray(9) { if (it % 4 == 0) 1f else 0f }
) {
    val isPointingUp: Boolean get() = elevationDeg > 30f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OrientationState

        if (azimuthDeg != other.azimuthDeg) return false
        if (elevationDeg != other.elevationDeg) return false
        if (!rotationMatrix.contentEquals(other.rotationMatrix)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = azimuthDeg.hashCode()
        result = 31 * result + elevationDeg.hashCode()
        result = 31 * result + rotationMatrix.contentHashCode()
        return result
    }
}
