package poincaresketchpad.geometry

import kotlin.math.*

object MeasurementUtils {

    fun measure(obj: GeometricObject): Double? = when (obj) {
        is Segment -> segmentLength(obj)
        is Circle -> circleCircumference(obj)
        is Angle -> angleDegrees(obj)
        is Region -> regionArea(obj)
        else -> null
    }

    fun measureLabel(obj: GeometricObject): String? = when (obj) {
        is Segment -> if (obj.isDefined) "%.2f".format(segmentLength(obj)) else "—"
        is Circle -> if (obj.isDefined) "%.2f".format(circleCircumference(obj)) else "—"
        is Angle -> if (obj.isDefined) "%.1f\u00B0".format(angleDegrees(obj)) else "—"
        is Region -> if (obj.isDefined) "%.2f".format(regionArea(obj)) else "—"
        else -> null
    }

    fun segmentLength(seg: Segment): Double {
        if (!seg.isDefined) return 0.0
        return HyperbolicUtils.hyperbolicDistance(seg.x1, seg.y1, seg.x2, seg.y2)
    }

    fun circleCircumference(circle: Circle): Double {
        if (!circle.isDefined) return 0.0
        return 2.0 * PI * sinh(circle.hyperbolicRadius)
    }

    fun angleDegrees(angle: Angle): Double {
        if (!angle.isDefined) return 0.0
        return angle.measureDegrees()
    }

    fun regionArea(region: Region): Double {
        if (!region.isDefined) return 0.0
        return region.measureArea()
    }
}
