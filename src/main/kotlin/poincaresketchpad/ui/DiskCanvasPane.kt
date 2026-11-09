package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.ConstructionApplicator
import poincaresketchpad.model.GeometricObjectManager
import poincaresketchpad.model.ManagerState
import poincaresketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Rectangle
import kotlin.math.*

enum class TransformationMode {
    NONE, MOBIUS, ROTATION
}

class DiskCanvasPane(
    val manager: GeometricObjectManager,
    val undoManager: UndoManager
) : Pane() {

    companion object {
        const val MARGIN = 20.0
    }

    // Display transform
    var displayScale: Double = 1.0; private set
    var diskCenterX: Double = 0.0; private set
    var diskCenterY: Double = 0.0; private set
    val diskRadius: Double get() = displayScale

    fun getDiskScreenDiameter(): Double = displayScale * 2.0

    // Viewport Mobius transform
    var viewportA: Pair<Double, Double> = Pair(0.0, 0.0)
        private set
    var viewportTheta: Double = 0.0
        private set

    // Scene graph nodes
    private val diskCircle = FxCircle().apply {
        fill = Color.WHITE
        stroke = null
    }
    private val gridGroup = Group().apply { isMouseTransparent = true }
    val objectGroup = Group().apply { isMouseTransparent = true }
    val previewGroup = Group().apply { isMouseTransparent = true }
    val constructionPreviewGroup = Group().apply { isMouseTransparent = true }
    val exportOverlayGroup = Group().apply { isMouseTransparent = true }

    // Overlay labels
    private val hintLabel = Label().apply {
        style = "-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-padding: 8; -fx-font-size: 13px;"
        isVisible = false
        isMouseTransparent = true
    }

    private val recordingBanner = Label("Recording Construction").apply {
        style = "-fx-background-color: #FF5722; -fx-text-fill: white; -fx-padding: 8; -fx-font-weight: bold; -fx-font-size: 13px;"
        isVisible = false
        isMouseTransparent = true
    }

    // Tools and selection
    private var currentTool: Tool? = null
    var selectedIds: MutableSet<Int> = mutableSetOf()
    var hoveredId: Int? = null
    var gridVisible = false
    var suppressHover = false
    var onEscapeFallthrough: (() -> Unit)? = null
    var onRightClick: ((Double, Double) -> Unit)? = null

    // Hit tester
    val hitTester = HitTester(manager)

    // Space+click/drag for viewport
    private var spaceHeld = false
    private var isViewportDragging = false
    private var viewportDragStartAngle = 0.0

    // Apply mode state
    private var applyMode: ApplyModeState? = null

    private class ApplyModeState(
        val expectedTypes: List<String>,
        var currentIndex: Int = 0,
        val collectedIds: MutableList<Int> = mutableListOf(),
        val onComplete: (List<Int>) -> Unit,
        val onCancel: () -> Unit,
        val previewProvider: ((List<Int>, Int) -> List<GeometricObject>)? = null
    )

    private var previewObjects: List<GeometricObject> = emptyList()

    // Transformation mode state
    var transformationMode: TransformationMode = TransformationMode.NONE
        private set
    private var transformSnapshot: ManagerState? = null
    var onTransformModeChanged: (() -> Unit)? = null

    init {
        styleClass.add("canvas-pane")
        clip = Rectangle().also { clipRect ->
            clipRect.widthProperty().bind(widthProperty())
            clipRect.heightProperty().bind(heightProperty())
        }

        // Disk circle is the background
        children.add(diskCircle)
        // Grid behind objects, inside the disk clipping
        children.add(gridGroup)
        // Object group clipped to disk
        children.add(objectGroup)
        children.add(previewGroup)
        children.add(constructionPreviewGroup)
        children.add(exportOverlayGroup)

        // Overlay labels (outside clip)
        hintLabel.layoutXProperty().bind(widthProperty().divide(2).subtract(hintLabel.widthProperty().divide(2)))
        hintLabel.layoutYProperty().bind(heightProperty().subtract(40))
        children.add(hintLabel)

        recordingBanner.layoutXProperty().bind(widthProperty().divide(2).subtract(recordingBanner.widthProperty().divide(2)))
        recordingBanner.layoutY = 8.0
        children.add(recordingBanner)

        // Recalculate display transform on resize
        widthProperty().addListener { _, _, _ -> updateDisplayTransform() }
        heightProperty().addListener { _, _, _ -> updateDisplayTransform() }

        setupInputHandlers()
    }

    // --- Coordinate conversion ---

    fun modelToScreenX(mx: Double): Double = diskCenterX + mx * displayScale
    fun modelToScreenY(my: Double): Double = diskCenterY - my * displayScale

    fun screenToModelX(sx: Double): Double = (sx - diskCenterX) / displayScale
    fun screenToModelY(sy: Double): Double = -(sy - diskCenterY) / displayScale

    fun modelToScreen(mx: Double, my: Double): Pair<Double, Double> {
        val (vx, vy) = applyViewport(mx, my)
        return Pair(diskCenterX + vx * displayScale, diskCenterY - vy * displayScale)
    }

    fun screenToModel(sx: Double, sy: Double): Pair<Double, Double> {
        val vx = (sx - diskCenterX) / displayScale
        val vy = -(sy - diskCenterY) / displayScale
        return inverseViewport(vx, vy)
    }

    fun isInsideDisk(screenX: Double, screenY: Double): Boolean {
        val dx = screenX - diskCenterX
        val dy = screenY - diskCenterY
        return dx * dx + dy * dy < displayScale * displayScale
    }

    // --- Viewport Mobius transform ---

    fun applyViewport(mx: Double, my: Double): Pair<Double, Double> {
        val (ar, ai) = viewportA
        val (tr, ti) = HyperbolicUtils.mobiusTransform(mx, my, ar, ai)
        return HyperbolicUtils.mobiusRotation(tr, ti, viewportTheta)
    }

    fun inverseViewport(vx: Double, vy: Double): Pair<Double, Double> {
        // Inverse: first undo rotation, then undo translation
        val (ur, ui) = HyperbolicUtils.mobiusRotation(vx, vy, -viewportTheta)
        return HyperbolicUtils.mobiusInverse(ur, ui, viewportA.first, viewportA.second)
    }

    fun resetViewport() {
        viewportA = Pair(0.0, 0.0)
        viewportTheta = 0.0
        updateHitTesterParams()
        renderAll()
    }

    // --- Tool management ---

    fun setTool(tool: Tool?) {
        currentTool?.deactivate(previewGroup)
        currentTool = tool
        currentTool?.activate()
    }

    fun setRecordingMode(recording: Boolean) {
        recordingBanner.isVisible = recording
        if (recording) {
            styleClass.add("recording-border")
        } else {
            styleClass.remove("recording-border")
        }
    }

    // --- Apply mode ---

    fun startApplyMode(
        expectedTypes: List<String>,
        onComplete: (List<Int>) -> Unit,
        onCancel: () -> Unit,
        previewProvider: ((collectedIds: List<Int>, hoveredId: Int) -> List<GeometricObject>)? = null
    ) {
        applyMode = ApplyModeState(expectedTypes, 0, mutableListOf(), onComplete, onCancel, previewProvider)
        updateHintLabel()
        requestFocus()
    }

    val isInApplyMode: Boolean get() = applyMode != null

    private fun cancelApplyMode() {
        val mode = applyMode ?: return
        mode.onCancel()
        applyMode = null
        hintLabel.isVisible = false
        clearPreviewObjects()
    }

    private fun clearPreviewObjects() {
        previewObjects = emptyList()
        constructionPreviewGroup.children.clear()
    }

    private fun updateHintLabel() {
        val mode = applyMode
        if (mode == null && transformationMode == TransformationMode.NONE) {
            hintLabel.isVisible = false
            return
        }
        if (mode != null) {
            val idx = mode.currentIndex + 1
            val type = mode.expectedTypes[mode.currentIndex]
            hintLabel.text = "Input $idx: select a $type"
            hintLabel.isVisible = true
            return
        }
        // Transformation mode hint
        hintLabel.text = when (transformationMode) {
            TransformationMode.MOBIUS -> "M\u00F6bius: click inside the disk to set point a"
            TransformationMode.ROTATION -> "Rotation: click inside the disk to set the angle"
            else -> ""
        }
        hintLabel.isVisible = transformationMode != TransformationMode.NONE
    }

    // --- Transformation mode ---

    fun enterTransformationMode(mode: TransformationMode) {
        if (mode == TransformationMode.NONE) return
        transformationMode = mode
        transformSnapshot = manager.snapshot()
        updateHintLabel()
        requestFocus()
        onTransformModeChanged?.invoke()
    }

    fun cancelTransformationMode() {
        val snap = transformSnapshot ?: return
        manager.restore(snap)
        transformationMode = TransformationMode.NONE
        transformSnapshot = null
        hintLabel.isVisible = false
        onTransformModeChanged?.invoke()
        renderAll()
    }

    private fun commitTransformation(modelX: Double, modelY: Double) {
        applyTransformation(modelX, modelY)
        undoManager.checkpoint()
        transformationMode = TransformationMode.NONE
        transformSnapshot = null
        hintLabel.isVisible = false
        onTransformModeChanged?.invoke()
        hitTester.rebuildIntersectionCache()
        renderAll()
    }

    private fun applyTransformation(modelX: Double, modelY: Double) {
        // Restore the snapshot to get original positions
        val snap = transformSnapshot ?: return
        manager.restore(snap)

        when (transformationMode) {
            TransformationMode.MOBIUS -> {
                applyMobiusTranslation(modelX, modelY)
            }
            TransformationMode.ROTATION -> {
                val theta = atan2(modelY, modelX)
                applyRotation(theta)
            }
            TransformationMode.NONE -> {}
        }
    }

    private fun applyMobiusTranslation(ax: Double, ay: Double) {
        // Record semi-free point positions before transform
        val semiFreePositions = mutableMapOf<Int, Pair<Double, Double>>()
        for (obj in manager.allObjects()) {
            if (obj is FreePointOnLine || obj is FreePointOnRay ||
                obj is FreePointOnSegment || obj is FreePointOnCircle) {
                if (obj is PointLike) {
                    semiFreePositions[obj.id] = Pair(obj.x, obj.y)
                }
            }
        }

        // Apply T(z) = (z - a) / (1 - conj(a)*z) to all free points
        for (obj in manager.allObjects()) {
            if (obj is FreePoint) {
                val (nr, ni) = HyperbolicUtils.mobiusTransform(obj.x, obj.y, ax, ay)
                val (cx, cy) = HyperbolicUtils.clampToDisk(nr, ni)
                obj.x = cx
                obj.y = cy
            }
        }

        // Propagate
        manager.propagateFrom(0)

        // Update semi-free point parameters from transformed positions
        for ((id, pos) in semiFreePositions) {
            val (tz, ti) = HyperbolicUtils.mobiusTransform(pos.first, pos.second, ax, ay)
            manager.movePoint(id, tz, ti)
        }
    }

    private fun applyRotation(theta: Double) {
        val semiFreePositions = mutableMapOf<Int, Pair<Double, Double>>()
        for (obj in manager.allObjects()) {
            if (obj is FreePointOnLine || obj is FreePointOnRay ||
                obj is FreePointOnSegment || obj is FreePointOnCircle) {
                if (obj is PointLike) {
                    semiFreePositions[obj.id] = Pair(obj.x, obj.y)
                }
            }
        }

        for (obj in manager.allObjects()) {
            if (obj is FreePoint) {
                val (nr, ni) = HyperbolicUtils.mobiusRotation(obj.x, obj.y, theta)
                val (cx, cy) = HyperbolicUtils.clampToDisk(nr, ni)
                obj.x = cx
                obj.y = cy
            }
        }

        manager.propagateFrom(0)

        for ((id, pos) in semiFreePositions) {
            val (rr, ri) = HyperbolicUtils.mobiusRotation(pos.first, pos.second, theta)
            manager.movePoint(id, rr, ri)
        }
    }

    private fun previewTransformation(modelX: Double, modelY: Double) {
        applyTransformation(modelX, modelY)
        renderAll()
    }

    // --- Display transform ---

    private fun updateDisplayTransform() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        displayScale = min(w, h) / 2.0 - MARGIN
        diskCenterX = w / 2.0
        diskCenterY = h / 2.0

        // Update disk circle
        diskCircle.centerX = diskCenterX
        diskCircle.centerY = diskCenterY
        diskCircle.radius = displayScale

        // Update object group clip to disk
        objectGroup.clip = FxCircle(diskCenterX, diskCenterY, displayScale)

        updateHitTesterParams()
        renderAll()
    }

    private fun updateHitTesterParams() {
        hitTester.diskCenterX = diskCenterX
        hitTester.diskCenterY = diskCenterY
        hitTester.displayScale = displayScale
        hitTester.viewportA = viewportA
        hitTester.viewportTheta = viewportTheta
    }

    // --- Input handlers ---

    private fun setupInputHandlers() {
        isFocusTraversable = true

        setOnKeyPressed { e -> onKeyPressed(e) }
        setOnKeyReleased { e ->
            if (e.code == KeyCode.SPACE) {
                spaceHeld = false
                isViewportDragging = false
            }
        }

        setOnMousePressed { e -> onMousePressed(e) }
        setOnMouseDragged { e -> onMouseDragged(e) }
        setOnMouseReleased { e -> onMouseReleased(e) }
        setOnMouseMoved { e -> onMouseMoved(e) }
    }

    private fun onKeyPressed(e: KeyEvent) {
        when {
            e.code == KeyCode.SPACE -> spaceHeld = true
            e.code == KeyCode.ESCAPE -> handleEscape()
        }
    }

    private fun handleEscape() {
        // Priority order per SPEC.md Section 14
        when {
            transformationMode != TransformationMode.NONE -> cancelTransformationMode()
            applyMode != null -> cancelApplyMode()
            currentTool?.onEscapePressed(previewGroup) == true -> { /* tool handled it */ }
            else -> onEscapeFallthrough?.invoke()
        }
    }

    private fun onMousePressed(e: MouseEvent) {
        requestFocus()
        if (e.button == MouseButton.SECONDARY) {
            onRightClick?.invoke(e.screenX, e.screenY)
            return
        }
        if (e.button != MouseButton.PRIMARY) return

        // Space+Click: viewport Mobius translation (send clicked point to origin)
        if (spaceHeld) {
            if (isInsideDisk(e.x, e.y)) {
                val (mx, my) = screenToModel(e.x, e.y)
                // Compose with existing viewport: new viewportA sends clicked model point to origin
                // The viewport transform is T(z) = rotation(mobius(z, a), theta)
                // We want a new transform that additionally sends the clicked model point to the display origin.
                // The clicked point in viewport space is applyViewport(mx, my).
                val (vx, vy) = applyViewport(mx, my)
                // Compose: new transform sends vx,vy to origin first, then continues.
                // New transform: T_new(z) = (T_old(z) - v) / (1 - conj(v) * T_old(z))
                // This is equivalent to composing a new Mobius at the viewport output.
                // Actually, we can represent it as: after existing transform, apply another mobius.
                // Store as composed: apply existing transform, then translate by -v.
                // But the spec says Space+Click sends clicked point to origin via Mobius composition.
                // Let's compose directly: viewportA and viewportTheta define T(z).
                // T_composed(z) = mobius(T(z), v)
                // This needs to be expressed as a single mobius of the form e^{i*theta'} * (z-a')/(1-conj(a')*z).
                // Alternative: just compose the A parameter.
                // For simplicity: the viewport transform applied to the clicked model point
                // gives (vx, vy). We compose by setting a new a' such that (vx, vy) maps to origin.

                // Compose: T_new(z) = mobius(T_old(z), (vx, vy))
                // T_old(z) = e^{i*theta} * (z - a) / (1 - conj(a)*z)
                // T_new(z) = (T_old(z) - v) / (1 - conj(v) * T_old(z))
                // = (e^{i*theta} * (z-a)/(1-conj(a)z) - v) / (1 - conj(v) * e^{i*theta} * (z-a)/(1-conj(a)z))

                // Rather than computing the composed parameters algebraically, we can just
                // compose at evaluation time. But since our viewport is represented as (a, theta),
                // let's compute the new (a', theta') that give the same result.

                // Simpler approach: inverse-viewport vx,vy back to model, use that as new a parameter.
                // Actually, the cleanest approach: the viewport is a composition stack.
                // But to keep things simple with our single (a, theta) representation:

                // T_composed(z) = mobius(e^{i*theta} * mobius(z, a), v)
                //                = mobius(rotation(mobius(z, a), theta), v)
                //
                // We want T_composed in the form: e^{i*theta'} * (z - a') / (1 - conj(a')*z)
                //
                // Let's compute: T_composed maps a' (new a) to 0 (the model point that goes to origin).
                // The model point that goes to origin under T_composed is the point z where T_composed(z) = 0.
                // T_composed(z) = 0 means mobius(rotation(mobius(z,a), theta), v) = 0
                // => rotation(mobius(z,a), theta) = v
                // => mobius(z,a) = rotation(v, -theta) = e^{-i*theta} * v
                // Let w = e^{-i*theta} * v, then z = mobiusInverse(w, a)
                val (wr, wi) = HyperbolicUtils.mobiusRotation(vx, vy, -viewportTheta)
                val (newAr, newAi) = HyperbolicUtils.mobiusInverse(wr, wi, viewportA.first, viewportA.second)

                // For theta': T_composed(0) should equal the current T_old(0) composed with the extra mobius.
                // T_old(0) = e^{i*theta} * (0 - a)/(1 - 0) = e^{i*theta} * (-a)
                // T_composed(0) = mobius(T_old(0), v) = (T_old(0) - v) / (1 - conj(v)*T_old(0))
                // For the representation e^{i*theta'} * (0 - a')/(1 - conj(a')*0) = -e^{i*theta'} * a'
                // So e^{i*theta'} = T_composed(0) / (-a')
                // We can compute T_composed at any point to determine theta'.

                // Simpler: just compute T_composed at z=0 to find theta'.
                val (t0r, t0i) = HyperbolicUtils.mobiusTransform(0.0, 0.0, viewportA.first, viewportA.second)
                val (t0rr, t0ri) = HyperbolicUtils.mobiusRotation(t0r, t0i, viewportTheta)
                val (c0r, c0i) = HyperbolicUtils.mobiusTransform(t0rr, t0ri, vx, vy)

                // T_composed(0) = e^{i*theta'} * (0 - newA) / (1 - conj(newA)*0) = -e^{i*theta'} * newA
                // So e^{i*theta'} = -T_composed(0) / newA = T_composed(0) / (-newA)
                val (negA_r, negA_i) = Pair(-newAr, -newAi)
                val normSq = negA_r * negA_r + negA_i * negA_i
                if (normSq > HyperbolicUtils.EPSILON) {
                    val (eir, eii) = HyperbolicUtils.complexDiv(c0r, c0i, negA_r, negA_i)
                    val newTheta = atan2(eii, eir)
                    viewportA = Pair(newAr, newAi)
                    viewportTheta = newTheta
                } else {
                    // newA is at origin — just adjust theta
                    viewportA = Pair(newAr, newAi)
                    viewportTheta = atan2(c0i, c0r)
                }

                isViewportDragging = true
                viewportDragStartAngle = atan2(e.y - diskCenterY, e.x - diskCenterX)
                updateHitTesterParams()
                hitTester.rebuildIntersectionCache()
                renderAll()
            }
            return
        }

        // Transformation mode
        if (transformationMode != TransformationMode.NONE) {
            if (isInsideDisk(e.x, e.y)) {
                val (mx, my) = screenToModel(e.x, e.y)
                commitTransformation(mx, my)
            }
            return
        }

        // Apply mode intercepts clicks
        val mode = applyMode
        if (mode != null) {
            handleApplyModeClick(e)
            return
        }

        val (worldX, worldY) = screenToModel(e.x, e.y)
        currentTool?.onMousePressed(e, worldX, worldY)
    }

    private fun handleApplyModeClick(e: MouseEvent) {
        val mode = applyMode ?: return
        val expectedType = mode.expectedTypes[mode.currentIndex]

        val hit = hitTester.hitTest(e.x, e.y)
        val hitId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnGeodesic -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            else -> null
        }
        if (hitId == null) return

        val obj = manager.getObject(hitId) ?: return
        if (!ConstructionApplicator.matchesExpectedType(obj, expectedType)) return

        mode.collectedIds.add(hitId)
        mode.currentIndex++

        if (mode.currentIndex >= mode.expectedTypes.size) {
            val collected = mode.collectedIds.toList()
            applyMode = null
            hintLabel.isVisible = false
            clearPreviewObjects()
            mode.onComplete(collected)
        } else {
            updateHintLabel()
        }
    }

    private fun onMouseDragged(e: MouseEvent) {
        if (spaceHeld && isViewportDragging) {
            // Space+Drag: rotate viewport
            val currentAngle = atan2(e.y - diskCenterY, e.x - diskCenterX)
            val deltaAngle = -(currentAngle - viewportDragStartAngle) // negate for screen y-flip
            viewportTheta += deltaAngle
            viewportDragStartAngle = currentAngle
            updateHitTesterParams()
            hitTester.rebuildIntersectionCache()
            renderAll()
            return
        }

        if (transformationMode != TransformationMode.NONE) return

        if (e.isPrimaryButtonDown) {
            if (applyMode != null) return
            val (worldX, worldY) = screenToModel(e.x, e.y)
            currentTool?.onMouseDragged(e, worldX, worldY)
            updateHover(e.x, e.y)
        }
    }

    private fun onMouseReleased(e: MouseEvent) {
        if (spaceHeld && isViewportDragging) {
            isViewportDragging = false
            return
        }
        if (transformationMode != TransformationMode.NONE) return
        if (applyMode != null) return
        val (worldX, worldY) = screenToModel(e.x, e.y)
        currentTool?.onMouseReleased(e, worldX, worldY)
    }

    private fun onMouseMoved(e: MouseEvent) {
        // Transformation mode: live preview
        if (transformationMode != TransformationMode.NONE) {
            if (isInsideDisk(e.x, e.y)) {
                val (mx, my) = screenToModel(e.x, e.y)
                previewTransformation(mx, my)
            }
            return
        }

        val (worldX, worldY) = screenToModel(e.x, e.y)
        if (applyMode == null) {
            currentTool?.onMouseMoved(e, worldX, worldY)
        }
        updateHover(e.x, e.y)
    }

    private fun updateHover(screenX: Double, screenY: Double) {
        if (suppressHover) {
            if (hoveredId != null) {
                hoveredId = null
                renderAll()
            }
            return
        }

        val mode = applyMode
        if (mode != null) {
            // Type-filtered hover: only highlight objects matching the current expected type
            val expectedType = mode.expectedTypes[mode.currentIndex]
            val hit = hitTester.hitTest(screenX, screenY)
            val hitId = when (hit) {
                is HitResult.ConstructedPoint -> hit.id
                is HitResult.OnAngle -> hit.id
                is HitResult.OnGeodesic -> hit.id
                is HitResult.OnCircle -> hit.id
                is HitResult.OnRegion -> hit.id
                else -> null
            }
            val newHoveredId = if (hitId != null) {
                val obj = manager.getObject(hitId)
                if (obj != null && ConstructionApplicator.matchesExpectedType(obj, expectedType)) hitId else null
            } else null

            if (newHoveredId != hoveredId) {
                hoveredId = newHoveredId
                updatePreviewObjects(mode, newHoveredId)
                renderAll()
            }
            return
        }

        val newHoveredId = when (val hit = hitTester.hitTest(screenX, screenY)) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnGeodesic -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            else -> null
        }
        if (newHoveredId != hoveredId) {
            hoveredId = newHoveredId
            renderAll()
        }
    }

    private fun updatePreviewObjects(mode: ApplyModeState, newHoveredId: Int?) {
        val provider = mode.previewProvider
        val isLastInput = mode.currentIndex == mode.expectedTypes.size - 1
        if (provider != null && isLastInput && newHoveredId != null) {
            previewObjects = provider(mode.collectedIds.toList(), newHoveredId)
        } else {
            previewObjects = emptyList()
        }
    }

    // --- Rendering ---

    fun renderAll() {
        gridGroup.children.clear()
        if (gridVisible) {
            GeometryRenderer.renderGrid(
                gridGroup, diskCenterX, diskCenterY, displayScale, viewportA, viewportTheta
            )
        }

        GeometryRenderer.renderAll(
            manager, objectGroup, diskCenterX, diskCenterY, displayScale,
            viewportA, viewportTheta, selectedIds, hoveredId
        )

        constructionPreviewGroup.children.clear()
        if (previewObjects.isNotEmpty()) {
            GeometryRenderer.renderPreviewObjects(
                previewObjects, constructionPreviewGroup,
                diskCenterX, diskCenterY, displayScale,
                viewportA, viewportTheta
            )
        }
    }

    /**
     * Create a ViewportParams for use by tools calling static renderer methods.
     */
    fun viewportParams(): GeometryRenderer.ViewportParams {
        return GeometryRenderer.ViewportParams(
            diskCenterX, diskCenterY, displayScale, viewportA, viewportTheta
        )
    }
}
