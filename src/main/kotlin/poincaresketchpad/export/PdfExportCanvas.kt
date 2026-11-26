package poincaresketchpad.export

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import poincaresketchpad.APP_VERSION
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Writes a single-page vector PDF whose page is the crop rectangle, [PageGeometry.width] x
 * [PageGeometry.height] points. A page-level transform flips y and applies the crop offset,
 * so all drawing calls use the walker's screen coordinates directly.
 */
internal class PdfExportCanvas(private val page: PageGeometry) : ExportCanvas, java.io.Closeable {

    private val doc = PDDocument()
    private val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
    private val cs: PDPageContentStream
    private var font: PDType0Font? = null
    private val alphaStates = HashMap<Pair<Double, Double>, PDExtendedGraphicsState>()
    private val replacedLabels = LinkedHashSet<String>()

    /** User-facing messages about label characters that were replaced with `?`. */
    val warnings: List<String>
        get() = replacedLabels.map {
            "Label \"$it\" contains characters that the PDF label font (DejaVu Sans) cannot display; " +
                "they were replaced with \"?\"."
        }

    init {
        doc.addPage(pdPage)
        cs = PDPageContentStream(doc, pdPage)
        cs.transform(Matrix(1f, 0f, 0f, -1f, (-page.cropX).toFloat(), (page.height + page.cropY).toFloat()))
    }

    override fun fillRect(x: Double, y: Double, width: Double, height: Double, fill: Rgb) {
        cs.setNonStrokingColor(fill.red.toFloat(), fill.green.toFloat(), fill.blue.toFloat())
        cs.addRect(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
        cs.fill()
    }

    override fun fillCircle(cx: Double, cy: Double, r: Double, fill: Rgb) {
        cs.setNonStrokingColor(fill.red.toFloat(), fill.green.toFloat(), fill.blue.toFloat())
        circlePath(cx, cy, r)
        cs.fill()
    }

    override fun strokeCircle(cx: Double, cy: Double, r: Double, stroke: Stroke) {
        withStyle(null, stroke) {
            circlePath(cx, cy, r)
            cs.stroke()
        }
    }

    override fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke) {
        withStyle(null, stroke) {
            cs.moveTo(x1.toFloat(), y1.toFloat())
            cs.lineTo(x2.toFloat(), y2.toFloat())
            cs.stroke()
        }
    }

    override fun sector(cx: Double, cy: Double, r: Double, startAngle: Double, sweep: Double, fill: Fill, stroke: Stroke) {
        withStyle(fill, stroke) {
            cs.moveTo(cx.toFloat(), cy.toFloat())
            cs.lineTo((cx + r * cos(startAngle)).toFloat(), (cy - r * sin(startAngle)).toFloat())
            arcCurves(cx, cy, r, startAngle, sweep)
            cs.closeAndFillAndStroke()
        }
    }

    override fun path(
        startX: Double, startY: Double, segments: List<PathSegment>,
        closed: Boolean, fill: Fill?, stroke: Stroke?
    ) {
        if (fill == null && stroke == null) return
        withStyle(fill, stroke) {
            cs.moveTo(startX.toFloat(), startY.toFloat())
            for (seg in segments) {
                when (seg) {
                    is PathSegment.LineTo -> cs.lineTo(seg.endX.toFloat(), seg.endY.toFloat())
                    is PathSegment.ArcTo -> arcCurves(seg.centerX, seg.centerY, seg.radius, seg.startAngle, seg.sweep)
                }
            }
            if (closed) cs.closePath()
            when {
                fill != null && stroke != null -> cs.fillAndStroke()
                fill != null -> cs.fill()
                else -> cs.stroke()
            }
        }
    }

    override fun text(x: Double, y: Double, content: String, fontSize: Double, color: Rgb) {
        val f = font ?: loadFont().also { font = it }
        val safe = encodable(f, content)
        if (safe != content) replacedLabels.add(content)
        cs.beginText()
        cs.setFont(f, fontSize.toFloat())
        cs.setNonStrokingColor(color.red.toFloat(), color.green.toFloat(), color.blue.toFloat())
        // Undo the page-level y-flip so glyphs are upright
        cs.setTextMatrix(Matrix(1f, 0f, 0f, -1f, x.toFloat(), y.toFloat()))
        cs.showText(safe)
        cs.endText()
    }

