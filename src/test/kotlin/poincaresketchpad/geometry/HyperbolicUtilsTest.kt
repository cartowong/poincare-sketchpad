package poincaresketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class HyperbolicUtilsTest {

    private val EPS = 1e-7

    // --- Mobius transform tests ---

    @Test
    fun `mobiusTransform maps a to origin`() {
        val ax = 0.3; val ay = 0.4
        val (rx, ry) = HyperbolicUtils.mobiusTransform(ax, ay, ax, ay)
        assertEquals(0.0, rx, EPS)
        assertEquals(0.0, ry, EPS)
    }

    @Test
    fun `mobiusTransform maps origin to negative a`() {
        val ax = 0.3; val ay = 0.4
        val (rx, ry) = HyperbolicUtils.mobiusTransform(0.0, 0.0, ax, ay)
        assertEquals(-ax, rx, EPS)
        assertEquals(-ay, ry, EPS)
    }

    @Test
    fun `mobiusInverse is inverse of mobiusTransform`() {
        val ax = 0.3; val ay = -0.2
        val zx = 0.5; val zy = 0.1
        val (wx, wy) = HyperbolicUtils.mobiusTransform(zx, zy, ax, ay)
        val (rx, ry) = HyperbolicUtils.mobiusInverse(wx, wy, ax, ay)
        assertEquals(zx, rx, EPS)
        assertEquals(zy, ry, EPS)
    }

    @Test
    fun `mobiusTransform preserves disk`() {
        val ax = 0.5; val ay = 0.3
        val zx = -0.2; val zy = 0.6
        val (wx, wy) = HyperbolicUtils.mobiusTransform(zx, zy, ax, ay)
        assertTrue(wx * wx + wy * wy < 1.0)
    }

    @Test
    fun `mobiusRotation rotates correctly`() {
        val (rx, ry) = HyperbolicUtils.mobiusRotation(1.0, 0.0, PI / 2)
        assertEquals(0.0, rx, EPS)
        assertEquals(1.0, ry, EPS)
    }

    // --- Hyperbolic distance tests ---

    @Test
    fun `hyperbolicDistance is zero for same point`() {
        assertEquals(0.0, HyperbolicUtils.hyperbolicDistance(0.3, 0.4, 0.3, 0.4), EPS)
    }

    @Test
    fun `hyperbolicDistance is symmetric`() {
        val d1 = HyperbolicUtils.hyperbolicDistance(0.1, 0.2, 0.5, -0.3)
        val d2 = HyperbolicUtils.hyperbolicDistance(0.5, -0.3, 0.1, 0.2)
        assertEquals(d1, d2, EPS)
    }

    @Test
    fun `hyperbolicDistance from origin is 2 arctanh`() {
        val px = 0.5; val py = 0.0
        val d = HyperbolicUtils.hyperbolicDistance(0.0, 0.0, px, py)
        val expected = 2.0 * atanh(0.5)
        assertEquals(expected, d, EPS)
    }

    @Test
    fun `hyperbolicDistance is Mobius invariant`() {
        val x1 = 0.1; val y1 = 0.2
        val x2 = -0.3; val y2 = 0.4
        val ax = 0.2; val ay = -0.1
        val d1 = HyperbolicUtils.hyperbolicDistance(x1, y1, x2, y2)
        val (mx1, my1) = HyperbolicUtils.mobiusTransform(x1, y1, ax, ay)
        val (mx2, my2) = HyperbolicUtils.mobiusTransform(x2, y2, ax, ay)
        val d2 = HyperbolicUtils.hyperbolicDistance(mx1, my1, mx2, my2)
        assertEquals(d1, d2, EPS)
    }

    // --- Geodesic arc tests ---

    @Test
    fun `geodesicArc through origin is diameter`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.0, -0.2, 0.0)
        assertTrue(arc.isDiameter)
    }

    @Test
    fun `geodesicArc non-collinear is not diameter`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.2, -0.1, 0.4)
        assertFalse(arc.isDiameter)
    }

    @Test
    fun `geodesicArc circle is orthogonal to unit circle`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.2, -0.1, 0.4)
        if (!arc.isDiameter) {
            // For orthogonality: |center|^2 = r^2 + 1
            val centerNormSq = arc.centerX * arc.centerX + arc.centerY * arc.centerY
            assertEquals(arc.radius * arc.radius + 1.0, centerNormSq, 1e-6)
        }
    }

    @Test
    fun `geodesicArc passes through both points`() {
        val x1 = 0.3; val y1 = 0.2
        val x2 = -0.1; val y2 = 0.4
        val arc = HyperbolicUtils.geodesicArc(x1, y1, x2, y2)
        if (!arc.isDiameter) {
            val d1 = hypot(x1 - arc.centerX, y1 - arc.centerY)
            val d2 = hypot(x2 - arc.centerX, y2 - arc.centerY)
            assertEquals(arc.radius, d1, 1e-6)
            assertEquals(arc.radius, d2, 1e-6)
        }
    }

    // --- Ideal points tests ---

    @Test
    fun `idealPoints are on unit circle`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.2, -0.1, 0.4)
        val ideals = HyperbolicUtils.idealPoints(0.3, 0.2, -0.1, 0.4, arc)
        assertEquals(1.0, hypot(ideals.x1, ideals.y1), 1e-6)
        assertEquals(1.0, hypot(ideals.x2, ideals.y2), 1e-6)
    }

    @Test
    fun `idealPoints for diameter are antipodal`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.0, -0.2, 0.0)
        val ideals = HyperbolicUtils.idealPoints(0.3, 0.0, -0.2, 0.0, arc)
        assertEquals(-ideals.x1, ideals.x2, EPS)
        assertEquals(-ideals.y1, ideals.y2, EPS)
    }

    // --- Reflection tests ---

    @Test
    fun `reflectThroughGeodesic is involution`() {
        val px = 0.2; val py = 0.3
        val arc = HyperbolicUtils.geodesicArc(0.4, 0.1, -0.1, 0.5)
        val (rx, ry) = HyperbolicUtils.reflectThroughGeodesic(px, py, arc, 0.4, 0.1, -0.1, 0.5)
        val (rrx, rry) = HyperbolicUtils.reflectThroughGeodesic(rx, ry, arc, 0.4, 0.1, -0.1, 0.5)
        assertEquals(px, rrx, 1e-6)
        assertEquals(py, rry, 1e-6)
    }

    @Test
    fun `reflectThroughGeodesic preserves hyperbolic distance`() {
        val px = 0.2; val py = 0.3
        val qx = -0.1; val qy = 0.1
        val arc = HyperbolicUtils.geodesicArc(0.4, 0.1, -0.1, 0.5)
        val (rpx, rpy) = HyperbolicUtils.reflectThroughGeodesic(px, py, arc, 0.4, 0.1, -0.1, 0.5)
        val (rqx, rqy) = HyperbolicUtils.reflectThroughGeodesic(qx, qy, arc, 0.4, 0.1, -0.1, 0.5)
        val d1 = HyperbolicUtils.hyperbolicDistance(px, py, qx, qy)
        val d2 = HyperbolicUtils.hyperbolicDistance(rpx, rpy, rqx, rqy)
        assertEquals(d1, d2, 1e-5)
    }

    @Test
    fun `reflectThroughGeodesic diameter case`() {
        // Reflect (0.3, 0.2) across the x-axis (diameter through (1,0) and (-1,0))
        val arc = HyperbolicUtils.geodesicArc(0.5, 0.0, -0.5, 0.0)
        val (rx, ry) = HyperbolicUtils.reflectThroughGeodesic(0.3, 0.2, arc, 0.5, 0.0, -0.5, 0.0)
        assertEquals(0.3, rx, EPS)
        assertEquals(-0.2, ry, EPS)
    }

    // --- Hyperbolic midpoint tests ---

    @Test
    fun `hyperbolicMidpoint is equidistant`() {
        val x1 = 0.2; val y1 = 0.3
        val x2 = -0.1; val y2 = 0.5
        val (mx, my) = HyperbolicUtils.hyperbolicMidpoint(x1, y1, x2, y2)
        val d1 = HyperbolicUtils.hyperbolicDistance(x1, y1, mx, my)
        val d2 = HyperbolicUtils.hyperbolicDistance(x2, y2, mx, my)
        assertEquals(d1, d2, 1e-6)
    }

    @Test
    fun `hyperbolicMidpoint at origin`() {
        val (mx, my) = HyperbolicUtils.hyperbolicMidpoint(0.5, 0.0, -0.5, 0.0)
        assertEquals(0.0, mx, EPS)
        assertEquals(0.0, my, EPS)
    }

    @Test
    fun `hyperbolicMidpoint halves hyperbolic distance`() {
        val x1 = 0.3; val y1 = 0.1
        val x2 = -0.2; val y2 = 0.4
        val (mx, my) = HyperbolicUtils.hyperbolicMidpoint(x1, y1, x2, y2)
        val totalDist = HyperbolicUtils.hyperbolicDistance(x1, y1, x2, y2)
        val halfDist = HyperbolicUtils.hyperbolicDistance(x1, y1, mx, my)
        assertEquals(totalDist / 2.0, halfDist, 1e-6)
    }

    // --- Clamp to disk tests ---

    @Test
    fun `clampToDisk inside point unchanged`() {
        val (cx, cy) = HyperbolicUtils.clampToDisk(0.3, 0.4)
        assertEquals(0.3, cx, EPS)
        assertEquals(0.4, cy, EPS)
    }

    @Test
    fun `clampToDisk outside point clamped`() {
        val (cx, cy) = HyperbolicUtils.clampToDisk(2.0, 0.0)
        assertTrue(cx * cx + cy * cy <= 1.0)
    }

    // --- Tangent tests ---

    @Test
    fun `tangentAtPoint for diameter is direction vector`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.0, -0.2, 0.0)
        val (tx, ty) = HyperbolicUtils.tangentAtPoint(0.1, 0.0, 0.3, 0.0, -0.2, 0.0, arc)
        // Should point from p1 toward p2 (negative x direction)
        assertTrue(tx < 0)
        assertEquals(0.0, ty, EPS)
    }

    // --- Arc interpolation tests ---

    @Test
    fun `interpolateOnArc t=0 gives start`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.2, -0.1, 0.4)
        val (px, py) = HyperbolicUtils.interpolateOnArc(0.0, 0.3, 0.2, -0.1, 0.4, arc)
        assertEquals(0.3, px, 1e-6)
        assertEquals(0.2, py, 1e-6)
    }

    @Test
    fun `interpolateOnArc t=1 gives end`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.2, -0.1, 0.4)
        val (px, py) = HyperbolicUtils.interpolateOnArc(1.0, 0.3, 0.2, -0.1, 0.4, arc)
        assertEquals(-0.1, px, 1e-6)
        assertEquals(0.4, py, 1e-6)
    }

    @Test
    fun `parameterOnArc round-trips with interpolateOnArc`() {
        val arc = HyperbolicUtils.geodesicArc(0.3, 0.2, -0.1, 0.4)
        val t = 0.4
        val (px, py) = HyperbolicUtils.interpolateOnArc(t, 0.3, 0.2, -0.1, 0.4, arc)
        val t2 = HyperbolicUtils.parameterOnArc(px, py, 0.3, 0.2, -0.1, 0.4, arc)
        assertEquals(t, t2, 1e-5)
    }
}
