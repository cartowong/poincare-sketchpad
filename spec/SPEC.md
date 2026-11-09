# Poincare Sketchpad — Application Specification

A desktop application for creating geometric constructions using compass and straightedge in the Poincare disk model of hyperbolic geometry. Geometry engine details are in [GEOMETRY.md](GEOMETRY.md).

---

## 1. Technology Stack

| Decision | Choice |
|----------|--------|
| Language | Kotlin |
| UI framework | JavaFX (OpenJFX) |
| Rendering | JavaFX scene graph (Shape nodes in a transformed Group) |
| Build tool | Gradle (Kotlin DSL) |
| Testing | JUnit 5 (geometry library unit tests only) |
| Serialization | kotlinx.serialization (JSON) |
| Distribution | `jpackage` for native installer; also runnable via `./gradlew run` |
| UI theme | AtlantaFX Primer Light (user agent stylesheet) |
| Input | Desktop only (mouse), no touch support |

---

## 2. Architecture

### 2.1 Package Structure

```
src/main/kotlin/poincaresketchpad/
  geometry/         # Pure geometry library (no JavaFX imports) — see GEOMETRY.md
  model/            # GeometricObjectManager, undo/redo, construction recording/applying
  persistence/      # JSON serialization/deserialization for diagrams and constructions
  ui/               # JavaFX views, canvas, tools, renderer, sidebar, toolbar
  export/           # PNG and SVG export (dialog + renderer)
  Main.kt           # Application entry point; dispatches --export to HeadlessExportApp

src/main/resources/
  styles/geometry.css   # JavaFX CSS stylesheet
  icons/                # Tool button icons (24x24 PNG) and app icon
```

### 2.2 Key Classes

| Class | Package | Responsibility |
|-------|---------|---------------|
| `GeometricObjectManager` | model | Owns the object map (`LinkedHashMap<Int, GeometricObject>`) and metadata map. Provides `addObject`, `removeWithDescendants`, `movePoint`, `propagateFrom`, `snapshot`/`restore`, `nextDefaultLabel`. |
| `ObjectMetadata` | model | Per-object mutable state: `visible`, `label`, `labelVisible`, `labelOffsetX/Y`, `inMeasurementTable`, `labelAnchorAngle`, `labelAnchorT`, `isInput`, `isLocked`, style fields (`color`, `size`, `strokeStyle`, `strokeThickness`). |
| `StyleSettings` | model | Application-level default styles for newly created objects. One set of defaults per object category (Point, Geodesic, Circle, Angle, Region). |
| `UndoManager` | model | Snapshot-based undo/redo. Stores a list of `ManagerState` snapshots. Tracks saved index for unsaved-changes detection. Supports `minIndex` clamping for recording mode. |
| `ConstructionRecorder` | model | Manages recording lifecycle: `startRecording`, `cancelRecording`, `endRecording`. Sets `isInput`/`isLocked` flags. |
| `ConstructionApplicator` | model | Applies a loaded construction to the current diagram. Handles ID remapping, type matching, and construction preview computation. |
| `DiskCanvasPane` | ui | JavaFX `Pane` subclass. Owns the disk clipping circle, object group, preview group, construction preview group. Handles mouse dispatch, apply mode, transformation mode, and rendering. |
| `GeometryRenderer` | ui | Renders all geometric objects into a JavaFX Group. Scales model coordinates to screen. Renders geodesics as arcs, circles as circles, points as dots, labels as text. |
| `HitTester` | ui | Screen-pixel-space hit-testing. Intersection cache + tiered hit-testing. All distance comparisons in screen pixels. |
| `Sidebar` | ui | Left pane (objects table) and right pane (constructions + measurements tables). |
| `DiagramSerializer` | persistence | Serializes/deserializes the full diagram to/from JSON. |
| `ConstructionSerializer` | persistence | Saves constructions and loads construction files into `LoadedConstruction` records. |
| `ExportRenderer` | export | Renders to PNG (via JavaFX snapshot) or SVG (manual string generation). |
| `HeadlessExportApp` | export | CLI-driven headless export. Loads a diagram JSON and exports files. |

### 2.3 Coordinate System

- **Model coordinates**: the open unit disk `{(x, y) : x^2 + y^2 < 1}`. Origin at center.
- **Display transform**: a uniform scale from the unit disk to screen pixels, plus translation to the disk center on screen. `screenPos = diskCenter + displayScale * modelPos`, with y-axis flipped for screen coordinates.
- **Display scale**: computed dynamically as `min(availableWidth, availableHeight) / 2 - margin`, where margin is a fixed pixel value.
- **No user-controlled zoom.** The disk always fills the available canvas area.

### 2.4 Dependency Graph

- Every geometric object has an **immutable integer ID** starting at 0, assigned by `GeometricObjectManager.addObject()` via an incrementing counter.
- Each object stores a list of parent IDs. A parent's ID is always lower than its child's.
- **Update propagation**: `propagateFrom(startId)` iterates objects by ID from `startId` upward, calling `recompute()` on each. The ID ordering is a valid topological sort by construction.
- **Undefined cascading**: when an object's `recompute()` finds an undefined parent, it sets `isDefined = false`. All descendants will also become undefined via their own `recompute()` calls.

---

## 3. Tools

### 3.1 Tool Interface

All tools implement the `Tool` interface:

```
interface Tool {
    activate()
    deactivate(previewGroup)
    onMousePressed(event, worldX, worldY)
    onMouseMoved(event, worldX, worldY)
    onMouseDragged(event, worldX, worldY)
    onMouseReleased(event, worldX, worldY)
    onEscapePressed(previewGroup) -> Boolean
}
```

