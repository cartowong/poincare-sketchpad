package poincaresketchpad.geometry

import kotlin.math.*

class Angle(
    override val id: Int,
    val pointAId: Int,
    val vertexId: Int,
    val pointCId: Int
) : GeometricObject {
    override val parentIds: List<Int> = listOf(pointAId, vertexId, pointCId)
    override var isDefined: Boolean = false; private set

    var vertexX: Double = 0.0; private set
    var vertexY: Double = 0.0; private set
    var startAngleRadians: Double = 0.0; private set
    var sweepAngleRadians: Double = 0.0; private set

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pA = objects[pointAId] as? PointLike
        val pV = objects[vertexId] as? PointLike
        val pC = objects[pointCId] as? PointLike
        if (pA == null || pV == null || pC == null ||
            !(objects[pointAId]!!.isDefined) || !(objects[vertexId]!!.isDefined) || !(objects[pointCId]!!.isDefined)) {
            isDefined = false; return
        }

        vertexX = pV.x; vertexY = pV.y

        // Compute tangent of geodesic VA at vertex
        val arcVA = HyperbolicUtils.geodesicArc(pV.x, pV.y, pA.x, pA.y)
        val (taX, taY) = HyperbolicUtils.tangentAtPoint(pV.x, pV.y, pV.x, pV.y, pA.x, pA.y, arcVA)

        // Compute tangent of geodesic VC at vertex
        val arcVC = HyperbolicUtils.geodesicArc(pV.x, pV.y, pC.x, pC.y)
        val (tcX, tcY) = HyperbolicUtils.tangentAtPoint(pV.x, pV.y, pV.x, pV.y, pC.x, pC.y, arcVC)

        // Start angle: direction of tangent VA
        startAngleRadians = atan2(taY, taX)

        // Sweep: CW from VA to VC (negative = clockwise in JavaFX)
        var sweep = atan2(tcY, tcX) - startAngleRadians
        while (sweep >= 0) sweep -= 2 * PI
        while (sweep < -2 * PI) sweep += 2 * PI

        sweepAngleRadians = sweep
        isDefined = true
    }

    fun measureDegrees(): Double = -sweepAngleRadians * 180.0 / PI
}
