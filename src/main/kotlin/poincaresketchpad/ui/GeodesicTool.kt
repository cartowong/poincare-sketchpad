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
import javafx.scene.shape.Line as FxLine
import javafx.scene.shape.Circle as FxCircle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class GeodesicSubMode { LINE, RAY, SEGMENT }

class GeodesicTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val previewGroup: Group,
    private val canvasPane: DiskCanvasPane,
    private val requestRender: () -> Unit
) : Tool {

    var subMode: GeodesicSubMode = GeodesicSubMode.SEGMENT

    private companion object {
        const val POINT_RADIUS = 4.0
        const val STROKE_WIDTH = 2.0
    }

    // First point state
    private var firstPointId: Int? = null
    private var firstX: Double = 0.0
    private var firstY: Double = 0.0
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

        // Show ghost at intersection preview
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

        if (firstPointId != null) {
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

            drawPreviewGeodesic(firstX, firstY, targetX, targetY)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        // Ignore clicks outside the disk
        if (worldX * worldX + worldY * worldY >= 1.0) return

        val hit = hitTester.hitTest(e.x, e.y)

        if (firstPointId == null) {
            // First click: set first point
            preConstructionSnapshot = manager.snapshot()
            val pointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender)
            val pt = manager.getObject(pointId) as? PointLike ?: return
            firstPointId = pointId
            firstX = pt.x
            firstY = pt.y
        } else {
            // Second click: set second point and create geodesic object
            val secondPointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender)
            if (secondPointId == firstPointId) return // same point, ignore

            when (subMode) {
                GeodesicSubMode.LINE -> manager.addObject { id -> Line(id, firstPointId!!, secondPointId) }
                GeodesicSubMode.RAY -> manager.addObject { id -> Ray(id, firstPointId!!, secondPointId) }
                GeodesicSubMode.SEGMENT -> manager.addObject { id -> Segment(id, firstPointId!!, secondPointId) }
            }
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

    private fun drawPreviewGeodesic(x1: Double, y1: Double, x2: Double, y2: Double) {
        val dist = hypot(x2 - x1, y2 - y1)
        if (dist < HyperbolicUtils.EPSILON) return

        val displayScale = canvasPane.displayScale
        val diskCenterX = canvasPane.diskCenterX
        val diskCenterY = canvasPane.diskCenterY

        val arc = HyperbolicUtils.geodesicArc(x1, y1, x2, y2)

        when (subMode) {
            GeodesicSubMode.SEGMENT -> {
                drawArcBetweenPoints(x1, y1, x2, y2, arc, displayScale, diskCenterX, diskCenterY)
            }
            GeodesicSubMode.RAY -> {
                // Ray from x1,y1 through x2,y2 to the boundary
                val ideals = HyperbolicUtils.idealPoints(x1, y1, x2, y2, arc)
                drawArcBetweenPoints(x1, y1, ideals.x2, ideals.y2, arc, displayScale, diskCenterX, diskCenterY)
            }
            GeodesicSubMode.LINE -> {
                // Full geodesic line between both ideal points
                val ideals = HyperbolicUtils.idealPoints(x1, y1, x2, y2, arc)
                drawArcBetweenPoints(ideals.x1, ideals.y1, ideals.x2, ideals.y2, arc, displayScale, diskCenterX, diskCenterY)
            }
        }

        // Ghost endpoints
        val s1x = canvasPane.modelToScreenX(x1)
        val s1y = canvasPane.modelToScreenY(y1)
        val s2x = canvasPane.modelToScreenX(x2)
        val s2y = canvasPane.modelToScreenY(y2)

        val ghost1 = FxCircle(s1x, s1y, POINT_RADIUS)
        ghost1.fill = Color.BLACK; ghost1.opacity = 0.4; ghost1.isMouseTransparent = true
        val ghost2 = FxCircle(s2x, s2y, POINT_RADIUS)
        ghost2.fill = Color.BLACK; ghost2.opacity = 0.4; ghost2.isMouseTransparent = true
        previewGroup.children.addAll(ghost1, ghost2)
    }

    private fun drawArcBetweenPoints(
        x1: Double, y1: Double, x2: Double, y2: Double,
        arc: HyperbolicUtils.GeodesicArc,
        displayScale: Double, diskCenterX: Double, diskCenterY: Double
    ) {
        if (arc.isDiameter) {
            // Straight line in screen space
            val sx1 = diskCenterX + x1 * displayScale
            val sy1 = diskCenterY - y1 * displayScale
            val sx2 = diskCenterX + x2 * displayScale
            val sy2 = diskCenterY - y2 * displayScale
            val line = FxLine(sx1, sy1, sx2, sy2)
            line.strokeWidth = STROKE_WIDTH
            line.stroke = Color.BLACK
            line.opacity = 0.4
            line.isMouseTransparent = true
            previewGroup.children.add(line)
        } else {
            // Arc of the Euclidean circle
            val screenCX = diskCenterX + arc.centerX * displayScale
            val screenCY = diskCenterY - arc.centerY * displayScale
            val screenR = arc.radius * displayScale

            val startAngle = atan2(y1 - arc.centerY, x1 - arc.centerX)
            val endAngle = atan2(y2 - arc.centerY, x2 - arc.centerX)

            var sweep = endAngle - startAngle
            while (sweep < -PI) sweep += 2 * PI
            while (sweep > PI) sweep -= 2 * PI

            // Convert to JavaFX Arc coordinates (degrees, CCW positive)
            val startDeg = Math.toDegrees(startAngle)
            val sweepDeg = Math.toDegrees(sweep)

            val fxArc = Arc(screenCX, screenCY, screenR, screenR, startDeg, sweepDeg)
            fxArc.type = ArcType.OPEN
            fxArc.fill = Color.TRANSPARENT
            fxArc.strokeWidth = STROKE_WIDTH
            fxArc.stroke = Color.BLACK
            fxArc.opacity = 0.4
            fxArc.isMouseTransparent = true
            previewGroup.children.add(fxArc)
        }
    }

    private fun reset() {
        firstPointId = null
    }
}