All methods have default no-op implementations. `onEscapePressed` returns `true` if an in-progress operation was cancelled. The `worldX, worldY` parameters are in model coordinates (unit disk), obtained by inverting the display transform from screen coordinates.

### 3.2 Toolbar

- **9 tool buttons** in a horizontal `HBox`, left-aligned, above the canvas: Select, Point, Segment, Ray, Line, Circle, Angle, Region, Label.
- **Radio behavior** via a `ToggleGroup`: one tool active at a time.
- Active tool has a thick dark border (`2.5px #333`). Inactive buttons have a gray background (`#E0E0E0`).
- Each button is 32x32px with a 24x24px icon loaded from `/icons/<name>.png`. Falls back to text label if icon is missing.
- Switching tools clears the current selection.

### 3.3 Point Tool

- **Click inside the disk**: creates a `FreePoint` at the click position (in model coordinates).
- **Click outside the disk**: ignored.
- **Click on intersection preview**: constructs the appropriate intersection point (`GeodesicGeodesicIntersection`, `GeodesicCircleIntersection`, or `CircleCircleIntersection`).
- **Click on a geodesic object**: creates a semi-free point (`FreePointOnLine`, `FreePointOnRay`, or `FreePointOnSegment`) constrained to that geodesic, with `t` computed from the click position.
- **Click on a circle**: creates a `FreePointOnCircle` constrained to that circle, with `theta` computed from the click position.
- **Click on an existing point**: no-op.
- **Mouse move**: when hovering near a cached intersection, shows a ghost point at preview opacity.

### 3.4 Selection Tool

- **Click an object**: deselects all other objects, selects the clicked object. If the clicked object is already the only selected object, deselects it.
- **Modifier+click an object** (`isShortcutDown` — Cmd on macOS, Ctrl on others): toggles the clicked object's selection without affecting other selected objects.
- **Click on empty canvas**: clears all selection (with or without modifier).
- **Selected objects** are rendered in blue (stroke and fill).
- **Dragging**: works only when **all** selected objects pass `manager.isMovable()` (free or semi-free points). The tool:
  1. On mousedown, takes a snapshot. Selection rules (plain click deselects others, modifier+click toggles) are applied before the drag begins.
  2. On drag, applies the displacement to each selected point via `manager.movePoint()`. Free points are clamped to `|z| < 1 - epsilon`.
  3. On mouseup, creates an undo checkpoint. The entire drag is a single undo entry.
- If any selected object is not movable, dragging does nothing.

### 3.5 Line / Ray / Segment Tools

A single `GeodesicTool` class with a `subMode: GeodesicSubMode` enum (`LINE`, `RAY`, `SEGMENT`). Three toolbar buttons each set the sub-mode.

- **First click**: resolves or creates the first point (snapping to existing points or intersections). Takes a pre-construction snapshot.
- **Second click**: resolves or creates the second point. Creates the geodesic object (`Line`, `Ray`, or `Segment`). Creates an undo checkpoint.
- **Same point**: if second click resolves to the same point as the first, the click is ignored.
- **Preview**: after first click, mouse move renders a ghost geodesic arc from the first point to the cursor position, plus ghost endpoints.
- **Escape**: restores the pre-construction snapshot, cancelling any auto-constructed first point.

### 3.6 Circle Tool

Same two-click pattern as the Geodesic Tool:
- First click: center point (hyperbolic center).
- Second click: radius point (point on the circle). Creates a `Circle`.
- The circle is the set of points at equal hyperbolic distance from the center.

### 3.7 Angle Tool

Three-click pattern:
- Click point A, then vertex B, then point C.
- Creates an `Angle(A, B, C)`.
- Same auto-construction and preview behavior as the Geodesic Tool.

### 3.8 Region Tool

Multi-click pattern:
- Each click adds a vertex point (auto-constructed if needed).
- **Closing**: when hovering within snap threshold of the first point, clicking closes the polygon, creating a `Region` with the collected point IDs.
- **Escape**: restores to pre-construction snapshot, cancelling all auto-constructed points.

### 3.9 Label Tool

- **Click an unlabeled object**: assigns the next default label (A, B, C, ..., AA, AB, ...) and makes it visible.
- **Click a visible-label object**: hides the label (keeps the label text, sets `labelVisible = false`).
- **Click a hidden-label object**: re-shows the previous label.
- **Click on a visible label** (without dragging): hides the label. This ensures labels on angles and regions (where the label text overlaps the only clickable area) can always be toggled off.
- **Drag a label**: repositions it. The offset (`labelOffsetX`, `labelOffsetY`) is stored in screen pixels relative to the object's anchor point on screen. A drag threshold of 3px distinguishes clicks from drags.
- Label anchor points (all in screen coordinates after display transform):
  - Points: screen position of the point.
  - Geodesics: interpolated along the rendered arc at parameter `t`. Default `t = 0.5` for lines/segments, `t = 0.0` for rays.
  - Circles: point on the rendered Euclidean circle at `labelAnchorAngle` (default `PI/2`).
  - Angles: screen position of the vertex.
  - Regions: centroid of vertex screen positions.

---

## 4. UI Layout

### 4.1 Overall Layout

Three-column layout. Initial window size: **1440x720**. Initial divider positions: **30% / 70%** (left edge at 30%, right edge at 70%).

