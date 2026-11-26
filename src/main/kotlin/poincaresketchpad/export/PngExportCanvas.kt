package poincaresketchpad.export

import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Rectangle2D
import javafx.scene.Group
import javafx.scene.SnapshotParameters
import javafx.scene.paint.Color
import javafx.scene.shape.*
import javafx.scene.text.Font
import javafx.scene.text.Text
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine

/** Builds a JavaFX scene graph and writes it with `Node.snapshot`. Must run on the FX thread. */
internal class PngExportCanvas(private val page: PageGeometry) : ExportCanvas {

    private val root = Group()
    private var target = root

    override fun fillRect(x: Double, y: Double, width: Double, height: Double, fill: Rgb) {
        target.children.add(Rectangle(x, y, width, height).apply { this.fill = fx(fill) })
    }

    override fun fillCircle(cx: Double, cy: Double, r: Double, fill: Rgb) {
        target.children.add(FxCircle(cx, cy, r).apply {
            this.fill = fx(fill)
            stroke = null
        })
    }

    override fun strokeCircle(cx: Double, cy: Double, r: Double, stroke: Stroke) {
        val circle = FxCircle(cx, cy, r)
        circle.fill = Color.TRANSPARENT
        applyStroke(circle, stroke)
        target.children.add(circle)
    }

    override fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke) {
        val line = FxLine(x1, y1, x2, y2)
        applyStroke(line, stroke)
        target.children.add(line)
    }

    override fun sector(cx: Double, cy: Double, r: Double, startAngle: Double, sweep: Double, fill: Fill, stroke: Stroke) {
        val arc = Arc(cx, cy, r, r, Math.toDegrees(startAngle), Math.toDegrees(sweep))
        arc.type = ArcType.ROUND
        arc.fill = fx(fill.color, fill.opacity)
        applyStroke(arc, stroke)
        target.children.add(arc)
    }

    override fun path(
        startX: Double, startY: Double, segments: List<PathSegment>,
        closed: Boolean, fill: Fill?, stroke: Stroke?
    ) {
        val path = Path()
        path.elements.add(MoveTo(startX, startY))
        for (seg in segments) {
            path.elements.add(
                when (seg) {
                    is PathSegment.LineTo -> LineTo(seg.endX, seg.endY)
                    is PathSegment.ArcTo -> ArcTo(
                        seg.radius, seg.radius, 0.0, seg.endX, seg.endY,
                        abs(seg.sweep) > PI, seg.sweep < 0
                    )
                }
            )
        }
        if (closed) path.elements.add(ClosePath())
        path.fill = if (fill != null) fx(fill.color, fill.opacity) else Color.TRANSPARENT
        if (stroke != null) applyStroke(path, stroke) else path.stroke = null
        target.children.add(path)
    }

    override fun text(x: Double, y: Double, content: String, fontSize: Double, color: Rgb) {
        val text = Text(content)
        text.font = Font.font(fontSize)
        text.x = x
        text.y = y
        text.fill = fx(color)
        target.children.add(text)
    }

    override fun clipToDisk(draw: () -> Unit) {
        val group = Group()
        group.clip = FxCircle(page.centerX, page.centerY, page.diskRadius)
        val previous = target
        target = group
        try {
            draw()
        } finally {
            target = previous
        }
        target.children.add(group)
    }

    fun write(file: File, background: Rgb?) {
        val params = SnapshotParameters()
        params.fill = if (background == null) Color.TRANSPARENT else fx(background)
        params.viewport = Rectangle2D(page.cropX, page.cropY, page.width, page.height)
        val image = root.snapshot(params, null)
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file)
    }

    private fun applyStroke(shape: Shape, stroke: Stroke) {
        shape.stroke = fx(stroke.color, stroke.opacity)
        shape.strokeWidth = stroke.width
        stroke.dash?.let { shape.strokeDashArray.addAll(it) }
    }

    private fun fx(c: Rgb, opacity: Double = 1.0): Color = Color.color(c.red, c.green, c.blue, opacity)
}
