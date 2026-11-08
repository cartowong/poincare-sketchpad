package poincaresketchpad.geometry

class GeodesicGeodesicIntersection(
    override val id: Int,
    val geodesic1Id: Int,
    val geodesic2Id: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(geodesic1Id, geodesic2Id)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val geo1 = objects[geodesic1Id] as? GeodesicLike
        val geo2 = objects[geodesic2Id] as? GeodesicLike
        if (geo1 == null || geo2 == null || !(objects[geodesic1Id]!!.isDefined) || !(objects[geodesic2Id]!!.isDefined)) {
            isDefined = false; return
        }
        val result = IntersectionUtils.geodesicGeodesicIntersection(geo1, geo2)
        if (result != null) {
            x = result.x; y = result.y
            isDefined = true
        } else {
            isDefined = false
        }
    }
}

class GeodesicCircleIntersection(
    override val id: Int,
    val geodesicId: Int,
    val circleId: Int,
    val sign: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(geodesicId, circleId)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val geo = objects[geodesicId] as? GeodesicLike
        val circle = objects[circleId] as? Circle
        if (geo == null || circle == null || !(objects[geodesicId]!!.isDefined) || !circle.isDefined) {
            isDefined = false; return
        }
        val result = IntersectionUtils.geodesicCircleIntersection(geo, circle, sign)
        if (result != null) {
            x = result.x; y = result.y
            isDefined = true
        } else {
            isDefined = false
        }
    }
}

class CircleCircleIntersection(
    override val id: Int,
    val circle1Id: Int,
    val circle2Id: Int,
    val sign: Int
) : GeometricObject, PointLike {
    override val parentIds: List<Int> = listOf(circle1Id, circle2Id)
    override var isDefined: Boolean = false; private set
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val c1 = objects[circle1Id] as? Circle
        val c2 = objects[circle2Id] as? Circle
        if (c1 == null || c2 == null || !c1.isDefined || !c2.isDefined) {
            isDefined = false; return
        }
        val result = IntersectionUtils.circleCircleIntersectionPoints(c1, c2, sign)
        if (result != null) {
            x = result.x; y = result.y
            isDefined = true
        } else {
            isDefined = false
        }
    }
}
