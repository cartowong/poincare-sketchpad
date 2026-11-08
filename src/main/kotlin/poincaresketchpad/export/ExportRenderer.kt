package poincaresketchpad.export

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.ObjectMetadata
import poincaresketchpad.model.getObjectAnchor
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Rectangle2D
import javafx.scene.Group
import javafx.scene.SnapshotParameters
import javafx.scene.paint.Color
import javafx.scene.shape.*
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine
import javafx.scene.text.Font
import javafx.scene.text.Text
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*

enum class ExportMethod { CANVAS, CUSTOM }
enum class ExportFormat { PNG, SVG }

data class ExportSettings(
    val method: ExportMethod,
    val format: ExportFormat,
    val width: Int,
    val height: Int,
    val scale: Double,
    val transparentBackground: Boolean,
    val includeGrid: Boolean
)

object ExportRenderer {

    private const val POINT_RADIUS = 4.0
    private const val STROKE_WIDTH = 2.0
    private const val ANGLE_ARC_RADIUS = GeometryUtils.ANGLE_ARC_RADIUS
    private const val FONT_SIZE = 12.0
    private const val MARGIN_FRACTION = 0.03

    fun export(settings: ExportSettings, manager: GeometricObjectManager, file: File) {
        when (settings.format) {
            ExportFormat.PNG -> exportPng(settings, manager, file)
            ExportFormat.SVG -> exportSvg(settings, manager, file)
        }
    }

    // --- PNG Export ---

    private fun exportPng(settings: ExportSettings, manager: GeometricObjectManager, file: File) {
        val outW = settings.width.toDouble()
        val outH = settings.height.toDouble()
        val maxDim = max(outW, outH)
        // Disk diameter in pixels = maxDim / scale
        val diskDiameter = maxDim / settings.scale
        val size = diskDiameter + diskDiameter * MARGIN_FRACTION * 2
        val diskRadius = diskDiameter / 2.0
        val centerX = size / 2.0
        val centerY = size / 2.0
        val group = Group()

        // Background
        if (!settings.transparentBackground) {
            // Gray exterior
            group.children.add(Rectangle(size, size).apply { fill = Color.web("#E8E8E8") })
            // White disk
            group.children.add(FxCircle(centerX, centerY, diskRadius).apply {
                fill = Color.WHITE
                stroke = null
            })
        }

        val tx = { mx: Double -> centerX + mx * diskRadius }
        val ty = { my: Double -> centerY - my * diskRadius }

        // Clip group for objects inside the disk
        val objectGroup = Group()
        objectGroup.clip = FxCircle(centerX, centerY, diskRadius)

        if (settings.includeGrid) {
            renderGridPng(objectGroup, diskRadius, centerX, centerY, tx, ty)
        }

        renderObjectsPng(objectGroup, manager, diskRadius, tx, ty)
        group.children.add(objectGroup)

        // Crop the center outW × outH rectangle
        val cropX = (size - outW) / 2.0
        val cropY = (size - outH) / 2.0

        // Snapshot
        val params = SnapshotParameters()
        params.fill = if (settings.transparentBackground) Color.TRANSPARENT else Color.web("#E8E8E8")
        params.viewport = Rectangle2D(cropX, cropY, outW, outH)

        val image = group.snapshot(params, null)
        val buffered = SwingFXUtils.fromFXImage(image, null)
        ImageIO.write(buffered, "png", file)
    }

    private fun renderGridPng(
        group: Group, diskRadius: Double,
        centerX: Double, centerY: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ) {
        val gridColor = Color.gray(0.85)

        // Concentric hyperbolic circles at ~1.0 hyperbolic unit intervals
        val d = 1.0
        var n = 1
        while (true) {
            val eucR = HyperbolicUtils.hyperbolicRadiusToEuclidean(n * d)
            if (eucR >= 1.0) break
            val pixelR = eucR * diskRadius
            val circle = FxCircle(centerX, centerY, pixelR)
            circle.fill = Color.TRANSPARENT
            circle.stroke = gridColor
            circle.strokeWidth = 0.5
            group.children.add(circle)
            n++
        }

        // 6 radial diameters at 30-degree intervals
        for (i in 0 until 6) {
            val angle = i * PI / 6.0
            val x1 = tx(cos(angle))
            val y1 = ty(sin(angle))
            val x2 = tx(-cos(angle))
            val y2 = ty(-sin(angle))
            val line = FxLine(x1, y1, x2, y2)
            line.stroke = gridColor
            line.strokeWidth = 0.5
            group.children.add(line)
        }
    }

