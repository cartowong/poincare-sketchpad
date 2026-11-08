package poincaresketchpad.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import java.io.File

class ConstructionSerializerTest {

    @Test
    fun `saveConstruction nulls labels`() {
        val manager = GeometricObjectManager()
        val p = manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        manager.getMetadata(p.id)!!.label = "A"
        manager.getMetadata(p.id)!!.labelVisible = true
        manager.getMetadata(p.id)!!.isInput = true

        val file = File.createTempFile("test_construction", ".json")
        file.deleteOnExit()

        ConstructionSerializer.saveConstruction(manager, file)

        val loaded = ConstructionSerializer.loadFromFile(file)
        val sMeta = loaded.diagram.metadata[0]
        assertNotNull(sMeta)
        assertNull(sMeta!!.label)
        assertFalse(sMeta.labelVisible)
        assertTrue(sMeta.isInput)
    }

    @Test
    fun `loadFromFile identifies inputs`() {
        val manager = GeometricObjectManager()
        val p1 = manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        val p2 = manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        manager.getMetadata(p1.id)!!.isInput = true
        manager.getMetadata(p2.id)!!.isInput = true
        manager.addObject { id -> Line(id, 0, 1) }

        val file = File.createTempFile("test_construction", ".json")
        file.deleteOnExit()

        ConstructionSerializer.saveConstruction(manager, file)
        val loaded = ConstructionSerializer.loadFromFile(file)

        assertEquals(2, loaded.inputIds.size)
        assertEquals(listOf("Point", "Point"), loaded.inputTypes)
    }

    @Test
    fun `loadFromFile maps geodesic input types`() {
        val manager = GeometricObjectManager()
        val p1 = manager.addObject { id -> FreePoint(id, 0.1, 0.0) }
        val p2 = manager.addObject { id -> FreePoint(id, -0.1, 0.0) }
        val line = manager.addObject { id -> Line(id, 0, 1) }
        manager.getMetadata(p1.id)!!.isInput = true
        manager.getMetadata(line.id)!!.isInput = true

        val file = File.createTempFile("test_construction", ".json")
        file.deleteOnExit()

        ConstructionSerializer.saveConstruction(manager, file)
        val loaded = ConstructionSerializer.loadFromFile(file)

        assertEquals(listOf("Point", "Line"), loaded.inputTypes)
    }
}
