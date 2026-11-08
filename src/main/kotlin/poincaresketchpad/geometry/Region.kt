package poincaresketchpad.geometry

import kotlin.math.*

class Region(
    override val id: Int,
    val pointIds: List<Int>
) : GeometricObject {
    override val parentIds: List<Int> = pointIds
    override var isDefined: Boolean = false; private set

    var vertices: List<Pair<Double, Double>> = emptyList(); private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pts = pointIds.map { pid ->
            val p = objects[pid] as? PointLike
            if (p == null || !(objects[pid]!!.isDefined)) {
                isDefined = false; return
            }
            Pair(p.x, p.y)
        }
        vertices = pts
        isDefined = pts.size >= 3
    }

    /**
     * Gauss-Bonnet area: area = (n-2)*PI - sum(interior_angles)
     * Interior angles are computed from tangent vectors of geodesic edges at each vertex.
     */
    fun measureArea(): Double {
        if (!isDefined || vertices.size < 3) return 0.0
        val n = vertices.size
        var sumAngles = 0.0

        for (i in vertices.indices) {
            val prev = vertices[(i + n - 1) % n]
            val curr = vertices[i]
            val next = vertices[(i + 1) % n]

            // Tangent of geodesic from curr toward prev
            val arcPrev = HyperbolicUtils.geodesicArc(curr.first, curr.second, prev.first, prev.second)
            val (tpx, tpy) = HyperbolicUtils.tangentAtPoint(
                curr.first, curr.second, curr.first, curr.second, prev.first, prev.second, arcPrev
            )

            // Tangent of geodesic from curr toward next
            val arcNext = HyperbolicUtils.geodesicArc(curr.first, curr.second, next.first, next.second)
            val (tnx, tny) = HyperbolicUtils.tangentAtPoint(
                curr.first, curr.second, curr.first, curr.second, next.first, next.second, arcNext
            )

            // Interior angle: angle from tangent-to-prev to tangent-to-next, measured as the
            // angle swept CCW. For a convex polygon with vertices in CCW order, this is the interior angle.
            var angle = atan2(tnx * tpy - tny * tpx, tnx * tpx + tny * tpy)
            if (angle < 0) angle += 2 * PI
            sumAngles += angle
        }

        val area = (n - 2) * PI - sumAngles
        return abs(area)
    }
}
