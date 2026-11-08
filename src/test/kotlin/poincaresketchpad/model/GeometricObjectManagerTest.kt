package poincaresketchpad.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import poincaresketchpad.geometry.*

class GeometricObjectManagerTest {

    @Test
    fun `addObject assigns incrementing IDs`() {
        val manager = GeometricObjectManager()
        val p1 = manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        val p2 = manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        assertEquals(0, p1.id)
        assertEquals(1, p2.id)
    }

    @Test
    fun `removeWithDescendants removes object and children`() {
        val manager = GeometricObjectManager()
        val p1 = manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        val p2 = manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        manager.addObject { id -> Line(id, p1.id, p2.id) }

        manager.removeWithDescendants(p1.id)
        assertEquals(1, manager.objectCount()) // Only p2 remains
        assertNull(manager.getObject(p1.id))
        assertNull(manager.getObject(2)) // Line is also removed
        assertNotNull(manager.getObject(p2.id))
    }

    @Test
    fun `movePoint clamps to disk`() {
        val manager = GeometricObjectManager()
        val p = manager.addObject { id -> FreePoint(id, 0.0, 0.0) }
        manager.movePoint(p.id, 2.0, 0.0)
        val moved = manager.getObject(p.id) as FreePoint
        assertTrue(moved.x * moved.x + moved.y * moved.y < 1.0)
    }

    @Test
    fun `snapshot and restore`() {
        val manager = GeometricObjectManager()
        manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        val state = manager.snapshot()

        manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        assertEquals(2, manager.objectCount())

        manager.restore(state)
        assertEquals(1, manager.objectCount())
    }

    @Test
    fun `nextDefaultLabel generates sequential labels`() {
        val manager = GeometricObjectManager()
        assertEquals("A", manager.nextDefaultLabel())

        val p = manager.addObject { id -> FreePoint(id, 0.0, 0.0) }
        manager.getMetadata(p.id)!!.label = "A"
        assertEquals("B", manager.nextDefaultLabel())
    }

    @Test
    fun `isMovable returns true for free and semi-free points`() {
        val manager = GeometricObjectManager()
        val fp = manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        assertTrue(manager.isMovable(fp.id))

        val p2 = manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        val line = manager.addObject { id -> Line(id, fp.id, p2.id) }
        assertFalse(manager.isMovable(line.id))
    }
}
