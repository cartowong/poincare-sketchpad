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

    @Test
    fun `style fields round-trip through serialization`() {
        val manager = GeometricObjectManager()
        val p = manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        val meta = manager.getMetadata(p.id)!!
        meta.color = "red"
        meta.size = "large"

        val line = manager.addObject { id -> Line(id, 0, 0) } // degenerate but fine for serialization
        val lineMeta = manager.getMetadata(line.id)!!
        lineMeta.color = "purple"
        lineMeta.strokeStyle = "dashed"
        lineMeta.strokeThickness = "thick"

        val json = DiagramSerializer.serialize(manager)
        val manager2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, manager2)

        val meta2 = manager2.getMetadata(p.id)!!
        assertEquals("red", meta2.color)
        assertEquals("large", meta2.size)

        val lineMeta2 = manager2.getMetadata(line.id)!!
        assertEquals("purple", lineMeta2.color)
        assertEquals("dashed", lineMeta2.strokeStyle)
        assertEquals("thick", lineMeta2.strokeThickness)
    }

    @Test
    fun `backward compat - old files without style fields get correct defaults`() {
        // Simulate an old file with no style fields
        val json = """{
            "appName": "PoincareSketchpad",
            "version": "1.0.0",
            "model": "poincare",
            "objects": [
                {"id": 0, "type": "FreePoint", "parentIds": [], "x": 0.1, "y": 0.2},
                {"id": 1, "type": "FreePoint", "parentIds": [], "x": 0.3, "y": 0.4},
                {"id": 2, "type": "Line", "parentIds": [0, 1]},
                {"id": 3, "type": "Angle", "parentIds": [0, 1, 0]},
                {"id": 4, "type": "Region", "pointIds": [0, 1, 0]}
            ],
            "metadata": {
                "0": {"visible": true},
                "1": {"visible": true},
                "2": {"visible": true},
                "3": {"visible": true},
                "4": {"visible": true}
            }
        }"""

        val manager = GeometricObjectManager()
        DiagramSerializer.deserialize(json, manager)

        // Point gets black
        assertEquals("black", manager.getMetadata(0)!!.color)
        // Line gets black
        assertEquals("black", manager.getMetadata(2)!!.color)
        // Angle gets green
        assertEquals("green", manager.getMetadata(3)!!.color)
        // Region gets yellow
        assertEquals("yellow", manager.getMetadata(4)!!.color)
        // All get default size/stroke
        assertEquals("medium", manager.getMetadata(0)!!.size)
        assertEquals("solid", manager.getMetadata(2)!!.strokeStyle)
        assertEquals("normal", manager.getMetadata(2)!!.strokeThickness)
    }
}
