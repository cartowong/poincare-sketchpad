# Poincare Disk Geometry Engine

Specification for the geometry computation layer. This module is pure math — no UI framework imports. All classes live in the `geometry/` package.

The geometry uses the **Poincare disk model** of hyperbolic geometry: the open unit disk `{z in C : |z| < 1}` in the complex plane, with the Riemannian metric `ds = 2|dz| / (1 - |z|^2)`.

---

## 1. Core Interfaces

### 1.1 GeometricObject

```
sealed interface GeometricObject {
    id: Int                              // immutable, assigned in construction order, starting at 0
    parentIds: List<Int>                 // IDs of parent objects (empty for free points)
    isDefined: Boolean                   // false when geometrically invalid (e.g., non-intersecting)
    recompute(objects: Map<Int, Object>) // recompute position/state from parent objects
}
```

Every concrete geometric type implements this interface. The sealed constraint means the set of types is closed — adding a new type requires updating all exhaustive `when` expressions in serializers, the object manager, and the sidebar.

**Note:** Unlike EuclideanSketchpad, there is no `distanceFrom` method. Hit-testing is performed in screen-pixel space by the UI layer, not by the geometry objects.

### 1.2 PointLike

```
interface PointLike {
    x: Double    // x-coordinate in the unit disk
    y: Double    // y-coordinate in the unit disk
}
```

All point types implement both `GeometricObject` and `PointLike`. Coordinates satisfy `x^2 + y^2 < 1` (strictly inside the unit disk). Non-point code reads coordinates through this interface without knowing the concrete point type.

### 1.3 GeodesicLike

```
enum GeodesicType { LINE, RAY, SEGMENT }

interface GeodesicLike {
    point1Id: Int              // first defining point ID
    point2Id: Int              // second defining point ID
    geodesicType: GeodesicType
    arcCenterX: Double         // Euclidean center x of the circular arc (NaN if diameter)
    arcCenterY: Double         // Euclidean center y of the circular arc (NaN if diameter)
    arcRadius: Double          // Euclidean radius of the circular arc (NaN if diameter)
    isDiameter: Boolean        // true when the geodesic is a diameter of the disk
    x1, y1: Double             // first defining point coordinates
    x2, y2: Double             // second defining point coordinates
}
```

All geodesic objects (Line, Ray, Segment, PerpendicularLine) implement both `GeometricObject` and `GeodesicLike`. The arc properties describe the Euclidean circle whose arc represents the geodesic inside the disk.

**Geodesic computation:** Given two points `p1` and `p2` in the unit disk, the geodesic is:
- A **diameter** if `p1`, `p2`, and the origin are collinear (within epsilon).
- Otherwise, a **circular arc** orthogonal to the unit circle. The arc center and radius are computed from the constraint that the circle passes through `p1` and `p2` and is orthogonal to the unit circle (i.e., the center is at distance `sqrt(r^2 + 1)` from the origin, where `r` is the arc radius). The specific formulas:
  ```
  // Invert p1 through the unit circle: p1* = p1 / |p1|^2
  // The arc circle passes through p1, p2, and p1* (or equivalently p2*)
  // Use three-point circle formula to find center and radius
  ```

### 1.4 Geodesic Boundary Points

For rendering and parameterization, geodesics that extend to the disk boundary need their **ideal points** — where the geodesic (or its extension) meets the unit circle `|z| = 1`.

- **Line**: two ideal points (both boundary intersections of the arc/diameter).
- **Ray**: one ideal point (the boundary intersection in the extending direction).
- **Segment**: no ideal points (terminates at both defining points).

These are computed during `recompute()` and stored for use by the renderer and parameterization logic.

---

## 2. Object Types

### 2.1 Free Points

**FreePoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `x` | Double (mutable) | x-coordinate in unit disk |
| `y` | Double (mutable) | y-coordinate in unit disk |

- Parents: none.
- Always defined.
- `recompute()`: no-op.
- Coordinates are clamped to `|z| < 1 - epsilon` on construction and during drag.

**FreePointOnLine**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `lineId` | Int | Parent GeodesicLike object ID |
| `t` | Double (mutable) | Parameter in `[0, 1]`, mapping the arc from one ideal point to the other |

