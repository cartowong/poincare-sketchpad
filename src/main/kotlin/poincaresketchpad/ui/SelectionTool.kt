package poincaresketchpad.ui

import poincaresketchpad.geometry.FreePoint
import poincaresketchpad.geometry.HyperbolicUtils
import poincaresketchpad.geometry.PointLike
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent

class SelectionTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val requestRender: () -> Unit
) : Tool {

    val selectedIds = mutableSetOf<Int>()

    private var isDragging = false
    private var lastDragWorldX = 0.0
    private var lastDragWorldY = 0.0

    private var pressHitId: Int? = null
    private var wasAlreadySelected = false

    override fun activate() {
        selectedIds.clear()
    }

    override fun deactivate(previewGroup: Group) {
        selectedIds.clear()
        previewGroup.children.clear()
        requestRender()
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        isDragging = false
        lastDragWorldX = worldX
        lastDragWorldY = worldY

        val hit = hitTester.hitTest(e.x, e.y)
        if (hit != null) {
            val hitId = when (hit) {
                is HitResult.ConstructedPoint -> hit.id
                is HitResult.OnAngle -> hit.id
                is HitResult.OnGeodesic -> hit.id
                is HitResult.OnCircle -> hit.id
                is HitResult.OnRegion -> hit.id
                is HitResult.IntersectionPreview -> null
            }
            pressHitId = hitId
            if (hitId != null) {
                wasAlreadySelected = selectedIds.contains(hitId)
                if (e.isShortcutDown) {
                    // Modifier+click: toggle without affecting others
                    if (wasAlreadySelected) {
                        selectedIds.remove(hitId)
                    } else {
                        selectedIds.add(hitId)
                    }
                } else {
                    // Plain click: deselect all others, select this one
                    // If sole selection, defer deselect to mouseReleased (to allow drag)
                    if (!(wasAlreadySelected && selectedIds.size == 1)) {
                        selectedIds.clear()
                        selectedIds.add(hitId)
                    }
                }
                requestRender()
            }
        } else {
            pressHitId = null
            wasAlreadySelected = false
            if (selectedIds.isNotEmpty()) {
                selectedIds.clear()
                requestRender()
            }
        }
    }

    override fun onMouseDragged(e: MouseEvent, worldX: Double, worldY: Double) {
        if (selectedIds.isEmpty()) return

        // Only drag if all selected objects are movable
        if (!selectedIds.all { manager.isMovable(it) }) return

        val dx = worldX - lastDragWorldX
        val dy = worldY - lastDragWorldY
        isDragging = true

        for (id in selectedIds) {
            val obj = manager.getObject(id) ?: continue
            if (obj is PointLike) {
                var newX = obj.x + dx
                var newY = obj.y + dy
                // Clamp free points to the disk
                if (obj is FreePoint) {
                    val (cx, cy) = HyperbolicUtils.clampToDisk(newX, newY)
                    newX = cx; newY = cy
                }
                manager.movePoint(id, newX, newY)
            }
        }
        lastDragWorldX = worldX
        lastDragWorldY = worldY
        hitTester.rebuildIntersectionCache()
        requestRender()
    }

    override fun onMouseReleased(e: MouseEvent, worldX: Double, worldY: Double) {
        // Plain click on sole selected object: deselect on release (not during drag)
        if (!isDragging && !e.isShortcutDown && wasAlreadySelected && pressHitId != null
            && selectedIds.size == 1 && selectedIds.contains(pressHitId)) {
            selectedIds.remove(pressHitId)
            requestRender()
        }
        if (isDragging) {
            undoManager.checkpoint()
        }
        isDragging = false
        pressHitId = null
        wasAlreadySelected = false
    }
}
