package poincaresketchpad.export

import kotlin.math.max

/** Opaque RGB color with components in [0, 1]; [svg] is the exact SVG paint string. */
internal class Rgb(val red: Double, val green: Double, val blue: Double, val svg: String) {
    companion object {
        fun hex(hex: String): Rgb {
            val v = hex.removePrefix("#")
            return Rgb(
                v.substring(0, 2).toInt(16) / 255.0,
                v.substring(2, 4).toInt(16) / 255.0,
                v.substring(4, 6).toInt(16) / 255.0,
                hex
            )
        }
    }
}

internal class Fill(val color: Rgb, val opacity: Double = 1.0)

/** [svgWidth] is the exact SVG text for [width]; [dash] is an on/off pattern in pixels. */
internal class Stroke(
    val color: Rgb,
    val width: Double,
    val opacity: Double = 1.0,
    val dash: List<Double>? = null,
    val svgWidth: String = fmt(width)
)

/**
 * Path segment in screen pixels. Arc angles are in radians using the y-up convention
 * (counterclockwise positive, as in JavaFX `Arc`): a point at angle t on the arc is
 * `(centerX + radius * cos(t), centerY - radius * sin(t))`.
 */
internal sealed interface PathSegment {
    val endX: Double
    val endY: Double

    class LineTo(override val endX: Double, override val endY: Double) : PathSegment

    class ArcTo(
        override val endX: Double, override val endY: Double,
        val centerX: Double, val centerY: Double, val radius: Double,
        val startAngle: Double, val sweep: Double
    ) : PathSegment
}

/** Drawing backend used by [ExportRenderer]. All coordinates are screen pixels in page space. */
internal interface ExportCanvas {
    fun fillRect(x: Double, y: Double, width: Double, height: Double, fill: Rgb)
    fun fillCircle(cx: Double, cy: Double, r: Double, fill: Rgb)
    fun strokeCircle(cx: Double, cy: Double, r: Double, stroke: Stroke)
    fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke)
    /** Pie slice; angles follow the [PathSegment.ArcTo] convention. */
    fun sector(cx: Double, cy: Double, r: Double, startAngle: Double, sweep: Double, fill: Fill, stroke: Stroke)
    fun path(startX: Double, startY: Double, segments: List<PathSegment>, closed: Boolean, fill: Fill?, stroke: Stroke?)
    fun text(x: Double, y: Double, content: String, fontSize: Double, color: Rgb)
    /** Runs [draw] with output clipped to the disk. */
    fun clipToDisk(draw: () -> Unit)
}

/**
 * Page layout shared by all formats. The disk is drawn on a square of side [size]
 * (disk plus margin); the exported image is the centered [width] x [height] crop at ([cropX], [cropY]).
 */
internal class PageGeometry(settings: ExportSettings, marginFraction: Double) {
    val width = settings.width.toDouble()
    val height = settings.height.toDouble()
    val diskDiameter = max(width, height) / settings.scale
    val size = diskDiameter + diskDiameter * marginFraction * 2
    val diskRadius = diskDiameter / 2.0
    val centerX = size / 2.0
    val centerY = size / 2.0
    val cropX = (size - width) / 2.0
    val cropY = (size - height) / 2.0

    fun x(mx: Double): Double = centerX + mx * diskRadius
    fun y(my: Double): Double = centerY - my * diskRadius
}

internal fun fmt(v: Double): String = "%.2f".format(v)
