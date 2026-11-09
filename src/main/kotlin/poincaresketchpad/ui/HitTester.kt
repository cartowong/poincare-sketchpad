package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import kotlin.math.*

data class IntersectionCandidate(
    val screenX: Double,
    val screenY: Double,
    val x: Double,
    val y: Double,
    val parent1Id: Int,
    val parent2Id: Int,
    val sign: Int
)

sealed class HitResult {
    data class ConstructedPoint(val id: Int) : HitResult()
    data class OnAngle(val id: Int) : HitResult()
    data class IntersectionPreview(val candidate: IntersectionCandidate) : HitResult()
    data class OnGeodesic(val id: Int) : HitResult()
    data class OnCircle(val id: Int) : HitResult()
    data class OnRegion(val id: Int) : HitResult()
}

class HitTester(
    private val manager: GeometricObjectManager
) {
    private var intersectionCache: List<IntersectionCandidate> = emptyList()

    // Display params — must be updated whenever the canvas resizes or viewport changes
    var diskCenterX: Double = 0.0
    var diskCenterY: Double = 0.0
    var displayScale: Double = 1.0
    var viewportA: Pair<Double, Double> = Pair(0.0, 0.0)
    var viewportTheta: Double = 0.0

    companion object {
        const val SNAP_RADIUS = 10.0 // screen pixels
        const val ANGLE_ARC_RADIUS = 25.0
    }

    /** Apply viewport Mobius transform to model coordinates */
    private fun applyViewport(mx: Double, my: Double): Pair<Double, Double> {
        val (ar, ai) = viewportA
        val (tr, ti) = HyperbolicUtils.mobiusTransform(mx, my, ar, ai)
        return HyperbolicUtils.mobiusRotation(tr, ti, viewportTheta)
    }

    /** Convert model coordinates to screen coordinates (via viewport transform) */
    private fun modelToScreen(mx: Double, my: Double): Pair<Double, Double> {
        val (vx, vy) = applyViewport(mx, my)
        return Pair(diskCenterX + vx * displayScale, diskCenterY - vy * displayScale)
    }

    /**
     * Compute the screen-space representation of a geodesic arc.
     * Returns null if degenerate. Returns (screenCX, screenCY, screenR, isDiameter,
     * screenX1, screenY1, screenX2, screenY2) where the first three describe the Euclidean circle.
     */
    private fun geodesicScreenArc(geo: GeodesicLike): GeodesicScreenData? {
        val (sx1, sy1) = modelToScreen(geo.x1, geo.y1)
        val (sx2, sy2) = modelToScreen(geo.x2, geo.y2)

        // Apply viewport to both defining points and recompute arc in viewport space
        val (vx1, vy1) = applyViewport(geo.x1, geo.y1)
        val (vx2, vy2) = applyViewport(geo.x2, geo.y2)
        val arc = HyperbolicUtils.geodesicArc(vx1, vy1, vx2, vy2)

        if (arc.isDiameter) {
            // Get ideal points in viewport space
            val ideals = HyperbolicUtils.idealPoints(vx1, vy1, vx2, vy2, arc)
            val (si1x, si1y) = Pair(diskCenterX + ideals.x1 * displayScale, diskCenterY - ideals.y1 * displayScale)
            val (si2x, si2y) = Pair(diskCenterX + ideals.x2 * displayScale, diskCenterY - ideals.y2 * displayScale)
            return GeodesicScreenData(
                Double.NaN, Double.NaN, Double.NaN, true,
                sx1, sy1, sx2, sy2,
                si1x, si1y, si2x, si2y
            )
        }

        val screenCX = diskCenterX + arc.centerX * displayScale
        val screenCY = diskCenterY - arc.centerY * displayScale
        val screenR = arc.radius * displayScale

        val ideals = HyperbolicUtils.idealPoints(vx1, vy1, vx2, vy2, arc)
        val (si1x, si1y) = Pair(diskCenterX + ideals.x1 * displayScale, diskCenterY - ideals.y1 * displayScale)
        val (si2x, si2y) = Pair(diskCenterX + ideals.x2 * displayScale, diskCenterY - ideals.y2 * displayScale)

        return GeodesicScreenData(
            screenCX, screenCY, screenR, false,
            sx1, sy1, sx2, sy2,
            si1x, si1y, si2x, si2y
        )
    }

    private data class GeodesicScreenData(
        val arcCenterX: Double, val arcCenterY: Double, val arcRadius: Double,
        val isDiameter: Boolean,
        val screenX1: Double, val screenY1: Double,
        val screenX2: Double, val screenY2: Double,
        val idealScreenX1: Double, val idealScreenY1: Double,
        val idealScreenX2: Double, val idealScreenY2: Double
    )

    /**
     * Distance from a screen point to the nearest point on a rendered geodesic arc.
     */
    private fun distToGeodesic(screenX: Double, screenY: Double, geo: GeodesicLike): Double {
        val data = geodesicScreenArc(geo) ?: return Double.MAX_VALUE

        if (data.isDiameter) {
            // Distance from point to the line segment/ray/line between screen endpoints
            val (startX, startY, endX, endY) = getGeodesicScreenEndpoints(geo, data)
            return distToLineSegment(screenX, screenY, startX, startY, endX, endY)
        }

        // Arc case: distance from point to the nearest point on the rendered arc
        val (startX, startY, endX, endY) = getGeodesicScreenEndpoints(geo, data)
        return distToArc(screenX, screenY, data.arcCenterX, data.arcCenterY, data.arcRadius,
            startX, startY, endX, endY)
    }

    /**
     * Get the screen-space start and end points for a geodesic (accounting for type).
     */
    private fun getGeodesicScreenEndpoints(geo: GeodesicLike, data: GeodesicScreenData): ScreenEndpoints {
        return when (geo.geodesicType) {
            GeodesicType.LINE -> ScreenEndpoints(data.idealScreenX1, data.idealScreenY1, data.idealScreenX2, data.idealScreenY2)
            GeodesicType.RAY -> ScreenEndpoints(data.screenX1, data.screenY1, data.idealScreenX2, data.idealScreenY2)
            GeodesicType.SEGMENT -> ScreenEndpoints(data.screenX1, data.screenY1, data.screenX2, data.screenY2)
        }
    }

    private data class ScreenEndpoints(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

    /**
     * Distance from point to a line segment in screen space.
     */
    private fun distToLineSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-10) return hypot(px - x1, py - y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val ct = t.coerceIn(0.0, 1.0)
        val closestX = x1 + ct * dx
        val closestY = y1 + ct * dy
        return hypot(px - closestX, py - closestY)
    }

    /**
     * Distance from a screen point to the nearest point on an arc.
     * The arc is a portion of a circle (arcCenterX, arcCenterY, arcRadius)
     * between the two screen endpoints.
     */
    private fun distToArc(
        px: Double, py: Double,
        cx: Double, cy: Double, r: Double,
        startX: Double, startY: Double,
        endX: Double, endY: Double
    ): Double {
        // Angle of point relative to arc center
        val pAngle = atan2(py - cy, px - cx)
        val startAngle = atan2(startY - cy, startX - cx)
        val endAngle = atan2(endY - cy, endX - cx)

        // Determine the sweep direction (shorter arc through the disk)
        var sweep = endAngle - startAngle
        while (sweep < -PI) sweep += 2 * PI
        while (sweep > PI) sweep -= 2 * PI

        // Check if the projection of the point onto the circle falls within the arc
        var pRel = pAngle - startAngle
        while (pRel < -PI) pRel += 2 * PI
        while (pRel > PI) pRel -= 2 * PI

        val onArc = if (sweep >= 0) {
            pRel in 0.0..sweep
        } else {
            pRel in sweep..0.0
        }

        return if (onArc) {
            // Closest point is on the arc: distance = |dist_to_center - radius|
            abs(hypot(px - cx, py - cy) - r)
        } else {
            // Closest point is one of the endpoints
            min(hypot(px - startX, py - startY), hypot(px - endX, py - endY))
        }
    }

    /**
     * Distance from a screen point to the nearest point on a circle (in screen space).
     */
    private fun distToCircle(screenX: Double, screenY: Double, circle: Circle): Double {
        // Transform the hyperbolic center and radius point through viewport, then recompute
        // the Euclidean circle representation in viewport space
        val centerObj = manager.getObject(circle.centerId) as? PointLike ?: return Double.MAX_VALUE
        val radiusObj = manager.getObject(circle.radiusPointId) as? PointLike ?: return Double.MAX_VALUE
        val (tc1, tc2) = applyViewport(centerObj.x, centerObj.y)
        val (tr1, tr2) = applyViewport(radiusObj.x, radiusObj.y)

        // Recompute Euclidean circle from transformed center and radius point
        val hDist = HyperbolicUtils.hyperbolicDistance(tc1, tc2, tr1, tr2)
        if (hDist < HyperbolicUtils.EPSILON) return Double.MAX_VALUE

        val cNormSq = tc1 * tc1 + tc2 * tc2
        val t = tanh(hDist / 2.0)
        val denom = 1.0 - cNormSq * t * t
        if (abs(denom) < HyperbolicUtils.EPSILON) return Double.MAX_VALUE

        val scale = (1.0 - t * t) / denom
        val ecx = tc1 * scale
        val ecy = tc2 * scale
        val er = t * (1.0 - cNormSq) / denom
        if (er <= 0) return Double.MAX_VALUE

        val scx = diskCenterX + ecx * displayScale
        val scy = diskCenterY - ecy * displayScale
        val sr = er * displayScale

        return abs(hypot(screenX - scx, screenY - scy) - sr)
    }

    /**
     * Check if a screen point is inside an angle's arc sector (in screen space).
     */
    private fun isInsideAngle(screenX: Double, screenY: Double, angle: Angle): Boolean {
        val (svx, svy) = modelToScreen(angle.vertexX, angle.vertexY)
        val dist = hypot(screenX - svx, screenY - svy)
        if (dist > ANGLE_ARC_RADIUS) return false

        // The angle's start and sweep are computed from tangent vectors in model space.
        // Since the Poincare disk is conformal, angles are preserved under Mobius transforms.
        // We need to recompute tangent angles in viewport space.
        val pA = manager.getObject(angle.pointAId) as? PointLike ?: return false
        val pV = manager.getObject(angle.vertexId) as? PointLike ?: return false
        val pC = manager.getObject(angle.pointCId) as? PointLike ?: return false

        val (vaX, vaY) = applyViewport(pA.x, pA.y)
        val (vvX, vvY) = applyViewport(pV.x, pV.y)
        val (vcX, vcY) = applyViewport(pC.x, pC.y)

        val arcVA = HyperbolicUtils.geodesicArc(vvX, vvY, vaX, vaY)
        val (taX, taY) = HyperbolicUtils.tangentAtPoint(vvX, vvY, vvX, vvY, vaX, vaY, arcVA)
        val arcVC = HyperbolicUtils.geodesicArc(vvX, vvY, vcX, vcY)
        val (tcX, tcY) = HyperbolicUtils.tangentAtPoint(vvX, vvY, vvX, vvY, vcX, vcY, arcVC)

        // Screen-space tangent: model tangent (tx, ty) maps to screen as (tx, -ty) due to y-flip
        val startAngle = atan2(-taY, taX)
        var sweepAngle = atan2(-tcY, tcX) - startAngle
        while (sweepAngle <= 0) sweepAngle += 2 * PI
        while (sweepAngle > 2 * PI) sweepAngle -= 2 * PI

        // Check if mouse angle from vertex is within the sector
        val mouseAngle = atan2(screenY - svy, screenX - svx)
        var relAngle = mouseAngle - startAngle
        while (relAngle < 0) relAngle += 2 * PI
        while (relAngle > 2 * PI) relAngle -= 2 * PI

        return relAngle <= sweepAngle
    }

    /**
     * Check if a screen point is inside a region (in screen space).
     * Uses a point-in-polygon test with straight-line edges between screen-projected vertices.
     * A more accurate test would use geodesic-arc edges, but straight-line is sufficient
     * for the visual hit-testing purpose.
     */
    private fun isInsideRegion(screenX: Double, screenY: Double, region: Region): Boolean {
        if (region.vertices.size < 3) return false
        val screenVerts = region.vertices.map { (mx, my) -> modelToScreen(mx, my) }

        // Ray casting algorithm
        var inside = false
        var j = screenVerts.size - 1
        for (i in screenVerts.indices) {
            val (xi, yi) = screenVerts[i]
            val (xj, yj) = screenVerts[j]
            if (((yi > screenY) != (yj > screenY)) &&
                (screenX < (xj - xi) * (screenY - yi) / (yj - yi) + xi)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun rebuildIntersectionCache() {
        val candidates = mutableListOf<IntersectionCandidate>()
        val objects = manager.allObjects().filter {
            it.isDefined && manager.getMetadata(it.id)?.let { m -> m.visible && !m.isLocked } == true
        }
        val geodesics = objects.filter { it is GeodesicLike }
        val circles = objects.filter { it is Circle }

        // geodesic-geodesic
        for (i in geodesics.indices) {
            for (j in i + 1 until geodesics.size) {
                val g1 = geodesics[i] as GeodesicLike
                val g2 = geodesics[j] as GeodesicLike
                val pt = IntersectionUtils.geodesicGeodesicIntersection(g1, g2) ?: continue
                val (sx, sy) = modelToScreen(pt.x, pt.y)
                candidates.add(IntersectionCandidate(sx, sy, pt.x, pt.y, geodesics[i].id, geodesics[j].id, 0))
            }
        }

        // geodesic-circle
        for (gObj in geodesics) {
            val g = gObj as GeodesicLike
            for (cObj in circles) {
                val c = cObj as Circle
                val rawCandidates = IntersectionUtils.rawGeodesicCircleCandidates(g, c)
                val inDisk = IntersectionUtils.filterToDisk(rawCandidates)
                val onArc = inDisk.filter { IntersectionUtils.isOnGeodesicArc(it.x, it.y, g) }
                // Geodesic-circle intersection points lie ON the geodesic, so use
                // dot-product sign from the midpoint of the intersection pair
                val chordDx = g.x2 - g.x1
                val chordDy = g.y2 - g.y1
                val midX = if (onArc.size == 2) (onArc[0].x + onArc[1].x) / 2.0 else 0.0
                val midY = if (onArc.size == 2) (onArc[0].y + onArc[1].y) / 2.0 else 0.0
                for (pt in onArc) {
                    val sign = if (onArc.size == 2)
                        IntersectionUtils.assignSignAlong(pt.x, pt.y, midX, midY, chordDx, chordDy)
                    else 1
                    val (sx, sy) = modelToScreen(pt.x, pt.y)
                    candidates.add(IntersectionCandidate(sx, sy, pt.x, pt.y, gObj.id, cObj.id, sign))
                }
            }
        }

        // circle-circle
        for (i in circles.indices) {
            for (j in i + 1 until circles.size) {
                val c1 = circles[i] as Circle
                val c2 = circles[j] as Circle
                val rawCandidates = IntersectionUtils.circleCircleIntersection(
                    c1.euclideanCenterX, c1.euclideanCenterY, c1.euclideanRadius,
                    c2.euclideanCenterX, c2.euclideanCenterY, c2.euclideanRadius
                )
                val inDisk = IntersectionUtils.filterToDisk(rawCandidates)
                val refDx = c2.euclideanCenterX - c1.euclideanCenterX
                val refDy = c2.euclideanCenterY - c1.euclideanCenterY
                for (pt in inDisk) {
                    val sign = IntersectionUtils.assignSign(pt.x, pt.y, c1.euclideanCenterX, c1.euclideanCenterY, refDx, refDy)
                    val (sx, sy) = modelToScreen(pt.x, pt.y)
                    candidates.add(IntersectionCandidate(sx, sy, pt.x, pt.y, circles[i].id, circles[j].id, sign))
                }
            }
        }

        intersectionCache = candidates
    }

    fun hitTest(screenX: Double, screenY: Double): HitResult? {
        // Tier 1: Constructed points
        var bestPointDist = Double.MAX_VALUE
        var bestPointId = -1
        for (obj in manager.allObjects()) {
            if (obj is PointLike && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                val (sx, sy) = modelToScreen(obj.x, obj.y)
                val d = hypot(screenX - sx, screenY - sy)
                if (d < SNAP_RADIUS && d < bestPointDist) {
                    bestPointDist = d
                    bestPointId = obj.id
                }
            }
        }
        if (bestPointId >= 0) return HitResult.ConstructedPoint(bestPointId)

        // Tier 2: Angle objects (point inside arc sector)
        for (obj in manager.allObjects()) {
            if (obj is Angle && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                if (isInsideAngle(screenX, screenY, obj)) return HitResult.OnAngle(obj.id)
            }
        }

        // Tier 3: Intersection previews from cache
        var bestIxDist = Double.MAX_VALUE
        var bestIx: IntersectionCandidate? = null
        for (c in intersectionCache) {
            val d = hypot(screenX - c.screenX, screenY - c.screenY)
            if (d < SNAP_RADIUS && d < bestIxDist) {
                bestIxDist = d
                bestIx = c
            }
        }
        if (bestIx != null) return HitResult.IntersectionPreview(bestIx)

        // Tier 4: Geodesic objects
        var bestGeoDist = Double.MAX_VALUE
        var bestGeoId = -1
        for (obj in manager.allObjects()) {
            if (obj is GeodesicLike && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                val d = distToGeodesic(screenX, screenY, obj)
                if (d < SNAP_RADIUS && d < bestGeoDist) {
                    bestGeoDist = d
                    bestGeoId = obj.id
                }
            }
        }
        if (bestGeoId >= 0) return HitResult.OnGeodesic(bestGeoId)

        // Tier 5: Circle objects
        var bestCirDist = Double.MAX_VALUE
        var bestCirId = -1
        for (obj in manager.allObjects()) {
            if (obj is Circle && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                val d = distToCircle(screenX, screenY, obj)
                if (d < SNAP_RADIUS && d < bestCirDist) {
                    bestCirDist = d
                    bestCirId = obj.id
                }
            }
        }
        if (bestCirId >= 0) return HitResult.OnCircle(bestCirId)

        // Tier 6: Region objects (point inside polygon)
        for (obj in manager.allObjects()) {
            if (obj is Region && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                if (isInsideRegion(screenX, screenY, obj)) return HitResult.OnRegion(obj.id)
            }
        }

        return null
    }
}
