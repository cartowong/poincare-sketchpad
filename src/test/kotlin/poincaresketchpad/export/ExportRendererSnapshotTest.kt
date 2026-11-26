package poincaresketchpad.export

import javafx.application.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Golden-file snapshot tests for [ExportRenderer] PNG and SVG output.
 *
 * Goldens live in `src/test/resources/export-golden/`. SVG is compared as exact text,
 * PNG pixel-by-pixel (exact ARGB match) with labels hidden, since label glyphs
 * rasterize differently per OS. The test JVM pins `prism.order=sw` (see
 * build.gradle.kts) because the GPU and software pipelines antialias differently.
 *
 * To regenerate the goldens after an intentional rendering change, run:
 *
 *     ./gradlew test --tests '*ExportRendererSnapshotTest*' -PupdateGoldens
 *
 * and review the resulting diff before committing.
 */
class ExportRendererSnapshotTest {

    data class Case(
        val name: String,
        val method: ExportMethod,
        val width: Int,
        val height: Int,
        val scale: Double,
        val transparent: Boolean,
        val grid: Boolean
    ) {
        override fun toString() = name
    }

    companion object {
        private val goldenDir = File("src/test/resources/export-golden")
        private val updateGoldens = System.getProperty("updateGoldens") == "true"

        @JvmStatic
        fun cases(): List<Case> = listOf(
            Case("canvas_opaque_nogrid", ExportMethod.CANVAS, 400, 400, 1.0, transparent = false, grid = false),
            Case("canvas_transparent_grid", ExportMethod.CANVAS, 400, 400, 1.0, transparent = true, grid = true),
            Case("custom_wide_opaque_grid", ExportMethod.CUSTOM, 500, 300, 0.6, transparent = false, grid = true),
            Case("custom_tall_transparent_nogrid", ExportMethod.CUSTOM, 300, 450, 0.75, transparent = true, grid = false)
        )

        @JvmStatic
        @BeforeAll
        fun startToolkit() {
            val latch = CountDownLatch(1)
            try {
                Platform.startup { latch.countDown() }
            } catch (e: IllegalStateException) {
                latch.countDown()
            }
            Platform.setImplicitExit(false)
            check(latch.await(30, TimeUnit.SECONDS)) { "JavaFX toolkit did not start" }
        }

        fun buildFixture(): GeometricObjectManager {
            val m = GeometricObjectManager()
            val a = m.addObject { FreePoint(it, 0.1, 0.2) }
            val b = m.addObject { FreePoint(it, -0.4, 0.3) }
            val c = m.addObject { FreePoint(it, 0.3, -0.5) }
            val o = m.addObject { FreePoint(it, 0.0, 0.0) }
            val e = m.addObject { FreePoint(it, 0.5, 0.0) }
            val f = m.addObject { FreePoint(it, -0.3, -0.3) }

            m.getMetadata(a.id)!!.apply { label = "A"; labelVisible = true; color = "red"; size = "large" }
            m.getMetadata(b.id)!!.apply { label = "B<&>"; labelVisible = true; labelOffsetX = -20.0; labelOffsetY = 15.0 }
            m.getMetadata(c.id)!!.apply { color = "green"; size = "small" }
            m.getMetadata(e.id)!!.apply { label = "E"; labelVisible = false }

            val line = m.addObject { Line(it, a.id, b.id) }
            m.getMetadata(line.id)!!.apply {
                color = "green"; strokeStyle = "dashed"; strokeThickness = "thick"
                label = "l"; labelVisible = true
            }
            val diaRay = m.addObject { Ray(it, o.id, f.id) }
            m.getMetadata(diaRay.id)!!.apply { color = "purple"; strokeThickness = "thin" }
            val arcRay = m.addObject { Ray(it, b.id, c.id) }
            m.getMetadata(arcRay.id)!!.apply { color = "brown" }
            m.addObject { Segment(it, o.id, e.id) }
            val arcSeg = m.addObject { Segment(it, a.id, c.id) }
            m.getMetadata(arcSeg.id)!!.apply { strokeStyle = "dashed"; strokeThickness = "thin" }
            val diaLine = m.addObject { Line(it, e.id, o.id) }
            m.getMetadata(diaLine.id)!!.apply { color = "yellow"; strokeStyle = "dashed" }

            val circle = m.addObject { Circle(it, a.id, o.id) }
            m.getMetadata(circle.id)!!.apply {
                color = "purple"; strokeStyle = "dashed"; strokeThickness = "thick"
                label = "c"; labelVisible = true
            }

            val angle = m.addObject { Angle(it, b.id, a.id, c.id) }
            m.getMetadata(angle.id)!!.apply { color = "brown"; size = "large"; label = "α"; labelVisible = true }
            val reflex = m.addObject { Angle(it, c.id, o.id, e.id) }
            m.getMetadata(reflex.id)!!.apply { color = "green"; size = "small" }

            val region = m.addObject { Region(it, listOf(o.id, e.id, c.id)) }
            m.getMetadata(region.id)!!.apply { color = "red"; label = "R"; labelVisible = true }
            val region2 = m.addObject { Region(it, listOf(a.id, b.id, f.id, c.id)) }
            m.getMetadata(region2.id)!!.apply { color = "yellow" }

            val hiddenPoint = m.addObject { FreePoint(it, -0.6, 0.1) }
            m.getMetadata(hiddenPoint.id)!!.apply { visible = false; label = "H"; labelVisible = true }
            val hiddenCircle = m.addObject { Circle(it, b.id, f.id) }
            m.getMetadata(hiddenCircle.id)!!.visible = false
            val lockedPoint = m.addObject { FreePoint(it, 0.6, 0.4) }
            m.getMetadata(lockedPoint.id)!!.apply { isLocked = true; label = "L"; labelVisible = true }
            val lockedLine = m.addObject { Line(it, lockedPoint.id, c.id) }
            m.getMetadata(lockedLine.id)!!.isLocked = true

            return m
        }

        private fun settings(case: Case, format: ExportFormat) = ExportSettings(
            method = case.method,
            format = format,
            width = case.width,
            height = case.height,
            scale = case.scale,
            transparentBackground = case.transparent,
            includeGrid = case.grid
        )

        private fun runOnFxThread(block: () -> Unit) {
            val latch = CountDownLatch(1)
            var error: Throwable? = null
            Platform.runLater {
                try {
                    block()
                } catch (t: Throwable) {
                    error = t
                } finally {
                    latch.countDown()
                }
            }
            check(latch.await(60, TimeUnit.SECONDS)) { "FX task timed out" }
            error?.let { throw it }
        }
    }

