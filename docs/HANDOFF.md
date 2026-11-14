# Poincare Sketchpad — Handoff Document

**Date:** 2026-11-08
**Branch:** `main`

## Current State

- **Build:** `./gradlew build` succeeds. `./gradlew test` passes all 70 tests.
- **Code:** All source files for all 10 phases are written (geometry, model, persistence, UI, export). The app has not been manually tested via `./gradlew run` yet.
- **Git:** `.claude/` is gitignored (except `.claude/skills/`).
- **Spec:** `spec/SPEC.md` and `spec/GEOMETRY.md` describe the full application design.
- **Docs:** `README.md` and `docs/` product website (GitHub Pages) are present.
- **CI/CD:** GitHub Actions workflows in `.github/workflows/` — `ci.yml` runs tests on PRs; `release.yml` builds DMG and MSI installers on `v*` tag push and creates a GitHub Release.

## Package Structure

```
src/main/kotlin/poincaresketchpad/
  Main.kt                          # APP_NAME, APP_VERSION, entry point
  geometry/                        # Pure math (no JavaFX)
    GeometricObject.kt             # Sealed interface (no distanceFrom)
    HyperbolicUtils.kt             # Core hyperbolic math (~380 lines)
    Point.kt                       # PointLike, FreePoint, FreePointOn*, Midpoint, ReflectionPoint
    Geodesic.kt                    # GeodesicLike, Line, Ray, Segment, PerpendicularLine
    Circle.kt                      # Hyperbolic circle with Euclidean representation
    Intersection.kt                # GeodesicGeodesic, GeodesicCircle, CircleCircle intersections
    IntersectionUtils.kt           # Euclidean intersection algorithms + disk filtering
    Angle.kt                       # Tangent-based angle computation
    Region.kt                      # Gauss-Bonnet area
    MeasurementUtils.kt            # Hyperbolic measurement formulas
  model/
    ObjectMetadata.kt              # Per-object mutable state (incl. style fields) + getObjectAnchor
    GeometricObjectManager.kt      # Object map, add/remove/move/propagate/snapshot, styleSettings
    StyleSettings.kt               # Application-level default styles per object category
    StyleUtils.kt                  # Shared color/size/thickness maps
    UndoManager.kt                 # Snapshot-based undo/redo
    ConstructionRecorder.kt        # Recording lifecycle
    ConstructionApplicator.kt      # Apply constructions with ID remapping
  persistence/
    DiagramSerializer.kt           # JSON serialization with model:"poincare" validation
    ConstructionSerializer.kt      # Construction save/load
  ui/
    Tool.kt, Toolbar.kt            # Tool interface and toolbar
    ToolUtils.kt                   # resolveOrCreatePoint, constructIntersection
    PointTool.kt                   # Point creation tool
    SelectionTool.kt               # Selection and drag tool
    GeodesicTool.kt                # Line/Ray/Segment tool with arc previews
    CircleTool.kt                  # Circle tool
    AngleTool.kt                   # Three-click angle tool
    RegionTool.kt                  # Multi-click polygon tool
    LabelTool.kt                   # Label assignment and drag tool
    DiskCanvasPane.kt              # Main canvas (disk rendering, viewport, apply/transform modes)
    GeometryRenderer.kt            # 5-pass renderer (regions, angles, geodesics, points, labels)
    HitTester.kt                   # Screen-pixel hit-testing with intersection cache
    Sidebar.kt                     # Object list, constructions table, measurements table
    SketchpadApp.kt                # Full app shell with menus
  export/
    ExportDialog.kt                # Canvas/Custom export dialog
    ExportRenderer.kt              # PNG (snapshot) and SVG (manual XML) export
    HeadlessExportApp.kt           # CLI --export mode

src/test/kotlin/poincaresketchpad/
  geometry/
    HyperbolicUtilsTest.kt         # 27 tests
    GeodesicTest.kt                # 7 tests
    CircleTest.kt                  # 4 tests
    IntersectionTest.kt            # 4 tests
    MeasurementUtilsTest.kt        # 5 tests
  model/
    GeometricObjectManagerTest.kt  # 6 tests
    UndoManagerTest.kt             # 5 tests
  persistence/
    DiagramSerializerTest.kt       # 8 tests
    ConstructionSerializerTest.kt  # 4 tests
```

## Architecture Notes

