@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS", "EXTERNAL_DELEGATION", "unused", "UNUSED_PARAMETER", "NOTHING_TO_INLINE")

package HammerJS

import org.w3c.dom.HTMLElement
import org.w3c.dom.events.EventTarget
import org.w3c.dom.svg.SVGElement

external val Hammer: HammerStatic

external interface HammerStatic {
	var defaults: HammerDefaults
	var VERSION: Number
	var INPUT_START: String /* 1 */
	var INPUT_MOVE: String /* 2 */
	var INPUT_END: String /* 4 */
	var INPUT_CANCEL: String /* 8 */
	var STATE_POSSIBLE: String /* 1 */
	var STATE_BEGAN: String /* 2 */
	var STATE_CHANGED: String /* 4 */
	var STATE_ENDED: String /* 8 */
	var STATE_RECOGNIZED: String /* 8 */
	var STATE_CANCELLED: String /* 16 */
	var STATE_FAILED: String /* 32 */
	var DIRECTION_NONE: String /* 1 */
	var DIRECTION_LEFT: String /* 2 */
	var DIRECTION_RIGHT: String /* 4 */
	var DIRECTION_UP: String /* 8 */
	var DIRECTION_DOWN: String /* 16 */
	var DIRECTION_HORIZONTAL: String /* 6 */
	var DIRECTION_VERTICAL: String /* 24 */
	var DIRECTION_ALL: String /* 30 */
	var Manager: HammerManagerConstructor
	var Input: HammerInput
	var TouchAction: TouchAction
	var TouchInput: TouchInput
	var MouseInput: MouseInput
	var PointerEventInput: PointerEventInput
	var TouchMouseInput: TouchMouseInput
	var SingleTouchInput: SingleTouchInput
	var Recognizer: RecognizerStatic
	var AttrRecognizer: AttrRecognizerStatic
	var Tap: TapRecognizerStatic
	var Pan: PanRecognizerStatic
	var Swipe: SwipeRecognizerStatic
	var Pinch: PinchRecognizerStatic
	var Rotate: RotateRecognizerStatic
	var Press: PressRecognizerStatic
	fun on(target: EventTarget, types: String, handler: Function<*>)
	fun off(target: EventTarget, types: String, handler: Function<*>)
	fun each(obj: Any, iterator: Function<*>, context: Any)
	fun merge(dest: Any, src: Any): Any
	fun extend(dest: Any, src: Any, merge: Boolean): Any
	fun inherit(child: Function<*>, base: Function<*>, properties: Any): Any
	fun bindFn(fn: Function<*>, context: Any): Function<*>
	fun prefixed(obj: Any, property: String): String
}

inline operator fun HammerStatic.invoke(element: HTMLElement) = js("new Hammer(element)").unsafeCast<HammerManager>()
inline operator fun HammerStatic.invoke(element: HTMLElement, options: HammerOptions) = js("new Hammer(element, options)").unsafeCast<HammerManager>()

inline operator fun HammerStatic.invoke(element: SVGElement) = js("new Hammer(element)").unsafeCast<HammerManager>()
inline operator fun HammerStatic.invoke(element: SVGElement, options: HammerOptions) = js("new Hammer(element, options)").unsafeCast<HammerManager>()

external interface HammerDefaults : HammerOptions {
	override var enable: Boolean
}

external interface CssProps {
	var contentZooming: String
	var tapHighlightColor: String
	var touchCallout: String
	var touchSelect: String
	var userDrag: String
	var userSelect: String
}

external interface HammerOptions {
	var cssProps: CssProps?
		get() = definedExternally
		set(value) = definedExternally
	var domEvents: Boolean?
		get() = definedExternally
		set(value) = definedExternally
	var enable: dynamic /* Boolean | (manager: HammerManager) -> Boolean | Nothing? */
		get() = definedExternally
		set(value) = definedExternally
	var preset: Array<dynamic /* dynamic | dynamic | dynamic | dynamic */>?
		get() = definedExternally
		set(value) = definedExternally
	var touchAction: String?
		get() = definedExternally
		set(value) = definedExternally
	var recognizers: Array<dynamic /* dynamic | dynamic | dynamic | dynamic */>?
		get() = definedExternally
		set(value) = definedExternally
	var inputClass: HammerInput?
		get() = definedExternally
		set(value) = definedExternally
	var inputTarget: EventTarget?
		get() = definedExternally
		set(value) = definedExternally
}

external interface HammerManagerConstructor

external interface HammerManager {
	fun add(recogniser: Recognizer): Recognizer
	fun add(recogniser: Recognizer): HammerManager
	fun add(recogniser: Array<Recognizer>): Recognizer
	fun add(recogniser: Array<Recognizer>): HammerManager
	fun destroy()
	fun emit(event: String, data: Any)
	fun get(recogniser: Recognizer): Recognizer
	fun get(recogniser: String): Recognizer
	fun off(events: String, handler: (HammerInput) -> Unit = definedExternally)
	fun on(events: String, handler: (HammerInput) -> Unit)
	fun recognize(inputData: Any)
	fun remove(recogniser: Recognizer): HammerManager
	fun remove(recogniser: String): HammerManager
	fun set(options: HammerOptions): HammerManager
	fun stop(force: Boolean)
}

