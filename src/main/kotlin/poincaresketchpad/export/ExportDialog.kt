package poincaresketchpad.export

import poincaresketchpad.ui.DiskCanvasPane
import javafx.beans.value.ChangeListener
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import kotlin.math.max

class ExportDialog(
    private val owner: Stage,
    private val canvasPane: DiskCanvasPane,
    private val onExport: (ExportSettings, java.io.File) -> Unit,
    private val onDialogClosed: () -> Unit = {}
) {

    private val stage = Stage()

    // Method radios
    private val methodGroup = ToggleGroup()
    private val canvasRadio = RadioButton("Canvas").apply { toggleGroup = methodGroup; isSelected = true }
    private val customRadio = RadioButton("Custom").apply { toggleGroup = methodGroup }

    // Format radios
    private val formatGroup = ToggleGroup()
    private val pngRadio = RadioButton("PNG").apply { toggleGroup = formatGroup; isSelected = true }
    private val svgRadio = RadioButton("SVG").apply { toggleGroup = formatGroup }

    // Checkboxes
    private val transparentBgCheck = CheckBox("Transparent background")
    private val includeGridCheck = CheckBox("Include grid")

    // Canvas mode: read-only diameter field
    private val diameterField = TextField()
    private val diameterLabel = Label("Diameter:")

    // Custom mode controls
    private val widthField = TextField("600")
    private val heightField = TextField("600")
    private val widthLabel = Label("Width:")
    private val heightLabel = Label("Height:")
    private val lockToggle = ToggleButton("Lock").apply { isSelected = true }
    private val scaleSlider = Slider(10.0, 100.0, 100.0)
    private val scaleField = TextField("100")
    private val scaleLabel = Label("Scale:")

    // Track whether we're programmatically updating fields to avoid feedback loops
    private var updatingFields = false

    // Canvas resize listener for overlay updates
    private val canvasResizeListener = ChangeListener<Number> { _, _, _ -> updateOverlay() }

    init {
        stage.initOwner(owner)
        stage.initModality(Modality.NONE)
        stage.title = "Export"
        stage.isResizable = false

        val root = VBox(12.0).apply { padding = Insets(16.0) }

        // Method section
        root.children.add(Label("Method:").apply { style = "-fx-font-weight: bold;" })
        root.children.add(HBox(12.0, canvasRadio, customRadio))

        // Format section
        root.children.add(Label("Format:").apply { style = "-fx-font-weight: bold;" })
        root.children.add(HBox(12.0, pngRadio, svgRadio))

        // Options
        root.children.add(Label("Options:").apply { style = "-fx-font-weight: bold;" })
        root.children.add(VBox(6.0, transparentBgCheck, includeGridCheck))

        // Dimensions grid
        root.children.add(Label("Dimensions:").apply { style = "-fx-font-weight: bold;" })
        val grid = GridPane().apply {
            hgap = 8.0
            vgap = 6.0
        }

        // Canvas mode row
        grid.add(diameterLabel, 0, 0)
        grid.add(diameterField, 1, 0)
        diameterField.prefWidth = 100.0
        diameterField.isEditable = false

        // Custom mode rows
        grid.add(widthLabel, 0, 1)
        grid.add(HBox(8.0, widthField, lockToggle).apply { alignment = Pos.CENTER_LEFT }, 1, 1)
        widthField.prefWidth = 80.0

        grid.add(heightLabel, 0, 2)
        grid.add(heightField, 1, 2)
        heightField.prefWidth = 80.0

        grid.add(scaleLabel, 0, 3)
        grid.add(HBox(8.0, scaleSlider, scaleField).apply { alignment = Pos.CENTER_LEFT }, 1, 3)
        scaleSlider.prefWidth = 120.0
        scaleField.prefWidth = 50.0

        root.children.add(grid)

        // Buttons
        val exportBtn = Button("Export")
        val cancelBtn = Button("Cancel")
        val buttonBox = HBox(8.0, exportBtn, cancelBtn).apply { alignment = Pos.CENTER_RIGHT }
        root.children.add(buttonBox)

        exportBtn.setOnAction { doExport() }
        cancelBtn.setOnAction { close() }

        stage.setOnCloseRequest {
            clearOverlay()
            removeCanvasListeners()
            onDialogClosed()
        }

        // React to method changes
        methodGroup.selectedToggleProperty().addListener { _, _, _ -> updateFieldStates() }

        // Width/Height sync when locked
        widthField.textProperty().addListener { _, _, newVal ->
            if (updatingFields) return@addListener
            if (lockToggle.isSelected) {
                updatingFields = true
                heightField.text = newVal
                updatingFields = false
            }
            updateOverlay()
        }
        heightField.textProperty().addListener { _, _, newVal ->
            if (updatingFields) return@addListener
            if (lockToggle.isSelected) {
                updatingFields = true
                widthField.text = newVal
                updatingFields = false
            }
            updateOverlay()
        }

        // Lock toggle: snap to max on lock
        lockToggle.selectedProperty().addListener { _, _, isLocked ->
            if (isLocked) {
                val w = widthField.text.toIntOrNull() ?: 600
                val h = heightField.text.toIntOrNull() ?: 600
                val maxVal = max(w, h).toString()
                updatingFields = true
                widthField.text = maxVal
                heightField.text = maxVal
                updatingFields = false
                updateOverlay()
            }
        }

        // Scale slider <-> text field sync
        scaleSlider.valueProperty().addListener { _, _, newVal ->
            if (updatingFields) return@addListener
            updatingFields = true
            scaleField.text = newVal.toInt().toString()
            updatingFields = false
            updateOverlay()
        }
        scaleField.textProperty().addListener { _, _, newVal ->
            if (updatingFields) return@addListener
            val v = newVal.toIntOrNull() ?: return@addListener
            if (v in 10..100) {
                updatingFields = true
                scaleSlider.value = v.toDouble()
                updatingFields = false
                updateOverlay()
            }
        }

        stage.scene = Scene(root)
        updateFieldStates()
    }

    fun show() {
        stage.show()
        addCanvasListeners()
        updateFieldStates()
    }

    fun close() {
        clearOverlay()
        removeCanvasListeners()
        stage.close()
        onDialogClosed()
    }

    private fun addCanvasListeners() {
        canvasPane.widthProperty().addListener(canvasResizeListener)
        canvasPane.heightProperty().addListener(canvasResizeListener)
    }

    private fun removeCanvasListeners() {
        canvasPane.widthProperty().removeListener(canvasResizeListener)
        canvasPane.heightProperty().removeListener(canvasResizeListener)
    }

    private fun updateFieldStates() {
        val isCanvas = selectedMethod() == ExportMethod.CANVAS
        // Canvas mode fields
        diameterLabel.isVisible = isCanvas
        diameterLabel.isManaged = isCanvas
        diameterField.isVisible = isCanvas
        diameterField.isManaged = isCanvas

        // Custom mode fields
        val isCustom = !isCanvas
        widthLabel.isVisible = isCustom
        widthLabel.isManaged = isCustom
        widthField.isVisible = isCustom
        widthField.isManaged = isCustom
        heightLabel.isVisible = isCustom
        heightLabel.isManaged = isCustom
        heightField.isVisible = isCustom
        heightField.isManaged = isCustom
        lockToggle.isVisible = isCustom
        lockToggle.isManaged = isCustom
        scaleLabel.isVisible = isCustom
        scaleLabel.isManaged = isCustom
        scaleSlider.isVisible = isCustom
        scaleSlider.isManaged = isCustom
        scaleField.isVisible = isCustom
        scaleField.isManaged = isCustom

        if (isCanvas) {
            diameterField.text = canvasPane.getDiskScreenDiameter().toInt().toString()
            clearOverlay()
        } else {
            updateOverlay()
        }

        stage.sizeToScene()
    }

    private fun selectedMethod(): ExportMethod = when (methodGroup.selectedToggle) {
        customRadio -> ExportMethod.CUSTOM
        else -> ExportMethod.CANVAS
    }

    private fun selectedFormat(): ExportFormat = when (formatGroup.selectedToggle) {
        svgRadio -> ExportFormat.SVG
        else -> ExportFormat.PNG
    }

    // --- Overlay ---

    private fun updateOverlay() {
        if (selectedMethod() != ExportMethod.CUSTOM) {
            clearOverlay()
            return
        }

        val w = widthField.text.toIntOrNull() ?: return
        val h = heightField.text.toIntOrNull() ?: return
        if (w <= 0 || h <= 0) return
        val scalePct = scaleSlider.value
        val scaleFrac = scalePct / 100.0

        val diskDiam = canvasPane.getDiskScreenDiameter()
        val diskCX = canvasPane.diskCenterX
        val diskCY = canvasPane.diskCenterY

        // Crop rect: largest dimension = diskDiam * scaleFrac, aspect ratio = w:h
        val maxDim = max(w, h).toDouble()
        val cropW = (w / maxDim) * diskDiam * scaleFrac
        val cropH = (h / maxDim) * diskDiam * scaleFrac

        val cropX = diskCX - cropW / 2.0
        val cropY = diskCY - cropH / 2.0

        val group = canvasPane.exportOverlayGroup
        group.children.clear()

        val canvasW = canvasPane.width
        val canvasH = canvasPane.height
        val letterboxColor = Color.rgb(0, 0, 0, 0.4)

        // Top letterbox
        if (cropY > 0) {
            group.children.add(Rectangle(canvasW, cropY).apply { fill = letterboxColor })
        }
        // Bottom letterbox
        val bottomY = cropY + cropH
        if (bottomY < canvasH) {
            group.children.add(Rectangle(0.0, bottomY, canvasW, canvasH - bottomY).apply { fill = letterboxColor })
        }
        // Left letterbox
        if (cropX > 0) {
            group.children.add(Rectangle(0.0, cropY, cropX, cropH).apply { fill = letterboxColor })
        }
        // Right letterbox
        val rightX = cropX + cropW
        if (rightX < canvasW) {
            group.children.add(Rectangle(rightX, cropY, canvasW - rightX, cropH).apply { fill = letterboxColor })
        }

        // Orange border
        group.children.add(Rectangle(cropX, cropY, cropW, cropH).apply {
            fill = Color.TRANSPARENT
            stroke = Color.web("#FF5722")
            strokeWidth = 2.0
        })

        // Scale label
        group.children.add(Text("Scale: ${scalePct.toInt()}%").apply {
            x = cropX + 4
            y = cropY - 6
            fill = Color.web("#FF5722")
            style = "-fx-font-size: 12px; -fx-font-weight: bold;"
        })
    }

    private fun clearOverlay() {
        canvasPane.exportOverlayGroup.children.clear()
    }

    // --- Export ---

    private fun doExport() {
        val format = selectedFormat()
        val method = selectedMethod()

        val width: Int
        val height: Int
        val scale: Double

        when (method) {
            ExportMethod.CANVAS -> {
                val diam = canvasPane.getDiskScreenDiameter().toInt()
                width = diam
                height = diam
                scale = 1.0
            }
            ExportMethod.CUSTOM -> {
                val w = widthField.text.toIntOrNull()
                val h = heightField.text.toIntOrNull()
                if (w == null || w <= 0 || h == null || h <= 0) {
                    Alert(Alert.AlertType.ERROR, "Invalid dimensions.").showAndWait()
                    return
                }
                width = w
                height = h
                scale = scaleSlider.value / 100.0
            }
        }

        val ext = if (format == ExportFormat.PNG) "*.png" else "*.svg"
        val desc = if (format == ExportFormat.PNG) "PNG images" else "SVG images"
        val chooser = FileChooser()
        chooser.extensionFilters.add(FileChooser.ExtensionFilter(desc, ext))
        chooser.title = "Export Image"
        val file = chooser.showSaveDialog(stage) ?: return

        val settings = ExportSettings(
            method = method,
            format = format,
            width = width,
            height = height,
            scale = scale,
            transparentBackground = transparentBgCheck.isSelected,
            includeGrid = includeGridCheck.isSelected
        )

        onExport(settings, file)
        close()
    }
}
