package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.ManagerState
import poincaresketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Circle as FxCircle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.tanh

class CircleTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val previewGroup: Group,
    private val canvasPane: DiskCanvasPane,
    private val requestRender: () -> Unit
) : Tool {

    private companion object {
        const val POINT_RADIUS = 4.0
        const val STROKE_WIDTH = 2.0
    }

    private var centerPointId: Int? = null
    private var centerX: Double = 0.0
    private var centerY: Double = 0.0
    private var preConstructionSnapshot: ManagerState? = null

    override fun activate() {
        reset()
    }

    override fun deactivate(previewGroup: Group) {
        if (preConstructionSnapshot != null) {
            manager.restore(preConstructionSnapshot!!)
            preConstructionSnapshot = null
            hitTester.rebuildIntersectionCache()
        }
        reset()
        previewGroup.children.clear()
    }

    override fun onMouseDragged(e: MouseEvent, worldX: Double, worldY: Double) = onMouseMoved(e, worldX, worldY)

    override fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {
        previewGroup.children.clear()

        val hit = hitTester.hitTest(e.x, e.y)
        if (hit is HitResult.IntersectionPreview) {
            val screenX = canvasPane.modelToScreenX(hit.candidate.x)
            val screenY = canvasPane.modelToScreenY(hit.candidate.y)
            val ghost = FxCircle(screenX, screenY, POINT_RADIUS)
            ghost.fill = Color.BLACK
            ghost.opacity = 0.4
            ghost.isMouseTransparent = true
            previewGroup.children.add(ghost)
        }

        if (centerPointId != null) {
            val targetX = when (hit) {
                is HitResult.ConstructedPoint -> (manager.getObject(hit.id) as? PointLike)?.x ?: worldX
                is HitResult.IntersectionPreview -> hit.candidate.x
                else -> worldX
            }
            val targetY = when (hit) {
                is HitResult.ConstructedPoint -> (manager.getObject(hit.id) as? PointLike)?.y ?: worldY
                is HitResult.IntersectionPreview -> hit.candidate.y
                else -> worldY
            }

            // Compute the Euclidean center and radius of the hyperbolic circle
            val hypDist = HyperbolicUtils.hyperbolicDistance(centerX, centerY, targetX, targetY)
            if (hypDist > HyperbolicUtils.EPSILON) {
                val cNormSq = centerX * centerX + centerY * centerY
                val t = tanh(hypDist / 2.0)
                val denom = 1.0 - cNormSq * t * t
                if (abs(denom) > HyperbolicUtils.EPSILON) {
                    val scale = (1.0 - t * t) / denom
                    val eucCX = centerX * scale
                    val eucCY = centerY * scale
                    val eucR = t * (1.0 - cNormSq) / denom

                    if (eucR > 0) {
                        val displayScale = canvasPane.displayScale
                        val diskCenterX = canvasPane.diskCenterX
                        val diskCenterY = canvasPane.diskCenterY

                        val screenCX = diskCenterX + eucCX * displayScale
                        val screenCY = diskCenterY - eucCY * displayScale
                        val screenR = eucR * displayScale

                        val circle = FxCircle(screenCX, screenCY, screenR)
                        circle.fill = Color.TRANSPARENT
                        circle.strokeWidth = STROKE_WIDTH
                        circle.stroke = Color.BLACK
                        circle.opacity = 0.4
                        circle.isMouseTransparent = true
                        previewGroup.children.add(circle)
                    }
                }
            }

            // Ghost center and radius points
            val ghostCenter = FxCircle(canvasPane.modelToScreenX(centerX), canvasPane.modelToScreenY(centerY), POINT_RADIUS)
            ghostCenter.fill = Color.BLACK; ghostCenter.opacity = 0.4; ghostCenter.isMouseTransparent = true
            val ghostRadius = FxCircle(canvasPane.modelToScreenX(targetX), canvasPane.modelToScreenY(targetY), POINT_RADIUS)
            ghostRadius.fill = Color.BLACK; ghostRadius.opacity = 0.4; ghostRadius.isMouseTransparent = true
            previewGroup.children.addAll(ghostCenter, ghostRadius)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        // Ignore clicks outside the disk
        if (worldX * worldX + worldY * worldY >= 1.0) return

        val hit = hitTester.hitTest(e.x, e.y)

        if (centerPointId == null) {
            preConstructionSnapshot = manager.snapshot()
            val pointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender)
            val pt = manager.getObject(pointId) as? PointLike ?: return
            centerPointId = pointId
            centerX = pt.x
            centerY = pt.y
        } else {
            val radiusPointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender)
            if (radiusPointId == centerPointId) return
            manager.addObject { id -> Circle(id, centerPointId!!, radiusPointId) }
            preConstructionSnapshot = null
            reset()
            hitTester.rebuildIntersectionCache()
            undoManager.checkpoint()
            requestRender()
        }
        previewGroup.children.clear()
    }

    override fun onEscapePressed(previewGroup: Group): Boolean {
        val hadWork = preConstructionSnapshot != null
        if (hadWork) {
            manager.restore(preConstructionSnapshot!!)
            preConstructionSnapshot = null
            hitTester.rebuildIntersectionCache()
        }
        reset()
        previewGroup.children.clear()
        requestRender()
        return hadWork
    }

    private fun reset() {
        centerPointId = null
    }
}