- Parents: `[lineId]`.
- `recompute()`: interpolates along the Euclidean arc (or diameter) of the parent geodesic at parameter `t`. Undefined if parent is undefined.
- `updateParameterFromPosition(mx, my)`: computes the nearest `t` on the parent's arc.

**FreePointOnRay**

Same structure as FreePointOnLine, except:
- `t` is clamped to `[0, 1]`, where `t = 0` is the ray's endpoint and `t = 1` is the ideal point on the boundary.

**FreePointOnSegment**

Same structure as FreePointOnLine, except:
- `t` is clamped to `[0, 1]`, where `t = 0` is `point1` and `t = 1` is `point2`.

**FreePointOnCircle**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `circleId` | Int | Parent Circle object ID |
| `theta` | Double (mutable) | Angle parameter on the Euclidean circle representation |

- Parents: `[circleId]`.
- `recompute()`: position on the Euclidean circle (the rendered representation of the hyperbolic circle) at angle `theta`. `x = euclideanCenterX + euclideanRadius * cos(theta)`, `y = euclideanCenterY + euclideanRadius * sin(theta)`. Undefined if parent circle is undefined.
- `updateParameterFromPosition(mx, my)`: `theta = atan2(my - euclideanCenterY, mx - euclideanCenterX)`.

### 2.2 Derived Points

**Midpoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First parent point |
| `point2Id` | Int | Second parent point |

- Parents: `[point1Id, point2Id]`.
- `recompute()`: computes the hyperbolic midpoint — the unique point on the geodesic between `p1` and `p2` at equal hyperbolic distance from both.
  ```
  // Map p1 to origin via Mobius transform T(z) = (z - p1) / (1 - conj(p1) * z)
  // Image of p2 under T: q = T(p2)
  // Midpoint in transformed space: m' = q / 2 in hyperbolic sense
  //   m' = q * tanh(arctanh(|q|) / 2) / |q|
  // Map back: midpoint = T_inverse(m')
  ```
- Not movable.

**ReflectionPoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointId` | Int | Point to reflect |
| `geodesicId` | Int | GeodesicLike object to reflect across |

- Parents: `[pointId, geodesicId]`.
- `recompute()`: reflects the point across the geodesic. The geodesic is either a diameter or a circular arc orthogonal to the boundary.
  - **Diameter case**: reflect across the line through the origin (standard Euclidean reflection).
  - **Arc case**: invert through the Euclidean circle of the arc. For a circle with center `(cx, cy)` and radius `r`, the inversion of point `p` is: `p' = center + r^2 * (p - center) / |p - center|^2`.
  Inversion through a circle orthogonal to the unit circle is an isometry of the Poincare disk.
- Not movable.

### 2.3 Intersection Points

All intersection types implement `PointLike`. They become undefined when the intersection does not exist or falls outside the unit disk.

In the Poincare disk, geodesics are Euclidean circular arcs (or diameters), and hyperbolic circles are Euclidean circles. All intersection computations reduce to Euclidean circle-circle or line-circle intersection, filtered to points inside the unit disk.

**GeodesicGeodesicIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `geodesic1Id` | Int | First GeodesicLike parent |
| `geodesic2Id` | Int | Second GeodesicLike parent |

- Parents: `[geodesic1Id, geodesic2Id]`.
- `recompute()`:
  1. Determine the Euclidean representation of each geodesic (circle or diameter).
  2. Compute intersection(s) using Euclidean circle-circle, circle-line, or line-line intersection.
  3. Filter candidates to those inside the open unit disk (`|z| < 1 - epsilon`).
  4. Filter by parameter bounds: for rays, the candidate must be on the correct side of the endpoint; for segments, between both endpoints.
  5. Two geodesics intersect in at most one point inside the disk. Undefined if no valid candidate.

**Note:** Unlike EuclideanSketchpad, no `sign` field is needed — two distinct geodesics in the Poincare disk have at most one intersection point inside the disk.

**GeodesicCircleIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `geodesicId` | Int | GeodesicLike parent |
| `circleId` | Int | Circle parent |
| `sign` | Int | `+1` or `-1`, set at construction time |