```
+------------------+-------------------------------------------+------------------+
|   Left pane      |           Center column                   |   Right pane     |
|                  |  +-------------------------------------+  |                  |
| [x] Show hidden  |  | Toolbar: [S][P][/][/][/][O][<][tri][A]|  | Constructions   |
|                  |  +-------------------------------------+  | table (top)     |
| Objects table    |  |         +-----+                     |  |                  |
| (scrollable)     |  |        /       \    gray             |  +-----------+     |
|                  |  |       | white   |   exterior         |  | Measurements|    |
|                  |  |       | disk    |                    |  | table       |    |
|                  |  |        \       /                     |  | (bottom)    |    |
|                  |  |         +-----+                     |  |             |    |
+------------------+-------------------------------------------+------------------+
```

- **Left pane**: "Show hidden objects" checkbox + Object List table.
- **Center column**: `VBox` containing the toolbar and canvas pane. The canvas pane has a gray background; the disk is rendered as a white circle centered in the available space.
- **Right pane**: vertical `SplitPane` with Custom Constructions table (top) and Measurements table (bottom). Divider at 50%.
- **Table headers**: each table ("Object List", "Custom Constructions", "Measurements") has a header row with the title left-aligned and a close button ("X") right-aligned. Clicking the close button hides that table.
- **Pane visibility**: toggled via View menu or header close buttons. When all tables in a pane are hidden, the pane is removed from the outer `SplitPane`. When a table is shown again, its pane is re-added with default divider positions. Not persisted across sessions.
- **Canvas resizing**: when panes are hidden or shown, or when dividers are dragged, the canvas area changes. The disk automatically resizes to fill the available space (largest circle that fits with fixed margins), always centered.

### 4.2 Object List Table

| Column | Width | Description |
|--------|-------|-------------|
| ID | 40px fixed | Object ID |
| Type | 120px fixed | Display type name |
| Parents | flex | Comma-separated parent IDs |
| Label | flex | Editable inline (double-click); commit triggers undo checkpoint |
| Show | 55px fixed | Checkbox to toggle visibility. Lock icon during recording. |
| Del | 40px fixed | "X" button to delete (with confirmation dialog). Lock icon during recording. |

**Type display names** (exhaustive):

| Class | Display Name |
|-------|-------------|
| `FreePoint` | Free Point |
| `FreePointOnLine` | Point on Line |
| `FreePointOnRay` | Point on Ray |
| `FreePointOnSegment` | Point on Segment |
| `FreePointOnCircle` | Point on Circle |
| `GeodesicGeodesicIntersection` | Intersection Point |
| `GeodesicCircleIntersection` | Intersection Point |
| `CircleCircleIntersection` | Intersection Point |
| `Midpoint` | Midpoint |
| `ReflectionPoint` | Reflection Point |
| `Line` | Line |
| `Ray` | Ray |
| `Segment` | Segment |
| `PerpendicularLine` | Perpendicular Line |
| `Circle` | Circle |
| `Angle` | Angle |
| `Region` | Region |

- **"Show hidden objects"** checkbox: when unchecked, rows with `visible = false` are hidden from the table.
- **Two-way selection binding**: clicking a row selects the object on canvas (deselecting all others). Selecting on canvas highlights the row. Modifier+click (Cmd/Ctrl) toggles without deselecting others — same behavior as the canvas Selection Tool.
- **Delete**: removes the object and all descendants via `manager.removeWithDescendants()`.

### 4.3 Measurement Table

| Column | Width | Description |
|--------|-------|-------------|
| ID | 40px fixed | Object ID |
| Type | 120px fixed | Display type name |
| Label | flex | Object label |
| Value | flex | Formatted measurement value |
| Del | 40px fixed | "X" button to remove from table (does not delete object) |

**Measurable types**: Segment (hyperbolic length, `"%.2f"`), Circle (hyperbolic circumference, `"%.2f"`), Angle (degrees, `"%.1f\u00B0"`), Region (hyperbolic area, `"%.2f"`).

Measurements update on every render (real-time during drag).

### 4.4 Custom Constructions Table

| Column | Width | Description |
|--------|-------|-------------|
| Apply | 60px fixed | Button to start the apply flow |
| File | flex | Construction filename (without `.json`) |
| Inputs | flex | Comma-separated input type names |
| Del | 40px fixed | "X" button to remove from table |

- Populated via Construction > Load Construction or Load from Folder.
- Not persisted across sessions.
- Point-like input types display as "Point". Non-point types display as their exact type name.

### 4.5 Canvas

- JavaFX `Pane` (`DiskCanvasPane`) with clipping rectangle bound to its size.
- **Disk area**: a `Circle` shape centered in the pane, filled white. Radius = `min(width, height) / 2 - margin`.
- **Exterior area**: pane background is light gray (`#E8E8E8`).
- **No boundary circle stroke** — the disk is an open disk. The white-to-gray contrast defines the edge.
- **Object group**: `Group` for rendered geometric objects, clipped to the disk circle.
- **Preview group**: for tool previews (ghost arcs, ghost points).
- **Construction preview group**: for construction apply previews.
- **Export overlay group**: for the crop rectangle overlay during Custom export (see Section 12.4).
- **Hint label**: positioned at bottom center. Shows `"Input N: select a <type>"` during apply mode, or transformation hints during transformation mode (see Section 13).
- **Recording banner**: positioned at top center. Shows `"Recording Construction"` during recording. Orange background (`#FF5722`).

### 4.6 Grid

