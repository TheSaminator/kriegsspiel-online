import com.github.nwillc.ksvg.elements.CIRCLE
import com.github.nwillc.ksvg.elements.PATH
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.dom.addClass
import kotlinx.dom.clear
import kotlinx.dom.removeClass
import kotlinx.serialization.Serializable
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLParagraphElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.svg.SVGCircleElement
import org.w3c.dom.svg.SVGGElement
import org.w3c.dom.svg.SVGPathElement
import kotlin.coroutines.resume
import kotlin.math.PI
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
data class PickBoundaryUnitBased(
	val center: Vec2,
	val minRadius: Double?,
	val maxRadius: Double,
	val angleOrigin: Double,
	val minAngleDiff: Double?,
	val maxAngleDiff: Double?
) : PickBoundary() {
	override fun isInBoundary(pos: Vec2): Boolean {
		if ((pos - center).magnitude > maxRadius)
			return false
		
		if (minRadius != null) {
			if ((pos - center).magnitude < minRadius)
				return false
		}
		
		if (maxAngleDiff != null) {
			val angleRange = -maxAngleDiff..maxAngleDiff
			val currAngle = (angleOrigin - (pos - center).angle).asAngle()
			
			if (currAngle !in angleRange)
				return false
			
			if (minAngleDiff != null) {
				val subAngleRange = -minAngleDiff..minAngleDiff
				if (currAngle in subAngleRange)
					return false
			}
		}
		
		return true
	}
}

enum class TerrainRequirement {
	DEFAULT {
		override fun isTerrainOkay(terrainType: TerrainType): Boolean {
			return !terrainType.stats.isImpassible
		}
	},
	REQ_NONE {
		override fun isTerrainOkay(terrainType: TerrainType): Boolean {
			return false
		}
	},
	ALLOW_ANY {
		override fun isTerrainOkay(terrainType: TerrainType): Boolean {
			return true
		}
	},
	;
	
	abstract fun isTerrainOkay(terrainType: TerrainType): Boolean
}

@Serializable
sealed class PickRequest {
	@Serializable
	data class PickAngle(val center: Vec2, val fromAngle: Double, val maxAngleDiff: Double?, val displayArcRadius: Double) : PickRequest()
	
	@Serializable
	data class PickPosition(val inBoundary: PickBoundary, val origin: Vec2?, val restrictDistFromUnits: Double?, val restrictUnitsInLayer: PieceLayer?, val requireTerrain: TerrainRequirement, val displayCircleRadius: Double?) : PickRequest()
	
	@Serializable
	data class PickPiece(val inBoundary: PickBoundary, val onSameSideAs: GameServerSide?, val inSameLayerAs: PieceLayer?) : PickRequest()
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

object MobileTouchPicking {
	private val cancelPickBox by lazy {
		document.getElementById("cancel-pick").unsafeCast<HTMLDivElement>()
	}
	
	private val cancelPickButton by lazy {
		document.getElementById("pick-cancel-button").unsafeCast<HTMLAnchorElement>()
	}
	
	var onTap: (Vec2) -> Unit = { _ -> }
	var onHold: (Vec2) -> Unit = { _ -> }
	var onCancel: () -> Unit = {}
	
	init {
		cancelPickButton.addEventListener("click", { e ->
			e.preventDefault()
			
			onCancel()
		})
	}
	
	fun begin() {
		cancelPickBox.removeClass("hide")
	}
	
	fun clear() {
		cancelPickBox.addClass("hide")
		onTap = { _ -> }
		onHold = { _ -> }
		onCancel = {}
	}
}

object PickHandler {
	var isPicking = false
		private set
	
	private val gamePick by lazy {
		document.getElementById("game-picker").unsafeCast<SVGGElement>()
	}
	
	private val helpText by lazy {
		document.getElementById("help-text").unsafeCast<HTMLParagraphElement>()
	}
	
