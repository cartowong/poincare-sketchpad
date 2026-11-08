package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.ManagerState
import poincaresketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.ArcType
import javafx.scene.shape.Circle as FxCircle
import kotlin.math.PI
import kotlin.math.atan2

class AngleTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val previewGroup: Group,
    private val canvasPane: DiskCanvasPane,
    private val requestRender: () -> Unit
) : Tool {

    private companion object {
        const val POINT_RADIUS = 4.0
        const val ANGLE_ARC_RADIUS = GeometryUtils.ANGLE_ARC_RADIUS
    }

    private var pointAId: Int? = null
    private var pointAX: Double = 0.0
    private var pointAY: Double = 0.0

    private var vertexId: Int? = null
    private var vertexX: Double = 0.0
    private var vertexY: Double = 0.0

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

    override fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {
        previewGroup.children.clear()

        val hit = hitTester.hitTest(e.x, e.y)
        if (hit is HitResult.IntersectionPreview) {
            val screenX = canvasPane.modelToScreenX(hit.candidate.x)
            val screenY = canvasPane.modelToScreenY(hit.candidate.y)
            val ghost = FxCircle(screenX, screenY, POINT_RADIUS)
            ghost.fill = Color.BLACK; ghost.opacity = 0.4; ghost.isMouseTransparent = true
            previewGroup.children.add(ghost)
        }

        if (vertexId != null) {
            // Show ghost arc from vertex
            val targetX = resolveTargetX(hit, worldX)
            val targetY = resolveTargetY(hit, worldY)

            // Compute tangent directions at vertex in model space
            val arcVA = HyperbolicUtils.geodesicArc(vertexX, vertexY, pointAX, pointAY)
            val (taX, taY) = HyperbolicUtils.tangentAtPoint(vertexX, vertexY, vertexX, vertexY, pointAX, pointAY, arcVA)

            val arcVC = HyperbolicUtils.geodesicArc(vertexX, vertexY, targetX, targetY)
            val (tcX, tcY) = HyperbolicUtils.tangentAtPoint(vertexX, vertexY, vertexX, vertexY, targetX, targetY, arcVC)

            // Model-space tangent angle = JavaFX angle (y-flips cancel)
            val startDeg = Math.toDegrees(atan2(taY, taX))
            var sweep = atan2(tcY, tcX) - atan2(taY, taX)
            while (sweep >= 0) sweep -= 2 * PI
            while (sweep < -2 * PI) sweep += 2 * PI
            val sweepDeg = Math.toDegrees(sweep)

            val vertexScreenX = canvasPane.modelToScreenX(vertexX)
            val vertexScreenY = canvasPane.modelToScreenY(vertexY)

            val arc = Arc(vertexScreenX, vertexScreenY, ANGLE_ARC_RADIUS, ANGLE_ARC_RADIUS, startDeg, sweepDeg)
            arc.type = ArcType.ROUND
            arc.fill = Color.web("#4CAF50", 0.2)
            arc.stroke = Color.web("#4CAF50", 0.5)
            arc.strokeWidth = 1.0
            arc.isMouseTransparent = true
            previewGroup.children.add(arc)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        // Ignore clicks outside the disk
        if (worldX * worldX + worldY * worldY >= 1.0) return

        val hit = hitTester.hitTest(e.x, e.y)

        if (pointAId == null) {
            preConstructionSnapshot = manager.snapshot()
            val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender)
            val pt = manager.getObject(id) as? PointLike ?: return
            pointAId = id
            pointAX = pt.x
            pointAY = pt.y
        } else if (vertexId == null) {
            val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender)
            if (id == pointAId) return
            val pt = manager.getObject(id) as? PointLike ?: return
            vertexId = id
            vertexX = pt.x
            vertexY = pt.y
        } else {
            val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender)
            if (id == vertexId || id == pointAId) return
            manager.addObject { oid -> Angle(oid, pointAId!!, vertexId!!, id) }
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

    private fun resolveTargetX(hit: HitResult?, worldX: Double): Double = when (hit) {
        is HitResult.ConstructedPoint -> (manager.getObject(hit.id) as? PointLike)?.x ?: worldX
        is HitResult.IntersectionPreview -> hit.candidate.x
        else -> worldX
    }

    private fun resolveTargetY(hit: HitResult?, worldY: Double): Double = when (hit) {
        is HitResult.ConstructedPoint -> (manager.getObject(hit.id) as? PointLike)?.y ?: worldY
        is HitResult.IntersectionPreview -> hit.candidate.y
        else -> worldY
    }

    private fun reset() {
        pointAId = null
        vertexId = null
    }
}