- **Concentric circles** at equal hyperbolic distance intervals (~1.0 hyperbolic units) from the origin. Euclidean radii: `tanh(n * d / 2)` for `n = 1, 2, 3, ...`. Circles bunch up near the boundary.
- **Radial lines**: 6 diameters at 30-degree intervals through the disk center.
- Grid is always centered at the origin.
- Grid lines: gray (`0.85`), 0.5px stroke.
- **Toggleable** via View > Show Grid (Cmd+G). **Off by default.**

---

## 5. Visual Styling

### 5.1 Colors and Sizes

Colors are set **programmatically** in the renderer, not via CSS classes.

Each object has per-object style properties stored in `ObjectMetadata`. The renderer reads these to determine color, size, stroke style, and stroke thickness. See Section 15 for details.

| Element | Property | Default Value |
|---------|----------|---------------|
| Points | Fill | Solid filled circle, per-object color (default black) |
| Points | Radius | Per-object size: small 3px, medium 4px, large 6px (screen pixels). Default medium |
| Geodesics/Circles | Stroke | Per-object thickness: thin 1px, normal 2px, thick 3px (screen pixels). Default normal |
| Geodesics/Circles | Stroke style | Per-object: solid or dashed (6px dash, 4px gap). Default solid |
| Geodesics/Circles | Color | Per-object color (default black) |
| Region fill | | Per-object color at 0.3 opacity (default yellow `#FFEB3B`) |
| Angle fill | | Per-object color at 0.3 opacity (default green `#4CAF50`) |
| Angle stroke | | Per-object color at 0.7 opacity, 1px |
| Angle arc radius | | Per-object size: small 20px, medium 25px, large 30px (screen pixels). Default medium |
| Labels | Fill | Always black, regardless of object color |

**Color palette** (stored as name strings in metadata, mapped to hex at render time):

| Name | Hex |
|------|-----|
| black | `#000000` |
| brown | `#795548` |
| green | `#4CAF50` |
| purple | `#9C27B0` |
| red | `#F44336` |
| yellow | `#FFEB3B` |

### 5.2 State Styles

| State | Style |
|-------|-------|
| Normal | Per-object color, per-object size |
| Selected | Blue (`#2196F3`) fill and stroke, overrides per-object color |
| Hover | Blue (`#2196F3`), 1.5x stroke width (1.3x point radius), overrides per-object color |
| Preview | Black, 0.4 opacity |
| Construction preview | Orange (`#FF5722`), 0.4 opacity |

### 5.3 Rendering Order

The renderer makes 5 passes in order (back to front):
1. **Regions** (filled polygons with geodesic-arc edges)
2. **Angles** (arc sectors)
3. **Geodesics and Circles** (strokes — arcs for geodesics, circles for circles)
4. **Points** (filled circles, on top)
5. **Labels** (text)

### 5.4 Geodesic Rendering

- **Lines**: rendered as the full arc (or diameter) inside the disk, from one boundary intersection to the other. No arrowheads.
- **Rays**: rendered as the arc from the endpoint to the boundary intersection in the extending direction. No arrowheads.
- **Segments**: rendered as the arc between both endpoints. No arrowheads.
- **Diameter geodesics** (through the origin): rendered as straight line chords.
- **No viewport clipping needed** — all geometry is contained within the disk.

### 5.5 CSS Stylesheet

`geometry.css` handles structural styling only:
- Canvas background: `#E8E8E8` (light gray).
- Toolbar background: `#f0f0f0` with bottom border.
- Sidebar backgrounds: `#f8f8f8` with borders.
- Recording mode: orange border (`#FF5722`, 3px) on canvas.
- Table row selection: blue (`#2196F3`) background, white text.

---

## 6. Hit-Testing

### 6.1 Intersection Cache

All pairwise intersections among **visible, non-locked** geodesic and circle objects are precomputed. The cache computes Euclidean circle-circle (or line-circle, line-line) intersections and filters to points inside the unit disk. Results are stored as screen-pixel coordinates for hit-testing.

The cache is rebuilt when objects are added, removed, moved, or have visibility changed.

### 6.2 Hit-Testing in Screen-Pixel Space

All hit-testing operates in **screen-pixel coordinates**. The snap radius is **10 screen pixels** (fixed, no conversion needed).

For each object, the hit-tester computes the Euclidean distance in screen pixels between the mouse position and the object's rendered screen-space geometry:

- **Points**: distance from mouse to the point's screen position.
- **Geodesics**: distance from mouse to the nearest point on the rendered arc (or line segment for diameters) in screen space.
- **Circles**: distance from mouse to the nearest point on the rendered Euclidean circle in screen space.
- **Angles**: whether the mouse is inside the angle's arc sector in screen space.
- **Regions**: whether the mouse is inside the region's rendered polygon in screen space.

### 6.3 Hit-Testing Priority

When multiple objects are within the snap radius, the hit tester checks 6 tiers in order:

1. **Constructed points** (`PointLike` objects in the manager)
2. **Angles** (point inside arc sector)
3. **Intersection previews** (cached intersection candidates)
4. **Geodesic objects** (`GeodesicLike` objects)
5. **Circles**
6. **Regions** (point-in-polygon test)

Distance is the tiebreaker within tiers 1, 3-5. For angles, the first matching angle wins. For regions, the first matching region wins.

### 6.4 HitResult Types

```
sealed class HitResult {
    ConstructedPoint(id)
    OnAngle(id)
    IntersectionPreview(candidate)
    OnGeodesic(id)
    OnCircle(id)
    OnRegion(id)
}
```

---

## 7. State Management

