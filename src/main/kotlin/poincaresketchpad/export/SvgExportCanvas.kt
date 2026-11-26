package poincaresketchpad.export

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Writes SVG markup; the page's viewBox is the crop rectangle. */
internal class SvgExportCanvas(private val page: PageGeometry, width: Int, height: Int) : ExportCanvas {

    private val sb = StringBuilder()
    private var indent = "  "

    init {
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="${fmt(page.cropX)} ${fmt(page.cropY)} ${fmt(page.width)} ${fmt(page.height)}">""")
        sb.appendLine("""  <defs>""")
        sb.appendLine("""    <clipPath id="disk-clip">""")
        sb.appendLine("""      <circle cx="${fmt(page.centerX)}" cy="${fmt(page.centerY)}" r="${fmt(page.diskRadius)}"/>""")
        sb.appendLine("""    </clipPath>""")
        sb.appendLine("""  </defs>""")
    }

    override fun fillRect(x: Double, y: Double, width: Double, height: Double, fill: Rgb) {
        line("""<rect x="${fmt(x)}" y="${fmt(y)}" width="${fmt(width)}" height="${fmt(height)}" fill="${fill.svg}"/>""")
    }

    override fun fillCircle(cx: Double, cy: Double, r: Double, fill: Rgb) {
        line("""<circle cx="${fmt(cx)}" cy="${fmt(cy)}" r="${fmt(r)}" fill="${fill.svg}"/>""")
    }

    override fun strokeCircle(cx: Double, cy: Double, r: Double, stroke: Stroke) {
        line("""<circle cx="${fmt(cx)}" cy="${fmt(cy)}" r="${fmt(r)}" fill="none"${strokeAttrs(stroke)}/>""")
    }

    override fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke) {
        line("""<line x1="${fmt(x1)}" y1="${fmt(y1)}" x2="${fmt(x2)}" y2="${fmt(y2)}"${strokeAttrs(stroke)}/>""")
    }

    override fun sector(cx: Double, cy: Double, r: Double, startAngle: Double, sweep: Double, fill: Fill, stroke: Stroke) {
        val svgStart = -startAngle
        val svgEnd = svgStart - sweep
        val x1 = cx + r * cos(svgStart)
        val y1 = cy + r * sin(svgStart)
        val x2 = cx + r * cos(svgEnd)
        val y2 = cy + r * sin(svgEnd)
        val largeArc = if (-sweep > PI) 1 else 0
        val d = "M ${fmt(cx)} ${fmt(cy)} L ${fmt(x1)} ${fmt(y1)} A ${fmt(r)} ${fmt(r)} 0 $largeArc 0 ${fmt(x2)} ${fmt(y2)} Z"
        pathElement(d, fill, stroke)
    }

    override fun path(
        startX: Double, startY: Double, segments: List<PathSegment>,
        closed: Boolean, fill: Fill?, stroke: Stroke?
    ) {
        val d = StringBuilder("M ${fmt(startX)} ${fmt(startY)}")
        for (seg in segments) {
            when (seg) {
                is PathSegment.LineTo -> d.append(" L ${fmt(seg.endX)} ${fmt(seg.endY)}")
                is PathSegment.ArcTo -> {
                    val largeArc = if (abs(seg.sweep) > PI) 1 else 0
                    val sweepFlag = if (seg.sweep < 0) 1 else 0
                    d.append(" A ${fmt(seg.radius)} ${fmt(seg.radius)} 0 $largeArc $sweepFlag ${fmt(seg.endX)} ${fmt(seg.endY)}")
                }
            }
        }
        if (closed) d.append(" Z")
        pathElement(d.toString(), fill, stroke)
    }

    override fun text(x: Double, y: Double, content: String, fontSize: Double, color: Rgb) {
        val escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        line("""<text x="${fmt(x)}" y="${fmt(y)}" font-size="$fontSize" fill="${color.svg}">$escaped</text>""")
    }

    override fun clipToDisk(draw: () -> Unit) {
        line("""<g clip-path="url(#disk-clip)">""")
        val previous = indent
        indent = "$previous  "
        try {
            draw()
        } finally {
            indent = previous
        }
        line("</g>")
    }

    fun write(file: File) {
        sb.appendLine("</svg>")
        file.writeText(sb.toString())
    }

    private fun pathElement(d: String, fill: Fill?, stroke: Stroke?) {
        val fillAttrs = if (fill == null) """ fill="none"""" else {
            """ fill="${fill.color.svg}"""" + if (fill.opacity < 1.0) """ fill-opacity="${fill.opacity}"""" else ""
        }
        val strokeAttrs = if (stroke == null) """ stroke="none"""" else strokeAttrs(stroke)
        line("""<path d="$d"$fillAttrs$strokeAttrs/>""")
    }

    private fun strokeAttrs(stroke: Stroke): String {
        val sb = StringBuilder(""" stroke="${stroke.color.svg}"""")
        if (stroke.opacity < 1.0) sb.append(""" stroke-opacity="${stroke.opacity}"""")
        sb.append(""" stroke-width="${stroke.svgWidth}"""")
        stroke.dash?.let { dash -> sb.append(""" stroke-dasharray="${dash.joinToString(",") { compact(it) }}"""") }
        return sb.toString()
    }

    private fun compact(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    private fun line(s: String) {
        sb.append(indent).appendLine(s)
    }
}