- Parents: `[geodesicId, circleId]`.
- `recompute()`:
  1. The geodesic is a Euclidean circle (or diameter). The hyperbolic circle is a Euclidean circle.
  2. Compute Euclidean circle-circle (or line-circle) intersection.
  3. Filter candidates to the open unit disk.
  4. Filter by geodesic type bounds (ray/segment).
  5. If two candidates: select by `sign` using cross-product disambiguation.
  6. Undefined if no valid candidates.

**CircleCircleIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `circle1Id` | Int | First Circle parent |
| `circle2Id` | Int | Second Circle parent |
| `sign` | Int | `+1` or `-1`, set at construction time |

- Parents: `[circle1Id, circle2Id]`.
- `recompute()`:
  1. Both hyperbolic circles are Euclidean circles. Compute Euclidean circle-circle intersection.
  2. Filter candidates to the open unit disk.
  3. If two candidates: select by `sign`.
  4. Undefined if no valid candidates.

### 2.4 Geodesic Objects

All geodesic objects implement both `GeometricObject` and `GeodesicLike`.

**Line**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First defining point |
| `point2Id` | Int | Second defining point |

- `geodesicType`: `LINE` (extends to the boundary in both directions).
- `recompute()`: copies coordinates from both parent points. Computes arc center, arc radius, and both ideal points. Undefined if either parent is undefined or if both points coincide.

**Ray**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `endpointId` | Int | Ray's endpoint (origin) |
| `throughId` | Int | Point the ray passes through |

- `geodesicType`: `RAY` (extends from endpoint through throughPoint to the boundary).
- `recompute()`: same arc computation as Line, plus one ideal point in the extending direction.

**Segment**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First endpoint |
| `point2Id` | Int | Second endpoint |

- `geodesicType`: `SEGMENT` (arc between both endpoints only).
- `recompute()`: same arc computation as Line. No ideal points.

**PerpendicularLine**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointId` | Int | Point the line passes through |
| `geodesicId` | Int | GeodesicLike object to be perpendicular to |

- `geodesicType`: `LINE`.
- `recompute()`:
  1. Find the foot of the perpendicular from the point to the parent geodesic. This is the point on the geodesic closest to the given point in hyperbolic distance.
  2. Compute the tangent direction of the parent geodesic at the foot.
  3. The perpendicular geodesic passes through the given point with tangent direction orthogonal to the parent's tangent at the foot.
  4. Compute the arc center and radius for this perpendicular geodesic.
- Undefined if parents are undefined.

### 2.5 Circle

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `centerId` | Int | Center point (hyperbolic center) |
| `radiusPointId` | Int | Point on the circle |

- Parents: `[centerId, radiusPointId]`.
- Computed properties:
  - `hyperbolicRadius`: the hyperbolic distance from center to radius point.
  - `euclideanCenterX`, `euclideanCenterY`: center of the Euclidean circle representation (shifted toward origin relative to hyperbolic center).
  - `euclideanRadius`: radius of the Euclidean circle representation.
- Does not implement `GeodesicLike` or `PointLike`.
- `recompute()`:
  ```
  // Hyperbolic distance: d = 2 * arctanh(|p1 - p2| / |1 - conj(p1) * p2|)
  // Equivalently using Mobius transform: map center to origin, d = 2 * arctanh(|T(radiusPoint)|)
  //
  // Euclidean representation of a hyperbolic circle with center c and hyperbolic radius r:
  //   |c|^2 = cx^2 + cy^2
  //   euclideanCenter = c * (1 - t^2) / (1 - |c|^2 * t^2)  where t = tanh(r/2)
  //   euclideanRadius = t * (1 - |c|^2) / (1 - |c|^2 * t^2)
  ```
  Undefined if either parent is undefined.

### 2.6 Angle

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointAId` | Int | First ray point |
| `vertexId` | Int | Vertex point |
| `pointCId` | Int | Second ray point |

- Parents: `[pointAId, vertexId, pointCId]`.
- Computed properties: `vertexX`, `vertexY`, `startAngleRadians`, `sweepAngleRadians`.
- Does not implement `GeodesicLike` or `PointLike`.
- `recompute()`:
  The angle between two geodesics is the Euclidean angle between their tangent vectors at the vertex (the Poincare disk is conformal).
  ```
  // Compute tangent direction of geodesic VA at vertex V
  // Compute tangent direction of geodesic VC at vertex V
  // For a diameter geodesic: tangent = direction vector (trivial)
  // For an arc geodesic with center O: tangent at V is perpendicular to (V - O)
  //
  // startAngleRadians = atan2 of tangent VA
  // sweep = clockwise angular sweep from tangent VA to tangent VC (negative radians)
  // Normalize to [-2*PI, 0)
  ```
