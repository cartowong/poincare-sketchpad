# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- README.md with features, download instructions, and build-from-source guide
- Product website in `docs/` (landing page, privacy policy, support page) for GitHub Pages

### Fixed

- Label tool triggered hover highlights on nearby objects while dragging a label; now suppresses hover during label drag and when cursor is over a label, matching EuclideanSketchpad behavior
- Label tool could not hide labels on angles and regions: the label text overlapped the clickable area, causing clicks to start a drag instead of toggling visibility; clicking a label without dragging now hides it

### Changed

- Custom export default dimensions reduced from 1920×1920 to 600×600

### Added

- Custom export with rectangular crop: Width/Height fields, aspect ratio lock toggle, and Scale slider (10–100%)
- Export overlay on canvas showing crop region with dark letterbox, orange border, and scale label
- `exportOverlayGroup` on `DiskCanvasPane` for hosting the crop overlay
- Spec for Custom export rectangular crop with scale slider, aspect ratio lock, and letterbox overlay (Section 12.3–12.4)

### Changed

- `ExportSettings` now uses `width`, `height`, `scale` instead of `diameter`
- PNG export renders full disk scene and crops center rectangle via `SnapshotParameters.viewport`
- SVG export renders full disk scene and crops center rectangle via `viewBox`

### Changed

- Angle arc radius reduced from 30px to 25px
- Angle sweep convention changed from CCW (positive) to CW (negative) for explementary angles

### Fixed

- PerpendicularLine invisible when the point lies on the geodesic: `x1,y1` and `x2,y2` (foot) were coincident, causing the renderer to skip drawing; now computes a distinct second point along the perpendicular direction
- Label tool could not drag labels: hit-testing and offset computation used inverted Y-sign (`- labelOffsetY`) vs renderer (`+ labelOffsetY`), placing the hit box 20px from the actual label
- Label tool used `modelToScreenX/Y` (no Möbius viewport) instead of `modelToScreen` (with viewport), misaligning hit-testing when viewport is non-identity
- Exported labels (PNG and SVG) positioned incorrectly: used `- labelOffsetY + baselineShift` instead of `+ labelOffsetY` to match the canvas renderer
- AngleTool preview, HitTester, and ExportRenderer used a stale `ANGLE_ARC_RADIUS` of 30px instead of the spec's 25px
- Geodesic tool preview arcs rendered at wrong position (spurious Y-axis negation in model-to-JavaFX angle conversion)
- Angle rendering (actual and construction preview) reflected across X-axis due to same negation bug
- Angle rendering in PNG and SVG export had matching angle conversion bugs
- Region boundary rendered with wrong arc curvature due to inverted `ArcTo` sweep flag (`sweep > 0` should be `sweep < 0`) in both `renderRegion` and `renderPreviewRegion`
- Region tool preview drew Euclidean straight lines instead of geodesic arcs for edges and polygon fill

### Added

- `GeometryUtils` object in geometry package as shared source for rendering constants (starting with `ANGLE_ARC_RADIUS`)
- Project skeleton: CLAUDE.md, CHANGELOG.md, handoff document, .gitignore
- Application specification (spec/SPEC.md) and geometry engine specification (spec/GEOMETRY.md)
- save-changes Claude Code skill
