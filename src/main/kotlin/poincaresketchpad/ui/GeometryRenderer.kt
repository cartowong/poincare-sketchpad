package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.ObjectMetadata
import poincaresketchpad.model.getObjectAnchor
import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.ArcTo
import javafx.scene.shape.ArcType
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import javafx.scene.text.Font
import javafx.scene.text.Text
import kotlin.math.*

private val SELECTED_COLOR = Color.web("#2196F3")
private val HOVER_COLOR = Color.web("#2196F3")
private val REGION_FILL = Color.web("#FFEB3B", 0.3)
private val ANGLE_FILL = Color.web("#4CAF50", 0.3)
private val ANGLE_STROKE = Color.web("#4CAF50", 0.7)
private val PREVIEW_COLOR = Color.web("#FF5722")
private const val PREVIEW_OPACITY = 0.4

private const val POINT_RADIUS = 4.0
private const val STROKE_WIDTH = 2.0
private const val ANGLE_ARC_RADIUS = GeometryUtils.ANGLE_ARC_RADIUS
private const val FONT_SIZE = 13.0

object GeometryRenderer {

    private fun isRenderable(meta: ObjectMetadata): Boolean =
        meta.visible && !meta.isLocked

    /**
     * Render all objects into the given group.
     *
     * @param manager the object manager
     * @param group target group (cleared and rebuilt)
     * @param diskCenterX screen X of disk center
     * @param diskCenterY screen Y of disk center
     * @param displayScale pixels per unit-disk radius
     * @param viewportA viewport Mobius translation parameter (re, im)
     * @param viewportTheta viewport rotation angle
     * @param selectedIds set of selected object IDs
     * @param hoveredId hovered object ID (nullable)
     */
    fun renderAll(
        manager: GeometricObjectManager,
        group: Group,
        diskCenterX: Double,
        diskCenterY: Double,
        displayScale: Double,
        viewportA: Pair<Double, Double>,
        viewportTheta: Double,
        selectedIds: Set<Int> = emptySet(),
        hoveredId: Int? = null
    ) {
        group.children.clear()

        val vp = ViewportParams(diskCenterX, diskCenterY, displayScale, viewportA, viewportTheta)

        // Pass 1: Regions
        for (obj in manager.allObjects()) {
            if (obj !is Region || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta)) continue
            val isSelected = obj.id in selectedIds
            val isHovered = obj.id == hoveredId
            renderRegion(group, obj, vp, isSelected, isHovered)
        }