- `measureDegrees()`: `-sweepAngleRadians * 180 / PI`.

### 2.7 Region

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointIds` | List\<Int\> | Ordered list of vertex point IDs |

- Parents: `pointIds` (all vertex points).
- Computed property: `vertices: List<Pair<Double, Double>>` — resolved coordinates.
- Does not implement `GeodesicLike` or `PointLike`.
- `recompute()`: resolves all point IDs to `(x, y)` pairs. Undefined if any parent point is undefined.
- `contains(x, y)`: adapted point-in-polygon test accounting for geodesic-arc edges.
- `measureArea()`: Gauss-Bonnet formula: `area = (n - 2) * PI - sum(interior_angles)`, where each interior angle is the angle between the tangent vectors of adjacent geodesic edges at each vertex.

---

## 3. Utility Functions

### 3.1 HyperbolicUtils

Core hyperbolic geometry functions operating in the Poincare disk model.

| Function | Description |
|----------|-------------|
| `hyperbolicDistance(x1, y1, x2, y2)` | `2 * arctanh(\|p1 - p2\| / \|1 - conj(p1) * p2\|)` |
| `mobiusTransform(z, a)` | `(z - a) / (1 - conj(a) * z)` — maps `a` to origin |
| `mobiusInverse(w, a)` | `(w + a) / (1 + conj(a) * w)` — inverse of the above |
| `mobiusRotation(z, theta)` | `z * e^(i*theta)` — rotation about the origin |
| `geodesicArc(p1, p2)` | Returns `(centerX, centerY, radius, isDiameter)` for the geodesic through `p1`, `p2` |
| `idealPoints(p1, p2)` | Returns the two points where the geodesic meets the unit circle |
| `tangentAtPoint(p, arcCenter, arcRadius, isDiameter)` | Tangent direction of a geodesic at point `p` |
| `reflectThroughGeodesic(p, arcCenter, arcRadius, isDiameter)` | Hyperbolic reflection (circle inversion or line reflection) |
| `hyperbolicMidpoint(p1, p2)` | Midpoint on the geodesic at equal hyperbolic distance |
| `clampToDisk(x, y, maxRadius)` | Clamps `(x, y)` to `\|z\| < maxRadius`, default `maxRadius = 1 - epsilon` |

### 3.2 IntersectionUtils

Epsilon constant: `1e-10`.

Intersection algorithms operate on Euclidean circles and lines (the Euclidean representations of hyperbolic objects), then filter results to the open unit disk.

**circleCircleIntersection(cx1, cy1, r1, cx2, cy2, r2)**:
- Standard Euclidean circle-circle intersection (same algorithm as EuclideanSketchpad).
- Returns 0, 1, or 2 candidate points.

**lineCircleIntersection(x1, y1, x2, y2, cx, cy, r)**:
- Standard Euclidean line-circle intersection (for diameter geodesics intersecting circles).
- Returns 0, 1, or 2 candidate points.

**lineLineIntersection(x1, y1, x2, y2, x3, y3, x4, y4)**:
- Standard Euclidean line-line intersection (for two diameter geodesics).
- Returns 0 or 1 candidate point.

**filterToDisk(candidates, epsilon)**:
- Filters candidate points to those satisfying `x^2 + y^2 < 1 - epsilon`.

**isOnGeodesicArc(point, geodesic)**:
- Checks whether a candidate intersection point lies on the correct portion of the geodesic (respecting ray/segment bounds).
- For segments: point must be between the two endpoints along the arc.
- For rays: point must be on the extending side of the endpoint.
- For lines: always true (the full arc inside the disk).

**assignSign(ix, iy, refX, refY, refDx, refDy)**:
- Same cross-product sign disambiguation as EuclideanSketchpad.

### 3.3 MeasurementUtils

| Function | Input | Formula |
|----------|-------|---------|
| `segmentLength(seg)` | Segment | `hyperbolicDistance(x1, y1, x2, y2)` |
| `circleCircumference(circle)` | Circle | `2 * PI * sinh(hyperbolicRadius)` |
| `angleDegrees(angle)` | Angle | `-sweepAngleRadians * 180 / PI` |
| `regionArea(region)` | Region | `(n - 2) * PI - sum(interiorAngles)` (Gauss-Bonnet) |

`measure(obj)` dispatches by type and returns `null` for non-measurable types (points, lines, rays).

`measureLabel(obj)` returns a formatted string: `"%.2f"` for lengths/circumferences/areas, `"%.1f\u00B0"` for angles.

---

## 4. Dependency Graph

- Every object's ID is assigned in construction order, starting at 0.
- A parent's ID is always lower than its child's ID.
- **Update propagation**: iterate objects from lowest to highest ID. Each object with a dirty parent calls `recompute()` once. The ID ordering guarantees a valid topological sort.
- **Undefined cascading**: when an object becomes undefined, all descendants that depend on it also become undefined via their `recompute()` checks.

---

## 5. Mobius Viewport Transform

The viewport transform is a Mobius transformation (isometry of the hyperbolic plane), stored separately from the geometry objects. It does **not** modify model coordinates.

**State**: a single Mobius transformation `T(z) = e^(i*theta) * (z - a) / (1 - conj(a) * z)`, represented by complex parameters `a` (translation) and `theta` (rotation).

**Operations**:
- **Space + Click at model point `p`**: compose with the Mobius transform that sends `p` to the origin.
- **Space + Drag**: compose with a rotation `e^(i*delta_theta)`.

**Rendering pipeline**: for each geometry object, the renderer applies `T(z)` to all model coordinates before scaling to screen pixels. Since Mobius transformations map geodesics to geodesics and circles to circles, the renderer can apply `T` to the defining points, then recompute the Euclidean arc representation from the transformed points.

**Identity**: at launch, `a = 0`, `theta = 0` (identity transform).

---

## 6. Type Summary

| Type | Implements | Parents | Movable | Measurable |
|------|-----------|---------|---------|------------|
| FreePoint | PointLike | none | yes | no |
| FreePointOnLine | PointLike | GeodesicLike | yes (constrained) | no |
| FreePointOnRay | PointLike | GeodesicLike | yes (constrained) | no |
| FreePointOnSegment | PointLike | GeodesicLike | yes (constrained) | no |
| FreePointOnCircle | PointLike | Circle | yes (constrained) | no |
| Midpoint | PointLike | Point, Point | no | no |
| ReflectionPoint | PointLike | Point, GeodesicLike | no | no |
| GeodesicGeodesicIntersection | PointLike | GeodesicLike, GeodesicLike | no | no |
| GeodesicCircleIntersection | PointLike | GeodesicLike, Circle | no | no |
| CircleCircleIntersection | PointLike | Circle, Circle | no | no |
| Line | GeodesicLike | Point, Point | no | no |
| Ray | GeodesicLike | Point, Point | no | no |
| Segment | GeodesicLike | Point, Point | no | yes (length) |
| PerpendicularLine | GeodesicLike | Point, GeodesicLike | no | no |
| Circle | — | Point, Point | no | yes (circumference) |
| Angle | — | Point, Point, Point | no | yes (degrees) |
| Region | — | Point list | no | yes (area) |

---

## 7. File Layout

```
geometry/
  GeometricObject.kt    # GeometricObject sealed interface
  Point.kt              # PointLike interface, FreePoint, FreePointOn*, Midpoint, ReflectionPoint
  Geodesic.kt           # GeodesicType enum, GeodesicLike interface, Line, Ray, Segment, PerpendicularLine
  Circle.kt             # Circle
  Angle.kt              # Angle
  Region.kt             # Region
  Intersection.kt       # GeodesicGeodesicIntersection, GeodesicCircleIntersection, CircleCircleIntersection
  IntersectionUtils.kt  # IntersectionUtils object (Euclidean intersection algorithms + disk filtering)
  HyperbolicUtils.kt    # HyperbolicUtils object (Mobius transforms, hyperbolic distance, geodesic computation)
  MeasurementUtils.kt   # MeasurementUtils object (measurement dispatch)
```
