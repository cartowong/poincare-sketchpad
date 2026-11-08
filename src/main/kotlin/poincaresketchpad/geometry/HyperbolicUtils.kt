package poincaresketchpad.geometry

import kotlin.math.*

object HyperbolicUtils {

    const val EPSILON = 1e-10
    const val DISK_CLAMP = 1.0 - 1e-7

    // --- Complex arithmetic helpers ---

    /** Multiply two complex numbers (a+bi)(c+di) */
    fun complexMul(ar: Double, ai: Double, br: Double, bi: Double): Pair<Double, Double> =
        Pair(ar * br - ai * bi, ar * bi + ai * br)

    /** Divide (a+bi) / (c+di) */
    fun complexDiv(ar: Double, ai: Double, br: Double, bi: Double): Pair<Double, Double> {
        val denom = br * br + bi * bi
        if (denom < EPSILON * EPSILON) return Pair(Double.NaN, Double.NaN)
        return Pair((ar * br + ai * bi) / denom, (ai * br - ar * bi) / denom)
    }

    /** |z|^2 */
    fun complexNormSq(re: Double, im: Double): Double = re * re + im * im

    /** |z| */
    fun complexAbs(re: Double, im: Double): Double = hypot(re, im)

    // --- Mobius transforms ---

    /** T(z) = (z - a) / (1 - conj(a)*z), maps a to origin */
    fun mobiusTransform(zr: Double, zi: Double, ar: Double, ai: Double): Pair<Double, Double> {
        // numerator = z - a
        val nr = zr - ar
        val ni = zi - ai
        // denominator = 1 - conj(a)*z = 1 - (ar - ai*i)(zr + zi*i)
        val dr = 1.0 - (ar * zr + ai * zi)
        val di = -(- ai * zr + ar * zi)
        return complexDiv(nr, ni, dr, di)
    }

    /** T_inverse(w) = (w + a) / (1 + conj(a)*w) */
    fun mobiusInverse(wr: Double, wi: Double, ar: Double, ai: Double): Pair<Double, Double> {
        val nr = wr + ar
        val ni = wi + ai
        val dr = 1.0 + (ar * wr + ai * wi)
        val di = -ai * wr + ar * wi
        return complexDiv(nr, ni, dr, di)
    }

    /** z * e^(i*theta) */
    fun mobiusRotation(zr: Double, zi: Double, theta: Double): Pair<Double, Double> {
        val c = cos(theta)
        val s = sin(theta)
        return Pair(zr * c - zi * s, zr * s + zi * c)
    }

    // --- Hyperbolic distance ---

