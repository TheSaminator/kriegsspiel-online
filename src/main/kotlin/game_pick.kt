import com.github.nwillc.ksvg.elements.CIRCLE
import com.github.nwillc.ksvg.elements.PATH
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.clear
import kotlinx.serialization.Serializable
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.svg.SVGCircleElement
import org.w3c.dom.svg.SVGGElement
import org.w3c.dom.svg.SVGPathElement
import kotlin.math.abs
import kotlin.math.sin

@Serializable
sealed class PickBoundary {
	abstract fun isInBoundary(pos: Vec2): Boolean
}

@Serializable
data class PickBoundaryRectangle(
	val center: Vec2,
	val width: Double,
	val height: Double
) : PickBoundary() {
	override fun isInBoundary(pos: Vec2): Boolean {
		val xRange = (center.x - width / 2)..(center.x + width / 2)
		val yRange = (center.y - height / 2)..(center.y + height / 2)
		
		return pos.x in xRange && pos.y in yRange
	}
}

@Serializable
data class PickBoundaryCircle(
	val center: Vec2,
	val radius: Double
) : PickBoundary() {
	override fun isInBoundary(pos: Vec2): Boolean {
		return (pos - center).magnitude < radius
	}
}

@Serializable
data class PickBoundaryUnitBased(
	val center: Vec2,
	val minRadius: Double?,
	val maxRadius: Double,
	val angleOrigin: Double,
	val maxAngleDiff: Double
) : PickBoundary() {
	override fun isInBoundary(pos: Vec2): Boolean {
		console.log("Checking distance ${(pos - center).magnitude} up to $maxRadius")
		
		if ((pos - center).magnitude > maxRadius)
			return false
		
		console.log("Checking distance ${(pos - center).magnitude} at least $minRadius")
		
		if (minRadius != null) {
			if ((pos - center).magnitude < minRadius)
				return false
		}
		
		val angleRange = -maxAngleDiff..maxAngleDiff
		val currAngle = (angleOrigin - (pos - center).angle).asAngle()
		
		console.log("Checking angle $currAngle in $maxAngleDiff")
		
		if (currAngle !in angleRange)
			return false
		
		return true
	}
}

@Serializable
sealed class PickRequest {
	@Serializable
	data class PickAngle(val center: Vec2, val fromAngle: Double, val maxAngleDiff: Double?, val displayArcRadius: Double) : PickRequest()
	
	@Serializable
	data class PickPosition(val inBoundary: PickBoundary, val origin: Vec2?, val restrictDistFromUnits: Double?, val displayCircleRadius: Double?) : PickRequest()
	
	@Serializable
	data class PickPiece(val inBoundary: PickBoundary, val onSameSideAs: GameServerSide?) : PickRequest()
}

@Serializable
sealed class PickResponse {
	@Serializable
	object Cancel : PickResponse()
	
	@Serializable
	data class PickedAngle(val newAngle: Double) : PickResponse()
	
	@Serializable
	data class PickedPosition(val pos: Vec2) : PickResponse()
	
	@Serializable
	data class PickedPiece(val pieceId: String) : PickResponse()
}

object PickHandler {
	var isPicking = false
		private set
	
	private val gamePick by lazy {
		document.getElementById("game-picker").unsafeCast<SVGGElement>()
	}
	
	private fun renderAnglePathD(angleReq: PickRequest.PickAngle, toAngle: Double): String {
		if (toAngle isEqualTo 0.0)
			return ""
		
		val fromNormal = angleReq.fromAngle.asAngle()
		val toNormal = toAngle.asAngle()
		
		val begin = Vec2.polar(angleReq.displayArcRadius, fromNormal) + angleReq.center
		val end = Vec2.polar(angleReq.displayArcRadius, toNormal) + angleReq.center
		
		val sweep = if (sin(toNormal - fromNormal) < 0.0) "1" else "0"
		
		return "M ${begin.x} ${begin.y} A ${angleReq.displayArcRadius} ${angleReq.displayArcRadius} 0 0 $sweep ${end.x} ${end.y}"
	}
	
	private fun renderPosPathD(posReq: PickRequest.PickPosition, pos: Vec2): String {
		val line = if (posReq.origin != null)
			"M ${posReq.origin.x} ${posReq.origin.y} L ${pos.x} ${pos.y}" else ""
		
		val circle = posReq.displayCircleRadius?.let { rad ->
			val circleX0 = pos.x
			val circleX1 = pos.x + rad
			val circleY0 = pos.y - rad
			val circleY1 = pos.y
			
			" M $circleX0 $circleY0 A $rad $rad 0 0 1 $circleX1 $circleY1 A $rad $rad 0 1 1 $circleX0 $circleY0 Z"
		} ?: ""
		
		return "$line$circle"
	}
	
	private fun checkAngleValid(angleReq: PickRequest.PickAngle, toAngle: Double): Boolean {
		val fromNormal = angleReq.fromAngle.asAngle()
		val toNormal = toAngle.asAngle()
		
		return angleReq.maxAngleDiff?.let { abs(fromNormal - toNormal) <= it } != false
	}
	
	private fun checkPosValid(posReq: PickRequest.PickPosition, pos: Vec2): Boolean {
		if (!posReq.inBoundary.isInBoundary(pos))
			return false
		
		if (posReq.restrictDistFromUnits != null && GameSessionData.currentSession!!.allPieces().any { piece ->
				(pos - piece.location).magnitude < piece.type.imageRadius + 15 + posReq.restrictDistFromUnits
			})
			return false
		
		if (pos.x !in 0.0..GameSessionData.currentSession!!.mapSize.x || pos.y !in 0.0..GameSessionData.currentSession!!.mapSize.y)
			return false
		
		return true
	}
	
