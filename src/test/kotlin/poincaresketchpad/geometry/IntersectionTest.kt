package poincaresketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class IntersectionTest {

    private val EPS = 1e-6

    @Test
    fun `two crossing lines intersect inside disk`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        // Horizontal line through origin
        val p1 = FreePoint(0, 0.5, 0.0); objects[0] = p1
        val p2 = FreePoint(1, -0.5, 0.0); objects[1] = p2
        val line1 = Line(2, 0, 1); objects[2] = line1; line1.recompute(objects)

        // Vertical line through origin
        val p3 = FreePoint(3, 0.0, 0.5); objects[3] = p3
        val p4 = FreePoint(4, 0.0, -0.5); objects[4] = p4
        val line2 = Line(5, 3, 4); objects[5] = line2; line2.recompute(objects)

        val isect = GeodesicGeodesicIntersection(6, 2, 5)
        objects[6] = isect
        isect.recompute(objects)

        assertTrue(isect.isDefined)
        assertEquals(0.0, isect.x, EPS)
        assertEquals(0.0, isect.y, EPS)
    }

    @Test
    fun `two non-crossing geodesics have no intersection`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        // Two arcs that don't cross
        val p1 = FreePoint(0, 0.5, 0.3); objects[0] = p1
        val p2 = FreePoint(1, 0.5, -0.3); objects[1] = p2
        val line1 = Segment(2, 0, 1); objects[2] = line1; line1.recompute(objects)

        val p3 = FreePoint(3, -0.5, 0.3); objects[3] = p3
        val p4 = FreePoint(4, -0.5, -0.3); objects[4] = p4
        val line2 = Segment(5, 3, 4); objects[5] = line2; line2.recompute(objects)

        val isect = GeodesicGeodesicIntersection(6, 2, 5)
        objects[6] = isect
        isect.recompute(objects)

        assertFalse(isect.isDefined)
    }

    @Test
    fun `geodesic circle intersection`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, -0.5, 0.0); objects[0] = p1
        val p2 = FreePoint(1, 0.5, 0.0); objects[1] = p2
        val line = Line(2, 0, 1); objects[2] = line; line.recompute(objects)

        // Circle center off the line so sign disambiguation works (cross product non-zero)
        val center = FreePoint(3, 0.0, 0.2); objects[3] = center
        val rp = FreePoint(4, 0.3, 0.2); objects[4] = rp
        val circle = Circle(5, 3, 4); objects[5] = circle; circle.recompute(objects)

        val isect1 = GeodesicCircleIntersection(6, 2, 5, 1)
        objects[6] = isect1
        isect1.recompute(objects)
        assertTrue(isect1.isDefined)

        val isect2 = GeodesicCircleIntersection(7, 2, 5, -1)
        objects[7] = isect2
        isect2.recompute(objects)
        assertTrue(isect2.isDefined)

        // The two intersections should be different
        val dist = hypot(isect1.x - isect2.x, isect1.y - isect2.y)
        assertTrue(dist > EPS)
    }

    @Test
    fun `circle circle intersection`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val c1 = FreePoint(0, -0.2, 0.0); objects[0] = c1
        val r1 = FreePoint(1, 0.1, 0.0); objects[1] = r1
        val circle1 = Circle(2, 0, 1); objects[2] = circle1; circle1.recompute(objects)

        val c2 = FreePoint(3, 0.2, 0.0); objects[3] = c2
        val r2 = FreePoint(4, -0.1, 0.0); objects[4] = r2
        val circle2 = Circle(5, 3, 4); objects[5] = circle2; circle2.recompute(objects)

        val isect1 = CircleCircleIntersection(6, 2, 5, 1)
        objects[6] = isect1
        isect1.recompute(objects)

        val isect2 = CircleCircleIntersection(7, 2, 5, -1)
        objects[7] = isect2
        isect2.recompute(objects)

        // At least one should be defined (circles overlap near origin)
        assertTrue(isect1.isDefined || isect2.isDefined)
    }
}
