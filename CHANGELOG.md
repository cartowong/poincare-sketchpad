# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-11-08

### Added

- Per-object styling: color, size, stroke style, and stroke thickness properties on `ObjectMetadata`
- Style menu (between View and Transformation) with sub-menus for Point, Geodesic, Circle, Angle, and Region default styles
- Right-click context menu on canvas and object list table to change styles of same-type selected objects
- `StyleSettings` class for application-level default styles applied to newly created objects
- Color palette: black, brown, green, purple, red, yellow
- Point size options: small (3px), medium (4px), large (6px)
- Angle arc radius options: small (20px), medium (25px), large (30px)
- Stroke thickness options: thin (1px), normal (2px), thick (3px)
- Dashed stroke style option (6px dash, 4px gap)
- Style fields (`color`, `size`, `strokeStyle`, `strokeThickness`) in serialized metadata with backward-compatible defaults
- Backward compatibility: old files without style fields load with type-aware color defaults (green for angles, yellow for regions)

## [1.2.0] - 2026-11-08

### Changed

- Implement modifier-click selection behavior: plain click single-selects (deselects all others), Cmd/Ctrl+click toggles without affecting other selections. Applies to both canvas (SelectionTool) and Object List table (Sidebar)
- Update Euclidean Sketchpad link to Euclid Sketchpad in README

## [1.1.0] - 2026-11-08

### Changed

- UI-facing app name now uses the diacritic mark: "Poincaré Sketchpad" (window title, About dialog, error messages)
- Selection behavior: plain click now selects only the clicked object (deselecting all others). Modifier+click (Cmd on macOS, Ctrl on others) toggles selection without affecting other objects. Applies to both canvas and Object List table

## [1.0.2] - 2026-11-07

### Fixed

- `footOfPerpendicular()` now uses the correct hyperbolic construction (reflect + hyperbolic midpoint) instead of an incorrect Euclidean nearest-point-on-circle projection, fixing PerpendicularLine foot placement and ensuring circles through the foot are tangent to the geodesic

## [1.0.1] - 2026-11-07

### Fixed

- ESC key now cancels construction apply mode (canvas requests focus on entering apply mode)
- ESC key now cancels transformation mode (canvas requests focus on entering transformation mode)
- Removed Escape accelerator from "Cancel Recording" menu item to prevent it from intercepting ESC when higher-priority handlers (apply mode, transformation mode, tool cancel) should take precedence

## [1.0.0] - 2026-11-07

### Changed

- Gradle project version now extracted from `APP_VERSION` in Main.kt instead of being hardcoded

### Added

- Poincare disk model geometry engine (geodesics, circles, intersections, angles, regions)
- 9 construction tools: Select, Point, Segment, Ray, Line, Circle, Angle, Region, Label
- Snapshot-based undo/redo system
- Custom construction recording, saving, loading, and applying with preview
- 3 built-in constructions: Midpoint, Perpendicular Line, Reflection Point
- Möbius translation and rotation transformations with live preview
- Object list, measurements table, and custom constructions table sidebars
- PNG and SVG export (Canvas and Custom methods) with headless CLI mode
- Custom export with rectangular crop, aspect ratio lock, and scale slider
- Export overlay on canvas showing crop region with letterbox and scale label
- Hyperbolic grid (concentric circles + radial lines), toggleable via View menu
- JSON serialization for diagrams and constructions with model validation
- README, product website for GitHub Pages (landing page, privacy policy, support)
- GitHub Actions CI (test on PR) and release workflow (DMG + MSI on tag push)
- Application specification (spec/SPEC.md) and geometry engine specification (spec/GEOMETRY.md)
