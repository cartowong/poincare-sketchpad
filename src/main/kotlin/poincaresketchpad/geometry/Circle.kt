package poincaresketchpad.geometry

import kotlin.math.*

class Circle(
    override val id: Int,
    val centerId: Int,
    val radiusPointId: Int
) : GeometricObject {
    override val parentIds: List<Int> = listOf(centerId, radiusPointId)
    override var isDefined: Boolean = false; private set

    var hyperbolicRadius: Double = 0.0; private set
    var euclideanCenterX: Double = 0.0; private set
    var euclideanCenterY: Double = 0.0; private set
    var euclideanRadius: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val center = objects[centerId] as? PointLike
        val radiusPoint = objects[radiusPointId] as? PointLike
        if (center == null || radiusPoint == null || !(objects[centerId]!!.isDefined) || !(objects[radiusPointId]!!.isDefined)) {
            isDefined = false; return
        }

        hyperbolicRadius = HyperbolicUtils.hyperbolicDistance(center.x, center.y, radiusPoint.x, radiusPoint.y)
        if (hyperbolicRadius < HyperbolicUtils.EPSILON) {
            isDefined = false; return
        }

        val cNormSq = center.x * center.x + center.y * center.y
        val t = tanh(hyperbolicRadius / 2.0)
        val denom = 1.0 - cNormSq * t * t
        if (abs(denom) < HyperbolicUtils.EPSILON) {
            isDefined = false; return
        }

        val scale = (1.0 - t * t) / denom
        euclideanCenterX = center.x * scale
        euclideanCenterY = center.y * scale
        euclideanRadius = t * (1.0 - cNormSq) / denom

        if (euclideanRadius <= 0) {
            isDefined = false; return
        }

        isDefined = true
    }
}
