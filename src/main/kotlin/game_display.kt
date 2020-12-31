import SvgPanZoom.SVGPanZoomInstance
import SvgPanZoom.Sizes
import com.github.nwillc.ksvg.elements.G
import com.github.nwillc.ksvg.elements.RECT
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.dom.addClass
import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.p
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.svg.SVGGElement
import org.w3c.dom.svg.SVGPathElement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

object ChatBox {
	private val history = document.getElementById("chat-history").unsafeCast<HTMLDivElement>()
	private val entry = document.getElementById("chat-entry").unsafeCast<HTMLFormElement>()
	private val input = document.getElementById("chat-input").unsafeCast<HTMLInputElement>()
	private val send = document.getElementById("chat-send").unsafeCast<HTMLInputElement>()
	
	private val sendMessageListener = object : EventListener {
		override fun handleEvent(event: Event) {
			val message = input.value
			input.value = ""
			
			if (message.isNotBlank()) {
				addMessage("You", message)
				GamePacket.send(GamePacket.ChatMessage(message))
			}
			
			event.preventDefault()
		}
	}
	
	fun enable() {
		history.clear()
		
		input.readOnly = false
		send.disabled = false
		
		entry.addEventListener("submit", sendMessageListener)
	}
	
	fun disable() {
		input.readOnly = false
		send.disabled = false
		
		entry.removeEventListener("submit", sendMessageListener)
	}
	
	fun addMessage(sender: String, message: String) {
		history.append {
			p(classes = "chat-message") {
				strong { +"$sender: " }
				+message
			}
		}
	}
}

object GameField {
	private val mapField by lazy {
		document.getElementById("game-map").unsafeCast<SVGGElement>()
	}
	
	private val selectionPath by lazy {
		document.getElementById("game-picker-bounds").unsafeCast<SVGPathElement>()
	}
	
	private val gamePieces by lazy {
		document.getElementById("game-pieces").unsafeCast<SVGGElement>()
	}
	
	private val selection by lazy {
		document.getElementById("game-picker").unsafeCast<SVGGElement>()
	}
	
	private lateinit var gameFieldPanZoom: SVGPanZoomInstance
	
	const val MAP_COLOR = "#194"
	
	fun drawMap(size: Vec2) {
		mapField.clear()
		
		mapField.append(RECT()) {
			fill = MAP_COLOR
			width = size.x.toString()
			height = size.y.toString()
		}
		
		if (this::gameFieldPanZoom.isInitialized)
			gameFieldPanZoom.destroy()
		
		gameFieldPanZoom = svgPanZoom("#game-field", configure {
			beforePan = { _, newPan ->
				val sizes = js("this").getSizes().unsafeCast<Sizes>()
				
				val leftLimit = -((sizes.viewBox.x + sizes.viewBox.width) * sizes.realZoom) + (sizes.width / 2)
				val rightLimit = (sizes.width / 2) - (sizes.viewBox.x * sizes.realZoom)
				val topLimit = -((sizes.viewBox.y + sizes.viewBox.height) * sizes.realZoom) + (sizes.height / 2)
				val bottomLimit = (sizes.height / 2) - (sizes.viewBox.y * sizes.realZoom)
				
				val customPan = js("{}")
				customPan.x = newPan.x.coerceIn(leftLimit, rightLimit)
				customPan.y = newPan.y.coerceIn(topLimit, bottomLimit)
				
				customPan
			}
		})
		
		gameFieldPanZoom.fit().center()
	}
	
	fun drawPickBoundary(bounds: PickBoundary?) {
		val d = when (bounds) {
			null -> ""
			is PickBoundaryRectangle -> {
				val w2 = bounds.width / 2
				val h2 = bounds.height / 2
				
				val x0 = bounds.center.x - w2
				val x1 = bounds.center.x + w2
				val y0 = bounds.center.y - h2
				val y1 = bounds.center.y + h2
				
				"M $x0 $y0 L $x1 $y0 L $x1 $y1 L $x0 $y1 Z"
			}
			is PickBoundaryCircle -> {
				val r = bounds.radius
				
				val x0 = bounds.center.x
				val x1 = bounds.center.x + r
				val y0 = bounds.center.y - r
				val y1 = bounds.center.y
				
				"M $x0 $y0 A $r $r 0 0 1 $x1 $y1 A $r $r 0 1 1 $x0 $y0 Z"
			}
			is PickBoundaryUnitBased -> {
				val minRad = bounds.minRadius
				val theta0 = bounds.angleOrigin - bounds.maxAngleDiff
				val theta1 = bounds.angleOrigin + bounds.maxAngleDiff
				val largeArc = if (abs(theta0 - theta1) > PI) 1 else 0
				
				val (outerX0, outerY0) = Vec2.polar(bounds.maxRadius, theta0) + bounds.center
				val (outerX1, outerY1) = Vec2.polar(bounds.maxRadius, theta1) + bounds.center
				
				val endPart = "L $outerX0 $outerY0 A ${bounds.maxRadius} ${bounds.maxRadius} 0 $largeArc 0 $outerX1 $outerY1 Z"
				
				val beginPart = if (minRad != null) {
					val (innerX0, innerY0) = Vec2.polar(minRad, theta0) + bounds.center
					val (innerX1, innerY1) = Vec2.polar(minRad, theta1) + bounds.center
					
					"M $innerX1 $innerY1 A $minRad $minRad 0 $largeArc 1 $innerX0 $innerY0"
				} else {
					val (innerX, innerY) = bounds.center
					
					"M $innerX $innerY"
				}
				
				"$beginPart $endPart"
			}
		}
		
		selectionPath.setAttribute("d", d)
	}
	