    override fun clipToDisk(draw: () -> Unit) {
        cs.saveGraphicsState()
        circlePath(page.centerX, page.centerY, page.diskRadius)
        cs.clip()
        try {
            draw()
        } finally {
            cs.restoreGraphicsState()
        }
    }

    fun write(file: File) {
        cs.close()
        doc.documentInformation.title = file.nameWithoutExtension
        doc.documentInformation.creator = "Poincar\u00E9 Sketchpad $APP_VERSION"
        doc.save(file)
    }

    override fun close() = doc.close()

    private fun loadFont(): PDType0Font {
        val stream = PdfExportCanvas::class.java.getResourceAsStream(FONT_RESOURCE)
            ?: error("Missing font resource $FONT_RESOURCE")
        return stream.use { PDType0Font.load(doc, it) }
    }

    private fun encodable(f: PDType0Font, s: String): String {
        val sb = StringBuilder()
        s.codePoints().forEach { cp ->
            val ch = String(Character.toChars(cp))
            val ok = try {
                f.encode(ch)
                true
            } catch (e: IllegalArgumentException) {
                false
            }
            sb.append(if (ok) ch else "?")
        }
        return sb.toString()
    }

    private inline fun withStyle(fill: Fill?, stroke: Stroke?, block: () -> Unit) {
        cs.saveGraphicsState()
        val fillAlpha = fill?.opacity ?: 1.0
        val strokeAlpha = stroke?.opacity ?: 1.0
        if (fillAlpha < 1.0 || strokeAlpha < 1.0) {
            cs.setGraphicsStateParameters(alphaStates.getOrPut(fillAlpha to strokeAlpha) {
                PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = fillAlpha.toFloat()
                    strokingAlphaConstant = strokeAlpha.toFloat()
                }
            })
        }
        if (fill != null) {
            cs.setNonStrokingColor(fill.color.red.toFloat(), fill.color.green.toFloat(), fill.color.blue.toFloat())
        }
        if (stroke != null) {
            cs.setStrokingColor(stroke.color.red.toFloat(), stroke.color.green.toFloat(), stroke.color.blue.toFloat())
            cs.setLineWidth(stroke.width.toFloat())
            stroke.dash?.let { dash -> cs.setLineDashPattern(dash.map { it.toFloat() }.toFloatArray(), 0f) }
        }
        try {
            block()
        } finally {
            cs.restoreGraphicsState()
        }
    }

    private fun circlePath(cx: Double, cy: Double, r: Double) {
        cs.moveTo((cx + r).toFloat(), cy.toFloat())
        arcCurves(cx, cy, r, 0.0, 2 * PI)
        cs.closePath()
    }

    /**
     * Appends cubic Béziers (each spanning at most 90°) approximating the arc from [startAngle]
     * through [sweep]; the current point must already be at the arc's start.
     */
    private fun arcCurves(cx: Double, cy: Double, r: Double, startAngle: Double, sweep: Double) {
        val n = ceil(abs(sweep) / (PI / 2) - 1e-9).toInt().coerceAtLeast(1)
        val delta = sweep / n
        val k = 4.0 / 3.0 * tan(delta / 4.0)
        var a0 = startAngle
        for (i in 0 until n) {
            val a1 = a0 + delta
            val x0 = cx + r * cos(a0)
            val y0 = cy - r * sin(a0)
            val x3 = cx + r * cos(a1)
            val y3 = cy - r * sin(a1)
            // Tangent of (cx + r cos t, cy - r sin t) is (-r sin t, -r cos t)
            val x1 = x0 - k * r * sin(a0)
            val y1 = y0 - k * r * cos(a0)
            val x2 = x3 + k * r * sin(a1)
            val y2 = y3 + k * r * cos(a1)
            cs.curveTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x3.toFloat(), y3.toFloat())
            a0 = a1
        }
    }

    private companion object {
        const val FONT_RESOURCE = "/fonts/DejaVuSans.ttf"
    }
}
