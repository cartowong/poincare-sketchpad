package poincaresketchpad.geometry

import kotlin.math.*

enum class GeodesicType { LINE, RAY, SEGMENT }

interface GeodesicLike {
    val point1Id: Int
    val point2Id: Int
    val geodesicType: GeodesicType
    val arcCenterX: Double
    val arcCenterY: Double
    val arcRadius: Double
    val isDiameter: Boolean
    val x1: Double
    val y1: Double
    val x2: Double
    val y2: Double
    // Ideal points (where geodesic meets unit circle)
    val idealX1: Double
    val idealY1: Double
    val idealX2: Double
    val idealY2: Double
}

class Line(
    override val id: Int,
    override val point1Id: Int,
    override val point2Id: Int
) : GeometricObject, GeodesicLike {
    override val parentIds: List<Int> = listOf(point1Id, point2Id)
    override var isDefined: Boolean = false; private set
    override val geodesicType: GeodesicType = GeodesicType.LINE
    override var arcCenterX: Double = Double.NaN; private set
    override var arcCenterY: Double = Double.NaN; private set
    override var arcRadius: Double = Double.NaN; private set
    override var isDiameter: Boolean = false; private set
    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var idealX1: Double = 0.0; private set
    override var idealY1: Double = 0.0; private set
    override var idealX2: Double = 0.0; private set
    override var idealY2: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id] as? PointLike
        val p2 = objects[point2Id] as? PointLike
        if (p1 == null || p2 == null || !(objects[point1Id]!!.isDefined) || !(objects[point2Id]!!.isDefined)) {
            isDefined = false; return
        }
        if (hypot(p1.x - p2.x, p1.y - p2.y) < HyperbolicUtils.EPSILON) {
            isDefined = false; return
        }
        x1 = p1.x; y1 = p1.y; x2 = p2.x; y2 = p2.y
        val arc = HyperbolicUtils.geodesicArc(x1, y1, x2, y2)
        arcCenterX = arc.centerX; arcCenterY = arc.centerY
        arcRadius = arc.radius; isDiameter = arc.isDiameter
        val ideals = HyperbolicUtils.idealPoints(x1, y1, x2, y2, arc)
        idealX1 = ideals.x1; idealY1 = ideals.y1
        idealX2 = ideals.x2; idealY2 = ideals.y2
        isDefined = true
    }
}

class Ray(
    override val id: Int,
    val endpointId: Int,
    val throughId: Int
) : GeometricObject, GeodesicLike {
    override val point1Id: Int get() = endpointId
    override val point2Id: Int get() = throughId
    override val parentIds: List<Int> = listOf(endpointId, throughId)
    override var isDefined: Boolean = false; private set
    override val geodesicType: GeodesicType = GeodesicType.RAY
    override var arcCenterX: Double = Double.NaN; private set
    override var arcCenterY: Double = Double.NaN; private set
    override var arcRadius: Double = Double.NaN; private set
    override var isDiameter: Boolean = false; private set
    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var idealX1: Double = 0.0; private set
    override var idealY1: Double = 0.0; private set
    override var idealX2: Double = 0.0; private set
    override var idealY2: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[endpointId] as? PointLike
        val p2 = objects[throughId] as? PointLike
        if (p1 == null || p2 == null || !(objects[endpointId]!!.isDefined) || !(objects[throughId]!!.isDefined)) {
            isDefined = false; return
        }
        if (hypot(p1.x - p2.x, p1.y - p2.y) < HyperbolicUtils.EPSILON) {
            isDefined = false; return
        }
        x1 = p1.x; y1 = p1.y; x2 = p2.x; y2 = p2.y
        val arc = HyperbolicUtils.geodesicArc(x1, y1, x2, y2)
        arcCenterX = arc.centerX; arcCenterY = arc.centerY
        arcRadius = arc.radius; isDiameter = arc.isDiameter
        val ideals = HyperbolicUtils.idealPoints(x1, y1, x2, y2, arc)
        idealX1 = ideals.x1; idealY1 = ideals.y1
        idealX2 = ideals.x2; idealY2 = ideals.y2
        isDefined = true
    }
}