### 7.1 Undo / Redo

- **Pattern**: Snapshot-based. Each checkpoint stores a deep copy of the entire `GeometricObjectManager` state (all objects + all metadata + nextId counter).
- **Checkpoint creation**: after each undoable action (add, delete, move, visibility toggle, label edit, measurement toggle, construction apply, transformation apply).
- **Undo**: restores the previous snapshot. **Redo**: restores the next snapshot.
- **Redo truncation**: any new checkpoint truncates the redo stack.
- **Drag granularity**: snapshot on mousedown, checkpoint on mouseup. One undo entry per drag.
- **Recording mode clamping**: `minIndex` prevents undoing past the recording start.
- **Shortcuts**: Cmd+Z (undo), Cmd+Shift+Z (redo).

### 7.2 Unsaved Changes Detection

Tracks the undo stack index at save time (`savedIndex`). If `currentIndex != savedIndex`, there are unsaved changes.

**Warning triggered on**: New (Cmd+N), Open (Cmd+O), window close.

---

## 8. Menu Bar

### 8.1 File Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| New | Cmd+N | Clear canvas (with unsaved-changes confirmation) |
| *(separator)* | | |
| Save | Cmd+S | Save diagram to JSON via FileChooser |
| Open | Cmd+O | Load diagram from JSON via FileChooser (with unsaved-changes confirmation) |
| *(separator)* | | |
| Export | Cmd+E | Open export dialog |

All File menu items are disabled during construction recording.

### 8.2 Action Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| Select All | Cmd+A | Select all objects |
| Deselect All | Cmd+Shift+A | Clear selection |
| Select Parents | Cmd+Shift+P | Select common parents of all currently selected objects |
| Select Children | Cmd+Shift+C | Select objects whose parents include all currently selected objects |
| *(separator)* | | |
| Show | Cmd+Shift+I | Make selected objects visible |
| Hide | Cmd+I | Hide selected objects (also hides their labels) |
| Delete | Delete | Delete selected objects and descendants (with confirmation) |
| *(separator)* | | |
| Undo | Cmd+Z | Undo |
| Redo | Cmd+Shift+Z | Redo |
| *(separator)* | | |
| Measure | Cmd+M | Add measurable selected objects to measurement table |
| Clear Measurements | Cmd+Shift+M | Remove all objects from measurement table |

### 8.3 Construction Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| Start Recording | Cmd+R | Begin recording |
| Save Recording | Cmd+Shift+S | Save construction to JSON |
| Cancel Recording | — | Discard and restore (Escape key, handled via canvas fallthrough) |
| *(separator)* | | |
| Load Construction | Cmd+L | Load `.json` files (multi-select) |
| Load from Folder... | Cmd+Shift+L | Load all `.json` from a directory |
| Unload All | Cmd+U | Clear constructions table |
| *(separator)* | | |
| Midpoint | — | Built-in: Point, Point -> Midpoint |
| Perpendicular Line | — | Built-in: Point, Geodesic -> PerpendicularLine |
| Reflection Point | — | Built-in: Point, Geodesic -> ReflectionPoint |

**Menu states**:
- Not recording: Start, Load, Load from Folder, Unload All, and built-ins enabled. Save Recording and Cancel disabled.
- Recording: Start, Load, Load from Folder, Unload All disabled. Save Recording, Cancel, and built-ins enabled. File menu items also disabled.

### 8.4 Style Menu

Controls the default styles applied to newly created objects. Located between the Construction and Transformation menus. All items use radio menu items with checkmarks indicating the current setting. No keyboard shortcuts. The Style menu remains enabled during construction recording.

Not persisted across sessions — resets to defaults on each launch.

| Sub-menu | Properties | Default |
|----------|-----------|---------|
| Point | Size (small, medium, large), Color (black, brown, green, purple, red, yellow) | medium, black |
| Geodesic | Color, Stroke Style (solid, dashed), Stroke Thickness (thin, normal, thick) | black, solid, normal |
| Circle | Color, Stroke Style (solid, dashed), Stroke Thickness (thin, normal, thick) | black, solid, normal |
| Angle | Size (small, medium, large), Color | medium, green |
| Region | Color | yellow |

When a new object is created (by a tool or construction apply), its style is copied from the current `StyleSettings` for its object category. `StyleSettings.applyDefaults(metadata, obj)` inspects the object type and copies the matching fields.

### 8.5 Transformation Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| Mobius | — | Enter Mobius transformation mode (see Section 13.1) |
| Rotation | — | Enter rotation transformation mode (see Section 13.2) |

All Transformation menu items are disabled during construction recording and during apply mode.

### 8.6 View Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| Show Grid / Hide Grid | Cmd+G | Toggle grid visibility (label toggles) |
| *(separator)* | | |
| Show / Hide Object List | — | Toggle left pane visibility (label toggles) |
| Show / Hide Custom Constructions | — | Toggle constructions table in right pane (label toggles) |
| Show / Hide Measurements | — | Toggle measurements table in right pane (label toggles) |

### 8.7 About Menu

| Item | Description |
|------|-------------|
| About Poincare Sketchpad | Dialog with app icon, version, description, copyright, license, GitHub link |

---

## 9. Construction System

### 9.1 Object Flags

Two boolean flags on `ObjectMetadata`, independent of `visible`:

| Flag | Default | Purpose |
|------|---------|---------|
| `isInput` | false | Marks objects as construction inputs during recording |
| `isLocked` | false | Hides objects from canvas and prevents interaction during recording |

