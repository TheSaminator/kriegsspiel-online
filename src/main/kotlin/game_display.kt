import com.github.nwillc.ksvg.elements.CIRCLE
import com.github.nwillc.ksvg.elements.G
import com.github.nwillc.ksvg.elements.RECT
import externals.hammer.Hammer
import externals.hammer.HammerManager
import externals.hammer.invoke
import externals.svgpanzoom.CustomEventHandler
import externals.svgpanzoom.SVGPanZoomInstance
import externals.svgpanzoom.SVGPanZoomOptions
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.dom.addClass
import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.p
import kotlinx.serialization.Serializable
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
				addChatMessage("You", message)
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
	
	fun addChatMessage(sender: String, message: String) {
		history.append {
			p(classes = "chat-message") {
				b { +"<$sender> " }
				+message
			}
		}.last().scrollIntoView()
	}
	
	fun notifyAttack(source: DamageSource, target: GamePiece, amount: Double) {
		if (Game.currentSide == GameServerSide.HOST)
			GamePacket.send(GamePacket.AttackMessage(source, target, amount))
		
		if (!target.canBeIdentified)
			return
		
		val amountWritten = amount.toTruncatedString(1) + " damage "
		
		val isYours = target.owner == Game.currentSide!!
		val owner = if (isYours) "Your " else "Opponent's "
		val verb = if (target.health <= 0.0) " was killed by " else " took "
		
		val message = "$owner${target.type.displayName}$verb$amountWritten${source.toPrepositionalPhrase()}."
		
		history.append {
			p(classes = "chat-message") {
				+message
			}
		}.last().scrollIntoView()
	}
}

@Serializable
sealed class DamageSource {
	@Serializable
	data class Piece(val piece: GamePiece) : DamageSource()
	
	@Serializable
	data class Terrain(val terrainType: TerrainType) : DamageSource()
	
	@Serializable
	object AirPieceCrash : DamageSource()
	
	fun toPrepositionalPhrase(): String {
		return when (this) {
			is Piece -> {
				val isYours = piece.owner == Game.currentSide!!
				val owner = if (isYours) "your" else "opponent's"
				"from $owner ${piece.type.displayName}"
			}
			is Terrain -> {
				"in ${terrainType.displayName.lowercase()} terrain"
			}
			AirPieceCrash -> {
				"in an airplane crash"
			}
		}
	}
}

object GameField {
	private val field by lazy {
		document.getElementById("game-field").unsafeCast<SVGGElement>()
	}
	
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
	
	private fun SVGPanZoomOptions.configurePanBox() {
		beforePan = { _, newPan ->
			val sizes = gameFieldPanZoom.getSizes()
			
			val leftLimit = -((sizes.viewBox.x + sizes.viewBox.width) * sizes.realZoom) + (sizes.width / 2)
			val rightLimit = (sizes.width / 2) - (sizes.viewBox.x * sizes.realZoom)
			val topLimit = -((sizes.viewBox.y + sizes.viewBox.height) * sizes.realZoom) + (sizes.height / 2)
			val bottomLimit = (sizes.height / 2) - (sizes.viewBox.y * sizes.realZoom)
			
			val customPan = js("{}")
			customPan.x = newPan.x.coerceIn(leftLimit, rightLimit)
			customPan.y = newPan.y.coerceIn(topLimit, bottomLimit)
			
			customPan
		}
	}
	
	private fun createPanZoomDesktop() {
		gameFieldPanZoom = svgPanZoom(field, configure {
			configurePanBox()
			zoomScaleSensitivity = 0.3
		})
		
		gameFieldPanZoom.fit().center()
		
		window.addEventListener("resize", {
			gameFieldPanZoom.resize()
			gameFieldPanZoom.fit()
			gameFieldPanZoom.center()
		})
	}
	
