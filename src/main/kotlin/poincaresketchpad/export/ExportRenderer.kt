package poincaresketchpad.export

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.ObjectMetadata
import poincaresketchpad.model.StyleUtils
import poincaresketchpad.model.getObjectAnchor
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class ExportMethod { CANVAS, CUSTOM }
enum class ExportFormat { PNG, SVG, PDF }

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

    private const val FONT_SIZE = 12.0
    private const val MARGIN_FRACTION = 0.03
    private val BACKGROUND = Rgb.hex("#E8E8E8")
    private val DISK = Rgb(1.0, 1.0, 1.0, "white")
    private val GRID = Rgb(0.85, 0.85, 0.85, "#d9d9d9")
    private val LABEL = Rgb(0.0, 0.0, 0.0, "black")
    private val DASH = listOf(6.0, 4.0)

    /**
     * Writes the diagram to [file] and returns user-facing warnings about the output
     * (for example, label characters the PDF font cannot display). Empty for PNG and SVG.
     */
    fun export(settings: ExportSettings, manager: GeometricObjectManager, file: File): List<String> {
        val page = PageGeometry(settings, MARGIN_FRACTION)
        return when (settings.format) {
            ExportFormat.PNG -> {
                val canvas = PngExportCanvas(page)
                draw(canvas, page, settings, manager)
                canvas.write(file, if (settings.transparentBackground) null else BACKGROUND)
                emptyList()
            }
            ExportFormat.SVG -> {
                val canvas = SvgExportCanvas(page, settings.width, settings.height)
                draw(canvas, page, settings, manager)
                canvas.write(file)
                emptyList()
            }
            ExportFormat.PDF -> PdfExportCanvas(page).use { canvas ->
                draw(canvas, page, settings, manager)
                canvas.write(file)
                canvas.warnings
            }
        }
    }

    private fun draw(canvas: ExportCanvas, page: PageGeometry, settings: ExportSettings, manager: GeometricObjectManager) {
        if (!settings.transparentBackground) {
            canvas.fillRect(page.cropX, page.cropY, page.width, page.height, BACKGROUND)
            canvas.fillCircle(page.centerX, page.centerY, page.diskRadius, DISK)
        }
        canvas.clipToDisk {
            if (settings.includeGrid) drawGrid(canvas, page)
            drawObjects(canvas, page, manager)
        }
    }

    private fun drawGrid(canvas: ExportCanvas, page: PageGeometry) {
        val stroke = Stroke(GRID, 0.5, svgWidth = "0.5")

        // Concentric hyperbolic circles at 1.0 hyperbolic unit intervals
        var n = 1
        while (true) {
            val eucR = HyperbolicUtils.hyperbolicRadiusToEuclidean(n * 1.0)
            if (eucR >= 1.0) break
            canvas.strokeCircle(page.centerX, page.centerY, eucR * page.diskRadius, stroke)
            n++
        }

        // 6 radial diameters at 30-degree intervals
        for (i in 0 until 6) {
            val angle = i * PI / 6.0
            canvas.strokeLine(
                page.x(cos(angle)), page.y(sin(angle)),
                page.x(-cos(angle)), page.y(-sin(angle)),
                stroke
            )
        }
    }

    private fun drawObjects(canvas: ExportCanvas, page: PageGeometry, manager: GeometricObjectManager) {
        // Pass 1: Regions
        for (obj in manager.allObjects()) {
            if (obj !is Region || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || obj.vertices.size < 3) continue
            val segments = regionSegments(obj, page)
            val (sx, sy) = obj.vertices[0]
            canvas.path(page.x(sx), page.y(sy), segments, closed = true, fill = Fill(color(meta), 0.3), stroke = null)
        }

        // Pass 2: Angles
        for (obj in manager.allObjects()) {
            if (obj !is Angle || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            val c = color(meta)
            // Model-space angle = screen y-up angle (the y-flips cancel)
            canvas.sector(
                page.x(obj.vertexX), page.y(obj.vertexY), StyleUtils.angleArcRadius(meta.size),
                obj.startAngleRadians, obj.sweepAngleRadians,
                Fill(c, 0.3), Stroke(c, 1.0, opacity = 0.7, svgWidth = "1")
            )
        }

        // Pass 3: Geodesics and Circles
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !obj.isDefined) continue
            val stroke = Stroke(
                color(meta), StyleUtils.strokeThickness(meta.strokeThickness),
                dash = if (meta.strokeStyle == "dashed") DASH else null
            )
            if (obj is GeodesicLike) {
                drawGeodesic(canvas, page, obj, stroke)
            } else if (obj is Circle) {
                canvas.strokeCircle(
                    page.x(obj.euclideanCenterX), page.y(obj.euclideanCenterY),
                    obj.euclideanRadius * page.diskRadius, stroke
                )
            }
        }

        // Pass 4: Points
        for (obj in manager.allObjects()) {
            if (obj !is PointLike || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            canvas.fillCircle(page.x(obj.x), page.y(obj.y), StyleUtils.pointRadius(meta.size), color(meta))
        }

        // Pass 5: Labels
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !meta.labelVisible || meta.label == null) continue
            if (!obj.isDefined) continue
            val anchor = getObjectAnchor(obj, meta) ?: continue
            canvas.text(
                page.x(anchor.first) + meta.labelOffsetX,
                page.y(anchor.second) + meta.labelOffsetY,
                meta.label!!, FONT_SIZE, LABEL
            )
        }
    }

    private fun drawGeodesic(canvas: ExportCanvas, page: PageGeometry, geo: GeodesicLike, stroke: Stroke) {
        val (startX, startY, endX, endY) = geodesicEndpoints(geo)
        if (geo.isDiameter) {
            canvas.strokeLine(page.x(startX), page.y(startY), page.x(endX), page.y(endY), stroke)
        } else {
            val arc = arcSegment(page, startX, startY, endX, endY, geo.arcCenterX, geo.arcCenterY, geo.arcRadius)
            canvas.path(page.x(startX), page.y(startY), listOf(arc), closed = false, fill = null, stroke = stroke)
        }
    }

    private fun regionSegments(region: Region, page: PageGeometry): List<PathSegment> {
        val n = region.vertices.size
        return (0 until n).map { i ->
            val (cx, cy) = region.vertices[i]
            val (nx, ny) = region.vertices[(i + 1) % n]
            val arc = HyperbolicUtils.geodesicArc(cx, cy, nx, ny)
            if (arc.isDiameter) {
                PathSegment.LineTo(page.x(nx), page.y(ny))
            } else {
                arcSegment(page, cx, cy, nx, ny, arc.centerX, arc.centerY, arc.radius)
            }
        }
    }

    /**
     * Arc from (fromX, fromY) to (toX, toY) in model coordinates along the Euclidean circle
     * with the given center and radius, taking the shorter way around.
     */
    private fun arcSegment(
        page: PageGeometry,
        fromX: Double, fromY: Double, toX: Double, toY: Double,
        centerX: Double, centerY: Double, radius: Double
    ): PathSegment.ArcTo {
        val startAngle = atan2(fromY - centerY, fromX - centerX)
        val endAngle = atan2(toY - centerY, toX - centerX)
        var sweep = endAngle - startAngle
        while (sweep < -PI) sweep += 2 * PI
        while (sweep > PI) sweep -= 2 * PI
        return PathSegment.ArcTo(
            page.x(toX), page.y(toY),
            page.x(centerX), page.y(centerY), radius * page.diskRadius,
            startAngle, sweep
        )
    }

    /**
     * Returns the (startX, startY, endX, endY) in model coordinates for the visible
     * portion of a geodesic based on its type.
     */
    private fun geodesicEndpoints(geo: GeodesicLike): List<Double> = when (geo.geodesicType) {
        GeodesicType.LINE -> listOf(geo.idealX1, geo.idealY1, geo.idealX2, geo.idealY2)
        GeodesicType.RAY -> listOf(geo.x1, geo.y1, geo.idealX2, geo.idealY2)
        GeodesicType.SEGMENT -> listOf(geo.x1, geo.y1, geo.x2, geo.y2)
    }

    private fun color(meta: ObjectMetadata): Rgb = Rgb.hex(StyleUtils.colorHex(meta.color))

    private fun isExportable(meta: ObjectMetadata): Boolean =
        meta.visible && !meta.isLocked
}
