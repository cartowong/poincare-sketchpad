package poincaresketchpad.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import poincaresketchpad.geometry.*

class UndoManagerTest {

    @Test
    fun `undo restores previous state`() {
        val manager = GeometricObjectManager()
        val undo = UndoManager(manager)

        manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        undo.checkpoint()

        manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        undo.checkpoint()

        assertEquals(2, manager.objectCount())
        undo.undo()
        assertEquals(1, manager.objectCount())
    }

    @Test
    fun `redo restores undone state`() {
        val manager = GeometricObjectManager()
        val undo = UndoManager(manager)

        manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        undo.checkpoint()

        manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        undo.checkpoint()

        undo.undo()
        assertEquals(1, manager.objectCount())
        undo.redo()
        assertEquals(2, manager.objectCount())
    }

    @Test
    fun `new checkpoint truncates redo stack`() {
        val manager = GeometricObjectManager()
        val undo = UndoManager(manager)

        manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        undo.checkpoint()

        manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        undo.checkpoint()

        undo.undo()
        manager.addObject { id -> FreePoint(id, 0.5, 0.6) }
        undo.checkpoint()

        assertFalse(undo.canRedo())
    }

    @Test
    fun `hasUnsavedChanges detects changes`() {
        val manager = GeometricObjectManager()
        val undo = UndoManager(manager)

        manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        undo.checkpoint()
        undo.markSaved()
        assertFalse(undo.hasUnsavedChanges())

        manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        undo.checkpoint()
        assertTrue(undo.hasUnsavedChanges())
    }

    @Test
    fun `minIndex prevents undoing past recording start`() {
        val manager = GeometricObjectManager()
        val undo = UndoManager(manager)

        manager.addObject { id -> FreePoint(id, 0.1, 0.2) }
        undo.checkpoint()

        undo.setMinIndex(undo.currentIndex())

        manager.addObject { id -> FreePoint(id, 0.3, 0.4) }
        undo.checkpoint()

        assertTrue(undo.undo()) // Can undo the second add
        assertFalse(undo.undo()) // Can't undo past minIndex
    }
}