	fun drawPiece(piece: GamePiece) {
		document.getElementById(piece.id)?.remove()
		
		gamePieces.append(G()) {
			id = piece.id
			cssClass = "game-piece"
			transform = "translate(${piece.location.x} ${piece.location.y}) rotate(${piece.facing.asAngle(flipY = true) * 180 / PI})"
			
			val outerRadius = piece.type.imageRadius + 15
			val healthRadius = piece.type.imageRadius + 7.5
			
			circle {
				cssClass = "game-piece-back"
				
				cx = "0"
				cy = "0"
				r = outerRadius.toString()
			}
			
			if (piece.health isEqualTo 1.0)
				circle {
					fill = "none"
					stroke = piece.healthBarColor
					strokeWidth = "5"
					
					cx = "0"
					cy = "0"
					r = healthRadius.toString()
				}
			else
				path {
					fill = "none"
					stroke = piece.healthBarColor
					strokeWidth = "5"
					
					val begin = Vec2(0.0, -healthRadius)
					val end = begin.rotateBy(piece.health * 2 * PI)
					
					val largeArc = if (piece.health > 0.5) "1" else "0"
					
					d = "M ${begin.x} ${begin.y} A $healthRadius $healthRadius 0 $largeArc 1 ${end.x} ${end.y}"
				}
			
			image {
				val w = piece.type.imageWidth
				val h = piece.type.imageHeight
				
				x = (-w * 0.5).toString()
				y = (-h * 0.5).toString()
				width = w.toString()
				height = h.toString()
				
				href = piece.type.getImagePath(piece.owner)
			}
		}
		
		window.requestAnimationFrame {
			val pieceElements = document.getElementsByClassName("game-piece")
			
			repeat(pieceElements.length) { i ->
				val pieceElement = pieceElements[i].unsafeCast<SVGGElement>()
				
				pieceElement.onclick = { _ ->
					GameSidebar.select(pieceElement.id)
				}
			}
		}
	}
	
	fun redrawAllPieces(pieces: Set<GamePiece>) {
		gamePieces.clear()
		
		pieces.forEach { piece ->
			drawPiece(piece)
		}
	}
	
	fun undrawPiece(pieceId: String) {
		document.getElementById(pieceId)?.remove()
	}
	
	fun drawEverything(gameSessionData: GameSessionData) {
		drawMap(gameSessionData.mapSize)
		redrawAllPieces(gameSessionData.allPieces())
	}
	
	fun eraseEverything() {
		drawPickBoundary(null)
		
		mapField.clear()
		gamePieces.clear()
		
		selection.clear()
	}
}

object GameSidebar {
	private val sidebar by lazy {
		document.getElementById("piece-selection").unsafeCast<HTMLDivElement>()
	}
	
	const val POINTS_TO_SPEND = 1000
	const val DEPLOY_ZONE_WIDTH = 300.0
	
	const val HOST_FACING = PI / 2
	const val GUEST_FACING = PI * 3 / 2
	
	var currentPoints = POINTS_TO_SPEND
	
	private var deployJob: Job? = null
	
	fun deployMenu() {
		sidebar.clear()
		
		if (GamePhase.Deployment.localIsDone)
			sidebar.append {
				sidebar.clear()
				
				sidebar.append {
					p(classes = "all-info") {
						+"Wait for the game to begin"
					}
				}
			}
		else
			sidebar.append {
				div(classes = "button-set col") {
					p(classes = "info") {
						+"You have $currentPoints points left to spend."
					}
					PieceType.values().forEach { pieceType ->
						a(href = "#") {
							+"${pieceType.displayName} (${pieceType.pointCost})"
							
							if (currentPoints < pieceType.pointCost)
								classes = setOf("disabled")
							else
								onClickFunction = onClickFunction@{ e ->
									e.preventDefault()
									
									deployPiece(pieceType)
								}
						}
					}
					
					a(href = "#") {
						+"CLEAR DEPLOYMENT"
						
						if (GameSessionData.currentSession!!.anyPiecesWithOwner(Game.currentSide!!))
							onClickFunction = { e ->
								e.preventDefault()
								
								Player.currentPlayer!!.clearDeploying()
								currentPoints = POINTS_TO_SPEND
								
								deployMenu()
							}
						else
							classes = setOf("disabled")
					}
					
					a(href = "#") {
						id = "finish-deploy"
						
						+"DONE DEPLOYING"
						
						if (GameSessionData.currentSession!!.anyPiecesWithOwner(Game.currentSide!!))
							onClickFunction = { e ->
								e.preventDefault()
								
								Player.currentPlayer!!.finishDeploying()
								
								if (GamePhase.Deployment.bothAreDone)
									updateSidebar()
								else
									deployMenu()
							}
						else
							classes = setOf("disabled")
					}
				}
			}
	}
	
