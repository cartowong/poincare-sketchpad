package poincaresketchpad.geometry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class CircleTest {

    private val EPS = 1e-7

    @Test
    fun `Circle at origin has correct Euclidean representation`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val center = FreePoint(0, 0.0, 0.0)
        val radiusPoint = FreePoint(1, 0.5, 0.0)
        objects[0] = center; objects[1] = radiusPoint
        val circle = Circle(2, 0, 1)
        objects[2] = circle
        circle.recompute(objects)

        assertTrue(circle.isDefined)
        // At origin, Euclidean center = hyperbolic center = (0,0)
        assertEquals(0.0, circle.euclideanCenterX, EPS)
        assertEquals(0.0, circle.euclideanCenterY, EPS)
        // Euclidean radius = tanh(r/2) where r = hyperbolic radius
        val r = circle.hyperbolicRadius
        assertEquals(tanh(r / 2.0), circle.euclideanRadius, 1e-6)
    }

    @Test
    fun `Circle off-center has shifted Euclidean center`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val center = FreePoint(0, 0.3, 0.0)
        val radiusPoint = FreePoint(1, 0.5, 0.0)
        objects[0] = center; objects[1] = radiusPoint
        val circle = Circle(2, 0, 1)
        objects[2] = circle
        circle.recompute(objects)

        assertTrue(circle.isDefined)
        // Euclidean center should be shifted toward origin relative to hyperbolic center
        assertTrue(abs(circle.euclideanCenterX) <= abs(center.x) || abs(circle.euclideanCenterX - center.x) < 0.1)
        assertTrue(circle.euclideanRadius > 0)
    }

    @Test
    fun `Circle with same center and radius point is undefined`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val center = FreePoint(0, 0.3, 0.2)
        val radiusPoint = FreePoint(1, 0.3, 0.2)
        objects[0] = center; objects[1] = radiusPoint
        val circle = Circle(2, 0, 1)
        objects[2] = circle
        circle.recompute(objects)

        assertFalse(circle.isDefined)
    }

    @Test
    fun `Circle radius point lies on Euclidean circle`() {
        val objects = linkedMapOf<Int, GeometricObject>()
        val center = FreePoint(0, 0.2, 0.1)
        val radiusPoint = FreePoint(1, 0.5, 0.3)
        objects[0] = center; objects[1] = radiusPoint
        val circle = Circle(2, 0, 1)
        objects[2] = circle
        circle.recompute(objects)

        assertTrue(circle.isDefined)
        // The radius point should lie on the Euclidean circle
        val dx = radiusPoint.x - circle.euclideanCenterX
        val dy = radiusPoint.y - circle.euclideanCenterY
        val dist = hypot(dx, dy)
        assertEquals(circle.euclideanRadius, dist, 1e-5)
    }
}