class Segment(
    override val id: Int,
    override val point1Id: Int,
    override val point2Id: Int
) : GeometricObject, GeodesicLike {
    override val parentIds: List<Int> = listOf(point1Id, point2Id)
    override var isDefined: Boolean = false; private set
    override val geodesicType: GeodesicType = GeodesicType.SEGMENT
    override var arcCenterX: Double = Double.NaN; private set
    override var arcCenterY: Double = Double.NaN; private set
    override var arcRadius: Double = Double.NaN; private set
    override var isDiameter: Boolean = false; private set
    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var idealX1: Double = 0.0; private set
    override var idealY1: Double = 0.0; private set
    override var idealX2: Double = 0.0; private set
    override var idealY2: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id] as? PointLike
        val p2 = objects[point2Id] as? PointLike
        if (p1 == null || p2 == null || !(objects[point1Id]!!.isDefined) || !(objects[point2Id]!!.isDefined)) {
            isDefined = false; return
        }
        if (hypot(p1.x - p2.x, p1.y - p2.y) < HyperbolicUtils.EPSILON) {
            isDefined = false; return
        }
        x1 = p1.x; y1 = p1.y; x2 = p2.x; y2 = p2.y
        val arc = HyperbolicUtils.geodesicArc(x1, y1, x2, y2)
        arcCenterX = arc.centerX; arcCenterY = arc.centerY
        arcRadius = arc.radius; isDiameter = arc.isDiameter
        // Segments don't need ideal points for rendering, but compute them for parameterization
        val ideals = HyperbolicUtils.idealPoints(x1, y1, x2, y2, arc)
        idealX1 = ideals.x1; idealY1 = ideals.y1
        idealX2 = ideals.x2; idealY2 = ideals.y2
        isDefined = true
    }
}

class PerpendicularLine(
    override val id: Int,
    val pointId: Int,
    val geodesicId: Int
) : GeometricObject, GeodesicLike {
    override val parentIds: List<Int> = listOf(pointId, geodesicId)
    override var isDefined: Boolean = false; private set
    override val geodesicType: GeodesicType = GeodesicType.LINE
    // For GeodesicLike, point1 is the given point, point2 is the foot
    override var point1Id: Int = pointId; private set
    override var point2Id: Int = geodesicId; private set
    override var arcCenterX: Double = Double.NaN; private set
    override var arcCenterY: Double = Double.NaN; private set
    override var arcRadius: Double = Double.NaN; private set
    override var isDiameter: Boolean = false; private set
    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var idealX1: Double = 0.0; private set
    override var idealY1: Double = 0.0; private set
    override var idealX2: Double = 0.0; private set
    override var idealY2: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pt = objects[pointId] as? PointLike
        val geo = objects[geodesicId] as? GeodesicLike
        if (pt == null || geo == null || !(objects[pointId]!!.isDefined) || !(objects[geodesicId]!!.isDefined)) {
            isDefined = false; return
        }

        val parentArc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        val (footX, footY) = HyperbolicUtils.footOfPerpendicular(pt.x, pt.y, geo.x1, geo.y1, geo.x2, geo.y2, parentArc)

        // If point is on the geodesic, use the perpendicular direction
        x1 = pt.x; y1 = pt.y; x2 = footX; y2 = footY

        if (hypot(x1 - x2, y1 - y2) < HyperbolicUtils.EPSILON) {
            // Point is on the geodesic; perpendicular is orthogonal
            val perpArc = HyperbolicUtils.perpendicularGeodesic(pt.x, pt.y, geo.x1, geo.y1, geo.x2, geo.y2, parentArc)
            arcCenterX = perpArc.centerX; arcCenterY = perpArc.centerY
            arcRadius = perpArc.radius; isDiameter = perpArc.isDiameter
            // x2,y2 must be distinct from x1,y1 for the renderer to compute the arc.
            // Use a point along the perpendicular direction.
            val (tx, ty) = HyperbolicUtils.tangentAtPoint(pt.x, pt.y, geo.x1, geo.y1, geo.x2, geo.y2, parentArc)
            val (q2x, q2y) = HyperbolicUtils.clampToDisk(pt.x + 0.1 * (-ty), pt.y + 0.1 * tx)
            x2 = q2x; y2 = q2y
        } else {
            val arc = HyperbolicUtils.geodesicArc(x1, y1, x2, y2)
            arcCenterX = arc.centerX; arcCenterY = arc.centerY
            arcRadius = arc.radius; isDiameter = arc.isDiameter
        }

        val arc = HyperbolicUtils.GeodesicArc(arcCenterX, arcCenterY, arcRadius, isDiameter)
        val ideals = HyperbolicUtils.idealPoints(x1, y1, x2, y2, arc)
        idealX1 = ideals.x1; idealY1 = ideals.y1
        idealX2 = ideals.x2; idealY2 = ideals.y2
        isDefined = true
    }
}
