package poincaresketchpad.export

import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.persistence.DiagramSerializer
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import java.io.File

/**
 * Headless JavaFX application that loads a diagram and exports it in all
 * method/format combinations (4 files total), then exits.
 *
 * Usage: --export <diagram.json> [outputDir]
 */
class HeadlessExportApp : Application() {

    override fun start(stage: Stage) {
        try {
            val diagramPath = exportArgs[0]
            val outputDir = if (exportArgs.size > 1) exportArgs[1] else "exports"

            val diagramFile = File(diagramPath)
            require(diagramFile.exists()) { "Diagram file not found: $diagramPath" }

            val outDir = File(outputDir)
            outDir.mkdirs()

            val json = diagramFile.readText()
            val manager = GeometricObjectManager()
            DiagramSerializer.deserialize(json, manager)

            val baseName = diagramFile.nameWithoutExtension

            // Canvas: 700px diameter
            for (format in ExportFormat.entries) {
                val settings = ExportSettings(
                    method = ExportMethod.CANVAS,
                    format = format,
                    width = 700,
                    height = 700,
                    scale = 1.0,
                    transparentBackground = false,
                    includeGrid = false
                )
                val ext = format.name.lowercase()
                val file = File(outDir, "${baseName}_canvas.$ext")
                ExportRenderer.export(settings, manager, file)
                println("Exported: ${file.path}")
            }

            // Custom: 1920px diameter
            for (format in ExportFormat.entries) {
                val settings = ExportSettings(
                    method = ExportMethod.CUSTOM,
                    format = format,
                    width = 1920,
                    height = 1920,
                    scale = 1.0,
                    transparentBackground = false,
                    includeGrid = false
                )
                val ext = format.name.lowercase()
                val file = File(outDir, "${baseName}_custom.$ext")
                ExportRenderer.export(settings, manager, file)
                println("Exported: ${file.path}")
            }

            println("Done. 4 files written to $outputDir")
        } catch (e: Exception) {
            System.err.println("Export failed: ${e.message}")
            e.printStackTrace()
        } finally {
            Platform.exit()
        }
    }

    companion object {
        private lateinit var exportArgs: Array<String>

        fun run(args: Array<String>) {
            exportArgs = args
            launch(HeadlessExportApp::class.java)
        }
    }
}