	private fun createPanZoomMobile() {
		gameFieldPanZoom = svgPanZoom(field, configure {
			configurePanBox()
			
			customEventsHandler = configure<CustomEventHandler> {
				lateinit var hammer: HammerManager
				
				haltEventListeners = arrayOf("touchstart", "touchend", "touchmove", "touchleave", "touchcancel")
				
				init = { opts ->
					val instance = opts.instance
					
					var initialScale = 1.0
					var panX = 0.0
					var panY = 0.0
					
					hammer = Hammer(opts.svgElement)
					
					hammer.get("pan").set(configure { direction = Hammer.DIRECTION_ALL })
					hammer.get("pinch").set(configure { enable = true })
					hammer.get("tap").set(configure { enable = true; time = 150.0 })
					hammer.get("press").set(configure { enable = true; time = 151.0 })
					
					hammer.on("panstart panmove") { e ->
						e.preventDefault()
						
						if (e.type == "panstart") {
							panX = 0.0
							panY = 0.0
						}
						
						val dx = e.deltaX.toDouble()
						val dy = e.deltaY.toDouble()
						
						instance.panBy(configure {
							x = dx - panX
							y = dy - panY
						})
						
						panX = dx
						panY = dy
					}
					
					hammer.on("pinchstart pinchmove") { e ->
						e.preventDefault()
						
						if (e.type == "pinchstart")
							initialScale = instance.getZoom()
						
						instance.zoomAtPoint(initialScale * e.scale.toDouble(), configure {
							x = e.center.x.toDouble()
							y = e.center.y.toDouble()
						})
					}
					
					hammer.on("tap") { e ->
						if (PickHandler.isPicking) {
							e.preventDefault()
							
							val domVec = Vec2(e.center.x.toDouble(), e.center.y.toDouble())
							MobileTouchPicking.onTap(domVec)
						}
					}
					
					hammer.on("press") { e ->
						if (PickHandler.isPicking) {
							e.preventDefault()
							
							val domVec = Vec2(e.center.x.toDouble(), e.center.y.toDouble())
							MobileTouchPicking.onHold(domVec)
						}
					}
					
					opts.svgElement.addEventListener("touchmove", { e -> e.preventDefault() })
				}
				
				destroy = {
					hammer.destroy()
				}
			}
		})
		
		gameFieldPanZoom.fit().center()
		
		window.addEventListener("resize", {
			gameFieldPanZoom.resize()
			gameFieldPanZoom.fit()
			gameFieldPanZoom.center()
		})
	}
	
	private fun drawMap(map: GameMap) {
		mapField.clear()
		
		mapField.append(RECT()) {
			fill = GameMap.defaultColor
			width = map.size.x.toString()
			height = map.size.y.toString()
		}
		
		map.terrainBlobs.forEach { blob ->
			mapField.append(CIRCLE()) {
				fill = blob.type.color
				cx = blob.center.x.toString()
				cy = blob.center.y.toString()
				r = blob.radius.toString()
			}
		}
		
		if (this::gameFieldPanZoom.isInitialized)
			gameFieldPanZoom.destroy()
		
		if (window.matchMedia("(pointer: fine)").matches)
			createPanZoomDesktop()
		else
			createPanZoomMobile()
	}
	
