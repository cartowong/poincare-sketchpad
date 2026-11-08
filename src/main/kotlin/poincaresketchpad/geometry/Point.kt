package poincaresketchpad.geometry

import kotlin.math.*

interface PointLike {
    val x: Double
    val y: Double
}

class FreePoint(
    override val id: Int,
    override var x: Double,
    override var y: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = emptyList()
    override val isDefined: Boolean = true
    override fun recompute(objects: Map<Int, GeometricObject>) { /* no-op */ }
}

class FreePointOnLine(
    override val id: Int,
    val lineId: Int,
    var t: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(lineId)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val geo = objects[lineId] as? GeodesicLike
        if (geo == null || !(objects[lineId]!!.isDefined)) { isDefined = false; return }
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        val (px, py) = HyperbolicUtils.interpolateOnArc(t, geo.idealX1, geo.idealY1, geo.idealX2, geo.idealY2, arc)
        x = px; y = py
        isDefined = HyperbolicUtils.isInsideDisk(x, y)
    }

    fun updateParameterFromPosition(mx: Double, my: Double, objects: Map<Int, GeometricObject>) {
        val geo = objects[lineId] as? GeodesicLike ?: return
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        t = HyperbolicUtils.parameterOnArc(mx, my, geo.idealX1, geo.idealY1, geo.idealX2, geo.idealY2, arc)
            .coerceIn(0.0, 1.0)
    }
}

class FreePointOnRay(
    override val id: Int,
    val rayId: Int,
    var t: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(rayId)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val geo = objects[rayId] as? GeodesicLike
        if (geo == null || !(objects[rayId]!!.isDefined)) { isDefined = false; return }
        // Ray: from endpoint (x1,y1) to ideal point (idealX2,idealY2)
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        val (px, py) = HyperbolicUtils.interpolateOnArc(t, geo.x1, geo.y1, geo.idealX2, geo.idealY2, arc)
        x = px; y = py
        isDefined = HyperbolicUtils.isInsideDisk(x, y)
    }

    fun updateParameterFromPosition(mx: Double, my: Double, objects: Map<Int, GeometricObject>) {
        val geo = objects[rayId] as? GeodesicLike ?: return
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        t = HyperbolicUtils.parameterOnArc(mx, my, geo.x1, geo.y1, geo.idealX2, geo.idealY2, arc)
            .coerceIn(0.0, 1.0)
    }
}

class FreePointOnSegment(
    override val id: Int,
    val segmentId: Int,
    var t: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(segmentId)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val geo = objects[segmentId] as? GeodesicLike
        if (geo == null || !(objects[segmentId]!!.isDefined)) { isDefined = false; return }
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        val (px, py) = HyperbolicUtils.interpolateOnArc(t, geo.x1, geo.y1, geo.x2, geo.y2, arc)
        x = px; y = py
        isDefined = true
    }

    fun updateParameterFromPosition(mx: Double, my: Double, objects: Map<Int, GeometricObject>) {
        val geo = objects[segmentId] as? GeodesicLike ?: return
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        t = HyperbolicUtils.parameterOnArc(mx, my, geo.x1, geo.y1, geo.x2, geo.y2, arc)
            .coerceIn(0.0, 1.0)
    }
}

class FreePointOnCircle(
    override val id: Int,
    val circleId: Int,
    var theta: Double
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(circleId)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val circle = objects[circleId] as? Circle
        if (circle == null || !circle.isDefined) { isDefined = false; return }
        x = circle.euclideanCenterX + circle.euclideanRadius * cos(theta)
        y = circle.euclideanCenterY + circle.euclideanRadius * sin(theta)
        isDefined = HyperbolicUtils.isInsideDisk(x, y)
    }

    fun updateParameterFromPosition(mx: Double, my: Double, objects: Map<Int, GeometricObject>) {
        val circle = objects[circleId] as? Circle ?: return
        if (!circle.isDefined) return
        theta = atan2(my - circle.euclideanCenterY, mx - circle.euclideanCenterX)
    }
}

class Midpoint(
    override val id: Int,
    val point1Id: Int,
    val point2Id: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(point1Id, point2Id)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id] as? PointLike
        val p2 = objects[point2Id] as? PointLike
        if (p1 == null || p2 == null || !(objects[point1Id]!!.isDefined) || !(objects[point2Id]!!.isDefined)) {
            isDefined = false; return
        }
        val (mx, my) = HyperbolicUtils.hyperbolicMidpoint(p1.x, p1.y, p2.x, p2.y)
        x = mx; y = my
        isDefined = true
    }
}

class ReflectionPoint(
    override val id: Int,
    val pointId: Int,
    val geodesicId: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(pointId, geodesicId)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pt = objects[pointId] as? PointLike
        val geo = objects[geodesicId] as? GeodesicLike
        if (pt == null || geo == null || !(objects[pointId]!!.isDefined) || !(objects[geodesicId]!!.isDefined)) {
            isDefined = false; return
        }
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        val (rx, ry) = HyperbolicUtils.reflectThroughGeodesic(pt.x, pt.y, arc, geo.x1, geo.y1, geo.x2, geo.y2)
        x = rx; y = ry
        isDefined = HyperbolicUtils.isInsideDisk(x, y)
    }
}