	private fun renderAnglePathD(angleReq: PickRequest.PickAngle, toAngle: Double, toPos: Vec2): String {
		val fromNormal = angleReq.fromAngle.asAngle()
		val begin = Vec2.polar(angleReq.displayArcRadius, fromNormal) + angleReq.center
		
		val toNormal = toAngle.asAngle()
		val end = Vec2.polar(angleReq.displayArcRadius, toNormal) + angleReq.center
		
		val pointer = toPos + Vec2.polar(5.0, toAngle)
		
		if (toNormal isEqualTo fromNormal)
			return "M ${begin.x} ${begin.y} L ${pointer.x} ${pointer.y}"
		
		val sweep = if (sin(toNormal - fromNormal) < 0.0) "1" else "0"
		
		return "M ${begin.x} ${begin.y} A ${angleReq.displayArcRadius} ${angleReq.displayArcRadius} 0 0 $sweep ${end.x} ${end.y} L ${pointer.x} ${pointer.y}"
	}
	
	private fun renderPosPathD(posReq: PickRequest.PickPosition, pos: Vec2): String {
		val line = posReq.origin?.let { origin ->
			"M ${origin.x} ${origin.y} L ${pos.x} ${pos.y}"
		}.orEmpty()
		
		val circle = posReq.displayCircleRadius?.let { rad ->
			val circleX0 = pos.x
			val circleX1 = pos.x + rad
			val circleY0 = pos.y - rad
			val circleY1 = pos.y
			
			" M $circleX0 $circleY0 A $rad $rad 0 0 1 $circleX1 $circleY1 A $rad $rad 0 1 1 $circleX0 $circleY0 Z"
		}.orEmpty()
		
		return "$line$circle"
	}
	
	private fun checkAngleValid(angleReq: PickRequest.PickAngle, toAngle: Double): Boolean {
		if (angleReq.maxAngleDiff == null || angleReq.maxAngleDiff >= PI)
			return true
		
		return abs((angleReq.fromAngle - toAngle).asAngle()) <= angleReq.maxAngleDiff
	}
	
	private fun checkPosValid(posReq: PickRequest.PickPosition, pos: Vec2): Boolean {
		if (!posReq.inBoundary.isInBoundary(pos))
			return false
		
		if (
			posReq.restrictDistFromUnits != null && GameSessionData.currentSession!!.allPieces().any restrictPiece@{ piece ->
				if (piece.type.layer != posReq.restrictUnitsInLayer)
					return@restrictPiece false
				
				(pos - piece.location).magnitude < piece.type.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE + posReq.restrictDistFromUnits
			}
		)
			return false
		
		if (pos.x !in 0.0..GameSessionData.currentSession!!.gameMap.size.x || pos.y !in 0.0..GameSessionData.currentSession!!.gameMap.size.y)
			return false
		
		if (
			GameSessionData.currentSession!!.gameMap.terrainBlobs.any {
				!posReq.requireTerrain.isTerrainOkay(it.type) && (it.center - pos).magnitude < it.radius
			}
		)
			return false
		
		return true
	}
	
	private fun checkPieceValid(pieceReq: PickRequest.PickPiece, piece: GamePiece): Boolean {
		if (!pieceReq.inBoundary.isInBoundary(piece.location))
			return false
		
		if (pieceReq.onSameSideAs == piece.owner.other)
			return false
		
		if (pieceReq.inSameLayerAs != null && pieceReq.inSameLayerAs != piece.type.layer)
			return false
		
		if (!piece.canBeRendered)
			return false
		
		return true
	}
	
	private val topListeners = TempEvents(window)
	
	private fun endRequest() {
		topListeners.deregister()
		
		isPicking = false
		helpText.innerHTML = ""
		MobileTouchPicking.clear()
		
		gamePick.clear()
		GameField.drawPickBoundary(null)
	}
	
