package poincaresketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class GeodesicTest {

    private val EPS = 1e-7

    private fun makeManager(): Map<Int, GeometricObject> {
        val objects = linkedMapOf<Int, GeometricObject>()
        return objects
    }

    @Test
    fun `Line through two points is defined`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.3, 0.2)
        val p2 = FreePoint(1, -0.1, 0.4)
        objects[0] = p1; objects[1] = p2
        val line = Line(2, 0, 1)
        objects[2] = line
        line.recompute(objects)
        assertTrue(line.isDefined)
        assertEquals(0.3, line.x1, EPS)
        assertEquals(0.2, line.y1, EPS)
    }

    @Test
    fun `Line through same point is undefined`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.3, 0.2)
        val p2 = FreePoint(1, 0.3, 0.2)
        objects[0] = p1; objects[1] = p2
        val line = Line(2, 0, 1)
        objects[2] = line
        line.recompute(objects)
        assertFalse(line.isDefined)
    }

    @Test
    fun `Line through origin is diameter`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.3, 0.0)
        val p2 = FreePoint(1, -0.5, 0.0)
        objects[0] = p1; objects[1] = p2
        val line = Line(2, 0, 1)
        objects[2] = line
        line.recompute(objects)
        assertTrue(line.isDefined)
        assertTrue(line.isDiameter)
    }

    @Test
    fun `Line arc is orthogonal to unit circle`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.3, 0.2)
        val p2 = FreePoint(1, -0.1, 0.4)
        objects[0] = p1; objects[1] = p2
        val line = Line(2, 0, 1)
        objects[2] = line
        line.recompute(objects)
        if (!line.isDiameter) {
            val centerNormSq = line.arcCenterX * line.arcCenterX + line.arcCenterY * line.arcCenterY
            assertEquals(line.arcRadius * line.arcRadius + 1.0, centerNormSq, 1e-6)
        }
    }

    @Test
    fun `Segment between two points is defined`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.2, 0.3)
        val p2 = FreePoint(1, -0.4, 0.1)
        objects[0] = p1; objects[1] = p2
        val seg = Segment(2, 0, 1)
        objects[2] = seg
        seg.recompute(objects)
        assertTrue(seg.isDefined)
        assertEquals(GeodesicType.SEGMENT, seg.geodesicType)
    }

    @Test
    fun `Ray from endpoint through point is defined`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.1, 0.2)
        val p2 = FreePoint(1, 0.5, 0.3)
        objects[0] = p1; objects[1] = p2
        val ray = Ray(2, 0, 1)
        objects[2] = ray
        ray.recompute(objects)
        assertTrue(ray.isDefined)
        assertEquals(GeodesicType.RAY, ray.geodesicType)
    }

    @Test
    fun `PerpendicularLine is defined`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.3, 0.0)
        val p2 = FreePoint(1, -0.3, 0.0)
        objects[0] = p1; objects[1] = p2
        val line = Line(2, 0, 1)
        objects[2] = line
        line.recompute(objects)

        val p3 = FreePoint(3, 0.0, 0.4)
        objects[3] = p3
        val perp = PerpendicularLine(4, 3, 2)
        objects[4] = perp
        perp.recompute(objects)
        assertTrue(perp.isDefined)
    }
}
