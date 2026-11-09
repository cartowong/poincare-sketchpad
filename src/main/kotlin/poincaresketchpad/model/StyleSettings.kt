package poincaresketchpad.model

import poincaresketchpad.geometry.*

data class CategoryDefaults(
    var color: String = "black",
    var size: String = "medium",
    var strokeStyle: String = "solid",
    var strokeThickness: String = "normal"
)

class StyleSettings {
    val point = CategoryDefaults(color = "black", size = "medium")
    val geodesic = CategoryDefaults(color = "black", strokeStyle = "solid", strokeThickness = "normal")
    val circle = CategoryDefaults(color = "black", strokeStyle = "solid", strokeThickness = "normal")
    val angle = CategoryDefaults(color = "green", size = "medium")
    val region = CategoryDefaults(color = "yellow")

    fun applyDefaults(metadata: ObjectMetadata, obj: GeometricObject) {
        when {
            obj is PointLike -> {
                metadata.color = point.color
                metadata.size = point.size
            }
            obj is GeodesicLike -> {
                metadata.color = geodesic.color
                metadata.strokeStyle = geodesic.strokeStyle
                metadata.strokeThickness = geodesic.strokeThickness
            }
            obj is Circle -> {
                metadata.color = circle.color
                metadata.strokeStyle = circle.strokeStyle
                metadata.strokeThickness = circle.strokeThickness
            }
            obj is Angle -> {
                metadata.color = angle.color
                metadata.size = angle.size
            }
            obj is Region -> {
                metadata.color = region.color
            }
        }
    }
}