	private fun checkPieceValid(posReq: PickRequest.PickPiece, piece: GamePiece): Boolean {
		if (!posReq.inBoundary.isInBoundary(piece.location))
			return false
		
		if (posReq.onSameSideAs == piece.owner.other)
			return false
		
		return true
	}
	
	fun cancelRequest() {
		isPicking = false
		
		gamePick.clear()
		GameField.drawPickBoundary(null)
	}
	
	private fun beginRequest(pickRequest: PickRequest, responder: (PickResponse) -> Unit) {
		isPicking = true
		
		val listeners = TempEvents(window)
		
		val wrappedResponder: (PickResponse) -> Unit = {
			cancelRequest()
			
			listeners.deregister()
			
			responder(it)
		}
		
		val escapeListener = object : EventListener {
			override fun handleEvent(event: Event) {
				val keyEvent = event.unsafeCast<KeyboardEvent>()
				
				if (keyEvent.key == "Escape") {
					keyEvent.preventDefault()
					wrappedResponder(PickResponse.Cancel)
				}
			}
		}
		
		listeners.register("keyup", escapeListener)
		
		when (pickRequest) {
			is PickRequest.PickAngle -> {
				var angle = 0.0
				
				val pickerAngleId = "game-picker-angle"
				
				gamePick.append(PATH()) {
					id = pickerAngleId
					
					fill = "none"
					stroke = if (checkAngleValid(pickRequest, angle)) "#0F0" else "#F00"
					strokeWidth = "5"
					
					d = renderAnglePathD(pickRequest, angle)
				}
				
				val pickerAngle = document.getElementById(pickerAngleId) as SVGPathElement
				
				listeners.register("mousemove", object : EventListener {
					override fun handleEvent(event: Event) {
						val mouseEvent = event.unsafeCast<MouseEvent>()
						
						val (domX, domY) = mouseEvent.x to mouseEvent.y
						val svgVec = SVGCoordinates.domToSvg(Vec2(domX, domY))
						angle = (svgVec - pickRequest.center).angle
						
						pickerAngle.setAttribute("d", renderAnglePathD(pickRequest, angle))
						pickerAngle.setAttribute("stroke", if (checkAngleValid(pickRequest, angle)) "#0F0" else "#F00")
					}
				})
				
				listeners.register("mouseup", object : EventListener {
					override fun handleEvent(event: Event) {
						if (checkAngleValid(pickRequest, angle)) {
							val setAngle = angle.asAngle()
							
							wrappedResponder(PickResponse.PickedAngle(setAngle))
						}
					}
				})
			}
			is PickRequest.PickPosition -> {
				var position = Vec2(0.0, 0.0)
				
				GameField.drawPickBoundary(pickRequest.inBoundary)
				
				val pickerPosId = "game-picker-pos"
				
				gamePick.append(PATH()) {
					id = pickerPosId
					
					fill = "none"
					stroke = if (checkPosValid(pickRequest, position)) "#0F0" else "#F00"
					strokeWidth = "5"
					
					d = renderPosPathD(pickRequest, position)
				}
				
				val pickerPos = document.getElementById(pickerPosId) as SVGPathElement
				
				listeners.register("mousemove", object : EventListener {
					override fun handleEvent(event: Event) {
						val mouseEvent = event.unsafeCast<MouseEvent>()
						
						val (domX, domY) = mouseEvent.x to mouseEvent.y
						val svgVec = SVGCoordinates.domToSvg(Vec2(domX, domY))
						position = svgVec
						
						pickerPos.setAttribute("d", renderPosPathD(pickRequest, position))
						pickerPos.setAttribute("stroke", if (checkPosValid(pickRequest, position)) "#0F0" else "#F00")
					}
				})
				
				listeners.register("mouseup", object : EventListener {
					override fun handleEvent(event: Event) {
						if (checkPosValid(pickRequest, position))
							wrappedResponder(PickResponse.PickedPosition(position))
					}
				})
			}
			is PickRequest.PickPiece -> {
				GameField.drawPickBoundary(pickRequest.inBoundary)
				
				val pieces = GameSessionData.currentSession!!.allPieces().filter {
					checkPieceValid(pickRequest, it)
				}
				
				pieces.forEach { piece ->
					gamePick.append(CIRCLE()) {
						id = "${piece.id}-pick-halo"
						cssClass = "game-piece-pick-halo"
						fill = "rgba(0, 0, 0, 0.01)"
						strokeWidth = "5"
						cx = piece.location.x.toString()
						cy = piece.location.y.toString()
						r = (piece.pieceRadius + 5).toString()
					}
				}
				
				pieces.map { it.id }.forEach { pieceId ->
					val halo = document.getElementById("${pieceId}-pick-halo").unsafeCast<SVGCircleElement>()
					
					halo.addEventListener("click", { e ->
						wrappedResponder(PickResponse.PickedPiece(pieceId))
					})
				}
			}
		}
	}
	
	suspend fun pickLocal(pickRequest: PickRequest): PickResponse {
		return awaitCallback { callback ->
			beginRequest(pickRequest) {
				callback(it)
			}
		}
	}
	
	suspend fun pickRemote(pickRequest: PickRequest): PickResponse {
		GamePacket.send(GamePacket.PickReq(pickRequest))
		return GamePacket.receivePickResponse()
	}
}
