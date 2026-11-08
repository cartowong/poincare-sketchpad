package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Circle as FxCircle
import kotlin.math.atan2

class PointTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val previewGroup: Group,
    private val canvasPane: DiskCanvasPane,
    private val requestRender: () -> Unit
) : Tool {

    private companion object {
        const val POINT_RADIUS = 4.0
    }

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
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        previewGroup.children.clear()

        // Ignore clicks outside the disk
        if (worldX * worldX + worldY * worldY >= 1.0) return

        val hit = hitTester.hitTest(e.x, e.y)
        when (hit) {
            is HitResult.ConstructedPoint -> {
                // Already a point here, no-op
            }
            is HitResult.IntersectionPreview -> {
                ToolUtils.constructIntersection(hit.candidate, manager, hitTester, requestRender)
            }
            is HitResult.OnGeodesic -> {
                createSemiFreePointOnGeodesic(hit.id, worldX, worldY)
            }
            is HitResult.OnCircle -> {
                createSemiFreePointOnCircle(hit.id, worldX, worldY)
            }
            else -> {
                val (px, py) = HyperbolicUtils.clampToDisk(worldX, worldY)
                manager.addObject { id -> FreePoint(id, px, py) }
            }
        }
        hitTester.rebuildIntersectionCache()
        undoManager.checkpoint()
        requestRender()
    }

    private fun createSemiFreePointOnGeodesic(geodesicId: Int, worldX: Double, worldY: Double) {
        val obj = manager.getObject(geodesicId) ?: return
        val geo = obj as? GeodesicLike ?: return
        val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)

        when (geo.geodesicType) {
            GeodesicType.LINE -> {
                val t = HyperbolicUtils.parameterOnArc(
                    worldX, worldY,
                    geo.idealX1, geo.idealY1, geo.idealX2, geo.idealY2, arc
                ).coerceIn(0.0, 1.0)
                manager.addObject { id -> FreePointOnLine(id, geodesicId, t) }
            }
            GeodesicType.RAY -> {
                val t = HyperbolicUtils.parameterOnArc(
                    worldX, worldY,
                    geo.x1, geo.y1, geo.idealX2, geo.idealY2, arc
                ).coerceAtLeast(0.0)
                manager.addObject { id -> FreePointOnRay(id, geodesicId, t) }
            }
            GeodesicType.SEGMENT -> {
                val t = HyperbolicUtils.parameterOnArc(
                    worldX, worldY,
                    geo.x1, geo.y1, geo.x2, geo.y2, arc
                ).coerceIn(0.0, 1.0)
                manager.addObject { id -> FreePointOnSegment(id, geodesicId, t) }
            }
        }
    }

    private fun createSemiFreePointOnCircle(circleId: Int, worldX: Double, worldY: Double) {
        val cir = manager.getObject(circleId) as? Circle ?: return
        val theta = atan2(worldY - cir.euclideanCenterY, worldX - cir.euclideanCenterX)
        manager.addObject { id -> FreePointOnCircle(id, circleId, theta) }
    }
}
