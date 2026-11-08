package poincaresketchpad.model

import poincaresketchpad.geometry.*

class ConstructionRecorder(private val manager: GeometricObjectManager) {

    var isRecording: Boolean = false; private set

    private var baselineNextId: Int = 0

    fun startRecording() {
        isRecording = true
        baselineNextId = manager.getNextId()

        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            val isParentCapable = obj is PointLike || obj is GeodesicLike || obj is Circle
            if (isParentCapable && meta.visible) {
                meta.isInput = true
            } else {
                meta.isLocked = true
            }
        }
    }

    fun cancelRecording() {
        if (!isRecording) return
        val toRemove = manager.allObjects()
            .filter { it.id >= baselineNextId }
            .map { it.id }
            .sortedDescending()
        for (id in toRemove) {
            manager.removeWithDescendants(id)
        }
        clearFlags()
        isRecording = false
    }

    fun endRecording() {
        if (!isRecording) return
        clearFlags()
        isRecording = false
    }

    private fun clearFlags() {
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            meta.isInput = false
            meta.isLocked = false
        }
    }
}