- **Sister project to EuclideanSketchpad.** Same technology stack (Kotlin, JavaFX, Gradle, kotlinx.serialization, AtlantaFX). Parallel folder structure. Separate Git repository.
- **Poincare disk model.** All geometry lives in the open unit disk `{z : |z| < 1}`. Geodesics are arcs of Euclidean circles orthogonal to the boundary (or diameters). Hyperbolic circles are Euclidean circles with shifted centers.
- **No `distanceFrom` on geometry objects.** Hit-testing is done entirely in screen-pixel space by the UI layer, not by the geometry classes. This avoids the metric distortion problem near the boundary.
- **Mobius viewport transform replaces pan/zoom.** Space+Click applies a Mobius translation (sends clicked point to origin). Space+Drag rotates the disk. No scroll-wheel zoom — the disk auto-fills available space.
- **`GeodesicLike` replaces `LinearLike`.** Geodesic objects store their Euclidean arc representation (center, radius, isDiameter) in addition to defining points.
- **All intersections reduce to Euclidean circle-circle/line-circle.** Since geodesics and hyperbolic circles are both Euclidean circles (or lines) in the Poincare model, intersection algorithms use Euclidean math then filter to the disk interior.
- **Geodesic-geodesic intersection needs no `sign` field.** Two distinct geodesics intersect in at most one point inside the disk.
- **Geodesic-circle sign disambiguation uses dot product.** Both intersection points lie on the geodesic, so cross-product disambiguation is degenerate. Uses `assignSignAlong` (dot product from midpoint of the two candidates along the chord direction) instead.
- **Serialization includes `"model": "poincare"`.** Files without this field or with a different model are rejected. Json config uses `encodeDefaults = true` to ensure the model field is always serialized.
- **UI-facing app name uses the diacritic: "Poincaré Sketchpad".** The `APP_NAME` constant (`"PoincareSketchpad"`) is ASCII-only for serialization compatibility. All user-visible strings (window title, About dialog, error messages) use `"Poincaré"` with the Unicode escape `\u00E9`.
- **Three built-in constructions** (no Parallel Line): Midpoint, Perpendicular Line, Reflection Point.
- **Grid is concentric hyperbolic circles + radial diameters**, not a square grid. Follows the Mobius viewport transform.
- **Model-space angles = JavaFX angles.** The Y-flip from model→screen and the `atan2` negation for JavaFX cancel out. Never negate model-space `atan2` results when passing to JavaFX `Arc`. This was the root cause of several rendering bugs.
- **Angle sweep is negative (CW).** `sweepAngleRadians` is in [-2π, 0) — clockwise from tangent-to-A to tangent-to-C. `measureDegrees()` negates it to return a positive value.
- **Label offsets are raw screen-pixel deltas, not y-inverted.** `labelOffsetX/Y` are added directly to the anchor's screen position: `text.x = sx + offsetX`, `text.y = sy + offsetY`. The default `offsetY = -10.0` means 10px UP in screen coords. The EuclideanSketchpad version uses `scaleY = -1.0` on text and a corresponding `baselineShift` in export, but PoincareSketchpad renders text directly in screen coords with no flip — so no negation or baseline compensation is needed anywhere. The LabelTool must use `modelToScreen` (with Möbius viewport), not the simpler `modelToScreenX/Y`.
- **`ArcTo` sweep flag is `sweep < 0`, not `sweep > 0`.** When building region paths with `ArcTo` elements, the model-space sweep sign does not directly map to `sweepFlag`. The SVG/JavaFX `ArcTo` uses sweep flag + large arc flag to select among 4 candidate arcs (2 circles × 2 arcs each). `sweepFlag = sweep < 0` selects the correct orthogonal circle. The `ExportRenderer` had this right; `GeometryRenderer` had it inverted. Segments avoid this issue entirely because `createScreenArc` uses `Arc` with an explicit center.
- **`PerpendicularLine` must store distinct `x1,y1` and `x2,y2`.** The renderer (`renderGeodesic`) early-returns when the two defining points are coincident. When the input point lies on the parent geodesic, the foot of perpendicular equals the point, so `recompute()` must override `x2,y2` with a distinct point along the perpendicular direction. Without this, the perpendicular line is invisible (both committed and in preview).
- **`footOfPerpendicular` uses reflect + hyperbolic midpoint.** The foot of perpendicular from P to a geodesic G is the hyperbolic midpoint of P and its reflection through G (via circle inversion). A previous implementation incorrectly used the Euclidean nearest point on the arc circle, which is NOT the hyperbolic perpendicular foot. The Euclidean shortcut only works for diameters (handled separately).
- **LabelTool click-vs-drag detection.** `hitTestLabel` runs before the general `hitTest`, so label text intercepts clicks on the underlying object. For angles and regions, the label text overlaps the only clickable area (arc sector / polygon interior). The fix: track mouse displacement and treat sub-threshold (< 3px) presses on a label as a click (toggle visibility) rather than a drag.

- **Per-object styling.** Each object has `color`, `size`, `strokeStyle`, `strokeThickness` fields in `ObjectMetadata`. `StyleSettings` holds application-level defaults per category (Point, Geodesic, Circle, Angle, Region), applied automatically in `addObject()`. Style menu controls defaults; right-click context menu changes styles on selected objects. Styles serialize in diagram JSON with nullable fields for backward compat. Construction files strip styles. `StyleUtils` provides shared color/size/thickness mappings used by both `GeometryRenderer` and `ExportRenderer`.

## Known Issues / Gaps

- **Compiler warnings.** A few unused parameter and name-shadowing warnings exist (HyperbolicUtils, ExportRenderer, GeometryRenderer, Sidebar). Non-blocking.
- **Custom export not manually tested.** The rectangular crop, scale slider, overlay, and aspect ratio lock have been implemented but not yet verified via `./gradlew run`.
- **Label drag not manually verified.** The Y-sign and viewport bugs have been fixed in code but not yet confirmed via `./gradlew run`.
- **ESC handling relies on canvas focus.** `DiskCanvasPane` calls `requestFocus()` when entering apply mode and transformation mode. If a future mode entry path bypasses this, ESC won't reach the canvas's key handler. The "Cancel Recording" menu item has no accelerator — recording cancellation is handled via `onEscapeFallthrough` as the lowest-priority ESC handler.
