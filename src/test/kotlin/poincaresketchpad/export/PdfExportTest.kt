package poincaresketchpad.export

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import poincaresketchpad.APP_VERSION
import poincaresketchpad.geometry.FreePoint
import poincaresketchpad.model.GeometricObjectManager
import java.awt.image.BufferedImage
import java.io.File

/** Parses PDF exports back with PDFBox and checks page size, metadata, text, fonts, and rendered pixels. */
class PdfExportTest {

    @TempDir
    lateinit var tempDir: File

    private fun settings(
        method: ExportMethod = ExportMethod.CANVAS,
        width: Int = 400,
        height: Int = 400,
        scale: Double = 1.0,
        transparent: Boolean = false
    ) = ExportSettings(method, ExportFormat.PDF, width, height, scale, transparent, includeGrid = false)

    private fun export(
        settings: ExportSettings,
        manager: GeometricObjectManager = ExportRendererSnapshotTest.buildFixture(),
        name: String = "diagram"
    ): Pair<File, List<String>> {
        val file = File(tempDir, "$name.pdf")
        val warnings = ExportRenderer.export(settings, manager, file)
        return file to warnings
    }

    private fun render(doc: PDDocument): BufferedImage = PDFRenderer(doc).renderImage(0, 1f, ImageType.ARGB)

    /** Screen pixel of model point (mx, my) within the exported page. */
    private fun pixel(settings: ExportSettings, mx: Double, my: Double): Pair<Int, Int> {
        val page = PageGeometry(settings, 0.03)
        return (page.x(mx) - page.cropX).toInt() to (page.y(my) - page.cropY).toInt()
    }

    private fun rgb(image: BufferedImage, xy: Pair<Int, Int>): Int = image.getRGB(xy.first, xy.second)

    @Test
    fun `page size, metadata, labels and embedded font`() {
        val (file, warnings) = export(settings(), name = "my diagram")
        assertTrue(warnings.isEmpty(), "unexpected warnings: $warnings")
        Loader.loadPDF(file).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val box = doc.getPage(0).mediaBox
            assertEquals(400f, box.width)
            assertEquals(400f, box.height)

            assertEquals("my diagram", doc.documentInformation.title)
            assertEquals("Poincar\u00E9 Sketchpad $APP_VERSION", doc.documentInformation.creator)

            val text = PDFTextStripper().getText(doc)
            for (label in listOf("A", "B<&>", "l", "c", "\u03B1", "R")) {
                assertTrue(text.contains(label), "missing label '$label' in: $text")
            }
            assertTrue(!text.contains("H") && !text.contains("L") && !text.contains("E"), "hidden/locked label exported: $text")

            val resources = doc.getPage(0).resources
            val fonts = resources.fontNames.map { resources.getFont(it) }
            assertEquals(1, fonts.size)
            val font = fonts.single() as PDType0Font
            assertTrue(Regex("[A-Z]{6}\\+DejaVuSans").matches(font.name), "font name ${font.name}")
            assertTrue(font.isEmbedded)
            assertTrue(font.descendantFont.fontDescriptor.fontFile2 != null)
        }
    }

    @Test
    fun `custom export has non-square page`() {
        val (file, _) = export(settings(ExportMethod.CUSTOM, 500, 300, 0.6))
        Loader.loadPDF(file).use { doc ->
            val box = doc.getPage(0).mediaBox
            assertEquals(500f, box.width)
            assertEquals(300f, box.height)
            val image = render(doc)
            assertEquals(500, image.width)
            assertEquals(300, image.height)
        }
    }

    @Test
    fun `unsupported label characters become question marks with a warning`() {
        val manager = GeometricObjectManager()
        val p = manager.addObject { FreePoint(it, 0.0, 0.0) }
        manager.getMetadata(p.id)!!.apply { label = "P\u4E2D\uD55C"; labelVisible = true }
        val (file, warnings) = export(settings(), manager)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("?"))
        Loader.loadPDF(file).use { doc ->
            assertEquals("P??", PDFTextStripper().getText(doc).trim())
        }
    }

    @Test
    fun `opaque export renders background, disk and points`() {
        val s = settings()
        val (file, _) = export(s)
        Loader.loadPDF(file).use { doc ->
            val image = render(doc)
            assertEquals(0xFFE8E8E8.toInt(), image.getRGB(2, 2), "gray exterior")
            assertEquals(0xFFFFFFFF.toInt(), rgb(image, pixel(s, -0.1, 0.8)), "white disk interior")
            assertEquals(0xFFF44336.toInt(), rgb(image, pixel(s, 0.1, 0.2)), "red point A")
            assertEquals(0xFF000000.toInt(), rgb(image, pixel(s, -0.4, 0.3)), "black point B")
        }
    }

    @Test
    fun `transparent export leaves the background empty`() {
        val s = settings(transparent = true)
        val (file, _) = export(s)
        Loader.loadPDF(file).use { doc ->
            val image = render(doc)
            assertEquals(0, image.getRGB(2, 2) ushr 24, "exterior alpha")
            assertEquals(0, rgb(image, pixel(s, -0.1, 0.8)) ushr 24, "disk interior alpha")
            assertEquals(0xFFF44336.toInt(), rgb(image, pixel(s, 0.1, 0.2)), "red point A")
        }
    }
}
