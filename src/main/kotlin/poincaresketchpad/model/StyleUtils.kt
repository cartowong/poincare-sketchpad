package poincaresketchpad.model

object StyleUtils {

    val COLOR_MAP = mapOf(
        "black" to "#000000",
        "brown" to "#795548",
        "green" to "#4CAF50",
        "purple" to "#9C27B0",
        "red" to "#F44336",
        "yellow" to "#FFEB3B"
    )

    val COLOR_NAMES = listOf("black", "brown", "green", "purple", "red", "yellow")

    fun colorHex(name: String): String = COLOR_MAP[name] ?: "#000000"

    fun pointRadius(size: String): Double = when (size) {
        "small" -> 3.0
        "medium" -> 4.0
        "large" -> 6.0
        else -> 4.0
    }

    fun angleArcRadius(size: String): Double = when (size) {
        "small" -> 20.0
        "medium" -> 25.0
        "large" -> 30.0
        else -> 25.0
    }

    fun strokeThickness(thickness: String): Double = when (thickness) {
        "thin" -> 1.0
        "normal" -> 2.0
        "thick" -> 3.0
        else -> 2.0
    }
}
