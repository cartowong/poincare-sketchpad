package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.ManagerState
import poincaresketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.ArcTo
import javafx.scene.shape.LineTo
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import javafx.scene.shape.Shape
import javafx.scene.shape.Circle as FxCircle
import kotlin.math.*

class RegionTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val previewGroup: Group,
    private val canvasPane: DiskCanvasPane,
    private val requestRender: () -> Unit
) : Tool {

    private companion object {
        const val POINT_RADIUS = 4.0
        const val SNAP_RADIUS = 10.0 // screen pixels
    }

    private val vertexIds = mutableListOf<Int>()
    private val vertexPositions = mutableListOf<Pair<Double, Double>>()
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
        if (vertexIds.isEmpty()) return

        val hit = hitTester.hitTest(e.x, e.y)
        if (hit is HitResult.IntersectionPreview) {
            val screenX = canvasPane.modelToScreenX(hit.candidate.x)
            val screenY = canvasPane.modelToScreenY(hit.candidate.y)
            val ghost = FxCircle(screenX, screenY, POINT_RADIUS)
            ghost.fill = Color.BLACK; ghost.opacity = 0.4; ghost.isMouseTransparent = true
            previewGroup.children.add(ghost)
        }

        val targetX = resolveTargetX(hit, worldX)
        val targetY = resolveTargetY(hit, worldY)

        // Check if near first point (closing) — in screen pixels
        val firstPos = vertexPositions[0]
        val firstScreenX = canvasPane.modelToScreenX(firstPos.first)
        val firstScreenY = canvasPane.modelToScreenY(firstPos.second)
        val targetScreenX = canvasPane.modelToScreenX(targetX)
        val targetScreenY = canvasPane.modelToScreenY(targetY)
        val distToFirst = hypot(targetScreenX - firstScreenX, targetScreenY - firstScreenY)
        val isClosing = vertexIds.size >= 3 && distToFirst < SNAP_RADIUS

        val vp = canvasPane.viewportParams()
        val previewColor = Color.web("#2196F3")

        // Draw edges as geodesic arcs
        for (i in 0 until vertexPositions.size - 1) {
            val (mx1, my1) = vertexPositions[i]
            val (mx2, my2) = vertexPositions[i + 1]
            addGeodesicEdgePreview(mx1, my1, mx2, my2, vp, previewColor)
        }

        // Preview edge to cursor or closing point
        val lastPos = vertexPositions.last()
        val endX = if (isClosing) firstPos.first else targetX
        val endY = if (isClosing) firstPos.second else targetY
        addGeodesicEdgePreview(lastPos.first, lastPos.second, endX, endY, vp, previewColor)

        // Highlight region and first point if closing
        if (isClosing) {
            addGeodesicPolygonFill(vertexPositions, vp)

            val highlight = FxCircle(firstScreenX, firstScreenY, POINT_RADIUS * 1.5)
            highlight.fill = Color.TRANSPARENT
            highlight.stroke = previewColor
            highlight.strokeWidth = 2.0
            highlight.isMouseTransparent = true
            previewGroup.children.add(highlight)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        // Ignore clicks outside the disk
        if (worldX * worldX + worldY * worldY >= 1.0) return

        val hit = hitTester.hitTest(e.x, e.y)
        val targetX = resolveTargetX(hit, worldX)
        val targetY = resolveTargetY(hit, worldY)

        // Check for closing
        if (vertexIds.size >= 3) {
            val firstPos = vertexPositions[0]
            val firstScreenX = canvasPane.modelToScreenX(firstPos.first)
            val firstScreenY = canvasPane.modelToScreenY(firstPos.second)
            val targetScreenX = canvasPane.modelToScreenX(targetX)
            val targetScreenY = canvasPane.modelToScreenY(targetY)
            val distToFirst = hypot(targetScreenX - firstScreenX, targetScreenY - firstScreenY)
            if (distToFirst < SNAP_RADIUS) {
                manager.addObject { id -> Region(id, vertexIds.toList()) }
                preConstructionSnapshot = null
                reset()
                hitTester.rebuildIntersectionCache()
                undoManager.checkpoint()
                requestRender()
                previewGroup.children.clear()
                return
            }
        }

        if (vertexIds.isEmpty()) {
            preConstructionSnapshot = manager.snapshot()
        }

        val pointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender)
        val pt = manager.getObject(pointId) as? PointLike ?: return
        vertexIds.add(pointId)
        vertexPositions.add(Pair(pt.x, pt.y))
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

    private fun addGeodesicEdgePreview(
        mx1: Double, my1: Double, mx2: Double, my2: Double,
        vp: GeometryRenderer.ViewportParams, color: Color
    ) {
        val nodes = GeometryRenderer.createPreviewGeodesicArc(mx1, my1, mx2, my2, GeodesicType.SEGMENT, vp)
        for (node in nodes) {
            if (node is Shape) {
                node.stroke = color
            }
            previewGroup.children.add(node)
        }
    }

    private fun addGeodesicPolygonFill(
        vertices: List<Pair<Double, Double>>,
        vp: GeometryRenderer.ViewportParams
    ) {
        val path = Path()
        val screenVerts = vertices.map { (mx, my) -> vp.modelToScreen(mx, my) }

        val (firstX, firstY) = screenVerts[0]
        path.elements.add(MoveTo(firstX, firstY))

        for (i in vertices.indices) {
            val j = (i + 1) % vertices.size
            val (mx1, my1) = vertices[i]
            val (mx2, my2) = vertices[j]
            val (sx2, sy2) = screenVerts[j]

            val (vx1, vy1) = vp.applyViewport(mx1, my1)
            val (vx2, vy2) = vp.applyViewport(mx2, my2)
            val arc = HyperbolicUtils.geodesicArc(vx1, vy1, vx2, vy2)

            if (arc.isDiameter || hypot(vx1 - vx2, vy1 - vy2) < HyperbolicUtils.EPSILON) {
                path.elements.add(LineTo(sx2, sy2))
            } else {
                val screenR = arc.radius * vp.displayScale
                val startAngle = atan2(vy1 - arc.centerY, vx1 - arc.centerX)
                val endAngle = atan2(vy2 - arc.centerY, vx2 - arc.centerX)
                var sweep = endAngle - startAngle
                while (sweep < -PI) sweep += 2 * PI
                while (sweep > PI) sweep -= 2 * PI

                val arcTo = ArcTo()
                arcTo.radiusX = screenR
                arcTo.radiusY = screenR
                arcTo.x = sx2
                arcTo.y = sy2
                arcTo.isSweepFlag = sweep < 0
                arcTo.isLargeArcFlag = false
                path.elements.add(arcTo)
            }
        }

        path.fill = Color.web("#FFEB3B", 0.3)
        path.stroke = null
        path.isMouseTransparent = true
        previewGroup.children.add(path)
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
        vertexIds.clear()
        vertexPositions.clear()
    }
}
