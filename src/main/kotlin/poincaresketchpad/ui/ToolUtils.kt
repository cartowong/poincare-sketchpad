package poincaresketchpad.ui

import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager

object ToolUtils {

    fun constructIntersection(
        candidate: IntersectionCandidate,
        manager: GeometricObjectManager,
        hitTester: HitTester,
        requestRender: () -> Unit
    ): Int {
        val obj1 = manager.getObject(candidate.parent1Id)
        val obj2 = manager.getObject(candidate.parent2Id)
        val isGeodesic1 = obj1 is GeodesicLike
        val isGeodesic2 = obj2 is GeodesicLike
        val isCircle1 = obj1 is Circle
        val isCircle2 = obj2 is Circle

        val ix = when {
            isGeodesic1 && isGeodesic2 ->
                manager.addObject { id -> GeodesicGeodesicIntersection(id, candidate.parent1Id, candidate.parent2Id) }
            isGeodesic1 && isCircle2 ->
                manager.addObject { id -> GeodesicCircleIntersection(id, candidate.parent1Id, candidate.parent2Id, candidate.sign) }
            isCircle1 && isGeodesic2 ->
                manager.addObject { id -> GeodesicCircleIntersection(id, candidate.parent2Id, candidate.parent1Id, candidate.sign) }
            isCircle1 && isCircle2 ->
                manager.addObject { id -> CircleCircleIntersection(id, candidate.parent1Id, candidate.parent2Id, candidate.sign) }
            else -> manager.addObject { id -> FreePoint(id, candidate.x, candidate.y) }
        }
        hitTester.rebuildIntersectionCache()
        requestRender()
        return ix.id
    }

    fun resolveOrCreatePoint(
        hit: HitResult?,
        worldX: Double,
        worldY: Double,
        manager: GeometricObjectManager,
        hitTester: HitTester,
        requestRender: () -> Unit
    ): Int {
        return when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.IntersectionPreview -> constructIntersection(hit.candidate, manager, hitTester, requestRender)
            else -> {
                val (px, py) = HyperbolicUtils.clampToDisk(worldX, worldY)
                val pt = manager.addObject { id -> FreePoint(id, px, py) }
                hitTester.rebuildIntersectionCache()
                requestRender()
                pt.id
            }
        }
    }
}