	private fun drawPickBoundaryArc(origin: Vec2, minAngle: Double, maxAngle: Double, minRadius: Double?, maxRadius: Double): String {
		val (outerX0, outerY0) = Vec2.polar(maxRadius, minAngle) + origin
		val (outerX1, outerY1) = Vec2.polar(maxRadius, maxAngle) + origin
		val largeArc = if (abs(maxAngle - minAngle) > PI) 1 else 0
		
		val endPart = "L $outerX0 $outerY0 A $maxRadius $maxRadius 0 $largeArc 0 $outerX1 $outerY1 Z"
		
		val beginPart = if (minRadius != null) {
			val (innerX0, innerY0) = Vec2.polar(minRadius, minAngle) + origin
			val (innerX1, innerY1) = Vec2.polar(minRadius, maxAngle) + origin
			
			"M $innerX1 $innerY1 A $minRadius $minRadius 0 $largeArc 1 $innerX0 $innerY0"
		} else {
			val (innerX, innerY) = origin
			
			"M $innerX $innerY"
		}
		
		return "$beginPart $endPart"
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
			is PickBoundaryUnitBased -> {
				if (bounds.minAngleDiff == null) {
					if (bounds.maxAngleDiff == null) {
						val inner = if (bounds.minRadius != null) {
							val r0 = bounds.minRadius
							
							val x0 = bounds.center.x
							val x1 = bounds.center.x + r0
							val y0 = bounds.center.y - r0
							val y1 = bounds.center.y
							
							"M $x0 $y0 A $r0 $r0 0 0 1 $x1 $y1 A $r0 $r0 0 1 1 $x0 $y0 Z "
						} else ""
						
						val r1 = bounds.maxRadius
						
						val x2 = bounds.center.x
						val x3 = bounds.center.x + r1
						val y2 = bounds.center.y - r1
						val y3 = bounds.center.y
						
						inner + "M $x2 $y2 A $r1 $r1 0 0 1 $x3 $y3 A $r1 $r1 0 1 1 $x2 $y2 Z"
					} else {
						drawPickBoundaryArc(
							bounds.center,
							bounds.angleOrigin - bounds.maxAngleDiff,
							bounds.angleOrigin + bounds.maxAngleDiff,
							bounds.minRadius,
							bounds.maxRadius
						)
					}
				} else {
					bounds.maxAngleDiff!!
					
					val leftArc = drawPickBoundaryArc(
						bounds.center,
						bounds.angleOrigin - bounds.maxAngleDiff,
						bounds.angleOrigin - bounds.minAngleDiff,
						bounds.minRadius,
						bounds.maxRadius
					)
					
					val rightArc = drawPickBoundaryArc(
						bounds.center,
						bounds.angleOrigin + bounds.minAngleDiff,
						bounds.angleOrigin + bounds.maxAngleDiff,
						bounds.minRadius,
						bounds.maxRadius
					)
					
					"$leftArc $rightArc"
				}
			}
		}
		
		selectionPath.setAttribute("d", d)
	}
	
	private fun G.drawCircleMeter(amount: Double, radius: Double, color: String) {
		if (amount isEqualTo 1.0) {
			circle {
				fill = "none"
				stroke = color
				strokeWidth = "5"
				
				cx = "0"
				cy = "0"
				r = radius.toString()
			}
		} else {
			path {
				fill = "none"
				stroke = color
				strokeWidth = "5"
				
				val origin = Vec2(0.0, radius)
				val begin = origin.rotateBy(amount * PI)
				val end = origin.rotateBy(-amount * PI)
				
				val largeArc = if (amount > 0.5) "1" else "0"
				
				d = "M ${begin.x} ${begin.y} A $radius $radius 0 $largeArc 0 ${end.x} ${end.y}"
			}
		}
	}
	
	private fun updatePieceEvents() {
		val pieceElements = document.getElementsByClassName("game-piece")
		
		repeat(pieceElements.length) { i ->
			val pieceElement = pieceElements[i].unsafeCast<SVGGElement>()
			
			pieceElement.onclick = { _ ->
				GameSidebar.select(pieceElement.id)
			}
		}
	}
	
	fun drawPiece(piece: GamePiece, updateEvents: Boolean) {
		document.getElementById(piece.id)?.remove()
		
		if (!piece.canBeRendered)
			return
		
		if (!PieceLayer.viewAirUnits && piece.type.layer == PieceLayer.AIR)
			return
		
		gamePieces.append(G()) {
			id = piece.id
			cssClass = "game-piece"
			transform = "translate(${piece.location.x} ${piece.location.y}) rotate(${piece.facing.asAngle(flipY = true) * 180 / PI})"
			
			val outerRadius = piece.type.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE
			val centerRadius = piece.type.imageRadius + (GamePiece.PIECE_RADIUS_OUTLINE / 2)
			
			circle {
				cssClass = "game-piece-back"
				
				cx = "0"
				cy = "0"
				r = outerRadius.toString()
			}
			
			if (piece.canBeIdentified) {
				drawCircleMeter(piece.health, centerRadius, piece.healthBarColor)
			}
			
			image {
				val w = piece.type.imageWidth
				val h = piece.type.imageHeight
				
				x = (-w * 0.5).toString()
				y = (-h * 0.5).toString()
				width = w.toString()
				height = h.toString()
				
				href = piece.imagePath
			}
		}
		
		if (updateEvents) {
			window.requestAnimationFrame {
				updatePieceEvents()
			}
		}
	}
	
	fun redrawAllPieces(pieces: Set<GamePiece>) {
		gamePieces.clear()
		
		pieces.forEach { piece ->
			drawPiece(piece, false)
		}
		
		window.requestAnimationFrame {
			updatePieceEvents()
		}
	}
	
	fun undrawPiece(pieceId: String) {
		document.getElementById(pieceId)?.remove()
	}
	
	fun drawEverything(gameSessionData: GameSessionData) {
		drawMap(gameSessionData.gameMap)
		redrawAllPieces(gameSessionData.allPieces())
	}
	
	fun eraseEverything() {
		drawPickBoundary(null)
		
		mapField.clear()
		gamePieces.clear()
		
		selection.clear()
	}
}

