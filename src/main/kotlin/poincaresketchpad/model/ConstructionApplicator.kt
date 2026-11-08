package poincaresketchpad.model

import poincaresketchpad.geometry.*
import poincaresketchpad.persistence.LoadedConstruction

object ConstructionApplicator {

    private val POINT_TYPES = setOf(
        "FreePoint", "FreePointOnLine", "FreePointOnRay",
        "FreePointOnSegment", "FreePointOnCircle",
        "GeodesicGeodesicIntersection", "GeodesicCircleIntersection", "CircleCircleIntersection",
        "Midpoint", "ReflectionPoint"
    )

    fun apply(
        construction: LoadedConstruction,
        selectedInputIds: List<Int>,
        manager: GeometricObjectManager
    ): List<Int> {
        val diagram = construction.diagram
        val constructionInputIds = construction.inputIds

        if (selectedInputIds.size != constructionInputIds.size) {
            throw IllegalArgumentException(
                "Expected ${constructionInputIds.size} inputs, got ${selectedInputIds.size}"
            )
        }

        val idMapping = mutableMapOf<Int, Int>()
        for (i in constructionInputIds.indices) {
            idMapping[constructionInputIds[i]] = selectedInputIds[i]
        }

        val newObjectIds = mutableListOf<Int>()
        val constructedObjects = getConstructedObjects(diagram)

        for (sObj in constructedObjects) {
            val remappedParentIds = sObj.parentIds.map { idMapping[it]!! }
            val remappedPointIds = sObj.pointIds?.map { idMapping[it]!! }

            val newObj = manager.addObject { id ->
                instantiateObject(id, sObj, remappedParentIds, remappedPointIds)
            }

            idMapping[sObj.id] = newObj.id
            newObjectIds.add(newObj.id)

            val sMeta = diagram.metadata[sObj.id]
            if (sMeta != null) {
                val meta = manager.getMetadata(newObj.id)
                if (meta != null) {
                    meta.visible = sMeta.visible
                }
            }
        }

        for (i in constructionInputIds.indices) {
            val sMeta = diagram.metadata[constructionInputIds[i]]
            if (sMeta != null && !sMeta.visible) {
                val globalId = selectedInputIds[i]
                val meta = manager.getMetadata(globalId)
                if (meta != null) {
                    meta.visible = false
                    meta.labelVisible = false
                }
            }
        }

        return newObjectIds
    }

    fun computePreview(
        construction: LoadedConstruction,
        selectedInputIds: List<Int>,
        manager: GeometricObjectManager
    ): List<GeometricObject> {
        val diagram = construction.diagram
        val constructionInputIds = construction.inputIds

        if (selectedInputIds.size != constructionInputIds.size) return emptyList()

        val idMapping = mutableMapOf<Int, Int>()
        for (i in constructionInputIds.indices) {
            idMapping[constructionInputIds[i]] = selectedInputIds[i]
        }

        val tempObjects = linkedMapOf<Int, GeometricObject>()
        tempObjects.putAll(manager.objectMap())

        val previewObjects = mutableListOf<GeometricObject>()
        var tempId = -1
        val constructedObjects = getConstructedObjects(diagram)

        for (sObj in constructedObjects) {
            val remappedParentIds = sObj.parentIds.map { idMapping[it]!! }
            val remappedPointIds = sObj.pointIds?.map { idMapping[it]!! }

            val id = tempId--
            val obj = instantiateObject(id, sObj, remappedParentIds, remappedPointIds)
            tempObjects[id] = obj
            obj.recompute(tempObjects)
            idMapping[sObj.id] = id

            val sMeta = diagram.metadata[sObj.id]
            if (sMeta == null || sMeta.visible) {
                previewObjects.add(obj)
            }
        }

        return previewObjects
    }

    private fun getConstructedObjects(diagram: poincaresketchpad.persistence.SerializedDiagram) =
        diagram.objects
            .filter { sObj ->
                val sMeta = diagram.metadata[sObj.id]
                sMeta != null && !sMeta.isInput && !sMeta.isLocked
            }
            .sortedBy { it.id }

    private fun instantiateObject(
        id: Int,
        sObj: poincaresketchpad.persistence.SerializedObject,
        remappedParentIds: List<Int>,
        remappedPointIds: List<Int>?
    ): GeometricObject = when (sObj.type) {
        "FreePoint" -> FreePoint(id, sObj.x ?: 0.0, sObj.y ?: 0.0)
        "FreePointOnLine" -> FreePointOnLine(id, remappedParentIds[0], sObj.t ?: 0.0)
        "FreePointOnRay" -> FreePointOnRay(id, remappedParentIds[0], sObj.t ?: 0.0)
        "FreePointOnSegment" -> FreePointOnSegment(id, remappedParentIds[0], sObj.t ?: 0.5)
        "FreePointOnCircle" -> FreePointOnCircle(id, remappedParentIds[0], sObj.theta ?: 0.0)
        "GeodesicGeodesicIntersection" -> GeodesicGeodesicIntersection(id, remappedParentIds[0], remappedParentIds[1])
        "GeodesicCircleIntersection" -> GeodesicCircleIntersection(id, remappedParentIds[0], remappedParentIds[1], sObj.sign ?: 1)
        "CircleCircleIntersection" -> CircleCircleIntersection(id, remappedParentIds[0], remappedParentIds[1], sObj.sign ?: 1)
        "Line" -> Line(id, remappedParentIds[0], remappedParentIds[1])
        "Ray" -> Ray(id, remappedParentIds[0], remappedParentIds[1])
        "Segment" -> Segment(id, remappedParentIds[0], remappedParentIds[1])
        "Circle" -> Circle(id, remappedParentIds[0], remappedParentIds[1])
        "Angle" -> Angle(id, remappedParentIds[0], remappedParentIds[1], remappedParentIds[2])
        "Region" -> Region(id, remappedPointIds ?: emptyList())
        "Midpoint" -> Midpoint(id, remappedParentIds[0], remappedParentIds[1])
        "ReflectionPoint" -> ReflectionPoint(id, remappedParentIds[0], remappedParentIds[1])
        "PerpendicularLine" -> PerpendicularLine(id, remappedParentIds[0], remappedParentIds[1])
        else -> throw IllegalArgumentException("Unknown type: ${sObj.type}")
    }

    fun isPointType(type: String): Boolean = type in POINT_TYPES || type == "Point"

    fun matchesExpectedType(obj: GeometricObject, expectedType: String): Boolean {
        if (expectedType == "Point" || expectedType in POINT_TYPES) {
            return obj is PointLike
        }
        return when (expectedType) {
            "Line" -> obj is Line
            "Ray" -> obj is Ray
            "Segment" -> obj is Segment
            "PerpendicularLine" -> obj is PerpendicularLine
            "Geodesic" -> obj is GeodesicLike
            "Circle" -> obj is Circle
            else -> false
        }
    }
}
