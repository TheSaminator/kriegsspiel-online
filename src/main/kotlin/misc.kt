import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.DOMMatrix
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.get
import org.w3c.dom.svg.SVGGElement
import org.w3c.dom.svg.SVGSVGElement

object ExitHandler {
	private val exitHandler = object : EventListener {
		override fun handleEvent(event: Event) {
			event.preventDefault()
			event.asDynamic().returnValue = ""
		}
	}
	
	var isAttached = false
		private set
	
	fun attach() {
		window.addEventListener("beforeunload", exitHandler)
		isAttached = true
	}
	
	fun detach() {
		window.removeEventListener("beforeunload", exitHandler)
		isAttached = false
	}
}

object SVGCoordinates {
	private val svgElement by lazy {
		document.getElementById("game-field").unsafeCast<SVGSVGElement>()
	}
	
	private val panZoomViewport: SVGGElement
		get() = svgElement.getElementsByClassName("svg-pan-zoom_viewport")[0].unsafeCast<SVGGElement>()
	
	private val transformMatrix: DOMMatrix
		get() = panZoomViewport.getScreenCTM()!!
	
	fun svgToDom(svgCoords: Vec2): Vec2 {
		val pt = svgElement.createSVGPoint()
		pt.x = svgCoords.x
		pt.y = svgCoords.y
		
		val domPt = pt.matrixTransform(transformMatrix)
		return Vec2(domPt.x, domPt.y)
	}
	
	fun domToSvg(svgCoords: Vec2): Vec2 {
		val pt = svgElement.createSVGPoint()
		pt.x = svgCoords.x
		pt.y = svgCoords.y
		
		val svgPt = pt.matrixTransform(transformMatrix.inverse())
		return Vec2(svgPt.x, svgPt.y)
	}
}