	private fun deployPiece(pieceType: PieceType) {
		if (currentPoints < pieceType.pointCost)
			return
		
		val mapSize = GameSessionData.currentSession!!.mapSize
		val side = Game.currentSide!!
		
		val width = DEPLOY_ZONE_WIDTH
		val height = mapSize.y
		
		val centerX = when (side) {
			GameServerSide.HOST -> width / 2
			GameServerSide.GUEST -> mapSize.x - (width / 2)
		}
		val centerY = height / 2
		
		val pReq = PickRequest.PickPosition(
			PickBoundaryRectangle(
				Vec2(centerX, centerY),
				width, height
			),
			null,
			pieceType.imageRadius + 15,
			pieceType.imageRadius + 15
		)
		
		deployJob?.cancel()
		PickHandler.cancelRequest()
		
		deployJob = GlobalScope.launch {
			val pRes = PickHandler.pickLocal(pReq) as? PickResponse.PickedPosition ?: return@launch
			
			val pos = pRes.pos
			val facing = when (side) {
				GameServerSide.HOST -> HOST_FACING
				GameServerSide.GUEST -> GUEST_FACING
			}
			
			currentPoints -= pieceType.pointCost
			Player.currentPlayer!!.deployPiece(pieceType, pos, facing)
			
			deployMenu()
		}
	}
	
	private var selectedPieceId: String? = null
	
	fun select(pieceId: String?) {
		if (GamePhase.currentPhase != GamePhase.PlayTurn(Game.currentSide!!))
			return
		
		selectedPieceId?.let { id ->
			document.getElementById(id)?.removeAttribute("data-selected-piece")
		}
		
		selectedPieceId = pieceId
		
		selectedPieceId?.let { id ->
			document.getElementById(id)?.setAttribute("data-selected-piece", "selected-piece")
		}
		
		updateSidebar()
	}
	
	fun updateSidebar() {
		sidebar.clear()
		
		val piece = selectedPieceId?.let { GameSessionData.currentSession!!.pieceByIdOrNull(it) }
		
		if (piece == null) {
			sidebar.append {
				p(classes = "all-info") {
					+"Click a game piece to select it"
				}
			}
		} else {
			sidebar.append {
				p(classes = "info") {
					+piece.type.displayName
				}
				
				div(classes = "measure-bar") {
					+"Health"
					span("meter red-green") {
						span("emptiness") {
							style = "width: ${((1 - piece.health) * 100).roundToInt()}%"
						}
					}
				}
				
				if (piece.owner == Game.currentSide!! && GamePhase.currentPhase == GamePhase.PlayTurn(Game.currentSide!!)) {
					div(classes = "measure-bar") {
						+"Action"
						span("meter orange-blue") {
							span("emptiness") {
								style = "width: ${((1 - piece.action) * 100).roundToInt()}%"
							}
						}
					}
					
					div(classes = "button-set col") {
						piece.type.stats.abilities.forEach { (abilityName, ability) ->
							a(href = "#") {
								id = "use-ability-${abilityName.toLowerCase()}"
								+abilityName
								
								if (ability.canUse(piece))
									onClickFunction = { e ->
										e.preventDefault()
										
										piece.type.stats.abilities.forEach { (abilityName, _) ->
											val btn = document.getElementById("use-ability-${abilityName.toLowerCase()}") as HTMLAnchorElement
											btn.onclick = null
										}
										
										GlobalScope.launch {
											Player.currentPlayer!!.useAbility(piece.id, abilityName)
											updateSidebar()
										}
									}
								else
									classes = setOf("disabled")
							}
						}
					}
				}
			}
		}
		
		if (GamePhase.currentPhase == GamePhase.PlayTurn(Game.currentSide!!)) {
			sidebar.append {
				hr()
				
				div(classes = "button-set col") {
					a(href = "#") {
						id = "end-turn-btn"
						+"End Turn"
						
						if (GameSessionData.currentSession!!.canEndTurn)
							onClickFunction = { e ->
								e.preventDefault()
								
								val btn = document.getElementById("end-turn-btn").unsafeCast<HTMLAnchorElement>()
								
								btn.addClass("disabled")
								
								GlobalScope.launch {
									Player.currentPlayer!!.endTurn()
									
									updateSidebar()
								}
							}
						else
							classes = setOf("disabled")
					}
				}
			}
		} else {
			sidebar.append {
				hr()
				
				p(classes = "info") {
					+"It is not your turn."
				}
			}
		}
	}
	
	fun clearSidebar() {
		sidebar.clear()
	}
}
