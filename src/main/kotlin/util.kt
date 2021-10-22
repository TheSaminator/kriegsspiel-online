import com.github.nwillc.ksvg.RenderMode
import com.github.nwillc.ksvg.elements.Container
import com.github.nwillc.ksvg.elements.Element
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.EventTarget
import org.w3c.dom.svg.SVGElement
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.random.Random

// Math
const val EPSILON = 0.00_000_1

infix fun Double.isEqualTo(other: Double) = abs(this - other) < EPSILON
infix fun Double.isNotEqualTo(other: Double) = abs(this - other) >= EPSILON

fun Double.toTruncatedString(maxFractionalDigits: Int): String {
	val parts = toString().split('.')
	
	val (whole, frac) = if (parts.size < 2)
		listOf(parts[0], "")
	else
		parts
	
	val clippedFrac = frac.padEnd(maxFractionalDigits, padChar = '0')
	return "$whole.$clippedFrac"
}

fun ClosedFloatingPointRange<Double>.random(source: Random = Random) = source.nextDouble(start, endInclusive)

// Detect development environment
val isDevEnv: Boolean
	get() = window.location.hostname == "localhost"

// Detect shitty browser
val isChrome = js("/Chrome/.test(navigator.userAgent) && /Google Inc/.test(navigator.vendor)").unsafeCast<Boolean>()

// Serializer
val jsonSerializer = Json {
	coerceInputValues = true
	encodeDefaults = true
	ignoreUnknownKeys = true
}

inline fun <T> configure(builder: T.() -> Unit) = js("{}").unsafeCast<T>().apply(builder)

inline fun jsonString(builder: (dynamic) -> Unit) = JSON.stringify(
	js("{}").unsafeCast<Any>().also(builder)
)

// Events
class TempEvents private constructor(private val receiver: EventTarget, private val map: MutableMap<String, EventListener>) : Map<String, EventListener> by map {
	constructor(receiver: EventTarget) : this(receiver, mutableMapOf())
	
	private fun register(event: String, listener: EventListener) {
		map[event]?.let { oldListener ->
			receiver.removeEventListener(event, oldListener)
		}
		
		receiver.addEventListener(event, listener)
		map[event] = listener
	}
	
	fun <T : Event> register(event: String, callback: (T) -> Unit) {
		val listener = object : EventListener {
			override fun handleEvent(event: Event) {
				callback(event.unsafeCast<T>())
			}
		}
		
		register(event, listener)
	}
	
	fun deregister() {
		map.forEach { (e, l) ->
			receiver.removeEventListener(e, l)
		}
		
		map.clear()
	}
}

// Concurrency
suspend fun EventTarget.awaitEvent(eventName: String, shouldPreventDefault: Boolean = false): Event = suspendCancellableCoroutine { continuation ->
	val listener = object : EventListener {
		override fun handleEvent(event: Event) {
			if (shouldPreventDefault)
				event.preventDefault()
			
			removeEventListener(eventName, this)
			continuation.resume(event)
		}
	}
	
	continuation.invokeOnCancellation {
		removeEventListener(eventName, listener)
	}
	
	addEventListener(eventName, listener)
}

// Rendering
fun <T : Element> SVGElement.append(svgTag: T, block: T.() -> Unit) {
	val sb = StringBuilder()
	svgTag.apply(block).render(sb, RenderMode.INLINE)
	innerHTML += sb.toString()
}

var Element.transform: String?
	get() = attributes["transform"]
	set(value) {
		attributes["transform"] = value
	}

fun Container.image(block: IMAGE.() -> Unit) = IMAGE(validation).also {
	it.block()
	children.add(it)
}

class IMAGE(validation: Boolean = false) : Element("image", validation) {
	var x: String? by attributes
	var y: String? by attributes
	var width: String? by attributes
	var height: String? by attributes
	var href: String? by attributes
}
