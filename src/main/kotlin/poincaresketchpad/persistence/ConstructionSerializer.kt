package poincaresketchpad.persistence

import poincaresketchpad.APP_NAME
import poincaresketchpad.APP_VERSION
import poincaresketchpad.geometry.*
import poincaresketchpad.model.GeometricObjectManager
import java.io.File

data class LoadedConstruction(
    val fileName: String,
    val diagram: SerializedDiagram,
    val inputTypes: List<String>,
    val inputIds: List<Int>
)

object ConstructionSerializer {

    fun loadFromFile(file: File): LoadedConstruction {
        val diagram = DiagramSerializer.parseDiagram(file.readText())
        DiagramSerializer.validateAppName(diagram.appName)
        DiagramSerializer.validateVersion(diagram.version)
        DiagramSerializer.validateModel(diagram.model)

        val inputIds = diagram.metadata.entries
            .filter { it.value.isInput }
            .map { it.key }
            .sorted()

        val pointTypes = setOf(
            "FreePoint", "FreePointOnLine", "FreePointOnRay",
            "FreePointOnSegment", "FreePointOnCircle",
            "GeodesicGeodesicIntersection", "GeodesicCircleIntersection", "CircleCircleIntersection",
            "Midpoint", "ReflectionPoint"
        )
        val objectsById = diagram.objects.associateBy { it.id }
        val inputTypes = inputIds.map { id ->
            val type = objectsById[id]?.type ?: "Point"
            if (type in pointTypes) "Point" else type
        }

        return LoadedConstruction(
            fileName = file.nameWithoutExtension,
            diagram = diagram,
            inputTypes = inputTypes,
            inputIds = inputIds
        )
    }

    fun saveConstruction(manager: GeometricObjectManager, file: File) {
        val objects = manager.allObjects().map { serializeObject(it) }
        val metadata = manager.allObjects().associate { obj ->
            val meta = manager.getMetadata(obj.id)!!
            obj.id to SerializedMetadata(
                visible = meta.visible,
                label = null,
                labelVisible = false,
                labelOffsetX = 10.0,
                labelOffsetY = -10.0,
                inMeasurementTable = false,
                labelAnchorAngle = null,
                labelAnchorT = null,
                isInput = meta.isInput,
                isLocked = meta.isLocked,
                color = null,
                size = null,
                strokeStyle = null,
                strokeThickness = null
            )
        }
        val diagram = SerializedDiagram(APP_NAME, APP_VERSION, "poincare", objects, metadata)
        file.writeText(DiagramSerializer.encodeDiagram(diagram))
    }

    private fun serializeObject(obj: GeometricObject): SerializedObject = when (obj) {
        is FreePoint -> SerializedObject(obj.id, "FreePoint", x = obj.x, y = obj.y)
        is FreePointOnLine -> SerializedObject(obj.id, "FreePointOnLine", parentIds = listOf(obj.lineId), t = obj.t)
        is FreePointOnRay -> SerializedObject(obj.id, "FreePointOnRay", parentIds = listOf(obj.rayId), t = obj.t)
        is FreePointOnSegment -> SerializedObject(obj.id, "FreePointOnSegment", parentIds = listOf(obj.segmentId), t = obj.t)
        is FreePointOnCircle -> SerializedObject(obj.id, "FreePointOnCircle", parentIds = listOf(obj.circleId), theta = obj.theta)
        is GeodesicGeodesicIntersection -> SerializedObject(obj.id, "GeodesicGeodesicIntersection", parentIds = listOf(obj.geodesic1Id, obj.geodesic2Id))
        is GeodesicCircleIntersection -> SerializedObject(obj.id, "GeodesicCircleIntersection", parentIds = listOf(obj.geodesicId, obj.circleId), sign = obj.sign)
        is CircleCircleIntersection -> SerializedObject(obj.id, "CircleCircleIntersection", parentIds = listOf(obj.circle1Id, obj.circle2Id), sign = obj.sign)
        is Line -> SerializedObject(obj.id, "Line", parentIds = listOf(obj.point1Id, obj.point2Id))
        is Ray -> SerializedObject(obj.id, "Ray", parentIds = listOf(obj.endpointId, obj.throughId))
        is Segment -> SerializedObject(obj.id, "Segment", parentIds = listOf(obj.point1Id, obj.point2Id))
        is Circle -> SerializedObject(obj.id, "Circle", parentIds = listOf(obj.centerId, obj.radiusPointId))
        is Angle -> SerializedObject(obj.id, "Angle", parentIds = listOf(obj.pointAId, obj.vertexId, obj.pointCId))
        is Region -> SerializedObject(obj.id, "Region", pointIds = obj.pointIds)
        is Midpoint -> SerializedObject(obj.id, "Midpoint", parentIds = listOf(obj.point1Id, obj.point2Id))
        is ReflectionPoint -> SerializedObject(obj.id, "ReflectionPoint", parentIds = listOf(obj.pointId, obj.geodesicId))
        is PerpendicularLine -> SerializedObject(obj.id, "PerpendicularLine", parentIds = listOf(obj.pointId, obj.geodesicId))
    }
}
