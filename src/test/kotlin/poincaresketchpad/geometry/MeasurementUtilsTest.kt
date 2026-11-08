package poincaresketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class MeasurementUtilsTest {

    private val EPS = 1e-6

    @Test
    fun `segmentLength computes hyperbolic distance`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.0, 0.0); objects[0] = p1
        val p2 = FreePoint(1, 0.5, 0.0); objects[1] = p2
        val seg = Segment(2, 0, 1); objects[2] = seg; seg.recompute(objects)

        val len = MeasurementUtils.segmentLength(seg)
        val expected = HyperbolicUtils.hyperbolicDistance(0.0, 0.0, 0.5, 0.0)
        assertEquals(expected, len, EPS)
    }

    @Test
    fun `circleCircumference is 2 pi sinh r`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val center = FreePoint(0, 0.0, 0.0); objects[0] = center
        val rp = FreePoint(1, 0.5, 0.0); objects[1] = rp
        val circle = Circle(2, 0, 1); objects[2] = circle; circle.recompute(objects)

        val circ = MeasurementUtils.circleCircumference(circle)
        val expected = 2.0 * PI * sinh(circle.hyperbolicRadius)
        assertEquals(expected, circ, EPS)
    }

    @Test
    fun `angleDegrees for right angle`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val pA = FreePoint(0, 0.5, 0.0); objects[0] = pA
        val pV = FreePoint(1, 0.0, 0.0); objects[1] = pV
        val pC = FreePoint(2, 0.0, 0.5); objects[2] = pC
        val angle = Angle(3, 0, 1, 2); objects[3] = angle; angle.recompute(objects)

        assertTrue(angle.isDefined)
        val degrees = MeasurementUtils.angleDegrees(angle)
        // At origin, geodesics are straight lines. CW sweep from A(right) to C(up) = 270 degrees.
        assertEquals(270.0, degrees, 1.0) // Allow some tolerance
    }

    @Test
    fun `measureLabel returns formatted string`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.0, 0.0); objects[0] = p1
        val p2 = FreePoint(1, 0.5, 0.0); objects[1] = p2
        val seg = Segment(2, 0, 1); objects[2] = seg; seg.recompute(objects)

        val label = MeasurementUtils.measureLabel(seg)
        assertNotNull(label)
        assertTrue(label!!.contains("."))
    }

    @Test
    fun `measure returns null for non-measurable types`() {
        val p = FreePoint(0, 0.3, 0.2)
        assertNull(MeasurementUtils.measure(p))

        val objects = linkedMapOf<Int, GeometricObject>()
        val p1 = FreePoint(0, 0.3, 0.0); objects[0] = p1
        val p2 = FreePoint(1, -0.3, 0.0); objects[1] = p2
        val line = Line(2, 0, 1); objects[2] = line; line.recompute(objects)
        assertNull(MeasurementUtils.measure(line))
    }
}
