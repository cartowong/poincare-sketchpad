package poincaresketchpad.ui

import poincaresketchpad.export.ExportDialog
import poincaresketchpad.export.ExportRenderer
import poincaresketchpad.geometry.*
import poincaresketchpad.model.ConstructionApplicator
import poincaresketchpad.model.ConstructionRecorder
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.StyleSettings
import poincaresketchpad.model.StyleUtils
import poincaresketchpad.model.UndoManager
import poincaresketchpad.APP_VERSION
import poincaresketchpad.persistence.ConstructionSerializer
import poincaresketchpad.persistence.DiagramSerializer
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCombination
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import atlantafx.base.theme.PrimerLight
import java.io.File

class SketchpadApp : Application() {

    override fun start(stage: Stage) {
        Application.setUserAgentStylesheet(PrimerLight().userAgentStylesheet)

        val manager = GeometricObjectManager()
        val styleSettings = StyleSettings()
        manager.styleSettings = styleSettings
        val undoManager = UndoManager(manager)
        val recorder = ConstructionRecorder(manager)

        val canvasPane = DiskCanvasPane(manager, undoManager)
        val hitTester = canvasPane.hitTester
        hitTester.rebuildIntersectionCache()

        // Initial checkpoint (empty state)
        undoManager.checkpoint()
        undoManager.markSaved()

        // Forward declarations
        lateinit var selectionTool: SelectionTool
        lateinit var sidebar: Sidebar

        val requestRender: () -> Unit = {
            canvasPane.selectedIds = selectionTool.selectedIds
            canvasPane.renderAll()
            sidebar.setSelectedIds(selectionTool.selectedIds)
            sidebar.refresh()
        }

        fun clearSelection() {
            selectionTool.selectedIds.clear()
            canvasPane.selectedIds = mutableSetOf()
            sidebar.setSelectedIds(emptySet())
        }

        fun setSelection(ids: Set<Int>) {
            selectionTool.selectedIds.clear()
            selectionTool.selectedIds.addAll(ids)
            canvasPane.selectedIds = ids.toMutableSet()
            sidebar.setSelectedIds(ids)
        }

        // Create sidebar (with onApplyConstruction callback, wired below)
        sidebar = Sidebar(manager, undoManager, hitTester, { ids ->
            // Sync selection to selectionTool and canvasPane only -- don't call back
            // into sidebar.setSelectedIds() since the change originated from the sidebar
            selectionTool.selectedIds.clear()
            selectionTool.selectedIds.addAll(ids)
            canvasPane.selectedIds = ids.toMutableSet()
            canvasPane.renderAll()
        }, requestRender, onApplyConstruction = { loaded ->
            if (loaded.inputTypes.isEmpty()) {
                // Zero inputs: apply immediately
                ConstructionApplicator.apply(loaded, emptyList(), manager)
                hitTester.rebuildIntersectionCache()
                undoManager.checkpoint()
                requestRender()
            } else {
                clearSelection()
                canvasPane.startApplyMode(
                    loaded.inputTypes,
                    onComplete = { selectedIds ->
                        ConstructionApplicator.apply(loaded, selectedIds, manager)
                        hitTester.rebuildIntersectionCache()
                        undoManager.checkpoint()
                        requestRender()
                    },
                    onCancel = {
                        canvasPane.renderAll()
                    },
                    previewProvider = { collectedIds, hoveredId ->
                        ConstructionApplicator.computePreview(loaded, collectedIds + hoveredId, manager)
                    }
                )
                requestRender()
            }
        })

        // Create tools
        selectionTool = SelectionTool(
            manager, hitTester, undoManager, requestRender
        )
        val pointTool = PointTool(manager, hitTester, undoManager, canvasPane.previewGroup, canvasPane, requestRender)
        val geodesicTool = GeodesicTool(manager, hitTester, undoManager, canvasPane.previewGroup, canvasPane, requestRender)
        val circleTool = CircleTool(manager, hitTester, undoManager, canvasPane.previewGroup, canvasPane, requestRender)
        val angleTool = AngleTool(manager, hitTester, undoManager, canvasPane.previewGroup, canvasPane, requestRender)
        val regionTool = RegionTool(manager, hitTester, undoManager, canvasPane.previewGroup, canvasPane, requestRender)
        val labelTool = LabelTool(manager, hitTester, undoManager, canvasPane, requestRender)

        val toolbar = Toolbar { toolType ->
            when (toolType) {
                ToolType.SELECT -> {
                    clearSelection()
                    canvasPane.setTool(selectionTool)
                }
                ToolType.POINT -> {
                    clearSelection()
                    canvasPane.setTool(pointTool)
                }
                ToolType.SEGMENT -> {
                    clearSelection()
                    geodesicTool.subMode = GeodesicSubMode.SEGMENT
                    canvasPane.setTool(geodesicTool)
                }
                ToolType.RAY -> {
                    clearSelection()
                    geodesicTool.subMode = GeodesicSubMode.RAY
                    canvasPane.setTool(geodesicTool)
                }
                ToolType.LINE -> {
                    clearSelection()
                    geodesicTool.subMode = GeodesicSubMode.LINE
                    canvasPane.setTool(geodesicTool)
                }
                ToolType.CIRCLE -> {
                    clearSelection()
                    canvasPane.setTool(circleTool)
                }
                ToolType.ANGLE -> {
                    clearSelection()
                    canvasPane.setTool(angleTool)
                }
                ToolType.REGION -> {
                    clearSelection()
                    canvasPane.setTool(regionTool)
                }
                ToolType.LABEL -> {
                    clearSelection()
                    canvasPane.setTool(labelTool)
                }
            }
            requestRender()
        }

        // Menu bar
        val menuBar = MenuBar()
        val fileMenu = Menu("File")

        val saveItem = MenuItem("Save")
        saveItem.accelerator = KeyCombination.valueOf("Shortcut+S")
        saveItem.setOnAction {
            val chooser = FileChooser()
            chooser.extensionFilters.add(FileChooser.ExtensionFilter("JSON files", "*.json"))
            chooser.title = "Save Diagram"
            val file = chooser.showSaveDialog(stage) ?: return@setOnAction
            val json = DiagramSerializer.serialize(manager)
            file.writeText(json)
            undoManager.markSaved()
        }

        val openItem = MenuItem("Open")
        openItem.accelerator = KeyCombination.valueOf("Shortcut+O")
        openItem.setOnAction {
            if (confirmUnsaved(undoManager)) {
                val chooser = FileChooser()
                chooser.extensionFilters.add(FileChooser.ExtensionFilter("JSON files", "*.json"))
                chooser.title = "Open Diagram"
                val file = chooser.showOpenDialog(stage) ?: return@setOnAction
                try {
                    val json = file.readText()
                    // Parse and validate version/model before resetting the current diagram
                    val diagram = DiagramSerializer.parseDiagram(json)
                    DiagramSerializer.validateAppName(diagram.appName)
                    DiagramSerializer.validateVersion(diagram.version)
                    DiagramSerializer.validateModel(diagram.model)
                    resetDiagram(manager, undoManager, hitTester)
                    DiagramSerializer.deserialize(json, manager)
                    undoManager.checkpoint()
                    undoManager.markSaved()
                    hitTester.rebuildIntersectionCache()
                    requestRender()
                } catch (e: Exception) {
                    Alert(Alert.AlertType.ERROR, e.message ?: "Failed to open file.").showAndWait()
                }
            }
        }

        val resetItem = MenuItem("New")
        resetItem.accelerator = KeyCombination.valueOf("Shortcut+N")
        resetItem.setOnAction {
            if (confirmUnsaved(undoManager)) {
                resetDiagram(manager, undoManager, hitTester)
                requestRender()
            }
        }

        // Forward declarations for view menu items disabled during export
        lateinit var toggleObjectsItem: MenuItem
        lateinit var toggleConstructionsItem: MenuItem
        lateinit var toggleMeasurementsItem: MenuItem

        val exportItem = MenuItem("Export")
        exportItem.accelerator = KeyCombination.valueOf("Shortcut+E")
        exportItem.setOnAction {
            clearSelection()
            canvasPane.hoveredId = null
            requestRender()
            toggleObjectsItem.isDisable = true
            toggleConstructionsItem.isDisable = true
            toggleMeasurementsItem.isDisable = true
            sidebar.setCloseButtonsDisabled(true)
            val dialog = ExportDialog(stage, canvasPane,
                onExport = { settings, file ->
                    ExportRenderer.export(settings, manager, file)
                },
                onDialogClosed = {
                    toggleObjectsItem.isDisable = false
                    toggleConstructionsItem.isDisable = false
                    toggleMeasurementsItem.isDisable = false
                    sidebar.setCloseButtonsDisabled(false)
                }
            )
            dialog.show()
        }

        fileMenu.items.addAll(resetItem, SeparatorMenuItem(), saveItem, openItem, SeparatorMenuItem(), exportItem)

        val actionMenu = Menu("Action")
        val selectAllItem = MenuItem("Select All")
        selectAllItem.accelerator = KeyCombination.valueOf("Shortcut+A")
        selectAllItem.setOnAction {
            setSelection(manager.allObjects().map { it.id }.toSet())
            requestRender()
        }
        val deselectAllItem = MenuItem("Deselect All")
        deselectAllItem.accelerator = KeyCombination.valueOf("Shortcut+Shift+A")
        deselectAllItem.setOnAction {
            clearSelection()
            requestRender()
        }
        val selectParentsItem = MenuItem("Select Parents")
        selectParentsItem.accelerator = KeyCombination.valueOf("Shortcut+Shift+P")
        selectParentsItem.setOnAction {
            val currentIds = selectionTool.selectedIds.toSet()
            if (currentIds.isNotEmpty()) {
                var parentIds: Set<Int>? = null
                for (id in currentIds) {
                    val obj = manager.getObject(id) ?: continue
                    val objParents = obj.parentIds.toSet()
                    parentIds = parentIds?.intersect(objParents) ?: objParents
                }
                setSelection(parentIds ?: emptySet())
                requestRender()
            }
        }
        val selectChildrenItem = MenuItem("Select Children")
        selectChildrenItem.accelerator = KeyCombination.valueOf("Shortcut+Shift+C")
        selectChildrenItem.setOnAction {
            val currentIds = selectionTool.selectedIds.toSet()
            if (currentIds.isNotEmpty()) {
                val childIds = mutableSetOf<Int>()
                for (obj in manager.allObjects()) {
                    if (currentIds.all { it in obj.parentIds }) {
                        childIds.add(obj.id)
                    }
                }
                setSelection(childIds)
                requestRender()
            }
        }
        val showItem = MenuItem("Show")
        showItem.accelerator = KeyCombination.valueOf("Shortcut+Shift+I")
        showItem.setOnAction {
            val ids = selectionTool.selectedIds.toList()
            if (ids.isNotEmpty()) {
                var changed = false
                for (id in ids) {
                    val meta = manager.getMetadata(id) ?: continue
                    if (!meta.visible) {
                        meta.visible = true
                        changed = true
                    }
                }
                if (changed) {
                    hitTester.rebuildIntersectionCache()
                    undoManager.checkpoint()
                    requestRender()
                }
            }
        }
        val hideItem = MenuItem("Hide")
        hideItem.accelerator = KeyCombination.valueOf("Shortcut+I")
        hideItem.setOnAction {
            val ids = selectionTool.selectedIds.toList()
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    val meta = manager.getMetadata(id) ?: continue
                    meta.visible = false
                    meta.labelVisible = false
                }
                clearSelection()
                hitTester.rebuildIntersectionCache()
                undoManager.checkpoint()
                requestRender()
            }
        }
        val deleteItem = MenuItem("Delete")
        deleteItem.accelerator = KeyCombination.valueOf("Delete")
        deleteItem.setOnAction {
            val ids = selectionTool.selectedIds.toList()
            if (ids.isEmpty()) return@setOnAction
            val alert = Alert(Alert.AlertType.CONFIRMATION, "Delete ${ids.size} selected object(s) and their descendants?")
            alert.showAndWait().ifPresent { result ->
                if (result == ButtonType.OK) {
                    for (id in ids) {
                        if (manager.getObject(id) != null) {
                            manager.removeWithDescendants(id)
                        }
                    }
                    clearSelection()
                    hitTester.rebuildIntersectionCache()
                    undoManager.checkpoint()
                    requestRender()
                }
            }
        }
        val measureItem = MenuItem("Measure")
        measureItem.accelerator = KeyCombination.valueOf("Shortcut+M")
        measureItem.setOnAction {
            val ids = selectionTool.selectedIds.toList()
            if (ids.isNotEmpty()) {
                var changed = false
                for (id in ids) {
                    val obj = manager.getObject(id) ?: continue
                    if (MeasurementUtils.measure(obj) == null) continue
                    val meta = manager.getMetadata(id) ?: continue
                    if (!meta.inMeasurementTable) {
                        changed = true
                        meta.inMeasurementTable = true
                    }
                }
                if (changed) {
                    undoManager.checkpoint()
                    requestRender()
                }
            }
        }
        val undoItem = MenuItem("Undo")
        undoItem.accelerator = KeyCombination.valueOf("Shortcut+Z")
        undoItem.setOnAction {
            if (undoManager.undo()) {
                clearSelection()
                hitTester.rebuildIntersectionCache()
                requestRender()
            }
        }
        val redoItem = MenuItem("Redo")
        redoItem.accelerator = KeyCombination.valueOf("Shortcut+Shift+Z")
        redoItem.setOnAction {
            if (undoManager.redo()) {
                clearSelection()
                hitTester.rebuildIntersectionCache()
                requestRender()
            }
        }
        val clearMeasurementsItem = MenuItem("Clear Measurements")
        clearMeasurementsItem.accelerator = KeyCombination.valueOf("Shortcut+Shift+M")
        clearMeasurementsItem.setOnAction {
            var changed = false
            for (obj in manager.allObjects()) {
                val meta = manager.getMetadata(obj.id) ?: continue
                if (meta.inMeasurementTable) {
                    meta.inMeasurementTable = false
                    changed = true
                }
            }
            if (changed) {
                undoManager.checkpoint()
                requestRender()
            }
        }
        actionMenu.items.addAll(selectAllItem, deselectAllItem, selectParentsItem, selectChildrenItem, SeparatorMenuItem(), showItem, hideItem, deleteItem, SeparatorMenuItem(), undoItem, redoItem, SeparatorMenuItem(), measureItem, clearMeasurementsItem)

        // Construction menu
        val constructionMenu = Menu("Construction")
        val startRecordingItem = MenuItem("Start Recording")
        startRecordingItem.accelerator = KeyCombination.valueOf("Shortcut+R")
        val saveRecordingItem = MenuItem("Save Recording")
        saveRecordingItem.accelerator = KeyCombination.valueOf("Shortcut+Shift+S")
        val cancelRecordingItem = MenuItem("Cancel Recording")
        val loadRecordingItem = MenuItem("Load Construction")
        loadRecordingItem.accelerator = KeyCombination.valueOf("Shortcut+L")

        // Initial states: not recording
        saveRecordingItem.isDisable = true
        cancelRecordingItem.isDisable = true

        // Forward declarations for items created after setRecordingMenuState
        lateinit var loadFolderItem: MenuItem
        lateinit var unloadAllItem: MenuItem

        // Transformation menu items (forward declared for state management)
        lateinit var mobiusItem: MenuItem
        lateinit var rotationItem: MenuItem

        fun setRecordingMenuState(recording: Boolean) {
            startRecordingItem.isDisable = recording
            loadRecordingItem.isDisable = recording
            loadFolderItem.isDisable = recording
            unloadAllItem.isDisable = recording
            saveRecordingItem.isDisable = !recording
            cancelRecordingItem.isDisable = !recording
            // Disable file menu items during recording
            saveItem.isDisable = recording
            openItem.isDisable = recording
            resetItem.isDisable = recording
            exportItem.isDisable = recording
            // Disable transformation menu items during recording
            mobiusItem.isDisable = recording
            rotationItem.isDisable = recording
        }

        startRecordingItem.setOnAction {
            recorder.startRecording()
            canvasPane.setRecordingMode(true)
            hitTester.rebuildIntersectionCache()
            undoManager.checkpoint()
            undoManager.setMinIndex(undoManager.currentIndex())
            setRecordingMenuState(true)
            requestRender()
        }

        saveRecordingItem.setOnAction {
            val chooser = FileChooser()
            chooser.extensionFilters.add(FileChooser.ExtensionFilter("JSON files", "*.json"))
            chooser.title = "Save Construction"
            val file = chooser.showSaveDialog(stage) ?: return@setOnAction
            ConstructionSerializer.saveConstruction(manager, file)
            recorder.endRecording()
            canvasPane.setRecordingMode(false)
            undoManager.setMinIndex(null)
            setRecordingMenuState(false)
            hitTester.rebuildIntersectionCache()
            undoManager.checkpoint()
            requestRender()
        }

        cancelRecordingItem.setOnAction {
            recorder.cancelRecording()
            canvasPane.setRecordingMode(false)
            undoManager.setMinIndex(null)
            setRecordingMenuState(false)
            hitTester.rebuildIntersectionCache()
            requestRender()
        }

        // ESC fallthrough from canvas: cancel recording when no tool/apply-mode handled it
        canvasPane.onEscapeFallthrough = {
            if (recorder.isRecording) {
                cancelRecordingItem.fire()
            }
        }

        fun loadConstructionFiles(files: List<File>) {
            if (files.isEmpty()) return
            val failures = mutableListOf<String>()
            for (file in files) {
                try {
                    val loaded = ConstructionSerializer.loadFromFile(file)
                    sidebar.addConstruction(loaded)
                } catch (e: Exception) {
                    failures.add("${file.name}: ${e.message}")
                }
            }
            if (failures.isNotEmpty()) {
                val msg = "Failed to load ${failures.size} file(s):\n" +
                    failures.joinToString("\n") { "- $it" }
                Alert(Alert.AlertType.ERROR, msg).showAndWait()
            }
        }

        loadRecordingItem.setOnAction {
            val chooser = FileChooser()
            chooser.extensionFilters.add(FileChooser.ExtensionFilter("JSON files", "*.json"))
            chooser.title = "Load Construction"
            val files = chooser.showOpenMultipleDialog(stage) ?: return@setOnAction
            loadConstructionFiles(files)
        }

        loadFolderItem = MenuItem("Load from Folder...")
        loadFolderItem.accelerator = KeyCombination.valueOf("Shortcut+Shift+L")
        loadFolderItem.setOnAction {
            val chooser = DirectoryChooser()
            chooser.title = "Load Constructions from Folder"
            val dir = chooser.showDialog(stage) ?: return@setOnAction
            val files = dir.listFiles { f: File -> f.extension.equals("json", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: emptyList()
            loadConstructionFiles(files)
        }

        unloadAllItem = MenuItem("Unload All")
        unloadAllItem.accelerator = KeyCombination.valueOf("Shortcut+U")
        unloadAllItem.setOnAction {
            sidebar.clearAllConstructions()
        }

        fun builtInPreviewProvider(
            factory: (id: Int, parentIds: List<Int>) -> GeometricObject
        ): (List<Int>, Int) -> List<GeometricObject> = { collectedIds, hoveredId ->
            val allIds = collectedIds + hoveredId
            val obj = factory(-1, allIds)
            val tempObjects = linkedMapOf<Int, GeometricObject>()
            tempObjects.putAll(manager.objectMap())
            tempObjects[-1] = obj
            obj.recompute(tempObjects)
            if (obj.isDefined) listOf(obj) else emptyList()
        }

        val midpointItem = MenuItem("Midpoint").apply {
            setOnAction {
                clearSelection()
                canvasPane.startApplyMode(
                    listOf("Point", "Point"),
                    onComplete = { ids ->
                        manager.addObject { id -> Midpoint(id, ids[0], ids[1]) }
                        manager.propagateFrom(0)
                        hitTester.rebuildIntersectionCache()
                        undoManager.checkpoint()
                        requestRender()
                    },
                    onCancel = { canvasPane.renderAll() },
                    previewProvider = builtInPreviewProvider { id, ids -> Midpoint(id, ids[0], ids[1]) }
                )
                requestRender()
            }
        }
        val perpendicularLineItem = MenuItem("Perpendicular Line").apply {
            setOnAction {
                clearSelection()
                canvasPane.startApplyMode(
                    listOf("Point", "Geodesic"),
                    onComplete = { ids ->
                        manager.addObject { id -> PerpendicularLine(id, ids[0], ids[1]) }
                        manager.propagateFrom(0)
                        hitTester.rebuildIntersectionCache()
                        undoManager.checkpoint()
                        requestRender()
                    },
                    onCancel = { canvasPane.renderAll() },
                    previewProvider = builtInPreviewProvider { id, ids -> PerpendicularLine(id, ids[0], ids[1]) }
                )
                requestRender()
            }
        }
        val reflectionPointItem = MenuItem("Reflection Point").apply {
            setOnAction {
                clearSelection()
                canvasPane.startApplyMode(
                    listOf("Point", "Geodesic"),
                    onComplete = { ids ->
                        manager.addObject { id -> ReflectionPoint(id, ids[0], ids[1]) }
                        manager.propagateFrom(0)
                        hitTester.rebuildIntersectionCache()
                        undoManager.checkpoint()
                        requestRender()
                    },
                    onCancel = { canvasPane.renderAll() },
                    previewProvider = builtInPreviewProvider { id, ids -> ReflectionPoint(id, ids[0], ids[1]) }
                )
                requestRender()
            }
        }

        constructionMenu.items.addAll(
            startRecordingItem, saveRecordingItem, cancelRecordingItem,
            SeparatorMenuItem(),
            loadRecordingItem, loadFolderItem, unloadAllItem,
            SeparatorMenuItem(),
            midpointItem, perpendicularLineItem, reflectionPointItem
        )

        // Transformation menu
        val transformationMenu = Menu("Transformation")
        mobiusItem = MenuItem("M\u00F6bius")
        mobiusItem.setOnAction {
            canvasPane.enterTransformationMode(TransformationMode.MOBIUS)
        }
        rotationItem = MenuItem("Rotation")
        rotationItem.setOnAction {
            canvasPane.enterTransformationMode(TransformationMode.ROTATION)
        }
        transformationMenu.items.addAll(mobiusItem, rotationItem)

        // Center column: toolbar on top, canvas below
        val centerColumn = VBox()
        centerColumn.children.addAll(toolbar, canvasPane)
        VBox.setVgrow(canvasPane, Priority.ALWAYS)

        // 3-column horizontal SplitPane
        val mainSplit = SplitPane()
        mainSplit.orientation = javafx.geometry.Orientation.HORIZONTAL
        mainSplit.items.addAll(sidebar.leftPane, centerColumn, sidebar.rightPane)
        mainSplit.setDividerPositions(0.30, 0.70)

        val viewMenu = Menu("View")
        val toggleGridItem = MenuItem("Show Grid")
        toggleGridItem.accelerator = KeyCombination.valueOf("Shortcut+G")
        toggleGridItem.setOnAction {
            canvasPane.gridVisible = !canvasPane.gridVisible
            toggleGridItem.text = if (canvasPane.gridVisible) "Hide Grid" else "Show Grid"
            requestRender()
        }
        // Table visibility toggles
        var objectsVisible = true
        var constructionsVisible = true
        var measurementsVisible = true

        fun updateMainSplitLayout() {
            mainSplit.items.clear()
            if (objectsVisible) mainSplit.items.add(sidebar.leftPane)
            mainSplit.items.add(centerColumn)
            if (!sidebar.isRightPaneEmpty) mainSplit.items.add(sidebar.rightPane)
            when {
                objectsVisible && !sidebar.isRightPaneEmpty -> mainSplit.setDividerPositions(0.30, 0.70)
                objectsVisible -> mainSplit.setDividerPositions(0.30)
                !sidebar.isRightPaneEmpty -> mainSplit.setDividerPositions(0.70)
            }
        }

        toggleObjectsItem = MenuItem("Hide Object List")
        toggleObjectsItem.setOnAction {
            objectsVisible = !objectsVisible
            toggleObjectsItem.text = if (objectsVisible) "Hide Object List" else "Show Object List"
            if (objectsVisible) requestRender()
            updateMainSplitLayout()
        }

        toggleConstructionsItem = MenuItem("Hide Custom Constructions")
        toggleConstructionsItem.setOnAction {
            constructionsVisible = !constructionsVisible
            toggleConstructionsItem.text = if (constructionsVisible) "Hide Custom Constructions" else "Show Custom Constructions"
            sidebar.setConstructionsVisible(constructionsVisible)
            updateMainSplitLayout()
        }

        toggleMeasurementsItem = MenuItem("Hide Measurements")
        toggleMeasurementsItem.setOnAction {
            measurementsVisible = !measurementsVisible
            toggleMeasurementsItem.text = if (measurementsVisible) "Hide Measurements" else "Show Measurements"
            sidebar.setMeasurementsVisible(measurementsVisible)
            updateMainSplitLayout()
        }

        viewMenu.items.addAll(toggleGridItem, SeparatorMenuItem(), toggleObjectsItem, toggleConstructionsItem, toggleMeasurementsItem)

        sidebar.onCloseObjectList = { toggleObjectsItem.fire() }
        sidebar.onCloseConstructions = { toggleConstructionsItem.fire() }
        sidebar.onCloseMeasurements = { toggleMeasurementsItem.fire() }

        val aboutMenu = Menu("About")
        val aboutItem = MenuItem("About Poincar\u00E9 Sketchpad")
        aboutItem.setOnAction {
            val dialog = Dialog<Void>()
            dialog.title = "About Poincar\u00E9 Sketchpad"
            dialog.dialogPane.buttonTypes.add(ButtonType.CLOSE)

            val content = VBox(12.0)
            content.alignment = Pos.CENTER
            content.padding = Insets(16.0)

            val iconStream = javaClass.getResourceAsStream("/icons/app-icon.png")
            if (iconStream != null) {
                val imageView = ImageView(Image(iconStream))
                imageView.fitWidth = 96.0
                imageView.fitHeight = 96.0
                imageView.isPreserveRatio = true
                content.children.add(imageView)
            }

            content.children.addAll(
                Label("Poincar\u00E9 Sketchpad").apply { style = "-fx-font-size: 18px; -fx-font-weight: bold;" },
                Label("Version $APP_VERSION"),
                Label("A compass-and-straightedge construction tool\nfor hyperbolic geometry in the Poincar\u00E9 disk model."),
                Label("Copyright \u00A9 2026 Carto Wong"),
                Label("Licensed under the GNU General Public License v3.0"),
                Hyperlink("github.com/cartowong/poincare-sketchpad").apply {
                    setOnAction {
                        hostServices.showDocument("https://github.com/cartowong/poincare-sketchpad")
                    }
                },
                Hyperlink("Third-Party Licenses").apply {
                    setOnAction {
                        val noticesDialog = Dialog<Void>()
                        noticesDialog.title = "Third-Party Licenses"
                        noticesDialog.dialogPane.buttonTypes.add(ButtonType.CLOSE)
                        val noticesText = SketchpadApp::class.java.getResourceAsStream("/THIRD_PARTY_NOTICES")
                            ?.bufferedReader()?.readText() ?: "Could not load third-party notices."
                        val textArea = javafx.scene.control.TextArea(noticesText)
                        textArea.isEditable = false
                        textArea.isWrapText = true
                        textArea.prefWidth = 600.0
                        textArea.prefHeight = 400.0
                        noticesDialog.dialogPane.content = textArea
                        noticesDialog.initOwner(stage)
                        noticesDialog.showAndWait()
                    }
                }
            )

            dialog.dialogPane.content = content
            dialog.initOwner(stage)
            dialog.showAndWait()
        }
        aboutMenu.items.add(aboutItem)

        // Style menu — controls defaults for newly created objects
        val styleMenu = buildStyleMenu(styleSettings)

        menuBar.menus.addAll(fileMenu, actionMenu, constructionMenu, viewMenu, styleMenu, transformationMenu, aboutMenu)

        val root = BorderPane()
        root.top = menuBar
        root.center = mainSplit

        val scene = Scene(root, 1440.0, 720.0)
        scene.stylesheets.add(javaClass.getResource("/styles/geometry.css")?.toExternalForm() ?: "")

        stage.title = "Poincar\u00E9 Sketchpad"
        val appIconStream = javaClass.getResourceAsStream("/icons/app-icon.png")
        if (appIconStream != null) {
            stage.icons.add(Image(appIconStream))
        }
        stage.scene = scene

        // Window close handler
        stage.setOnCloseRequest { event ->
            if (undoManager.hasUnsavedChanges()) {
                val alert = Alert(Alert.AlertType.CONFIRMATION, "You have unsaved changes. Close anyway?")
                val result = alert.showAndWait()
                if (result.isEmpty || result.get() != ButtonType.OK) {
                    event.consume()
                    return@setOnCloseRequest
                }
            }
        }

        stage.show()

        // Right-click context menu for per-object styling
        fun getStyleCategory(obj: GeometricObject): String? = when (obj) {
            is PointLike -> "Point"
            is Line, is Ray, is Segment, is PerpendicularLine -> "Geodesic"
            is Circle -> "Circle"
            is Angle -> "Angle"
            is Region -> "Region"
            else -> null
        }

        fun showStyleContextMenu(screenX: Double, screenY: Double) {
            val ids = selectionTool.selectedIds.toList()
            if (ids.isEmpty()) return

            val categories = ids.mapNotNull { id ->
                val obj = manager.getObject(id)
                if (obj != null) getStyleCategory(obj) else null
            }.toSet()
            if (categories.size != 1) return
            val category = categories.first()

            val contextMenu = ContextMenu()

            // Color sub-menu (all categories)
            val colorMenu = Menu("Color")
            val colorGroup = ToggleGroup()
            val currentColors = ids.mapNotNull { manager.getMetadata(it)?.color }.toSet()
            for (name in StyleUtils.COLOR_NAMES) {
                val item = RadioMenuItem(name.replaceFirstChar { it.uppercase() })
                item.toggleGroup = colorGroup
                item.isSelected = currentColors.size == 1 && currentColors.first() == name
                item.setOnAction {
                    for (id in ids) {
                        val meta = manager.getMetadata(id) ?: continue
                        meta.color = name
                    }
                    undoManager.checkpoint()
                    requestRender()
                }
                colorMenu.items.add(item)
            }
            contextMenu.items.add(colorMenu)

            // Size sub-menu (Point, Angle)
            if (category == "Point" || category == "Angle") {
                val sizeMenu = Menu("Size")
                val sizeGroup = ToggleGroup()
                val currentSizes = ids.mapNotNull { manager.getMetadata(it)?.size }.toSet()
                for (size in listOf("small", "medium", "large")) {
                    val item = RadioMenuItem(size.replaceFirstChar { it.uppercase() })
                    item.toggleGroup = sizeGroup
                    item.isSelected = currentSizes.size == 1 && currentSizes.first() == size
                    item.setOnAction {
                        for (id in ids) {
                            val meta = manager.getMetadata(id) ?: continue
                            meta.size = size
                        }
                        undoManager.checkpoint()
                        requestRender()
                    }
                    sizeMenu.items.add(item)
                }
                contextMenu.items.add(sizeMenu)
            }

            // Stroke Style sub-menu (Geodesic, Circle)
            if (category == "Geodesic" || category == "Circle") {
                val strokeStyleMenu = Menu("Stroke Style")
                val strokeStyleGroup = ToggleGroup()
                val currentStyles = ids.mapNotNull { manager.getMetadata(it)?.strokeStyle }.toSet()
                for (style in listOf("solid", "dashed")) {
                    val item = RadioMenuItem(style.replaceFirstChar { it.uppercase() })
                    item.toggleGroup = strokeStyleGroup
                    item.isSelected = currentStyles.size == 1 && currentStyles.first() == style
                    item.setOnAction {
                        for (id in ids) {
                            val meta = manager.getMetadata(id) ?: continue
                            meta.strokeStyle = style
                        }
                        undoManager.checkpoint()
                        requestRender()
                    }
                    strokeStyleMenu.items.add(item)
                }
                contextMenu.items.add(strokeStyleMenu)
            }

            // Stroke Thickness sub-menu (Geodesic, Circle)
            if (category == "Geodesic" || category == "Circle") {
                val thicknessMenu = Menu("Stroke Thickness")
                val thicknessGroup = ToggleGroup()
                val currentThickness = ids.mapNotNull { manager.getMetadata(it)?.strokeThickness }.toSet()
                for (thickness in listOf("thin", "normal", "thick")) {
                    val item = RadioMenuItem(thickness.replaceFirstChar { it.uppercase() })
                    item.toggleGroup = thicknessGroup
                    item.isSelected = currentThickness.size == 1 && currentThickness.first() == thickness
                    item.setOnAction {
                        for (id in ids) {
                            val meta = manager.getMetadata(id) ?: continue
                            meta.strokeThickness = thickness
                        }
                        undoManager.checkpoint()
                        requestRender()
                    }
                    thicknessMenu.items.add(item)
                }
                contextMenu.items.add(thicknessMenu)
            }

            contextMenu.show(canvasPane, screenX, screenY)
        }

        canvasPane.onRightClick = ::showStyleContextMenu
        sidebar.onContextMenuRequest = ::showStyleContextMenu

        // Default tool: selection
        canvasPane.setTool(selectionTool)

        // Render after layout pass so canvas has dimensions
        canvasPane.widthProperty().addListener { _, _, _ -> canvasPane.renderAll() }
        canvasPane.heightProperty().addListener { _, _, _ -> canvasPane.renderAll() }
        canvasPane.renderAll()
    }

    private fun buildStyleMenu(styleSettings: StyleSettings): Menu {
        val menu = Menu("Style")

        fun colorRadioMenu(label: String, getter: () -> String, setter: (String) -> Unit): Menu {
            val sub = Menu(label)
            val group = ToggleGroup()
            for (name in StyleUtils.COLOR_NAMES) {
                val item = RadioMenuItem(name.replaceFirstChar { it.uppercase() })
                item.toggleGroup = group
                item.isSelected = getter() == name
                item.setOnAction { setter(name) }
                sub.items.add(item)
            }
            // Update check state when menu is shown
            sub.setOnShowing {
                val current = getter()
                for (item in sub.items) {
                    if (item is RadioMenuItem) {
                        item.isSelected = item.text.lowercase() == current
                    }
                }
            }
            return sub
        }

        fun sizeRadioMenu(label: String, getter: () -> String, setter: (String) -> Unit): Menu {
            val sub = Menu(label)
            val group = ToggleGroup()
            for (size in listOf("small", "medium", "large")) {
                val item = RadioMenuItem(size.replaceFirstChar { it.uppercase() })
                item.toggleGroup = group
                item.isSelected = getter() == size
                item.setOnAction { setter(size) }
                sub.items.add(item)
            }
            sub.setOnShowing {
                val current = getter()
                for (item in sub.items) {
                    if (item is RadioMenuItem) {
                        item.isSelected = item.text.lowercase() == current
                    }
                }
            }
            return sub
        }

        fun strokeStyleRadioMenu(getter: () -> String, setter: (String) -> Unit): Menu {
            val sub = Menu("Stroke Style")
            val group = ToggleGroup()
            for (style in listOf("solid", "dashed")) {
                val item = RadioMenuItem(style.replaceFirstChar { it.uppercase() })
                item.toggleGroup = group
                item.isSelected = getter() == style
                item.setOnAction { setter(style) }
                sub.items.add(item)
            }
            sub.setOnShowing {
                val current = getter()
                for (item in sub.items) {
                    if (item is RadioMenuItem) {
                        item.isSelected = item.text.lowercase() == current
                    }
                }
            }
            return sub
        }

        fun strokeThicknessRadioMenu(getter: () -> String, setter: (String) -> Unit): Menu {
            val sub = Menu("Stroke Thickness")
            val group = ToggleGroup()
            for (thickness in listOf("thin", "normal", "thick")) {
                val item = RadioMenuItem(thickness.replaceFirstChar { it.uppercase() })
                item.toggleGroup = group
                item.isSelected = getter() == thickness
                item.setOnAction { setter(thickness) }
                sub.items.add(item)
            }
            sub.setOnShowing {
                val current = getter()
                for (item in sub.items) {
                    if (item is RadioMenuItem) {
                        item.isSelected = item.text.lowercase() == current
                    }
                }
            }
            return sub
        }

        // Point sub-menu
        val pointMenu = Menu("Point")
        pointMenu.items.addAll(
            sizeRadioMenu("Size", { styleSettings.point.size }, { styleSettings.point.size = it }),
            colorRadioMenu("Color", { styleSettings.point.color }, { styleSettings.point.color = it })
        )

        // Geodesic sub-menu
        val geodesicMenu = Menu("Geodesic")
        geodesicMenu.items.addAll(
            colorRadioMenu("Color", { styleSettings.geodesic.color }, { styleSettings.geodesic.color = it }),
            strokeStyleRadioMenu({ styleSettings.geodesic.strokeStyle }, { styleSettings.geodesic.strokeStyle = it }),
            strokeThicknessRadioMenu({ styleSettings.geodesic.strokeThickness }, { styleSettings.geodesic.strokeThickness = it })
        )

        // Circle sub-menu
        val circleMenu = Menu("Circle")
        circleMenu.items.addAll(
            colorRadioMenu("Color", { styleSettings.circle.color }, { styleSettings.circle.color = it }),
            strokeStyleRadioMenu({ styleSettings.circle.strokeStyle }, { styleSettings.circle.strokeStyle = it }),
            strokeThicknessRadioMenu({ styleSettings.circle.strokeThickness }, { styleSettings.circle.strokeThickness = it })
        )

        // Angle sub-menu
        val angleMenu = Menu("Angle")
        angleMenu.items.addAll(
            sizeRadioMenu("Size", { styleSettings.angle.size }, { styleSettings.angle.size = it }),
            colorRadioMenu("Color", { styleSettings.angle.color }, { styleSettings.angle.color = it })
        )

        // Region sub-menu
        val regionMenu = Menu("Region")
        regionMenu.items.add(
            colorRadioMenu("Color", { styleSettings.region.color }, { styleSettings.region.color = it })
        )

        menu.items.addAll(pointMenu, geodesicMenu, circleMenu, angleMenu, regionMenu)
        return menu
    }

    private fun confirmUnsaved(undoManager: UndoManager): Boolean {
        if (!undoManager.hasUnsavedChanges()) return true
        val alert = Alert(Alert.AlertType.CONFIRMATION, "You have unsaved changes. Continue?")
        val result = alert.showAndWait()
        return result.isPresent && result.get() == ButtonType.OK
    }

    private fun resetDiagram(manager: GeometricObjectManager, undoManager: UndoManager, hitTester: HitTester) {
        manager.clear()
        undoManager.clear()
        undoManager.checkpoint()
        undoManager.markSaved()
        hitTester.rebuildIntersectionCache()
    }
}