object DeployConstants {
	val pointLevels = mapOf(
		500 to "Skirmish",
		750 to "Small",
		1000 to "Medium",
		1500 to "Large",
		2000 to "Armageddon"
	)
	
	const val DEPLOY_ZONE_WIDTH = 500.0
	
	const val HOST_FACING = PI / 2
	const val GUEST_FACING = PI * 3 / 2
}

object GameSidebar {
	private val sidebar by lazy {
		document.getElementById("piece-selection").unsafeCast<HTMLDivElement>()
	}
	
	private var currentPoints = 0
	
	private var deployJob: Job? = null
	
	fun beginDeploy() {
		currentPoints = GameSessionData.currentSession!!.battleSize
	}
	
	fun deployMenu() {
		sidebar.clear()
		
		if (GamePhase.Deployment.localIsDone)
			sidebar.append {
				sidebar.clear()
				
				sidebar.append {
					p(classes = "info") {
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
					
					PieceType.values().forEach linkRender@{ pieceType ->
						if (pieceType.pointCost == null)
							return@linkRender
						
						a(href = "#") {
							+"${pieceType.displayName} (${pieceType.pointCost})"
							
							if (currentPoints < pieceType.pointCost) {
								classes = setOf("disabled")
								
								onClickFunction = { e ->
									e.preventDefault()
								}
							} else
								onClickFunction = { e ->
									e.preventDefault()
									
									deployPiece(pieceType)
								}
						}
					}
					
					a(href = "#") {
						+"Clear Deployment"
						
						if (GameSessionData.currentSession!!.anyPiecesWithOwner(Game.currentSide!!))
							onClickFunction = { e ->
								e.preventDefault()
								
								GameScope.launch clearDeploy@{
									val popup = Popup.YesNoDialogue("Yes", "No") {
										+"Are you sure you want to clear your deployment? This will erase all units you have deployed, and restore your pool of points to spend."
									}
									
									if (!popup.display())
										return@clearDeploy
									
									Player.currentPlayer!!.clearDeploying()
									currentPoints = GameSessionData.currentSession!!.battleSize
									
									deployMenu()
								}
							}
						else {
							classes = setOf("disabled")
							
							onClickFunction = { e ->
								e.preventDefault()
							}
						}
					}
					
					a(href = "#") {
						id = "finish-deploy"
						
						+"Done Deploying"
						
						if (GameSessionData.currentSession!!.anyPiecesWithOwner(Game.currentSide!!))
							onClickFunction = { e ->
								e.preventDefault()
								
								GameScope.launch finishDeploy@{
									val popup = Popup.YesNoDialogue("Yes", "No") {
										+"Are you sure you want to finish deploying? "
										if (currentPoints > 0)
											+"You won't be able to use your $currentPoints remaining points after the deployment phase!"
										else
											+"You won't be able to change your deployment after you confirm it!"
									}
									
									if (!popup.display())
										return@finishDeploy
									
									Player.currentPlayer!!.finishDeploying()
									
									if (GamePhase.Deployment.bothAreDone)
										updateSidebar()
									else
										deployMenu()
								}
							}
						else {
							classes = setOf("disabled")
							
							onClickFunction = { e ->
								e.preventDefault()
							}
						}
					}
				}
			}
	}
	
	private fun deployPiece(pieceType: PieceType) {
		if (pieceType.pointCost == null)
			return
		
		if (currentPoints < pieceType.pointCost)
			return
		
		val mapSize = GameSessionData.currentSession!!.gameMap.size
		val side = Game.currentSide!!
		
		val width = DeployConstants.DEPLOY_ZONE_WIDTH
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
			pieceType.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE,
			pieceType.layer,
			TerrainRequirement.DEFAULT,
			pieceType.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE
		)
		
		deployJob?.cancel()
		
		deployJob = GameScope.launch {
			val pRes = PickHandler.pickLocal(pReq) as? PickResponse.PickedPosition ?: return@launch
			
			val pos = pRes.pos
			val facing = when (side) {
				GameServerSide.HOST -> DeployConstants.HOST_FACING
				GameServerSide.GUEST -> DeployConstants.GUEST_FACING
			}
			
			currentPoints -= pieceType.pointCost
			Player.currentPlayer!!.deployPiece(pieceType, pos, facing)
			
			deployMenu()
		}
	}
	