    // Kept under build/ (not a temp dir) so CI can upload them when a snapshot differs.
    private val tempDir = File("build/snapshot-actual").also { it.mkdirs() }

    @ParameterizedTest
    @MethodSource("cases")
    fun `svg export matches golden`(case: Case) {
        val out = File(tempDir, "${case.name}.svg")
        ExportRenderer.export(settings(case, ExportFormat.SVG), buildFixture(), out)
        val golden = File(goldenDir, "${case.name}.svg")
        if (updateGoldens) {
            golden.parentFile.mkdirs()
            out.copyTo(golden, overwrite = true)
            return
        }
        if (!golden.exists()) fail<Unit>("Missing golden ${golden.path}; regenerate with -PupdateGoldens")
        assertEquals(golden.readText(), out.readText(), "SVG output differs from ${golden.path}")
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `png export matches golden`(case: Case) {
        val out = File(tempDir, "${case.name}.png")
        // Labels are off: glyph rasterization differs per OS and macOS image, and
        // label placement is already pinned by the SVG goldens.
        val manager = buildFixture().also { m -> m.allObjects().forEach { m.getMetadata(it.id)!!.labelVisible = false } }
        runOnFxThread { ExportRenderer.export(settings(case, ExportFormat.PNG), manager, out) }
        val golden = File(goldenDir, "${case.name}.png")
        if (updateGoldens) {
            golden.parentFile.mkdirs()
            out.copyTo(golden, overwrite = true)
            return
        }
        if (!golden.exists()) fail<Unit>("Missing golden ${golden.path}; regenerate with -PupdateGoldens")

        val expected = ImageIO.read(golden)
        val actual = ImageIO.read(out)
        assertEquals(expected.width, actual.width, "PNG width")
        assertEquals(expected.height, actual.height, "PNG height")
        var mismatches = 0
        var first: String? = null
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val e = expected.getRGB(x, y)
                val a = actual.getRGB(x, y)
                if (e != a) {
                    if (first == null) first = "($x,$y) expected #%08X got #%08X".format(e, a)
                    mismatches++
                }
            }
        }
        assertEquals(0, mismatches, "PNG differs from ${golden.path} in $mismatches pixels; first at $first")
    }
}
