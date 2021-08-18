import kotlinx.serialization.Serializable
import kotlin.math.*

// NOTE: Vec2.angle and Vec2.normal make X the SINE and Y the COSINE
// because rotations in CSS are CW from pointing up, not CCW from pointing right.

@Serializable
data class Vec2(val x: Double, val y: Double) {
	operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
	operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
	operator fun times(scale: Double) = Vec2(x * scale, y * scale)
	operator fun div(scale: Double) = Vec2(x / scale, y / scale)
	
	operator fun unaryPlus() = this
	operator fun unaryMinus() = Vec2(-x, -y)
	
	fun scaleX(scale: Double) = Vec2(x * scale, y)
	fun scaleY(scale: Double) = Vec2(x, y * scale)
	
	infix fun dot(other: Vec2) = x * other.x + y * other.y
	
	fun rotateBy(radians: Double) = Vec2(
		x * cos(radians) - y * sin(radians),
		x * sin(radians) + y * cos(radians),
	)
	
	val magnitude: Double
		get() = hypot(x, y)
	
	val angle: Double
		get() = atan2(x, y)
	
	fun projectOnto(other: Vec2) = (other / other.magnitude).let { otherHat -> (this dot otherHat) * otherHat }
	fun distanceToSegment(a: Vec2, b: Vec2): Double? = ((this - a).projectOnto(b - a) + a).takeIf { (cx, cy) ->
		val xRange = min(a.x, b.x)..max(a.x, b.x)
		val yRange = min(a.y, b.y)..max(a.y, b.y)
		
		cx in xRange && cy in yRange
	}?.let { c -> (this - c).magnitude }
	
	override fun toString(): String {
		return "(${x}î + ${y}ĵ)"
	}
	
	companion object {
		fun normal(radians: Double) = Vec2(sin(radians), cos(radians))
		fun polar(radius: Double, angleRadians: Double) = radius * normal(angleRadians)
	}
}

operator fun Double.times(vec2: Vec2) = vec2 * this

fun Double.asAngle(flipX: Boolean = false, flipY: Boolean = false) = Vec2.normal(this)
	.scaleX(if (flipX) -1.0 else 1.0)
	.scaleY(if (flipY) -1.0 else 1.0)
	.angle
