package poincaresketchpad.geometry

import kotlin.math.*

object IntersectionUtils {

    private const val EPSILON = 1e-10

    data class Point2D(val x: Double, val y: Double)

    // --- Euclidean intersection algorithms (same as EuclideanSketchpad) ---

    fun circleCircleIntersection(
        cx1: Double, cy1: Double, r1: Double,
        cx2: Double, cy2: Double, r2: Double
    ): List<Point2D> {
        val dx = cx2 - cx1
        val dy = cy2 - cy1
        val d = hypot(dx, dy)
        if (d < EPSILON) return emptyList()
        if (d > r1 + r2 + EPSILON) return emptyList()
        if (d < abs(r1 - r2) - EPSILON) return emptyList()

        val a = (r1 * r1 - r2 * r2 + d * d) / (2.0 * d)
        val hSq = r1 * r1 - a * a
        if (hSq < -EPSILON) return emptyList()
        val h = sqrt(hSq.coerceAtLeast(0.0))

        val mx = cx1 + a * dx / d
        val my = cy1 + a * dy / d

        if (h < EPSILON) {
            return listOf(Point2D(mx, my))
        }

        val px = -dy * h / d
        val py = dx * h / d
        return listOf(
            Point2D(mx + px, my + py),
            Point2D(mx - px, my - py)
        )
    }

    fun lineCircleIntersection(
        lx1: Double, ly1: Double, lx2: Double, ly2: Double,
        cx: Double, cy: Double, r: Double
    ): List<Point2D> {
        val dx = lx2 - lx1
        val dy = ly2 - ly1
        val fx = lx1 - cx
        val fy = ly1 - cy

        val a = dx * dx + dy * dy
        val b = 2.0 * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - r * r

        var discriminant = b * b - 4.0 * a * c
        if (discriminant < -EPSILON) return emptyList()
        discriminant = discriminant.coerceAtLeast(0.0)
        val sqrtDisc = sqrt(discriminant)

        val t1 = (-b - sqrtDisc) / (2.0 * a)
        val t2 = (-b + sqrtDisc) / (2.0 * a)

        val results = mutableListOf<Point2D>()
        results.add(Point2D(lx1 + t1 * dx, ly1 + t1 * dy))
        if (abs(t1 - t2) > EPSILON) {
            results.add(Point2D(lx1 + t2 * dx, ly1 + t2 * dy))
        }
        return results
    }

    fun lineLineIntersection(
        x1: Double, y1: Double, x2: Double, y2: Double,
        x3: Double, y3: Double, x4: Double, y4: Double
    ): Point2D? {
        val d1x = x2 - x1; val d1y = y2 - y1
        val d2x = x4 - x3; val d2y = y4 - y3
        val cross = d1x * d2y - d1y * d2x
        if (abs(cross) < EPSILON) return null
        val t = ((x3 - x1) * d2y - (y3 - y1) * d2x) / cross
        return Point2D(x1 + t * d1x, y1 + t * d1y)
    }

    // --- Disk filtering ---

    fun filterToDisk(candidates: List<Point2D>, epsilon: Double = 1e-7): List<Point2D> {
        return candidates.filter { it.x * it.x + it.y * it.y < (1.0 - epsilon) * (1.0 - epsilon) }
    }

    // --- Arc bounds checking ---

    fun isOnGeodesicArc(
        px: Double, py: Double,
        geo: GeodesicLike
    ): Boolean {
        return when (geo.geodesicType) {
            GeodesicType.LINE -> true
            GeodesicType.RAY -> isOnRay(px, py, geo)
            GeodesicType.SEGMENT -> isOnSegment(px, py, geo)
        }
    }

    private fun isOnRay(px: Double, py: Double, geo: GeodesicLike): Boolean {
        // Point must be on the same side of endpoint as throughPoint
        if (geo.isDiameter) {
            val dx = geo.x2 - geo.x1
            val dy = geo.y2 - geo.y1
            val dpx = px - geo.x1
            val dpy = py - geo.y1
            return dx * dpx + dy * dpy >= -EPSILON
        }
        // Arc case: angle from endpoint to p should be in the same direction as endpoint to throughPoint
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        val t = HyperbolicUtils.parameterOnArc(px, py, geo.x1, geo.y1, geo.idealX2, geo.idealY2, arc)
        return t >= -EPSILON
    }

    private fun isOnSegment(px: Double, py: Double, geo: GeodesicLike): Boolean {
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
        val t = HyperbolicUtils.parameterOnArc(px, py, geo.x1, geo.y1, geo.x2, geo.y2, arc)
        return t >= -EPSILON && t <= 1.0 + EPSILON
    }

    // --- Sign disambiguation ---

    fun assignSign(ix: Double, iy: Double, refX: Double, refY: Double, refDx: Double, refDy: Double): Int {
        val cross = (ix - refX) * refDy - (iy - refY) * refDx
        return if (cross >= 0) 1 else -1
    }