        // Pass 2: Angles
        for (obj in manager.allObjects()) {
            if (obj !is Angle || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta)) continue
            val isSelected = obj.id in selectedIds
            val isHovered = obj.id == hoveredId
            renderAngle(group, obj, manager, vp, isSelected, isHovered)
        }

        // Pass 3: Geodesics and Circles
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta) || !obj.isDefined) continue
            val isSelected = obj.id in selectedIds
            val isHovered = obj.id == hoveredId
            val color = when {
                isSelected -> SELECTED_COLOR
                isHovered -> HOVER_COLOR
                else -> Color.BLACK
            }
            val strokeWidth = if (isHovered && !isSelected) STROKE_WIDTH * 1.5 else STROKE_WIDTH

            when {
                obj is GeodesicLike -> renderGeodesic(group, obj, vp, color, strokeWidth)
                obj is Circle -> renderCircle(group, obj, manager, vp, color, strokeWidth)
            }
        }

        // Pass 4: Points
        for (obj in manager.allObjects()) {
            if (obj !is PointLike || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta)) continue
            val isSelected = obj.id in selectedIds
            val isHovered = obj.id == hoveredId
            val color = when {
                isSelected -> SELECTED_COLOR
                isHovered -> HOVER_COLOR
                else -> Color.BLACK
            }
            val radius = if (isHovered && !isSelected) POINT_RADIUS * 1.3 else POINT_RADIUS
            val (sx, sy) = vp.modelToScreen(obj.x, obj.y)
            val circle = FxCircle(sx, sy, radius)
            circle.fill = color
            group.children.add(circle)
        }

        // Pass 5: Labels
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta) || !meta.labelVisible || meta.label == null) continue
            if (!obj.isDefined) continue

            val anchor = getObjectAnchor(obj, meta) ?: continue
            val (sx, sy) = vp.modelToScreen(anchor.first, anchor.second)
            val text = Text(meta.label)
            text.font = Font.font(FONT_SIZE)
            text.x = sx + meta.labelOffsetX
            text.y = sy + meta.labelOffsetY
            text.fill = Color.BLACK
            group.children.add(text)
        }
    }

    /**
     * Render preview objects (for construction apply previews).
     */
    fun renderPreviewObjects(
        objects: List<GeometricObject>,
        group: Group,
        diskCenterX: Double,
        diskCenterY: Double,
        displayScale: Double,
        viewportA: Pair<Double, Double>,
        viewportTheta: Double
    ) {
        val vp = ViewportParams(diskCenterX, diskCenterY, displayScale, viewportA, viewportTheta)
        val color = PREVIEW_COLOR

        // Pass 1: Regions
        for (obj in objects) {
            if (obj !is Region || !obj.isDefined) continue
            renderPreviewRegion(group, obj, vp, color)
        }

        // Pass 2: Angles
        for (obj in objects) {
            if (obj !is Angle || !obj.isDefined) continue
            renderPreviewAngle(group, obj, vp, color)
        }

        // Pass 3: Geodesics and Circles
        for (obj in objects) {
            if (!obj.isDefined) continue
            when {
                obj is GeodesicLike -> renderGeodesic(group, obj, vp, color, STROKE_WIDTH, PREVIEW_OPACITY, true)
                obj is Circle -> renderPreviewCircle(group, obj, vp, color)
            }
        }

        // Pass 4: Points
        for (obj in objects) {
            if (obj !is PointLike || !obj.isDefined) continue
            val (sx, sy) = vp.modelToScreen(obj.x, obj.y)
            val circle = FxCircle(sx, sy, POINT_RADIUS)
            circle.fill = color
            circle.opacity = PREVIEW_OPACITY
            circle.isMouseTransparent = true
            group.children.add(circle)
        }
    }

    // --- Grid ---

    fun renderGrid(
        group: Group,
        diskCenterX: Double,
        diskCenterY: Double,
        displayScale: Double,
        viewportA: Pair<Double, Double>,
        viewportTheta: Double
    ) {
        val gridColor = Color.gray(0.85)
        val gridStrokeWidth = 0.5
        val hyperbolicSpacing = 1.0

        // Concentric hyperbolic circles at equal hyperbolic distance intervals from origin
        // Euclidean radii: tanh(n * d / 2) where d = spacing
        var n = 1
        while (true) {
            val euclideanR = HyperbolicUtils.hyperbolicRadiusToEuclidean(n * hyperbolicSpacing)
            if (euclideanR >= 1.0 - 1e-4) break
            val screenR = euclideanR * displayScale
            // Grid is always centered at the origin, so apply viewport to origin
            val (vox, voy) = applyViewport(0.0, 0.0, viewportA, viewportTheta)
            val sx = diskCenterX + vox * displayScale
            val sy = diskCenterY - voy * displayScale
            val circle = FxCircle(sx, sy, screenR)
            circle.fill = Color.TRANSPARENT
            circle.stroke = gridColor
            circle.strokeWidth = gridStrokeWidth
            circle.isMouseTransparent = true
            group.children.add(circle)
            n++
        }

        // 6 radial diameters at 30-degree intervals
        for (i in 0 until 6) {
            val angle = i * PI / 6
            // Endpoints on the unit circle boundary
            val bx1 = cos(angle)
            val by1 = sin(angle)
            val bx2 = -bx1
            val by2 = -by1

            val (vbx1, vby1) = applyViewport(bx1, by1, viewportA, viewportTheta)
            val (vbx2, vby2) = applyViewport(bx2, by2, viewportA, viewportTheta)
            val sx1 = diskCenterX + vbx1 * displayScale
            val sy1 = diskCenterY - vby1 * displayScale
            val sx2 = diskCenterX + vbx2 * displayScale
            val sy2 = diskCenterY - vby2 * displayScale

            val line = FxLine(sx1, sy1, sx2, sy2)
            line.stroke = gridColor
            line.strokeWidth = gridStrokeWidth
            line.isMouseTransparent = true
            group.children.add(line)
        }
    }

    // --- Static preview methods for tools ---

    /**
     * Create a preview point node at the given model coordinates.
     */
    fun createPreviewPoint(mx: Double, my: Double, vp: ViewportParams): FxCircle {
        val (sx, sy) = vp.modelToScreen(mx, my)
        return FxCircle(sx, sy, POINT_RADIUS).apply {
            fill = Color.BLACK
            opacity = PREVIEW_OPACITY
            isMouseTransparent = true
        }
    }

    /**
     * Create a preview geodesic arc node between two model points.
     * Returns null if degenerate.
     */
    fun createPreviewGeodesicArc(
        mx1: Double, my1: Double,
        mx2: Double, my2: Double,
        geodesicType: GeodesicType,
        vp: ViewportParams
    ): List<javafx.scene.Node> {
        val nodes = mutableListOf<javafx.scene.Node>()
        val (vx1, vy1) = vp.applyViewport(mx1, my1)
        val (vx2, vy2) = vp.applyViewport(mx2, my2)
        if (hypot(vx1 - vx2, vy1 - vy2) < HyperbolicUtils.EPSILON) return nodes

        val arc = HyperbolicUtils.geodesicArc(vx1, vy1, vx2, vy2)
        val sx1 = vp.diskCenterX + vx1 * vp.displayScale
        val sy1 = vp.diskCenterY - vy1 * vp.displayScale
        val sx2 = vp.diskCenterX + vx2 * vp.displayScale
        val sy2 = vp.diskCenterY - vy2 * vp.displayScale

        if (arc.isDiameter) {
            val ideals = HyperbolicUtils.idealPoints(vx1, vy1, vx2, vy2, arc)
            val (startX, startY, endX, endY) = when (geodesicType) {
                GeodesicType.LINE -> listOf(
                    vp.diskCenterX + ideals.x1 * vp.displayScale,
                    vp.diskCenterY - ideals.y1 * vp.displayScale,
                    vp.diskCenterX + ideals.x2 * vp.displayScale,
                    vp.diskCenterY - ideals.y2 * vp.displayScale
                )
                GeodesicType.RAY -> listOf(
                    sx1, sy1,
                    vp.diskCenterX + ideals.x2 * vp.displayScale,
                    vp.diskCenterY - ideals.y2 * vp.displayScale
                )
                GeodesicType.SEGMENT -> listOf(sx1, sy1, sx2, sy2)
            }
            val line = FxLine(startX, startY, endX, endY)
            line.stroke = Color.BLACK
            line.strokeWidth = STROKE_WIDTH
            line.opacity = PREVIEW_OPACITY
            line.isMouseTransparent = true
            nodes.add(line)
        } else {
            val ideals = HyperbolicUtils.idealPoints(vx1, vy1, vx2, vy2, arc)
            val screenCX = vp.diskCenterX + arc.centerX * vp.displayScale
            val screenCY = vp.diskCenterY - arc.centerY * vp.displayScale
            val screenR = arc.radius * vp.displayScale

            val (startScreenX, startScreenY, endScreenX, endScreenY) = when (geodesicType) {
                GeodesicType.LINE -> listOf(
                    vp.diskCenterX + ideals.x1 * vp.displayScale,
                    vp.diskCenterY - ideals.y1 * vp.displayScale,
                    vp.diskCenterX + ideals.x2 * vp.displayScale,
                    vp.diskCenterY - ideals.y2 * vp.displayScale
                )
                GeodesicType.RAY -> listOf(
                    sx1, sy1,
                    vp.diskCenterX + ideals.x2 * vp.displayScale,
                    vp.diskCenterY - ideals.y2 * vp.displayScale
                )
                GeodesicType.SEGMENT -> listOf(sx1, sy1, sx2, sy2)
            }

            val arcNode = createScreenArc(screenCX, screenCY, screenR,
                startScreenX, startScreenY, endScreenX, endScreenY)
            if (arcNode != null) {
                arcNode.stroke = Color.BLACK
                arcNode.strokeWidth = STROKE_WIDTH
                arcNode.fill = Color.TRANSPARENT
                arcNode.opacity = PREVIEW_OPACITY
                arcNode.isMouseTransparent = true
                nodes.add(arcNode)
            }
        }
        return nodes
    }

    // --- Private rendering helpers ---

    private fun renderGeodesic(
        group: Group,
        obj: GeodesicLike,
        vp: ViewportParams,
        color: Color,
        strokeWidth: Double,
        opacity: Double = 1.0,
        mouseTransparent: Boolean = false
    ) {
        val (vx1, vy1) = vp.applyViewport(obj.x1, obj.y1)
        val (vx2, vy2) = vp.applyViewport(obj.x2, obj.y2)

        if (hypot(vx1 - vx2, vy1 - vy2) < HyperbolicUtils.EPSILON) return

        val arc = HyperbolicUtils.geodesicArc(vx1, vy1, vx2, vy2)
        val sx1 = vp.diskCenterX + vx1 * vp.displayScale
        val sy1 = vp.diskCenterY - vy1 * vp.displayScale
        val sx2 = vp.diskCenterX + vx2 * vp.displayScale
        val sy2 = vp.diskCenterY - vy2 * vp.displayScale

        if (arc.isDiameter) {
            val ideals = HyperbolicUtils.idealPoints(vx1, vy1, vx2, vy2, arc)
            val (startX, startY, endX, endY) = getDiameterEndpoints(
                obj.geodesicType, sx1, sy1, sx2, sy2, ideals, vp
            )
            val line = FxLine(startX, startY, endX, endY)
            line.stroke = color
            line.strokeWidth = strokeWidth
            if (opacity < 1.0) line.opacity = opacity
            if (mouseTransparent) line.isMouseTransparent = true
            group.children.add(line)
        } else {
            val ideals = HyperbolicUtils.idealPoints(vx1, vy1, vx2, vy2, arc)
            val screenCX = vp.diskCenterX + arc.centerX * vp.displayScale
            val screenCY = vp.diskCenterY - arc.centerY * vp.displayScale
            val screenR = arc.radius * vp.displayScale

            val (startScreenX, startScreenY, endScreenX, endScreenY) = getArcEndpoints(
                obj.geodesicType, sx1, sy1, sx2, sy2, ideals, vp
            )

            val arcNode = createScreenArc(screenCX, screenCY, screenR,
                startScreenX, startScreenY, endScreenX, endScreenY)
            if (arcNode != null) {
                arcNode.stroke = color
                arcNode.strokeWidth = strokeWidth
                arcNode.fill = Color.TRANSPARENT
                if (opacity < 1.0) arcNode.opacity = opacity
                if (mouseTransparent) arcNode.isMouseTransparent = true
                group.children.add(arcNode)
            }
        }
    }

    private fun getDiameterEndpoints(
        type: GeodesicType,
        sx1: Double, sy1: Double, sx2: Double, sy2: Double,
        ideals: HyperbolicUtils.IdealPoints, vp: ViewportParams
    ): List<Double> {
        val si1x = vp.diskCenterX + ideals.x1 * vp.displayScale
        val si1y = vp.diskCenterY - ideals.y1 * vp.displayScale
        val si2x = vp.diskCenterX + ideals.x2 * vp.displayScale
        val si2y = vp.diskCenterY - ideals.y2 * vp.displayScale
        return when (type) {
            GeodesicType.LINE -> listOf(si1x, si1y, si2x, si2y)
            GeodesicType.RAY -> listOf(sx1, sy1, si2x, si2y)
            GeodesicType.SEGMENT -> listOf(sx1, sy1, sx2, sy2)
        }
    }

    private fun getArcEndpoints(
        type: GeodesicType,
        sx1: Double, sy1: Double, sx2: Double, sy2: Double,
        ideals: HyperbolicUtils.IdealPoints, vp: ViewportParams
    ): List<Double> {
        val si1x = vp.diskCenterX + ideals.x1 * vp.displayScale
        val si1y = vp.diskCenterY - ideals.y1 * vp.displayScale
        val si2x = vp.diskCenterX + ideals.x2 * vp.displayScale
        val si2y = vp.diskCenterY - ideals.y2 * vp.displayScale
        return when (type) {
            GeodesicType.LINE -> listOf(si1x, si1y, si2x, si2y)
            GeodesicType.RAY -> listOf(sx1, sy1, si2x, si2y)
            GeodesicType.SEGMENT -> listOf(sx1, sy1, sx2, sy2)
        }
    }

    /**
     * Create a JavaFX Arc node for a circular arc between two screen points
     * on a circle with given screen center and radius.
     */
    private fun createScreenArc(
        cx: Double, cy: Double, r: Double,
        startX: Double, startY: Double,
        endX: Double, endY: Double
    ): Arc? {
        if (r < 1e-6) return null

        val startAngle = Math.toDegrees(atan2(-(startY - cy), startX - cx))
        val endAngle = Math.toDegrees(atan2(-(endY - cy), endX - cx))

        // Compute sweep: choose the shorter arc (the one inside the disk)
        var sweep = endAngle - startAngle
        while (sweep < -180) sweep += 360
        while (sweep > 180) sweep -= 360

        val arc = Arc(cx, cy, r, r, startAngle, sweep)
        arc.type = ArcType.OPEN
        return arc
    }

    private fun renderCircle(
        group: Group,
        circle: Circle,
        manager: GeometricObjectManager,
        vp: ViewportParams,
        color: Color,
        strokeWidth: Double
    ) {
        val screenCircle = computeViewportCircle(circle, manager, vp) ?: return
        val fxCircle = FxCircle(screenCircle.cx, screenCircle.cy, screenCircle.r)
        fxCircle.fill = Color.TRANSPARENT
        fxCircle.stroke = color
        fxCircle.strokeWidth = strokeWidth
        group.children.add(fxCircle)
    }

    private fun renderPreviewCircle(
        group: Group,
        obj: GeometricObject,
        vp: ViewportParams,
        color: Color
    ) {
        // For preview circles, the circle object has already been computed with temporary objects.
        // We just need its Euclidean representation in viewport space.
        val circle = obj as? Circle ?: return
        if (!circle.isDefined) return

        // Transform the hyperbolic center through viewport, then recompute the
        // Euclidean circle representation using the preserved hyperbolic radius
        val (vcx, vcy) = vp.applyViewport(circle.euclideanCenterX, circle.euclideanCenterY)
        val hDist = circle.hyperbolicRadius
        val cNormSq = vcx * vcx + vcy * vcy
        val t = tanh(hDist / 2.0)
        val denom = 1.0 - cNormSq * t * t
        if (abs(denom) < HyperbolicUtils.EPSILON) return

        val s = (1.0 - t * t) / denom
        val ecx = vcx * s
        val ecy = vcy * s
        val er = t * (1.0 - cNormSq) / denom
        if (er <= 0) return

        val scx = vp.diskCenterX + ecx * vp.displayScale
        val scy = vp.diskCenterY - ecy * vp.displayScale
        val sr = er * vp.displayScale

        val fxCircle = FxCircle(scx, scy, sr)
        fxCircle.fill = Color.TRANSPARENT
        fxCircle.stroke = color
        fxCircle.strokeWidth = STROKE_WIDTH
        fxCircle.opacity = PREVIEW_OPACITY
        fxCircle.isMouseTransparent = true
        group.children.add(fxCircle)
    }

    private fun renderAngle(
        group: Group,
        angle: Angle,
        manager: GeometricObjectManager,
        vp: ViewportParams,
        isSelected: Boolean,
        isHovered: Boolean
    ) {
        val pA = manager.getObject(angle.pointAId) as? PointLike ?: return
        val pV = manager.getObject(angle.vertexId) as? PointLike ?: return
        val pC = manager.getObject(angle.pointCId) as? PointLike ?: return

        val (vaX, vaY) = vp.applyViewport(pA.x, pA.y)
        val (vvX, vvY) = vp.applyViewport(pV.x, pV.y)
        val (vcX, vcY) = vp.applyViewport(pC.x, pC.y)

        val arcVA = HyperbolicUtils.geodesicArc(vvX, vvY, vaX, vaY)
        val (taX, taY) = HyperbolicUtils.tangentAtPoint(vvX, vvY, vvX, vvY, vaX, vaY, arcVA)
        val arcVC = HyperbolicUtils.geodesicArc(vvX, vvY, vcX, vcY)
        val (tcX, tcY) = HyperbolicUtils.tangentAtPoint(vvX, vvY, vvX, vvY, vcX, vcY, arcVC)

        // Model-space tangent angle = JavaFX angle (y-flips cancel)
        val startAngleDeg = Math.toDegrees(atan2(taY, taX))
        val endAngleDeg = Math.toDegrees(atan2(tcY, tcX))
        var sweepDeg = endAngleDeg - startAngleDeg
        while (sweepDeg >= 0) sweepDeg -= 360.0
        while (sweepDeg < -360.0) sweepDeg += 360.0

        val (svx, svy) = vp.modelToScreen(pV.x, pV.y)
        val arc = Arc(svx, svy, ANGLE_ARC_RADIUS, ANGLE_ARC_RADIUS, startAngleDeg, sweepDeg)
        arc.type = ArcType.ROUND
        arc.fill = when {
            isSelected || isHovered -> Color.web("#2196F3", 0.5)
            else -> ANGLE_FILL
        }
        arc.stroke = if (isSelected) SELECTED_COLOR else if (isHovered) HOVER_COLOR else ANGLE_STROKE
        arc.strokeWidth = 1.0
        group.children.add(arc)
    }

    private fun renderPreviewAngle(
        group: Group,
        angle: Angle,
        vp: ViewportParams,
        color: Color
    ) {
        // For preview angles, vertex and points are already computed in model space.
        // Compute tangent angles in viewport space from the vertex coordinates.
        val (svx, svy) = vp.modelToScreen(angle.vertexX, angle.vertexY)

        // Model-space angles map directly to JavaFX angles (y-flips cancel)
        val startDeg = Math.toDegrees(angle.startAngleRadians)
        val sweepDeg = Math.toDegrees(angle.sweepAngleRadians)

        val arc = Arc(svx, svy, ANGLE_ARC_RADIUS, ANGLE_ARC_RADIUS, startDeg, sweepDeg)
        arc.type = ArcType.ROUND
        arc.fill = Color.web("#FF5722", 0.2)
        arc.stroke = color
        arc.strokeWidth = 1.0
        arc.opacity = PREVIEW_OPACITY
        arc.isMouseTransparent = true
        group.children.add(arc)
    }

    private fun renderRegion(
        group: Group,
        region: Region,
        vp: ViewportParams,
        isSelected: Boolean,
        isHovered: Boolean
    ) {
        if (region.vertices.size < 3) return

        val path = Path()
        val screenVerts = region.vertices.map { (mx, my) -> vp.modelToScreen(mx, my) }

        // Build a path with geodesic-arc edges between consecutive vertices
        val (firstX, firstY) = screenVerts[0]
        path.elements.add(MoveTo(firstX, firstY))

        for (i in 0 until region.vertices.size) {
            val j = (i + 1) % region.vertices.size
            val (mx1, my1) = region.vertices[i]
            val (mx2, my2) = region.vertices[j]
            val (sx2, sy2) = screenVerts[j]

            // Compute the geodesic arc in viewport space between consecutive vertices
            val (vx1, vy1) = vp.applyViewport(mx1, my1)
            val (vx2, vy2) = vp.applyViewport(mx2, my2)
            val arc = HyperbolicUtils.geodesicArc(vx1, vy1, vx2, vy2)

            if (arc.isDiameter || hypot(vx1 - vx2, vy1 - vy2) < HyperbolicUtils.EPSILON) {
                // Straight line
                path.elements.add(javafx.scene.shape.LineTo(sx2, sy2))
            } else {
                val screenR = arc.radius * vp.displayScale
                // Determine sweep direction
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

        path.fill = when {
            isSelected || isHovered -> Color.web("#2196F3", 0.4)
            else -> REGION_FILL
        }
        path.stroke = null
        group.children.add(path)
    }

    private fun renderPreviewRegion(
        group: Group,
        region: Region,
        vp: ViewportParams,
        color: Color
    ) {
        if (region.vertices.size < 3) return

        val path = Path()
        val screenVerts = region.vertices.map { (mx, my) -> vp.modelToScreen(mx, my) }

        val (firstX, firstY) = screenVerts[0]
        path.elements.add(MoveTo(firstX, firstY))

        for (i in 0 until region.vertices.size) {
            val j = (i + 1) % region.vertices.size
            val (mx1, my1) = region.vertices[i]
            val (mx2, my2) = region.vertices[j]
            val (sx2, sy2) = screenVerts[j]

            val (vx1, vy1) = vp.applyViewport(mx1, my1)
            val (vx2, vy2) = vp.applyViewport(mx2, my2)
            val arc = HyperbolicUtils.geodesicArc(vx1, vy1, vx2, vy2)

            if (arc.isDiameter || hypot(vx1 - vx2, vy1 - vy2) < HyperbolicUtils.EPSILON) {
                path.elements.add(javafx.scene.shape.LineTo(sx2, sy2))
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

        path.fill = Color.web("#FF5722", 0.2)
        path.stroke = null
        path.opacity = PREVIEW_OPACITY
        path.isMouseTransparent = true
        group.children.add(path)
    }

    /**
     * Compute the screen-space Euclidean circle for a hyperbolic circle after viewport transform.
     */
    private fun computeViewportCircle(
        circle: Circle,
        manager: GeometricObjectManager,
        vp: ViewportParams
    ): ScreenCircle? {
        if (!circle.isDefined) return null
        val centerPt = manager.getObject(circle.centerId) as? PointLike ?: return null
        val radiusPt = manager.getObject(circle.radiusPointId) as? PointLike ?: return null

        val (vcx, vcy) = vp.applyViewport(centerPt.x, centerPt.y)
        val (vrx, vry) = vp.applyViewport(radiusPt.x, radiusPt.y)

        // Recompute the Euclidean circle from the viewport-transformed center and radius point
        val hDist = HyperbolicUtils.hyperbolicDistance(vcx, vcy, vrx, vry)
        if (hDist < HyperbolicUtils.EPSILON) return null

        val cNormSq = vcx * vcx + vcy * vcy
        val t = tanh(hDist / 2.0)
        val denom = 1.0 - cNormSq * t * t
        if (abs(denom) < HyperbolicUtils.EPSILON) return null

        val scale = (1.0 - t * t) / denom
        val ecx = vcx * scale
        val ecy = vcy * scale
        val er = t * (1.0 - cNormSq) / denom
        if (er <= 0) return null

        return ScreenCircle(
            vp.diskCenterX + ecx * vp.displayScale,
            vp.diskCenterY - ecy * vp.displayScale,
            er * vp.displayScale
        )
    }

    private data class ScreenCircle(val cx: Double, val cy: Double, val r: Double)

    // --- Viewport helpers ---

    private fun applyViewport(
        mx: Double, my: Double,
        viewportA: Pair<Double, Double>, viewportTheta: Double
    ): Pair<Double, Double> {
        val (ar, ai) = viewportA
        val (tr, ti) = HyperbolicUtils.mobiusTransform(mx, my, ar, ai)
        return HyperbolicUtils.mobiusRotation(tr, ti, viewportTheta)
    }

    /**
     * Parameters needed for coordinate conversion during rendering.
     */
    data class ViewportParams(
        val diskCenterX: Double,
        val diskCenterY: Double,
        val displayScale: Double,
        val viewportA: Pair<Double, Double>,
        val viewportTheta: Double
    ) {
        fun applyViewport(mx: Double, my: Double): Pair<Double, Double> {
            val (ar, ai) = viewportA
            val (tr, ti) = HyperbolicUtils.mobiusTransform(mx, my, ar, ai)
            return HyperbolicUtils.mobiusRotation(tr, ti, viewportTheta)
        }

        fun modelToScreen(mx: Double, my: Double): Pair<Double, Double> {
            val (vx, vy) = applyViewport(mx, my)
            return Pair(diskCenterX + vx * displayScale, diskCenterY - vy * displayScale)
        }
    }
}