	private var selectedPieceId: String? = null
	
	fun select(pieceId: String?) {
		if (GamePhase.currentPhase !is GamePhase.PlayTurn)
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
				p(classes = "info") {
					+"Click a game piece to select it"
				}
			}
		} else {
			sidebar.append {
				if (piece.canBeIdentified) {
					p(classes = "info") {
						+piece.type.displayName
					}
					
					div(classes = "measure-bar") {
						+"Health"
						+Entities.nbsp
						span("meter health") {
							span("emptiness") {
								style = "width: ${((1 - piece.health) * 100).roundToInt()}%"
							}
						}
					}
				} else {
					p(classes = "info") {
						+"Unidentified"
					}
				}
				
				if (piece.owner == Game.currentSide!! && GamePhase.currentPhase == GamePhase.PlayTurn(Game.currentSide!!)) {
					div(classes = "measure-bar") {
						+"Action"
						+Entities.nbsp
						span("meter action") {
							span("emptiness") {
								style = "width: ${((1 - piece.action) * 100).roundToInt()}%"
							}
						}
					}
					
					div(classes = "button-set col") {
						piece.type.stats.abilities.forEach { (abilityName, ability) ->
							a(href = "#") {
								id = "use-ability-${abilityName.lowercase().replace(' ', '-')}"
								+abilityName
								
								if (ability.canUse(piece))
									onClickFunction = { e ->
										e.preventDefault()
										
										piece.type.stats.abilities.forEach { (abilityName, _) ->
											val btn = document.getElementById("use-ability-${abilityName.lowercase().replace(' ', '-')}").unsafeCast<HTMLAnchorElement>()
											btn.addClass("disabled")
											btn.onclick = { e1 ->
												e1.preventDefault()
											}
										}
										
										GameScope.launch {
											Player.currentPlayer!!.useAbility(piece.id, abilityName)
											updateSidebar()
										}
									}
								else {
									classes = setOf("disabled")
									onClickFunction = { e ->
										e.preventDefault()
									}
								}
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
						
						onClickFunction = { e ->
							e.preventDefault()
							
							val btn = document.getElementById("end-turn-btn").unsafeCast<HTMLAnchorElement>()
							
							btn.addClass("disabled")
							
							GameScope.launch {
								Player.currentPlayer!!.endTurn()
								
								updateSidebar()
							}
						}
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