    /**
     * Assigns sign based on position along a direction vector (dot product).
     * Used for geodesic-circle intersections where both points lie on the geodesic,
     * making cross-product disambiguation degenerate.
     * sign=1 for the point further along the direction from refX,refY.
     */
    fun assignSignAlong(ix: Double, iy: Double, refX: Double, refY: Double, dirX: Double, dirY: Double): Int {
        val dot = (ix - refX) * dirX + (iy - refY) * dirY
        return if (dot >= 0) 1 else -1
    }

    // --- High-level intersection for geodesic x geodesic ---

    fun geodesicGeodesicIntersection(geo1: GeodesicLike, geo2: GeodesicLike): Point2D? {
        val candidates = rawGeodesicGeodesicCandidates(geo1, geo2)
        val inDisk = filterToDisk(candidates)
        return inDisk.firstOrNull { isOnGeodesicArc(it.x, it.y, geo1) && isOnGeodesicArc(it.x, it.y, geo2) }
    }

    fun rawGeodesicGeodesicCandidates(geo1: GeodesicLike, geo2: GeodesicLike): List<Point2D> {
        return when {
            geo1.isDiameter && geo2.isDiameter -> {
                val p = lineLineIntersection(
                    geo1.x1, geo1.y1, geo1.x2, geo1.y2,
                    geo2.x1, geo2.y1, geo2.x2, geo2.y2
                )
                if (p != null) listOf(p) else emptyList()
            }
            geo1.isDiameter -> {
                lineCircleIntersection(
                    geo1.x1, geo1.y1, geo1.x2, geo1.y2,
                    geo2.arcCenterX, geo2.arcCenterY, geo2.arcRadius
                )
            }
            geo2.isDiameter -> {
                lineCircleIntersection(
                    geo2.x1, geo2.y1, geo2.x2, geo2.y2,
                    geo1.arcCenterX, geo1.arcCenterY, geo1.arcRadius
                )
            }
            else -> {
                circleCircleIntersection(
                    geo1.arcCenterX, geo1.arcCenterY, geo1.arcRadius,
                    geo2.arcCenterX, geo2.arcCenterY, geo2.arcRadius
                )
            }
        }
    }

    // --- High-level intersection for geodesic x circle ---

    fun geodesicCircleIntersection(
        geo: GeodesicLike, circle: Circle, sign: Int
    ): Point2D? {
        val candidates = rawGeodesicCircleCandidates(geo, circle)
        val inDisk = filterToDisk(candidates)
        val onArc = inDisk.filter { isOnGeodesicArc(it.x, it.y, geo) }

        return when {
            onArc.isEmpty() -> null
            onArc.size == 1 -> onArc[0]
            else -> {
                // Intersection points lie ON the geodesic, so cross product with the
                // geodesic direction is ~0. Use dot product along the geodesic direction
                // from the foot of perpendicular (midpoint between the two intersection
                // points projected onto the geodesic), which guarantees the two points
                // are on opposite sides.
                val midX = (onArc[0].x + onArc[1].x) / 2.0
                val midY = (onArc[0].y + onArc[1].y) / 2.0
                val chordDx = geo.x2 - geo.x1
                val chordDy = geo.y2 - geo.y1
                onArc.firstOrNull { assignSignAlong(it.x, it.y, midX, midY, chordDx, chordDy) == sign }
                    ?: onArc[0]
            }
        }
    }

    fun rawGeodesicCircleCandidates(geo: GeodesicLike, circle: Circle): List<Point2D> {
        return if (geo.isDiameter) {
            lineCircleIntersection(
                geo.x1, geo.y1, geo.x2, geo.y2,
                circle.euclideanCenterX, circle.euclideanCenterY, circle.euclideanRadius
            )
        } else {
            circleCircleIntersection(
                geo.arcCenterX, geo.arcCenterY, geo.arcRadius,
                circle.euclideanCenterX, circle.euclideanCenterY, circle.euclideanRadius
            )
        }
    }

    // --- High-level intersection for circle x circle ---

    fun circleCircleIntersectionPoints(
        c1: Circle, c2: Circle, sign: Int
    ): Point2D? {
        val candidates = circleCircleIntersection(
            c1.euclideanCenterX, c1.euclideanCenterY, c1.euclideanRadius,
            c2.euclideanCenterX, c2.euclideanCenterY, c2.euclideanRadius
        )
        val inDisk = filterToDisk(candidates)

        return when {
            inDisk.isEmpty() -> null
            inDisk.size == 1 -> inDisk[0]
            else -> {
                val refDx = c2.euclideanCenterX - c1.euclideanCenterX
                val refDy = c2.euclideanCenterY - c1.euclideanCenterY
                inDisk.firstOrNull {
                    assignSign(it.x, it.y, c1.euclideanCenterX, c1.euclideanCenterY, refDx, refDy) == sign
                } ?: inDisk[0]
            }
        }
    }
}
