package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.ObjectMetadata
import poincaresketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val MAX_LABEL_OFFSET = 30.0

class LabelTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val canvasPane: DiskCanvasPane,
    private val requestRender: () -> Unit
) : Tool {

    private var dragObjectId: Int? = null
    private var grabOffsetX = 0.0
    private var grabOffsetY = 0.0
    private var hasDragged = false
    private var pressScreenX = 0.0
    private var pressScreenY = 0.0

    companion object {
        private const val DRAG_THRESHOLD = 3.0 // screen pixels
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        // First, check if clicking on an existing label
        val labelHit = hitTestLabel(e.x, e.y)
        if (labelHit != null) {
            val obj = manager.getObject(labelHit) ?: return
            val meta = manager.getMetadata(labelHit) ?: return
            val (anchorX, anchorY) = getObjectAnchor(obj, meta) ?: return
            val (anchorScreenX, anchorScreenY) = canvasPane.modelToScreen(anchorX, anchorY)
            val labelScreenX = anchorScreenX + meta.labelOffsetX
            val labelScreenY = anchorScreenY + meta.labelOffsetY

            dragObjectId = labelHit
            grabOffsetX = e.x - labelScreenX
            grabOffsetY = e.y - labelScreenY
            hasDragged = false
            pressScreenX = e.x
            pressScreenY = e.y
            return
        }

        // Fall through to existing toggle logic
        val hit = hitTester.hitTest(e.x, e.y) ?: return
        val hitId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnGeodesic -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            is HitResult.IntersectionPreview -> return
        }

        val meta = manager.getMetadata(hitId) ?: return

        if (meta.label == null) {
            // Assign default label
            meta.label = manager.nextDefaultLabel()
            meta.labelVisible = true
            // For circles, store the angle from center to click point
            val obj = manager.getObject(hitId)
            if (obj is Circle && obj.isDefined) {
                meta.labelAnchorAngle = atan2(worldY - obj.euclideanCenterY, worldX - obj.euclideanCenterX)
            }
        } else if (meta.labelVisible) {
            // Hide label
            meta.labelVisible = false
        } else {
            // Re-show hidden label
            meta.labelVisible = true
        }
        undoManager.checkpoint()
        requestRender()
    }

    override fun deactivate(previewGroup: Group) {
        canvasPane.suppressHover = false
        super.deactivate(previewGroup)
    }

    override fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {
        canvasPane.suppressHover = hitTestLabel(e.x, e.y) != null
    }

    override fun onMouseDragged(e: MouseEvent, worldX: Double, worldY: Double) {
        canvasPane.suppressHover = true
        val id = dragObjectId ?: return
        if (!hasDragged && hypot(e.x - pressScreenX, e.y - pressScreenY) < DRAG_THRESHOLD) return
        hasDragged = true
        val meta = manager.getMetadata(id) ?: return
        val obj = manager.getObject(id) ?: return

        // For sliding objects, update anchor to follow cursor
        updateAnchor(obj, meta, worldX, worldY)

        // Compute offset from anchor screen position to target label position
        val (anchorX, anchorY) = getObjectAnchor(obj, meta) ?: return
        val (anchorScreenX, anchorScreenY) = canvasPane.modelToScreen(anchorX, anchorY)
        val targetLabelX = e.x - grabOffsetX
        val targetLabelY = e.y - grabOffsetY
        meta.labelOffsetX = (targetLabelX - anchorScreenX).coerceIn(-MAX_LABEL_OFFSET, MAX_LABEL_OFFSET)
        meta.labelOffsetY = (targetLabelY - anchorScreenY).coerceIn(-MAX_LABEL_OFFSET, MAX_LABEL_OFFSET)

        requestRender()
    }

    override fun onMouseReleased(e: MouseEvent, worldX: Double, worldY: Double) {
        val id = dragObjectId
        if (id != null) {
            if (hasDragged) {
                undoManager.checkpoint()
            } else {
                // Click on label without dragging: toggle label visibility
                val meta = manager.getMetadata(id)
                if (meta != null && meta.labelVisible) {
                    meta.labelVisible = false
                    undoManager.checkpoint()
                    requestRender()
                }
            }
        }
        dragObjectId = null
        hasDragged = false
    }

    private fun updateAnchor(obj: GeometricObject, meta: ObjectMetadata, worldX: Double, worldY: Double) {
        if (obj is Circle && obj.isDefined) {
            meta.labelAnchorAngle = atan2(worldY - obj.euclideanCenterY, worldX - obj.euclideanCenterX)
        } else if (obj is GeodesicLike) {
            // Project cursor onto geodesic to update t parameter
            val geo = obj
            val arc = HyperbolicUtils.GeodesicArc(geo.arcCenterX, geo.arcCenterY, geo.arcRadius, geo.isDiameter)
            val (startX, startY, endX, endY) = when (geo.geodesicType) {
                GeodesicType.LINE -> arrayOf(geo.idealX1, geo.idealY1, geo.idealX2, geo.idealY2)
                GeodesicType.RAY -> arrayOf(geo.x1, geo.y1, geo.idealX2, geo.idealY2)
                GeodesicType.SEGMENT -> arrayOf(geo.x1, geo.y1, geo.x2, geo.y2)
            }
            meta.labelAnchorT = HyperbolicUtils.parameterOnArc(worldX, worldY, startX, startY, endX, endY, arc)
                .coerceIn(0.0, 1.0)
        }
    }

    private fun hitTestLabel(screenX: Double, screenY: Double): Int? {
        for (obj in manager.allObjects()) {
            if (!obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!meta.visible || !meta.labelVisible || meta.label == null) continue

            val (anchorX, anchorY) = getObjectAnchor(obj, meta) ?: continue
            val (anchorScreenX, anchorScreenY) = canvasPane.modelToScreen(anchorX, anchorY)
            val labelX = anchorScreenX + meta.labelOffsetX
            val labelY = anchorScreenY + meta.labelOffsetY

            val textWidth = meta.label!!.length * 8.0
            val padding = 8.0
            if (screenX >= labelX - padding && screenX <= labelX + textWidth + padding &&
                screenY >= labelY - 10.0 && screenY <= labelY + 18.0) {
                return obj.id
            }
        }
        return null
    }

    private fun getObjectAnchor(obj: GeometricObject, meta: ObjectMetadata): Pair<Double, Double>? {
        if (obj is PointLike) return Pair(obj.x, obj.y)
        if (obj is GeodesicLike) {
            val t = meta.labelAnchorT ?: if (obj.geodesicType == GeodesicType.RAY) 0.0 else 0.5
            val arc = HyperbolicUtils.GeodesicArc(obj.arcCenterX, obj.arcCenterY, obj.arcRadius, obj.isDiameter)
            val (startX, startY, endX, endY) = when (obj.geodesicType) {
                GeodesicType.LINE -> arrayOf(obj.idealX1, obj.idealY1, obj.idealX2, obj.idealY2)
                GeodesicType.RAY -> arrayOf(obj.x1, obj.y1, obj.idealX2, obj.idealY2)
                GeodesicType.SEGMENT -> arrayOf(obj.x1, obj.y1, obj.x2, obj.y2)
            }
            val (px, py) = HyperbolicUtils.interpolateOnArc(t, startX, startY, endX, endY, arc)
            return Pair(px, py)
        }
        return when (obj) {
            is Circle -> {
                if (!obj.isDefined) return null
                val angle = meta.labelAnchorAngle ?: (PI / 2)
                Pair(
                    obj.euclideanCenterX + obj.euclideanRadius * cos(angle),
                    obj.euclideanCenterY + obj.euclideanRadius * sin(angle)
                )
            }
            is Angle -> Pair(obj.vertexX, obj.vertexY)
            is Region -> {
                if (obj.vertices.isNotEmpty()) {
                    val cx = obj.vertices.sumOf { it.first } / obj.vertices.size
                    val cy = obj.vertices.sumOf { it.second } / obj.vertices.size
                    Pair(cx, cy)
                } else null
            }
            else -> null
        }
    }
}