The renderer skips objects where `isLocked == true`. The `visible` field is never modified by recording.

### 9.2 Recording Flow

1. **Start**: all visible `PointLike`, `GeodesicLike`, and `Circle` objects are marked `isInput = true`. All other objects are marked `isLocked = true`. An undo checkpoint is created, and `minIndex` is set.
2. **During recording**: user builds with normal tools. Built-in and custom constructions are available.
3. **Save**: opens FileChooser. Serializes the entire diagram with `isInput`/`isLocked` flags, labels nulled. Clears all flags. Clears `minIndex`.
4. **Cancel**: removes all objects with ID >= baseline. Clears all flags. Clears `minIndex`.

### 9.2.1 Styles and Constructions

Construction files do not save or load style information. When a construction is saved, object styles are ignored. When a construction is applied:
- **Constructed objects** receive the current `StyleSettings` defaults for their object category (e.g., if Point color is currently red, all constructed points are red).
- **Existing input objects** keep their existing styles unchanged.
- **Invisible constructed objects** also receive the current style settings.

### 9.3 Recording Mode UI

- Orange border (`#FF5722`, 3px) around the canvas pane (via CSS class `recording-border`).
- Orange banner at top center: "Recording Construction".
- Locked objects show lock icon in the Vis and Del columns.

### 9.4 Loading Constructions

- **Load Construction** (Cmd+L): FileChooser with multi-select for `.json` files.
- **Load from Folder** (Cmd+Shift+L): DirectoryChooser. Loads all `.json` files (non-recursive, sorted by name).
- Each file is parsed into a `LoadedConstruction` record containing: `fileName`, `diagram` (parsed `SerializedDiagram`), `inputTypes` (list of type strings), `inputIds` (sorted IDs of input objects).
- Input types: all point subtypes map to `"Point"`. Non-point types use their exact type name.
- Parsing errors: a single alert lists all failed filenames. Successful files are added normally.
- Only files with `"model": "poincare"` are accepted. Files with a different model or missing the field are rejected.

### 9.5 Applying a Construction

1. Click **Apply** on a constructions table row.
2. If the construction has **zero inputs**, it is applied immediately.
3. Otherwise, the app enters **apply mode**:
   - Hint label at bottom: `"Input N: select a <type>"`.
   - Hit-testing is type-filtered: only objects matching the expected type are highlighted on hover.
   - Clicks collect input object IDs sequentially.
   - **Escape** cancels.
4. Once all inputs are collected, `ConstructionApplicator.apply()` runs:
   - Locked objects in the file are skipped.
   - Input objects are mapped to user-selected objects by ID.
   - Constructed objects are instantiated with fresh IDs. Parent references are remapped.
   - Visibility from the file is preserved.
   - If a construction input is hidden in the file, the corresponding user-selected object is hidden.
5. An undo checkpoint is created.

### 9.6 Construction Preview

When hovering over the **last input** during apply mode, a preview of the construction result is rendered. `ConstructionApplicator.computePreview()` creates temporary objects with negative IDs, computes them against the real object map, and returns only the visible ones. These are rendered in the construction preview group with preview opacity.

Built-in constructions also support preview via a `builtInPreviewProvider`.

### 9.7 Type Matching

| Expected type | Matches |
|--------------|---------|
| `"Point"` (or any point subtype name) | Any `PointLike` object |
| `"Geodesic"` | Any `GeodesicLike` object |
| `"Line"` | `Line` only |
| `"Ray"` | `Ray` only |
| `"Segment"` | `Segment` only |
| `"PerpendicularLine"` | `PerpendicularLine` only |
| `"Circle"` | `Circle` only |

---

## 10. Preview Objects

Preview objects live in **two separate groups**, outside the `GeometricObjectManager`:

1. **`previewGroup`**: tool previews (ghost arcs, ghost points during multi-click tools). Managed by each tool.
2. **`constructionPreviewGroup`**: construction apply previews. Managed by `DiskCanvasPane`.

Properties of preview objects:
- No IDs assigned (or negative IDs for construction previews).
- No undo entries.
- Rendered with preview opacity (0.4).
- `isMouseTransparent = true`.
- Cleared on tool switch, escape, or commit.

---

## 11. Persistence

### 11.1 Serialization Format

JSON via kotlinx.serialization. Top-level structure:

```json
{
  "appName": "PoincareSketchpad",
  "version": "1.0.0",
  "model": "poincare",
  "objects": [ { "id": 0, "type": "FreePoint", "x": 0.3, "y": -0.2 }, ... ],
  "metadata": { "0": { "visible": true, "label": "A", ... }, ... }
}
```

**Model field**: `"model": "poincare"` is required. Files without it or with a different model value are rejected on load.

**SerializedObject fields**: `id`, `type` (string), `parentIds`, `x`, `y`, `t`, `theta`, `sign`, `pointIds`. Only relevant fields are populated per type.

**SerializedMetadata fields**: `visible`, `label`, `labelVisible`, `labelOffsetX` (default 10), `labelOffsetY` (default -10), `inMeasurementTable`, `labelAnchorAngle`, `labelAnchorT`, `isInput`, `isLocked`, `color` (default `"black"`), `size` (default `"medium"`), `strokeStyle` (default `"solid"`), `strokeThickness` (default `"normal"`). Unknown keys are ignored on load.

### 11.2 Diagram Save/Load

- **Save** (Cmd+S): serializes the full manager state via `DiagramSerializer.serialize()`. Marks undo as saved.
- **Open** (Cmd+O): validates `model` field, clears current state, deserializes via `DiagramSerializer.deserialize()`, rebuilds intersection cache, creates checkpoint, marks saved.