    private fun renderObjectsPng(
        group: Group, manager: GeometricObjectManager,
        diskRadius: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ) {
        // Pass 1: Regions
        for (obj in manager.allObjects()) {
            if (obj !is Region || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            if (obj.vertices.size >= 3) {
                val path = buildRegionPathPng(obj, manager, diskRadius, tx, ty)
                if (path != null) {
                    path.fill = Color.web("#FFEB3B", 0.3)
                    path.stroke = null
                    group.children.add(path)
                }
            }
        }

        // Pass 2: Angles
        for (obj in manager.allObjects()) {
            if (obj !is Angle || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue

            val cx = tx(obj.vertexX)
            val cy = ty(obj.vertexY)

            // Model-space angle = JavaFX angle (y-flips cancel)
            val startDeg = Math.toDegrees(obj.startAngleRadians)
            val sweepDeg = Math.toDegrees(obj.sweepAngleRadians)

            val arc = Arc(cx, cy, ANGLE_ARC_RADIUS, ANGLE_ARC_RADIUS, startDeg, sweepDeg)
            arc.type = ArcType.ROUND
            arc.fill = Color.web("#4CAF50", 0.3)
            arc.stroke = Color.web("#4CAF50", 0.7)
            arc.strokeWidth = 1.0
            group.children.add(arc)
        }

        // Pass 3: Geodesics and Circles
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !obj.isDefined) continue

            if (obj is GeodesicLike) {
                renderGeodesicPng(group, obj, diskRadius, tx, ty)
            } else if (obj is Circle) {
                val ecx = tx(obj.euclideanCenterX)
                val ecy = ty(obj.euclideanCenterY)
                val er = obj.euclideanRadius * diskRadius
                val circle = FxCircle(ecx, ecy, er)
                circle.fill = Color.TRANSPARENT
                circle.stroke = Color.BLACK
                circle.strokeWidth = STROKE_WIDTH
                group.children.add(circle)
            }
        }

        // Pass 4: Points
        for (obj in manager.allObjects()) {
            if (obj !is PointLike || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            val circle = FxCircle(tx(obj.x), ty(obj.y), POINT_RADIUS)
            circle.fill = Color.BLACK
            group.children.add(circle)
        }

        // Pass 5: Labels
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !meta.labelVisible || meta.label == null) continue
            if (!obj.isDefined) continue

            val anchor = getObjectAnchor(obj, meta) ?: continue
            val text = Text(meta.label)
            text.font = Font.font(FONT_SIZE)
            text.x = tx(anchor.first) + meta.labelOffsetX
            text.y = ty(anchor.second) + meta.labelOffsetY
            text.fill = Color.BLACK
            group.children.add(text)
        }
    }

    private fun renderGeodesicPng(
        group: Group, geo: GeodesicLike,
        diskRadius: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ) {
        // Determine start and end points based on geodesic type
        val (startX, startY, endX, endY) = geodesicEndpoints(geo)

        if (geo.isDiameter) {
            // Render as a straight line
            val line = FxLine(tx(startX), ty(startY), tx(endX), ty(endY))
            line.stroke = Color.BLACK
            line.strokeWidth = STROKE_WIDTH
            group.children.add(line)
        } else {
            // Render as an arc
            val arcNode = buildArcNodePng(
                startX, startY, endX, endY,
                geo.arcCenterX, geo.arcCenterY, geo.arcRadius,
                diskRadius, tx, ty
            )
            if (arcNode != null) {
                arcNode.stroke = Color.BLACK
                arcNode.strokeWidth = STROKE_WIDTH
                arcNode.fill = Color.TRANSPARENT
                group.children.add(arcNode)
            }
        }
    }

    /**
     * Build a JavaFX Path representing an arc between two points on a Euclidean circle.
     */
    private fun buildArcNodePng(
        startX: Double, startY: Double, endX: Double, endY: Double,
        arcCX: Double, arcCY: Double, arcR: Double,
        diskRadius: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ): Path? {
        val sx = tx(startX)
        val sy = ty(startY)
        val ex = tx(endX)
        val ey = ty(endY)
        val pixelR = arcR * diskRadius

        // Determine sweep direction: the arc inside the disk takes the shorter path
        val startAngle = atan2(startY - arcCY, startX - arcCX)
        val endAngle = atan2(endY - arcCY, endX - arcCX)
        var sweep = endAngle - startAngle
        while (sweep < -PI) sweep += 2 * PI
        while (sweep > PI) sweep -= 2 * PI

        val largeArc = abs(sweep) > PI
        // In screen coords (y flipped), CW in model becomes CCW on screen
        val sweepFlag = sweep < 0

        val path = Path()
        path.elements.add(MoveTo(sx, sy))
        path.elements.add(ArcTo(pixelR, pixelR, 0.0, ex, ey, largeArc, sweepFlag))
        return path
    }

    private fun buildRegionPathPng(
        region: Region, manager: GeometricObjectManager,
        diskRadius: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ): Path? {
        if (region.vertices.size < 3) return null
        val path = Path()
        val n = region.vertices.size

        for (i in 0 until n) {
            val (cx, cy) = region.vertices[i]
            val (nx, ny) = region.vertices[(i + 1) % n]
            val sx = tx(cx)
            val sy = ty(cy)

            if (i == 0) {
                path.elements.add(MoveTo(sx, sy))
            }

            val ex = tx(nx)
            val ey = ty(ny)

            val arc = HyperbolicUtils.geodesicArc(cx, cy, nx, ny)
            if (arc.isDiameter) {
                path.elements.add(LineTo(ex, ey))
            } else {
                val pixelR = arc.radius * diskRadius
                val startAngle = atan2(cy - arc.centerY, cx - arc.centerX)
                val endAngle = atan2(ny - arc.centerY, nx - arc.centerX)
                var sweep = endAngle - startAngle
                while (sweep < -PI) sweep += 2 * PI
                while (sweep > PI) sweep -= 2 * PI
                val largeArc = abs(sweep) > PI
                val sweepFlag = sweep < 0
                path.elements.add(ArcTo(pixelR, pixelR, 0.0, ex, ey, largeArc, sweepFlag))
            }
        }

        path.elements.add(ClosePath())
        return path
    }

    // --- SVG Export ---

    private fun exportSvg(settings: ExportSettings, manager: GeometricObjectManager, file: File) {
        val outW = settings.width
        val outH = settings.height
        val maxDim = max(outW, outH).toDouble()
        val diskDiameter = maxDim / settings.scale
        val size = diskDiameter + diskDiameter * MARGIN_FRACTION * 2
        val diskRadius = diskDiameter / 2.0
        val centerX = size / 2.0
        val centerY = size / 2.0

        // viewBox crops the center outW × outH rectangle
        val vbX = (size - outW) / 2.0
        val vbY = (size - outH) / 2.0

        val tx = { mx: Double -> centerX + mx * diskRadius }
        val ty = { my: Double -> centerY - my * diskRadius }

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$outW" height="$outH" viewBox="${fmt(vbX)} ${fmt(vbY)} ${fmt(outW.toDouble())} ${fmt(outH.toDouble())}">""")

        // Clip definition for the disk
        sb.appendLine("""  <defs>""")
        sb.appendLine("""    <clipPath id="disk-clip">""")
        sb.appendLine("""      <circle cx="${fmt(centerX)}" cy="${fmt(centerY)}" r="${fmt(diskRadius)}"/>""")
        sb.appendLine("""    </clipPath>""")
        sb.appendLine("""  </defs>""")

        // Background — cover the full internal canvas, not just viewBox
        if (!settings.transparentBackground) {
            sb.appendLine("""  <rect x="${fmt(vbX)}" y="${fmt(vbY)}" width="${fmt(outW.toDouble())}" height="${fmt(outH.toDouble())}" fill="#E8E8E8"/>""")
            sb.appendLine("""  <circle cx="${fmt(centerX)}" cy="${fmt(centerY)}" r="${fmt(diskRadius)}" fill="white"/>""")
        }

        // Clipped group for objects
        sb.appendLine("""  <g clip-path="url(#disk-clip)">""")

        if (settings.includeGrid) {
            renderGridSvg(sb, diskRadius, centerX, centerY, tx, ty)
        }

        renderObjectsSvg(sb, manager, diskRadius, tx, ty)

        sb.appendLine("""  </g>""")
        sb.appendLine("</svg>")
        file.writeText(sb.toString())
    }

    private fun renderGridSvg(
        sb: StringBuilder, diskRadius: Double,
        centerX: Double, centerY: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ) {
        val gridColor = "#d9d9d9"

        // Concentric hyperbolic circles
        val d = 1.0
        var n = 1
        while (true) {
            val eucR = HyperbolicUtils.hyperbolicRadiusToEuclidean(n * d)
            if (eucR >= 1.0) break
            val pixelR = eucR * diskRadius
            sb.appendLine("""    <circle cx="${fmt(centerX)}" cy="${fmt(centerY)}" r="${fmt(pixelR)}" fill="none" stroke="$gridColor" stroke-width="0.5"/>""")
            n++
        }

        // 6 radial diameters at 30-degree intervals
        for (i in 0 until 6) {
            val angle = i * PI / 6.0
            val x1 = tx(cos(angle))
            val y1 = ty(sin(angle))
            val x2 = tx(-cos(angle))
            val y2 = ty(-sin(angle))
            sb.appendLine("""    <line x1="${fmt(x1)}" y1="${fmt(y1)}" x2="${fmt(x2)}" y2="${fmt(y2)}" stroke="$gridColor" stroke-width="0.5"/>""")
        }
    }

    private fun renderObjectsSvg(
        sb: StringBuilder, manager: GeometricObjectManager,
        diskRadius: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ) {
        // Pass 1: Regions
        for (obj in manager.allObjects()) {
            if (obj !is Region || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            if (obj.vertices.size >= 3) {
                val pathData = buildRegionPathSvg(obj, manager, diskRadius, tx, ty)
                if (pathData != null) {
                    sb.appendLine("""    <path d="$pathData" fill="#FFEB3B" fill-opacity="0.3" stroke="none"/>""")
                }
            }
        }

        // Pass 2: Angles
        for (obj in manager.allObjects()) {
            if (obj !is Angle || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue

            val cx = tx(obj.vertexX)
            val cy = ty(obj.vertexY)
            val r = ANGLE_ARC_RADIUS

            // In SVG, y is flipped relative to model. Negate startAngle to convert.
            val startAngle = -obj.startAngleRadians
            val sweepAngle = obj.sweepAngleRadians

            val x1 = cx + r * cos(startAngle)
            val y1 = cy + r * sin(startAngle)
            val endAngle = startAngle - sweepAngle
            val x2 = cx + r * cos(endAngle)
            val y2 = cy + r * sin(endAngle)

            val largeArc = if (-sweepAngle > PI) 1 else 0
            // CW in model with y-flip becomes CCW in SVG (sweep-flag=0)
            val sweepFlag = 0

            sb.appendLine("""    <path d="M ${fmt(cx)} ${fmt(cy)} L ${fmt(x1)} ${fmt(y1)} A $r $r 0 $largeArc $sweepFlag ${fmt(x2)} ${fmt(y2)} Z" fill="#4CAF50" fill-opacity="0.3" stroke="#4CAF50" stroke-opacity="0.7" stroke-width="1"/>""")
        }

        // Pass 3: Geodesics and Circles
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !obj.isDefined) continue

            if (obj is GeodesicLike) {
                renderGeodesicSvg(sb, obj, diskRadius, tx, ty)
            } else if (obj is Circle) {
                val ecx = tx(obj.euclideanCenterX)
                val ecy = ty(obj.euclideanCenterY)
                val er = obj.euclideanRadius * diskRadius
                sb.appendLine("""    <circle cx="${fmt(ecx)}" cy="${fmt(ecy)}" r="${fmt(er)}" fill="none" stroke="black" stroke-width="$STROKE_WIDTH"/>""")
            }
        }

        // Pass 4: Points
        for (obj in manager.allObjects()) {
            if (obj !is PointLike || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            sb.appendLine("""    <circle cx="${fmt(tx(obj.x))}" cy="${fmt(ty(obj.y))}" r="$POINT_RADIUS" fill="black"/>""")
        }

        // Pass 5: Labels
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !meta.labelVisible || meta.label == null) continue
            if (!obj.isDefined) continue

            val anchor = getObjectAnchor(obj, meta) ?: continue
            val lx = tx(anchor.first) + meta.labelOffsetX
            val ly = ty(anchor.second) + meta.labelOffsetY
            val escaped = meta.label!!.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            sb.appendLine("""    <text x="${fmt(lx)}" y="${fmt(ly)}" font-size="$FONT_SIZE" fill="black">$escaped</text>""")
        }
    }

    private fun renderGeodesicSvg(
        sb: StringBuilder, geo: GeodesicLike,
        diskRadius: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ) {
        val (startX, startY, endX, endY) = geodesicEndpoints(geo)

        if (geo.isDiameter) {
            sb.appendLine("""    <line x1="${fmt(tx(startX))}" y1="${fmt(ty(startY))}" x2="${fmt(tx(endX))}" y2="${fmt(ty(endY))}" stroke="black" stroke-width="$STROKE_WIDTH"/>""")
        } else {
            val sx = tx(startX)
            val sy = ty(startY)
            val ex = tx(endX)
            val ey = ty(endY)
            val pixelR = geo.arcRadius * diskRadius

            val startAngle = atan2(startY - geo.arcCenterY, startX - geo.arcCenterX)
            val endAngle = atan2(endY - geo.arcCenterY, endX - geo.arcCenterX)
            var sweep = endAngle - startAngle
            while (sweep < -PI) sweep += 2 * PI
            while (sweep > PI) sweep -= 2 * PI

            val largeArc = if (abs(sweep) > PI) 1 else 0
            // In SVG y-down pixel coords, model CW arc (sweep < 0) maps to sweep-flag=1,
            // model CCW arc (sweep > 0) maps to sweep-flag=0 (ty flips y, reversing winding)
            val sweepFlag = if (sweep < 0) 1 else 0

            sb.appendLine("""    <path d="M ${fmt(sx)} ${fmt(sy)} A ${fmt(pixelR)} ${fmt(pixelR)} 0 $largeArc $sweepFlag ${fmt(ex)} ${fmt(ey)}" fill="none" stroke="black" stroke-width="$STROKE_WIDTH"/>""")
        }
    }

    private fun buildRegionPathSvg(
        region: Region, manager: GeometricObjectManager,
        diskRadius: Double,
        tx: (Double) -> Double, ty: (Double) -> Double
    ): String? {
        if (region.vertices.size < 3) return null
        val sb = StringBuilder()
        val n = region.vertices.size

        for (i in 0 until n) {
            val (cx, cy) = region.vertices[i]
            val (nx, ny) = region.vertices[(i + 1) % n]
            val sx = tx(cx)
            val sy = ty(cy)

            if (i == 0) {
                sb.append("M ${fmt(sx)} ${fmt(sy)}")
            }

            val ex = tx(nx)
            val ey = ty(ny)

            val arc = HyperbolicUtils.geodesicArc(cx, cy, nx, ny)
            if (arc.isDiameter) {
                sb.append(" L ${fmt(ex)} ${fmt(ey)}")
            } else {
                val pixelR = arc.radius * diskRadius
                val startAngle = atan2(cy - arc.centerY, cx - arc.centerX)
                val endAngle = atan2(ny - arc.centerY, nx - arc.centerX)
                var sweep = endAngle - startAngle
                while (sweep < -PI) sweep += 2 * PI
                while (sweep > PI) sweep -= 2 * PI
                val largeArc = if (abs(sweep) > PI) 1 else 0
                val sweepFlag = if (sweep < 0) 1 else 0
                sb.append(" A ${fmt(pixelR)} ${fmt(pixelR)} 0 $largeArc $sweepFlag ${fmt(ex)} ${fmt(ey)}")
            }
        }

        sb.append(" Z")
        return sb.toString()
    }

    // --- Shared helpers ---

    /**
     * Returns the (startX, startY, endX, endY) in model coordinates for the visible
     * portion of a geodesic based on its type.
     */
    private fun geodesicEndpoints(geo: GeodesicLike): List<Double> = when (geo.geodesicType) {
        GeodesicType.LINE -> listOf(geo.idealX1, geo.idealY1, geo.idealX2, geo.idealY2)
        GeodesicType.RAY -> listOf(geo.x1, geo.y1, geo.idealX2, geo.idealY2)
        GeodesicType.SEGMENT -> listOf(geo.x1, geo.y1, geo.x2, geo.y2)
    }

    private fun isExportable(meta: ObjectMetadata): Boolean =
        meta.visible && !meta.isLocked

    private fun fmt(v: Double): String = "%.2f".format(v)
}
