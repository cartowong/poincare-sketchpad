package poincaresketchpad.geometry

sealed interface GeometricObject {
    val id: Int
    val parentIds: List<Int>
    val isDefined: Boolean
    fun recompute(objects: Map<Int, GeometricObject>)
}
