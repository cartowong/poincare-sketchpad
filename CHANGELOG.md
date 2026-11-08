# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-07

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