external open class HammerInput(manager: HammerManager, callback: Function<*>) {
	open fun destroy()
	open fun handler()
	open fun init()
	open fun preventDefault()
	open var type: String
	open var deltaX: Number
	open var deltaY: Number
	open var deltaTime: Number
	open var distance: Number
	open var angle: Number
	open var velocityX: Number
	open var velocityY: Number
	open var velocity: Number
	open var overallVelocity: Number
	open var overallVelocityX: Number
	open var overallVelocityY: Number
	open var direction: Number
	open var offsetDirection: Number
	open var scale: Number
	open var rotation: Number
	open var center: HammerPoint
	open var srcEvent: dynamic /* TouchEvent | MouseEvent | PointerEvent */
	open var target: HTMLElement
	open var pointerType: String
	open var eventType: dynamic /* Any */
	open var isFirst: Boolean
	open var isFinal: Boolean
	open var pointers: Array<Any>
	open var changedPointers: Array<Any>
	open var maxPointers: Number
	open var timeStamp: Number
}

external open class MouseInput(manager: HammerManager, callback: Function<*>) : HammerInput

external open class PointerEventInput(manager: HammerManager, callback: Function<*>) : HammerInput

external open class SingleTouchInput(manager: HammerManager, callback: Function<*>) : HammerInput

external open class TouchInput(manager: HammerManager, callback: Function<*>) : HammerInput

external open class TouchMouseInput(manager: HammerManager, callback: Function<*>) : HammerInput

external interface RecognizerOptions {
	var direction: dynamic
		get() = definedExternally
		set(value) = definedExternally
	var enable: dynamic /* Boolean | (recognizer: Recognizer, inputData: HammerInput) -> Boolean | Nothing? */
		get() = definedExternally
		set(value) = definedExternally
	var event: String?
		get() = definedExternally
		set(value) = definedExternally
	var interval: Number?
		get() = definedExternally
		set(value) = definedExternally
	var pointers: Number?
		get() = definedExternally
		set(value) = definedExternally
	var posThreshold: Number?
		get() = definedExternally
		set(value) = definedExternally
	var taps: Number?
		get() = definedExternally
		set(value) = definedExternally
	var threshold: Number?
		get() = definedExternally
		set(value) = definedExternally
	var time: Number?
		get() = definedExternally
		set(value) = definedExternally
	var velocity: Number?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RecognizerStatic

external interface Recognizer {
	var defaults: Any
	fun canEmit(): Boolean
	fun canRecognizeWith(otherRecognizer: Recognizer): Boolean
	fun dropRecognizeWith(otherRecognizer: Recognizer): Recognizer
	fun dropRecognizeWith(otherRecognizer: Array<Recognizer>): Recognizer
	fun dropRecognizeWith(otherRecognizer: String): Recognizer
	fun dropRequireFailure(otherRecognizer: Recognizer): Recognizer
	fun dropRequireFailure(otherRecognizer: Array<Recognizer>): Recognizer
	fun dropRequireFailure(otherRecognizer: String): Recognizer
	fun emit(input: HammerInput)
	fun getTouchAction(): Array<Any>
	fun hasRequireFailures(): Boolean
	fun process(inputData: HammerInput): String
	fun recognize(inputData: HammerInput)
	fun recognizeWith(otherRecognizer: Recognizer): Recognizer
	fun recognizeWith(otherRecognizer: Array<Recognizer>): Recognizer
	fun recognizeWith(otherRecognizer: String): Recognizer
	fun requireFailure(otherRecognizer: Recognizer): Recognizer
	fun requireFailure(otherRecognizer: Array<Recognizer>): Recognizer
	fun requireFailure(otherRecognizer: String): Recognizer
	fun reset()
	fun set(options: RecognizerOptions = definedExternally): Recognizer
	fun tryEmit(input: HammerInput)
}

external interface AttrRecognizerStatic {
	fun attrTest(input: HammerInput): Boolean
	fun process(input: HammerInput): Any
}

external interface AttrRecognizer : Recognizer

external interface PanRecognizerStatic

external interface PanRecognizer : AttrRecognizer

external interface PinchRecognizerStatic

external interface PinchRecognizer : AttrRecognizer

external interface PressRecognizerStatic

external interface PressRecognizer : AttrRecognizer

external interface RotateRecognizerStatic

external interface RotateRecognizer : AttrRecognizer

external interface SwipeRecognizerStatic

external interface SwipeRecognizer : AttrRecognizer

external interface TapRecognizerStatic

external interface TapRecognizer : AttrRecognizer

external open class TouchAction(manager: HammerManager, value: String) {
	open fun compute(): String
	open fun preventDefaults(input: HammerInput)
	open fun preventSrc(srcEvent: Any)
	open fun set(value: String)
	open fun update()
}

external interface HammerPoint {
	var x: Number
	var y: Number
}