    fun hyperbolicDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        val num = hypot(dx, dy)
        // |1 - conj(p1)*p2| = |1 - (x1-iy1)(x2+iy2)| = |1-(x1x2+y1y2) - i(x1y2-y1x2)|
        val dr = 1.0 - (x1 * x2 + y1 * y2)
        val di = -(x1 * y2 - y1 * x2)
        val denom = hypot(dr, di)
        if (denom < EPSILON) return Double.MAX_VALUE
        val ratio = (num / denom).coerceIn(0.0, 1.0 - EPSILON)
        return 2.0 * atanh(ratio)
    }

    // --- Geodesic arc computation ---

    data class GeodesicArc(
        val centerX: Double, val centerY: Double,
        val radius: Double, val isDiameter: Boolean
    )

    /** Returns true if p1, p2 and the origin are collinear */
    fun isCollinearWithOrigin(x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        // Cross product of p1 and p2 relative to origin
        return abs(x1 * y2 - y1 * x2) < EPSILON
    }

    /**
     * Compute the Euclidean circle (or diameter) representing the geodesic through p1 and p2.
     * The geodesic is the arc of a circle orthogonal to the unit circle.
     */
    fun geodesicArc(x1: Double, y1: Double, x2: Double, y2: Double): GeodesicArc {
        if (isCollinearWithOrigin(x1, y1, x2, y2)) {
            return GeodesicArc(Double.NaN, Double.NaN, Double.NaN, true)
        }

        // Invert p1 through the unit circle: p1* = p1 / |p1|^2
        val n1sq = complexNormSq(x1, y1)
        if (n1sq < EPSILON * EPSILON) {
            // p1 is at origin — geodesic is a diameter
            return GeodesicArc(Double.NaN, Double.NaN, Double.NaN, true)
        }
        val ix = x1 / n1sq
        val iy = y1 / n1sq

        // Three-point circle through p1, p2, p1*
        // Using the circumcircle formula
        val ax = x1; val ay = y1
        val bx = x2; val by = y2
        val cx = ix; val cy = iy

        val d = 2.0 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        if (abs(d) < EPSILON) {
            return GeodesicArc(Double.NaN, Double.NaN, Double.NaN, true)
        }

        val aSq = ax * ax + ay * ay
        val bSq = bx * bx + by * by
        val cSq = cx * cx + cy * cy

        val centerX = (aSq * (by - cy) + bSq * (cy - ay) + cSq * (ay - by)) / d
        val centerY = (aSq * (cx - bx) + bSq * (ax - cx) + cSq * (bx - ax)) / d
        val radius = hypot(ax - centerX, ay - centerY)

        return GeodesicArc(centerX, centerY, radius, false)
    }

    // --- Ideal points (where geodesic meets unit circle) ---

    data class IdealPoints(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

    /**
     * Compute the two points where the geodesic through p1, p2 meets the unit circle.
     * For a diameter: extend the line to the boundary.
     * For an arc: intersect the arc's Euclidean circle with the unit circle.
     * Returns ideal points ordered so that the arc from ideal1 through p1 then p2 to ideal2
     * goes in consistent direction.
     */
    fun idealPoints(x1: Double, y1: Double, x2: Double, y2: Double, arc: GeodesicArc): IdealPoints {
        if (arc.isDiameter) {
            // Direction from p1 to p2
            val dx = x2 - x1
            val dy = y2 - y1
            val len = hypot(dx, dy)
            if (len < EPSILON) {
                // Degenerate: use p1 direction
                val l = hypot(x1, y1)
                if (l < EPSILON) return IdealPoints(1.0, 0.0, -1.0, 0.0)
                return IdealPoints(x1 / l, y1 / l, -x1 / l, -y1 / l)
            }
            val ux = dx / len
            val uy = dy / len
            // ideal1 is in the -direction from p1, ideal2 is in the +direction
            return IdealPoints(-ux, -uy, ux, uy)
        }

        // Arc case: intersect circle(arcCenter, arcRadius) with unit circle(origin, 1)
        val cx = arc.centerX
        val cy = arc.centerY
        val r = arc.radius
        val d = hypot(cx, cy)

        if (d < EPSILON) {
            // Arc center at origin — degenerate
            return IdealPoints(1.0, 0.0, -1.0, 0.0)
        }

        // Line from origin to arc center
        val ex = cx / d
        val ey = cy / d

        // Distance from origin to the chord line
        val a = (d * d + 1.0 - r * r) / (2.0 * d)
        val hSq = 1.0 - a * a
        if (hSq < 0) {
            // No intersection
            return IdealPoints(Double.NaN, Double.NaN, Double.NaN, Double.NaN)
        }
        val h = sqrt(hSq.coerceAtLeast(0.0))

        // Two intersection points
        val mx = a * ex
        val my = a * ey
        val px = -ey * h
        val py = ex * h
        val ix1 = mx + px; val iy1 = my + py
        val ix2 = mx - px; val iy2 = my - py

        // Order: determine which ideal point is "before" p1 and which is "after" p2
        // Use the angle on the arc circle
        val ang1 = atan2(iy1 - cy, ix1 - cx)
        val ang2 = atan2(iy2 - cy, ix2 - cx)
        val angP1 = atan2(y1 - cy, x1 - cx)
        val angP2 = atan2(y2 - cy, x2 - cx)

        // Check arc ordering: from ideal1 -> p1 -> p2 -> ideal2 should be consistent
        // Use cross product to determine direction
        fun angleDiff(from: Double, to: Double): Double {
            var d = to - from
            while (d < -PI) d += 2 * PI
            while (d > PI) d -= 2 * PI
            return d
        }

        // Arc direction from p1 to p2
        val p1ToP2 = angleDiff(angP1, angP2)

        // Check if ideal1 -> p1 -> p2 -> ideal2 are in the same direction as p1->p2
        val i1ToP1 = angleDiff(ang1, angP1)
        val p2ToI2 = angleDiff(angP2, ang2)

        // If the signs agree, the ordering is (ideal1, ideal2), otherwise swap
        return if ((i1ToP1 * p1ToP2 >= 0) && (p2ToI2 * p1ToP2 >= 0)) {
            IdealPoints(ix1, iy1, ix2, iy2)
        } else {
            IdealPoints(ix2, iy2, ix1, iy1)
        }
    }

    // --- Tangent at point on geodesic ---

    /**
     * Tangent direction of geodesic at point p, pointing from p1 toward p2.
     * Returns (tx, ty) unit vector.
     */
    fun tangentAtPoint(
        px: Double, py: Double,
        p1x: Double, p1y: Double, p2x: Double, p2y: Double,
        arc: GeodesicArc
    ): Pair<Double, Double> {
        if (arc.isDiameter) {
            val dx = p2x - p1x
            val dy = p2y - p1y
            val len = hypot(dx, dy)
            return if (len < EPSILON) Pair(1.0, 0.0) else Pair(dx / len, dy / len)
        }

        // For arc: tangent at p is perpendicular to (p - arcCenter)
        val rx = px - arc.centerX
        val ry = py - arc.centerY

        // Two perpendicular directions: (-ry, rx) and (ry, -rx)
        // Pick the one that points from p1 toward p2 along the arc
        val tx = -ry
        val ty = rx
        val len = hypot(tx, ty)
        if (len < EPSILON) return Pair(1.0, 0.0)
        val ntx = tx / len
        val nty = ty / len

        // Check direction: at p, the tangent should point "toward p2" along the arc
        // Test by checking if moving a tiny step along tangent gets closer to p2 on the arc
        val angP = atan2(py - arc.centerY, px - arc.centerX)
        val angP2 = atan2(p2y - arc.centerY, p2x - arc.centerX)
        var diff = angP2 - angP
        while (diff < -PI) diff += 2 * PI
        while (diff > PI) diff -= 2 * PI

        // The tangent (-ry, rx) corresponds to increasing angle (CCW)
        // If diff > 0, p2 is in CCW direction, so tangent is correct
        // If diff < 0, p2 is in CW direction, so flip
        return if (diff >= 0) Pair(ntx, nty) else Pair(-ntx, -nty)
    }

    // --- Reflection through geodesic ---

    fun reflectThroughGeodesic(
        px: Double, py: Double,
        arc: GeodesicArc,
        p1x: Double, p1y: Double, p2x: Double, p2y: Double
    ): Pair<Double, Double> {
        if (arc.isDiameter) {
            // Reflect across the line through origin with direction p2-p1
            val dx = p2x - p1x
            val dy = p2y - p1y
            val len = hypot(dx, dy)
            if (len < EPSILON) return Pair(px, py)
            val ux = dx / len
            val uy = dy / len
            // Reflection: p' = 2*(p·u)*u - p
            val dot = px * ux + py * uy
            return Pair(2.0 * dot * ux - px, 2.0 * dot * uy - py)
        }

        // Arc case: inversion through the Euclidean circle
        val cx = arc.centerX
        val cy = arc.centerY
        val r = arc.radius
        val dx = px - cx
        val dy = py - cy
        val distSq = dx * dx + dy * dy
        if (distSq < EPSILON * EPSILON) return Pair(px, py)
        val scale = r * r / distSq
        return Pair(cx + dx * scale, cy + dy * scale)
    }

    // --- Hyperbolic midpoint ---

    fun hyperbolicMidpoint(x1: Double, y1: Double, x2: Double, y2: Double): Pair<Double, Double> {
        // Map p1 to origin via Mobius
        val (qr, qi) = mobiusTransform(x2, y2, x1, y1)
        val qAbs = hypot(qr, qi)

        if (qAbs < EPSILON) {
            // Points are the same
            return Pair(x1, y1)
        }

        // Midpoint in transformed space: m' = q * tanh(arctanh(|q|)/2) / |q|
        val halfDist = atanh(qAbs.coerceAtMost(DISK_CLAMP)) / 2.0
        val mAbs = tanh(halfDist)
        val mr = qr * mAbs / qAbs
        val mi = qi * mAbs / qAbs

        // Map back
        return mobiusInverse(mr, mi, x1, y1)
    }

    // --- Disk clamping ---

    fun clampToDisk(x: Double, y: Double, maxRadius: Double = DISK_CLAMP): Pair<Double, Double> {
        val r = hypot(x, y)
        return if (r >= maxRadius) {
            val scale = maxRadius / r
            Pair(x * scale, y * scale)
        } else {
            Pair(x, y)
        }
    }

    fun isInsideDisk(x: Double, y: Double, maxRadius: Double = DISK_CLAMP): Boolean {
        return x * x + y * y < maxRadius * maxRadius
    }

    // --- Foot of perpendicular ---

    /**
     * Find the foot of perpendicular from point (px,py) to the geodesic defined by arc.
     * This is the closest point on the geodesic to p in hyperbolic distance.
     */
    fun footOfPerpendicular(
        px: Double, py: Double,
        p1x: Double, p1y: Double, p2x: Double, p2y: Double,
        arc: GeodesicArc
    ): Pair<Double, Double> {
        if (arc.isDiameter) {
            // Project onto the line through origin
            val dx = p2x - p1x
            val dy = p2y - p1y
            val len = hypot(dx, dy)
            if (len < EPSILON) return Pair(p1x, p1y)
            val ux = dx / len
            val uy = dy / len
            val dot = px * ux + py * uy
            return Pair(dot * ux, dot * uy)
        }

        // Arc case: the foot is where the perpendicular geodesic from p meets this geodesic.
        // Simpler approach: the perpendicular from p to the geodesic (circle inversion).
        // The foot is the point on the arc circle closest to p along the "geodesic perpendicular."
        // For a circle orthogonal to unit circle, the foot of perpendicular from p is found by:
        // reflecting p through the geodesic, then the midpoint of p and its reflection lies on the geodesic.
        val (rx, ry) = reflectThroughGeodesic(px, py, arc, p1x, p1y, p2x, p2y)
        // The geodesic midpoint of p and its reflection is the foot
        // But actually, for a circle orthogonal to the unit circle, the foot is simply the
        // nearest point on the Euclidean circle to p (along the radius from circle center through p)
        val cx = arc.centerX
        val cy = arc.centerY
        val r = arc.radius
        val dx = px - cx
        val dy = py - cy
        val dist = hypot(dx, dy)
        if (dist < EPSILON) return Pair(cx + r, cy)
        return Pair(cx + dx * r / dist, cy + dy * r / dist)
    }

    // --- Arc interpolation ---

    /**
     * Interpolate along a geodesic arc at parameter t.
     * For a diameter: linear interpolation between the two boundary points.
     * For an arc: angular interpolation on the Euclidean circle.
     */
    fun interpolateOnArc(
        t: Double,
        startX: Double, startY: Double,
        endX: Double, endY: Double,
        arc: GeodesicArc
    ): Pair<Double, Double> {
        if (arc.isDiameter) {
            return Pair(startX + t * (endX - startX), startY + t * (endY - startY))
        }

        val startAngle = atan2(startY - arc.centerY, startX - arc.centerX)
        val endAngle = atan2(endY - arc.centerY, endX - arc.centerX)

        var sweep = endAngle - startAngle
        // Choose the shorter arc (which is the one inside the disk)
        while (sweep < -PI) sweep += 2 * PI
        while (sweep > PI) sweep -= 2 * PI

        val angle = startAngle + t * sweep
        return Pair(
            arc.centerX + arc.radius * cos(angle),
            arc.centerY + arc.radius * sin(angle)
        )
    }

    /**
     * Find the parameter t (in [0,1]) for a point on an arc between start and end.
     */
    fun parameterOnArc(
        px: Double, py: Double,
        startX: Double, startY: Double,
        endX: Double, endY: Double,
        arc: GeodesicArc
    ): Double {
        if (arc.isDiameter) {
            val dx = endX - startX
            val dy = endY - startY
            val len = dx * dx + dy * dy
            if (len < EPSILON) return 0.5
            return ((px - startX) * dx + (py - startY) * dy) / len
        }

        val startAngle = atan2(startY - arc.centerY, startX - arc.centerX)
        val endAngle = atan2(endY - arc.centerY, endX - arc.centerX)
        val pAngle = atan2(py - arc.centerY, px - arc.centerX)

        var sweep = endAngle - startAngle
        while (sweep < -PI) sweep += 2 * PI
        while (sweep > PI) sweep -= 2 * PI

        if (abs(sweep) < EPSILON) return 0.5

        var pSweep = pAngle - startAngle
        while (pSweep < -PI) pSweep += 2 * PI
        while (pSweep > PI) pSweep -= 2 * PI

        return (pSweep / sweep).coerceIn(0.0, 1.0)
    }

    // --- Perpendicular geodesic through a point ---

    /**
     * Compute the geodesic through point p that is perpendicular to the geodesic
     * defined by (p1, p2, arc). Returns the arc of the perpendicular geodesic,
     * plus a second point on it (the foot of perpendicular).
     */
    fun perpendicularGeodesic(
        px: Double, py: Double,
        p1x: Double, p1y: Double, p2x: Double, p2y: Double,
        arc: GeodesicArc
    ): GeodesicArc {
        val (footX, footY) = footOfPerpendicular(px, py, p1x, p1y, p2x, p2y, arc)

        // Check if foot and point are the same
        if (hypot(px - footX, py - footY) < EPSILON) {
            // Point is on the geodesic; perpendicular direction is tangent-orthogonal
            val (tx, ty) = tangentAtPoint(px, py, p1x, p1y, p2x, p2y, arc)
            // The perpendicular geodesic has tangent (-ty, tx) at p
            // Find a second point: move a small step in that direction
            val step = 0.1
            val q2x = px + step * (-ty)
            val q2y = py + step * tx
            val (cq2x, cq2y) = clampToDisk(q2x, q2y)
            return geodesicArc(px, py, cq2x, cq2y)
        }

        return geodesicArc(px, py, footX, footY)
    }

    // --- Grid helpers ---

    /** Euclidean radius for a hyperbolic circle of radius r centered at origin: tanh(r/2) */
    fun hyperbolicRadiusToEuclidean(r: Double): Double = tanh(r / 2.0)
}