### 11.3 Construction Files

Same JSON format as diagrams, with these differences:
- `isInput` and `isLocked` flags are set on the relevant objects.
- All label fields are nulled/defaulted.
- Serialized via `ConstructionSerializer.saveConstruction()`.

### 11.4 Versioning

Every JSON file includes top-level `"appName"` and `"version"` fields. The `appName` is always `"PoincareSketchpad"`. The version string follows `<major>.<minor>.<patch>` format and is set to the app's current version string (e.g. `"1.0.0"`).

**Constants**: `APP_NAME` and `APP_VERSION` are defined once as top-level `const val`s in `Main.kt`.

**Validation on load** (in order):
1. **App name check**: if `appName` is not `"PoincareSketchpad"`, the file is rejected with an `IncompatibleAppNameException`.
2. **Version check**: the app parses the major version from the file's `"version"` field and compares it to its own major version. If they differ, the file is rejected with an `IncompatibleVersionException`.
3. Both fields are required in `SerializedDiagram` (no defaults); files missing either field are rejected during deserialization.

**Compatibility contract**:
- Within a major version, all changes to the serialization format must be **additive only**.

---

## 12. Export

### 12.1 Export Dialog

Non-modal dialog with:

- **Method**: 2 radio buttons (Canvas, Custom).
- **Format**: 2 radio buttons (PNG, SVG).
- **Options**: "Transparent background" checkbox, "Include grid" checkbox (both default off).
- **Dimensions** (varies by method, see below).
- **Buttons**: Export, Cancel.

### 12.2 Canvas Method

Exports a square image at the disk's current screen diameter. The disk is centered; the exterior is light gray (or transparent if selected).

**Dimensions**: a single non-editable Diameter text field showing the current disk screen diameter in pixels.

### 12.3 Custom Method

Custom method allows exporting a rectangular crop of the canvas at a specified resolution.

**Dimensions controls** (shown only when Custom is selected):

- **Width** and **Height** text fields (pixels). Default: 600×600.
- **Lock aspect ratio toggle** (default: locked). When locked, enforces 1:1 (square) — changing either field updates the other to the same value. Toggling lock on snaps both dimensions to match the larger value. Unlocking allows independent Width and Height.
- **Scale slider with editable text field**. Range: 10%–100%. Default: 100%. Controls how much of the disk the crop rectangle covers. 100% = the rectangle spans the full disk diameter (in its largest dimension). Lower values zoom into the center of the disk.

**Crop region**: the export region is a rectangle centered on the disk. Its aspect ratio is determined by Width:Height. Its size relative to the disk is determined by the Scale value. The exported image contains whatever falls inside this rectangle — including partial disk boundary, white interior, and gray exterior.

### 12.4 Export Overlay (Custom Method Only)

When Custom method is selected, a crop overlay is displayed on the canvas:

- **Letterbox**: 4 dark semi-transparent rectangles covering the excluded area outside the crop region.
- **Border**: orange (`#FF5722`) border on the export rectangle.
- **Scale label**: displays current scale percentage (e.g., "Scale: 75%").
- The overlay is **centered on the disk** and is not draggable.
- The overlay updates **in real time** as the user adjusts Width, Height, lock toggle, or Scale slider.
- The overlay is hidden when switching to Canvas method or closing the dialog.

### 12.5 Export Rendering

Both PNG and SVG exports share the same rendering logic:

- Selection and hover states are cleared (all objects render in their per-object color).
- 5 passes: regions, angles, geodesics/circles, points, labels.
- Geodesics rendered as arcs (or line segments for diameters). No arrowheads.
- Per-object styles (color, size, stroke style, stroke thickness) are respected in exports.
- Grid uses concentric circles and radial lines.
- Background: white disk on gray exterior (or transparent).

**PNG**: builds a JavaFX `Group`, takes a snapshot via `Node.snapshot()`, writes via `ImageIO`.

**SVG**: generates SVG XML string manually. Points -> `<circle>`, geodesics -> `<path>` (arc), circles -> `<circle>`, angles -> `<path>` (arc), regions -> `<path>` (arcs), labels -> `<text>`. Numbers formatted to 2 decimal places.

### 12.6 Headless Export (CLI)

```
./gradlew run --args="--export <diagram.json> [outputDir]"
```

- **diagram.json** (required): path to a diagram file.
- **outputDir** (optional, default `exports/`): directory for output files.

Produces 4 files: `<name>_canvas.png/svg` (default 700px diameter), `<name>_custom.png/svg` (1920px diameter). All exports use white-on-gray background, no grid.

---

## 13. Transformations

Transformations modify the underlying model coordinates of all free and semi-free points, then propagate to recompute all derived objects. They are accessed via the Transformation menu (Section 8.5).

### 13.1 Transformation Mode

When the user selects a transformation from the menu, the app enters **transformation mode**:

- A hint label is shown at bottom center (same position as apply mode hints).
- The toolbar is disabled (no tool switching).
- The File, Action, Construction, and Transformation menus are disabled.
- **Mouse move**: the canvas renders a live preview — the entire diagram is re-rendered at the positions that would result from applying the transformation at the current cursor position. No ghost overlay; the live preview replaces the normal rendering while the mouse is inside the disk.
- **Click inside the disk**: commits the transformation. An undo checkpoint is created.
- **Click outside the disk**: ignored.
- **Escape**: cancels the transformation, restores the snapshot taken on mode entry, and exits transformation mode.

