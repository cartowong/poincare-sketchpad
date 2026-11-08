package poincaresketchpad.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager

class DiagramSerializerTest {

    @Test
    fun `serialize and deserialize round-trips`() {
        val manager = GeometricObjectManager()
        manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        manager.addObject { id -> Line(id, 0, 1) }

        val json = DiagramSerializer.serialize(manager)

        val manager2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, manager2)

        assertEquals(3, manager2.objectCount())
        val p1 = manager2.getObject(0) as FreePoint
        assertEquals(0.1, p1.x, 1e-10)
        assertEquals(0.2, p1.y, 1e-10)
    }

    @Test
    fun `serialized JSON includes model field`() {
        val manager = GeometricObjectManager()
        manager.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val json = DiagramSerializer.serialize(manager)
        assertTrue(json.contains("\"model\""))
        assertTrue(json.contains("\"poincare\""))
    }

    @Test
    fun `deserialize rejects wrong app name`() {
        val json = """{"appName":"WrongApp","version":"1.0.0","model":"poincare","objects":[],"metadata":{}}"""
        val manager = GeometricObjectManager()
        assertThrows(IncompatibleAppNameException::class.java) {
            DiagramSerializer.deserialize(json, manager)
        }
    }

    @Test
    fun `deserialize rejects wrong major version`() {
        val json = """{"appName":"PoincareSketchpad","version":"2.0.0","model":"poincare","objects":[],"metadata":{}}"""
        val manager = GeometricObjectManager()
        assertThrows(IncompatibleVersionException::class.java) {
            DiagramSerializer.deserialize(json, manager)
        }
    }

    @Test
    fun `deserialize rejects wrong model`() {
        val json = """{"appName":"PoincareSketchpad","version":"1.0.0","model":"euclidean","objects":[],"metadata":{}}"""
        val manager = GeometricObjectManager()
        assertThrows(IncompatibleModelException::class.java) {
            DiagramSerializer.deserialize(json, manager)
        }
    }

    @Test
    fun `metadata is preserved through serialization`() {
        val manager = GeometricObjectManager()
        val p = manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        val meta = manager.getMetadata(p.id)!!
        meta.label = "A"
        meta.labelVisible = true
        meta.visible = true
        meta.inMeasurementTable = true

        val json = DiagramSerializer.serialize(manager)
        val manager2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, manager2)

        val meta2 = manager2.getMetadata(0)!!
        assertEquals("A", meta2.label)
        assertTrue(meta2.labelVisible)
        assertTrue(meta2.inMeasurementTable)
    }
}