	private fun beginRequest(pickRequest: PickRequest, responder: (PickResponse) -> Unit) {
		isPicking = true
		
		val isDesktop = window.matchMedia("(pointer: fine)").matches
		
		helpText.innerHTML = when {
			isDesktop -> "Press the Escape key to cancel"
			pickRequest is PickRequest.PickPiece -> "Tap on a piece to make selection"
			else -> "Tap to select, long press to confirm selection"
		}
		
		topListeners.deregister()
		
		topListeners.register("keyup") { event: KeyboardEvent ->
			if (event.key == "Escape" && isDesktop) {
				event.preventDefault()
				responder(PickResponse.Cancel)
			}
		}
		
		MobileTouchPicking.begin()
		
		MobileTouchPicking.onCancel = {
			responder(PickResponse.Cancel)
		}
		
		when (pickRequest) {
			is PickRequest.PickAngle -> {
				var point: Vec2
				var angle = 0.0
				
				val pickerAngleId = "game-picker-angle"
				
				gamePick.append(PATH()) {
					id = pickerAngleId
					
					fill = "none"
					stroke = if (checkAngleValid(pickRequest, angle)) "#0F0" else "#F00"
					strokeWidth = "5"
					
					d = ""
				}
				
				val pickerAngle = document.getElementById(pickerAngleId).unsafeCast<SVGPathElement>()
				
				fun testAngle(domX: Double, domY: Double) {
					val svgVec = SVGCoordinates.domToSvg(Vec2(domX, domY))
					point = svgVec
					angle = (svgVec - pickRequest.center).angle
					
					pickerAngle.setAttribute("d", renderAnglePathD(pickRequest, angle, point))
					pickerAngle.setAttribute("stroke", if (checkAngleValid(pickRequest, angle)) "#0F0" else "#F00")
				}
				
				topListeners.register("mousemove") { mouseEvent: MouseEvent ->
					if (isDesktop)
						testAngle(mouseEvent.clientX.toDouble(), mouseEvent.clientY.toDouble())
				}
				
				MobileTouchPicking.onTap = { (domX, domY) ->
					testAngle(domX, domY)
				}
				
				fun chooseAngle(angle: Double) {
					if (checkAngleValid(pickRequest, angle))
						responder(PickResponse.PickedAngle(angle.asAngle()))
				}
				
				pickerAngle.addEventListener("click", {
					if (isDesktop)
						chooseAngle(angle)
				})
				
				MobileTouchPicking.onHold = { (domX, domY) ->
					val svgVec = SVGCoordinates.domToSvg(Vec2(domX, domY))
					point = svgVec
					angle = (svgVec - pickRequest.center).angle
					
					chooseAngle(angle)
				}
			}
			is PickRequest.PickPosition -> {
				var position = Vec2(0.0, 0.0)
				
				GameField.drawPickBoundary(pickRequest.inBoundary)
				
				val pickerPosId = "game-picker-pos"
				
				gamePick.append(PATH()) {
					id = pickerPosId
					
					fill = "rgba(0, 0, 0, 0.01)"
					stroke = if (checkPosValid(pickRequest, position)) "#0F0" else "#F00"
					strokeWidth = "5"
					
					d = ""
				}
				
				val pickerPos = document.getElementById(pickerPosId).unsafeCast<SVGPathElement>()
				
				fun testPos(domX: Double, domY: Double) {
					val svgVec = SVGCoordinates.domToSvg(Vec2(domX, domY))
					position = svgVec
					
					pickerPos.setAttribute("d", renderPosPathD(pickRequest, position))
					pickerPos.setAttribute("stroke", if (checkPosValid(pickRequest, position)) "#0F0" else "#F00")
				}
				
				topListeners.register("mousemove") { mouseEvent: MouseEvent ->
					if (isDesktop)
						testPos(mouseEvent.clientX.toDouble(), mouseEvent.clientY.toDouble())
				}
				
				MobileTouchPicking.onTap = { (domX, domY) ->
					testPos(domX, domY)
				}
				
				fun choosePos(svgPos: Vec2) {
					if (checkPosValid(pickRequest, svgPos))
						responder(PickResponse.PickedPosition(svgPos))
				}
				
				pickerPos.addEventListener("click", {
					if (isDesktop)
						choosePos(position)
				})
				
				MobileTouchPicking.onHold = { (domX, domY) ->
					val svgVec = SVGCoordinates.domToSvg(Vec2(domX, domY))
					choosePos(svgVec)
				}
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
					
					halo.addEventListener("click", {
						responder(PickResponse.PickedPiece(pieceId))
					})
				}
			}
		}
	}
	
	suspend fun pickLocal(pickRequest: PickRequest): PickResponse {
		return suspendCancellableCoroutine { continuation ->
			continuation.invokeOnCancellation {
				endRequest()
			}
			
			beginRequest(pickRequest) {
				endRequest()
				continuation.resume(it)
			}
		}
	}
	
	suspend fun pickRemote(pickRequest: PickRequest): PickResponse {
		GamePacket.send(GamePacket.PickReq(pickRequest))
		return GamePacket.awaitPickResponse()
	}
}
