package poincaresketchpad.model

import poincaresketchpad.geometry.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ObjectMetadata(
    var visible: Boolean = true,
    var label: String? = null,
    var labelVisible: Boolean = false,
    var labelOffsetX: Double = 10.0,
    var labelOffsetY: Double = -10.0,
    var inMeasurementTable: Boolean = false,
    var labelAnchorAngle: Double? = null,
    var labelAnchorT: Double? = null,
    var isInput: Boolean = false,
    var isLocked: Boolean = false,
    var color: String = "black",
    var size: String = "medium",
    var strokeStyle: String = "solid",
    var strokeThickness: String = "normal"
)

/**
 * Get the anchor point for an object's label in model coordinates.
 * For geodesics this is an arc-interpolated point; for circles it's a point on the Euclidean circle.
 */
fun getObjectAnchor(obj: GeometricObject, meta: ObjectMetadata? = null): Pair<Double, Double>? {
    if (obj is PointLike) return Pair(obj.x, obj.y)
    if (obj is GeodesicLike) {
        val t = meta?.labelAnchorT ?: if (obj.geodesicType == GeodesicType.RAY) 0.0 else 0.5
        // Interpolate along the arc
        val arc = HyperbolicUtils.GeodesicArc(obj.arcCenterX, obj.arcCenterY, obj.arcRadius, obj.isDiameter)
        // For LINE: interpolate between the two ideal points
        // For RAY: interpolate between endpoint and ideal point
        // For SEGMENT: interpolate between the two endpoints
        val (startX, startY, endX, endY) = when (obj.geodesicType) {
            GeodesicType.LINE -> listOf(obj.idealX1, obj.idealY1, obj.idealX2, obj.idealY2)
            GeodesicType.RAY -> listOf(obj.x1, obj.y1, obj.idealX2, obj.idealY2)
            GeodesicType.SEGMENT -> listOf(obj.x1, obj.y1, obj.x2, obj.y2)
        }
        val (ax, ay) = HyperbolicUtils.interpolateOnArc(t, startX, startY, endX, endY, arc)
        return Pair(ax, ay)
    }
    return when (obj) {
        is Circle -> {
            val angle = meta?.labelAnchorAngle ?: (PI / 2)
            Pair(obj.euclideanCenterX + obj.euclideanRadius * cos(angle),
                 obj.euclideanCenterY + obj.euclideanRadius * sin(angle))
        }
        is Angle -> Pair(obj.vertexX, obj.vertexY)
        is Region -> {
            if (obj.vertices.isNotEmpty()) {
                val cx = obj.vertices.sumOf { it.first } / obj.vertices.size
                val cy = obj.vertices.sumOf { it.second } / obj.vertices.size
                Pair(cx, cy)
            } else null
        }
        else -> null
    }
}
