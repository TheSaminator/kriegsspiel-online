import com.github.nwillc.ksvg.RenderMode
import com.github.nwillc.ksvg.elements.Container
import com.github.nwillc.ksvg.elements.Element
import kotlinx.html.HTMLTag
import kotlinx.html.unsafe
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.EventTarget
import org.w3c.dom.svg.SVGElement
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.random.Random
import kotlin.reflect.KMutableProperty0

// Math
const val EPSILON = 0.00_000_1

infix fun Double.isEqualTo(other: Double) = abs(this - other) < EPSILON
infix fun Double.isNotEqualTo(other: Double) = abs(this - other) >= EPSILON

infix fun Double.isLessThan(other: Double) = (this - other) <= -EPSILON
infix fun Double.isGreaterThan(other: Double) = (this - other) >= EPSILON
infix fun Double.isLessThanOrEqualTo(other: Double) = (this - other) < EPSILON
infix fun Double.isGreaterThanOrEqualTo(other: Double) = (this - other) > -EPSILON

fun ClosedFloatingPointRange<Double>.random(source: Random = Random) = source.nextDouble(start, endInclusive)

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
	js("{}").unsafeCast<Any?>().also(builder)
)

// Events
class TempEvents private constructor(val receiver: EventTarget, private val map: MutableMap<String, EventListener>) : Map<String, EventListener> by map {
	constructor(receiver: EventTarget) : this(receiver, mutableMapOf())
	
	fun register(event: String, listener: EventListener) {
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
suspend fun EventTarget.awaitEvent(eventName: String): Event = suspendCoroutine { continuation ->
	addEventListener(eventName, object : EventListener {
		override fun handleEvent(event: Event) {
			removeEventListener(eventName, this)
			continuation.resume(event)
		}
	})
}

suspend fun <T> KMutableProperty0<((T) -> Unit)?>.await(): T = suspendCoroutine { continuation ->
	val prevValue = this.get()
	this.set {
		this.set(prevValue)
		continuation.resume(it)
	}
}

suspend fun <T> awaitCallback(block: (callback: (T) -> Unit) -> Unit): T = suspendCoroutine { continuation ->
	block { result ->
		continuation.resume(result)
	}
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