### 13.2 Mobius Translation

- **Hint**: `"Möbius: click inside the disk to set point a"`
- The user clicks at model coordinates `a`. The transformation `T(z) = (z - a) / (1 - conj(a) * z)` is applied.

### 13.3 Rotation

- **Hint**: `"Rotation: click inside the disk to set the angle"`
- The user clicks at model coordinates `(x, y)`. The rotation angle is `theta = atan2(y, x)`. The transformation `T(z) = e^(i * theta) * z` is applied.
- Only the angle of the clicked point matters, not the distance from the origin.

### 13.4 Application Algorithm

When the user clicks to commit (or when computing the live preview):

1. For each semi-free point, record its current model position `z` and compute `T(z)`.
2. For each `FreePoint`, update `x, y` to `T(x + iy)`. Clamp to `|z| < 1 - epsilon`.
3. Run `propagateFrom(0)` to recompute all derived objects (geodesics, circles, intersection points, etc.).
4. For each semi-free point, call `updateParameterFromPosition(T(z).re, T(z).im)` to update `t` or `theta` to match the new parent geometry.

### 13.5 Undo

- A snapshot is taken when entering transformation mode.
- On commit (click), an undo checkpoint is created. The entire transformation is a single undo entry.
- On cancel (Escape), the snapshot is restored.

---

## 14. Escape Key Handling

Escape is handled in priority order:

1. **Transformation mode active**: cancel transformation, restore snapshot.
2. **Apply mode active**: cancel apply mode.
3. **Current tool has in-progress operation**: cancel it (e.g., cancel mid-construction geodesic). Tool's `onEscapePressed()` returns `true`.
4. **Recording mode active**: cancel recording.

---

## 15. Per-Object Styling

### 15.1 Style Properties

Each object has style properties stored as fields on `ObjectMetadata`. All four fields exist on every metadata instance; the renderer ignores fields that don't apply to a given object type.

| Field | Type | Default | Applies to |
|-------|------|---------|------------|
| `color` | String | `"black"` | All object types |
| `size` | String | `"medium"` | Point (radius), Angle (arc radius) |
| `strokeStyle` | String | `"solid"` | Geodesic, Circle |
| `strokeThickness` | String | `"normal"` | Geodesic, Circle |

**Style applicability by object category:**

| Category | Color | Size | Stroke Style | Stroke Thickness |
|----------|-------|------|-------------|-----------------|
| Point | Yes | Yes | — | — |
| Geodesic (Line, Ray, Segment, PerpendicularLine) | Yes | — | Yes | Yes |
| Circle | Yes | — | Yes | Yes |
| Angle | Yes | Yes | — | — |
| Region | Yes | — | — | — |

### 15.2 Size Mappings

**Point radius** (screen pixels):

| Value | Pixels |
|-------|--------|
| small | 3px |
| medium | 4px |
| large | 6px |

**Angle arc radius** (screen pixels):

| Value | Pixels |
|-------|--------|
| small | 20px |
| medium | 25px |
| large | 30px |

**Stroke thickness** (screen pixels):

| Value | Pixels |
|-------|--------|
| thin | 1px |
| normal | 2px |
| thick | 3px |

Hover multipliers apply on top of the per-object size/thickness: 1.3x for point radius, 1.5x for stroke width.

### 15.3 Stroke Styles

| Value | Rendering |
|-------|-----------|
| solid | Continuous stroke (default) |
| dashed | 6px dash, 4px gap (screen pixels) |

### 15.4 Default Styles for New Objects

When a new object is created, its style is copied from the current `StyleSettings` using `StyleSettings.applyDefaults(metadata, obj)`. This function inspects the object type and copies the relevant fields.

`StyleSettings` is owned by `SketchpadApp` and passed to tools and the construction applicator. Initial values:

| Category | color | size | strokeStyle | strokeThickness |
|----------|-------|------|-------------|-----------------|
| Point | black | medium | — | — |
| Geodesic | black | — | solid | normal |
| Circle | black | — | solid | normal |
| Angle | green | medium | — | — |
| Region | yellow | — | — | — |

The Style menu (Section 8.4) reads and writes these values. Not persisted across sessions.

### 15.5 Changing Styles on Existing Objects

Right-clicking on the canvas or object list table shows a context menu for changing styles of selected objects, subject to these conditions:

- **Selection required**: no context menu if nothing is selected.
- **Same-type selection**: all selected objects must belong to the same style category (Point, Geodesic, Circle, Angle, or Region). Line, Ray, and Segment are treated as the same category (Geodesic). No context menu for mixed-type selections.
- **Right-click position is irrelevant**: the menu operates on the existing selection regardless of where the right-click occurs.

The context menu contains sub-menus for each applicable style property (e.g., "Color", "Size" for points). Each sub-menu uses radio menu items. If all selected objects share the same value for a property, that value is checkmarked. If values are mixed, no item is checkmarked. Selecting a value applies it to all selected objects immediately.

Style changes on existing objects create an undo checkpoint.

### 15.6 Backward Compatibility

Style fields in `SerializedMetadata` have defaults (`color = "black"`, `size = "medium"`, `strokeStyle = "solid"`, `strokeThickness = "normal"`), so old files without these fields deserialize correctly. On load, the deserializer detects the object type and overrides the color default for Angles (`"green"`) and Regions (`"yellow"`) to match the pre-styling rendering behavior.

Construction files do not save or load style fields. The `ConstructionSerializer` strips style information when saving.
