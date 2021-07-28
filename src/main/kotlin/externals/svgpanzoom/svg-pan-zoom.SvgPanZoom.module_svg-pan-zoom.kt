@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS", "DEPRECATION")
package SvgPanZoom

import kotlin.js.*
import org.w3c.dom.*
import org.w3c.dom.svg.*

external interface SVGPanZoomOptions {
    var viewportSelector: dynamic /* String? | HTMLElement? | SVGElement? */
        get() = definedExternally
        set(value) = definedExternally
    var panEnabled: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var controlIconsEnabled: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var zoomEnabled: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var dblClickZoomEnabled: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var mouseWheelZoomEnabled: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var preventMouseEventsDefault: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var zoomScaleSensitivity: Double?
        get() = definedExternally
        set(value) = definedExternally
    var minZoom: Double?
        get() = definedExternally
        set(value) = definedExternally
    var maxZoom: Double?
        get() = definedExternally
        set(value) = definedExternally
    var fit: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var contain: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var center: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var refreshRate: dynamic /* Double? | "auto" */
        get() = definedExternally
        set(value) = definedExternally
    var beforeZoom: ((oldScale: Double, newScale: Double) -> dynamic)?
        get() = definedExternally
        set(value) = definedExternally
    var onZoom: ((newScale: Double) -> Unit)?
        get() = definedExternally
        set(value) = definedExternally
    var beforePan: ((oldPan: Point, newPan: Point) -> dynamic)?
        get() = definedExternally
        set(value) = definedExternally
    var onPan: ((newPan: Point) -> Unit)?
        get() = definedExternally
        set(value) = definedExternally
    var onUpdatedCTM: ((newCTM: DOMMatrix) -> Unit)?
        get() = definedExternally
        set(value) = definedExternally
    var customEventsHandler: CustomEventHandler?
        get() = definedExternally
        set(value) = definedExternally
    var eventsListenerElement: SVGElement?
        get() = definedExternally
        set(value) = definedExternally
}

external interface CustomEventHandler {
    var init: (options: CustomEventOptions) -> Unit
    var haltEventListeners: Array<String>
    var destroy: () -> Unit
}

external interface CustomEventOptions {
    var svgElement: SVGSVGElement
    var instance: SVGPanZoomInstance
}

external interface Point {
    var x: Double
    var y: Double
}

external interface PointModifier {
    var x: dynamic /* Double | Boolean */
        get() = definedExternally
        set(value) = definedExternally
    var y: dynamic /* Double | Boolean */
        get() = definedExternally
        set(value) = definedExternally
}

external interface ViewBox {
	var x: Double
	var y: Double
	var width: Double
	var height: Double
}

external interface Sizes {
    var width: Double
    var height: Double
    var realZoom: Double
    var viewBox: ViewBox
}

external interface SVGPanZoomInstance {
    @nativeInvoke
    operator fun invoke(svg: String, SVGPanZoomOptions: SVGPanZoomOptions = definedExternally): SVGPanZoomInstance
    @nativeInvoke
    operator fun invoke(svg: String): SVGPanZoomInstance
    @nativeInvoke
    operator fun invoke(svg: HTMLElement, SVGPanZoomOptions: SVGPanZoomOptions = definedExternally): SVGPanZoomInstance
    @nativeInvoke
    operator fun invoke(svg: HTMLElement): SVGPanZoomInstance
    @nativeInvoke
    operator fun invoke(svg: SVGElement, SVGPanZoomOptions: SVGPanZoomOptions = definedExternally): SVGPanZoomInstance
    @nativeInvoke
    operator fun invoke(svg: SVGElement): SVGPanZoomInstance
    fun enablePan(): SVGPanZoomInstance
    fun disablePan(): SVGPanZoomInstance
    fun isPanEnabled(): Boolean
    fun setBeforePan(fn: (oldPoint: Point, newPoint: Point) -> Any): SVGPanZoomInstance
    fun setOnPan(fn: (point: Point) -> Unit): SVGPanZoomInstance
    fun pan(point: Point): SVGPanZoomInstance
    fun panBy(point: Point): SVGPanZoomInstance
    fun getPan(): Point
    fun resetPan(): SVGPanZoomInstance
    fun enableZoom(): SVGPanZoomInstance
    fun disableZoom(): SVGPanZoomInstance
    fun isZoomEnabled(): Boolean
    fun enableControlIcons(): SVGPanZoomInstance
    fun disableControlIcons(): SVGPanZoomInstance
    fun isControlIconsEnabled(): Boolean
    fun enableDblClickZoom(): SVGPanZoomInstance
    fun disableDblClickZoom(): SVGPanZoomInstance
    fun isDblClickZoomEnabled(): Boolean
    fun enableMouseWheelZoom(): SVGPanZoomInstance
    fun disableMouseWheelZoom(): SVGPanZoomInstance
    fun isMouseWheelZoomEnabled(): Boolean
    fun setZoomScaleSensitivity(scale: Double): SVGPanZoomInstance
    fun setMinZoom(zoom: Double): SVGPanZoomInstance
    fun setMaxZoom(zoom: Double): SVGPanZoomInstance
    fun setBeforeZoom(fn: (oldScale: Double, newScale: Double) -> Any): SVGPanZoomInstance
    fun setOnZoom(fn: (scale: Double) -> Unit): SVGPanZoomInstance
    fun zoom(scale: Double)
    fun zoomIn(): SVGPanZoomInstance
    fun zoomOut(): SVGPanZoomInstance
    fun zoomBy(scale: Double): SVGPanZoomInstance
    fun zoomAtPoint(scale: Double, point: Point): SVGPanZoomInstance
    fun zoomAtPointBy(scale: Double, point: Point): SVGPanZoomInstance
    fun resetZoom(): SVGPanZoomInstance
    fun getZoom(): Double
    fun setOnUpdatedCTM(fn: (newCTM: DOMMatrix) -> Unit): SVGPanZoomInstance
    fun fit(): SVGPanZoomInstance
    fun contain(): SVGPanZoomInstance
    fun center(): SVGPanZoomInstance
    fun resize(): SVGPanZoomInstance
    fun getSizes(): Sizes
    fun reset(): SVGPanZoomInstance
    fun updateBBox(): SVGPanZoomInstance
    fun destroy()
}