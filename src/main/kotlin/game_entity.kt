import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.w3c.dom.Image

@Serializable
class GameSessionData(val gameMap: GameMap, val battleSize: Int) {
	private val pieces = mutableSetOf<GamePiece>()
	
	fun allPieces() = setOf<GamePiece>() + pieces
	
	fun allPiecesWithOwner(owner: GameServerSide) = pieces.filter { it.owner == owner }.toSet()
	
	fun anyPiecesWithOwner(owner: GameServerSide) = pieces.any { it.owner == owner }
	
	fun pieceById(id: String) = pieces.single { it.id == id }
	fun pieceByIdOrNull(id: String) = pieces.singleOrNull { it.id == id }
	
	fun markDirty(id: String, redraw: Boolean = true) {
		val dirty = pieceByIdOrNull(id)
		
		if (redraw)
			GameField.redrawAllPieces(pieces)
		
		if (Game.currentSide == GameServerSide.HOST) {
			if (dirty == null)
				GamePacket.send(GamePacket.PieceDeleted(id))
			else
				GamePacket.send(GamePacket.PieceAddedOrChanged(dirty))
		}
		
		if (GamePhase.currentPhase == GamePhase.Deployment)
			GameSidebar.deployMenu()
		else
			GameSidebar.updateSidebar()
	}
	
	fun addOrReplace(piece: GamePiece, redraw: Boolean = true) {
		pieces.removeAll { it.id == piece.id }
		pieces.add(piece)
		
		if (redraw)
			GameField.redrawAllPieces(pieces)
		
		if (Game.currentSide == GameServerSide.HOST)
			GamePacket.send(GamePacket.PieceAddedOrChanged(piece))
		
		if (GamePhase.currentPhase == GamePhase.Deployment)
			GameSidebar.deployMenu()
		else
			GameSidebar.updateSidebar()
	}
	
	fun removeById(id: String, redraw: Boolean = true) {
		if (pieces.removeAll { it.id == id }) {
			if (redraw)
				GameField.redrawAllPieces(pieces)
			
			if (Game.currentSide == GameServerSide.HOST)
				GamePacket.send(GamePacket.PieceDeleted(id))
			
			if (GamePhase.currentPhase == GamePhase.Deployment)
				GameSidebar.deployMenu()
			else
				GameSidebar.updateSidebar()
		}
	}
	
	fun removeAllByOwner(owner: GameServerSide) {
		allPiecesWithOwner(owner).map { it.id }.forEach { id ->
			removeById(id, false)
		}
		
		GameField.redrawAllPieces(pieces)
	}
	
	fun endTurn(player: GameServerSide) {
		pieces.filter { it.owner == player }.forEach { piece ->
			piece.doNextTurn()
			markDirty(piece.id, false)
		}
		
		GameField.redrawAllPieces(pieces)
	}
	
	val winner: GameServerSide?
		get() = when {
			pieces.all { it.owner == GameServerSide.HOST } -> GameServerSide.HOST
			pieces.all { it.owner == GameServerSide.GUEST } -> GameServerSide.GUEST
			else -> null
		}
	
	companion object {
		var currentSession: GameSessionData? = null
	}
}

private var idCount = 0
fun newGamePieceId() = "game-piece-${++idCount}"

@Serializable
data class GamePiece(
	val id: String,
	var type: PieceType,
	val owner: GameServerSide,
	private val initialLocation: Vec2,
	private val initialFacing: Double
) {
	val canUndoMove: Boolean
		get() = (prevLocation != location || prevFacing != facing) && prevAction != action
	
	var prevLocation: Vec2 = initialLocation
		private set
	
	var location: Vec2 = initialLocation
		set(value) {
			prevLocation = field
			field = value
		}
	
	var prevFacing: Double = initialFacing
		private set
	
	var facing: Double = initialFacing
		get() = field.asAngle()
		set(value) {
			prevFacing = field
			field = value
		}
	
	var prevAction = 1.0
		private set
	
	var action = 1.0
		set(value) {
			prevAction = field
			field = value
		}
	
	var airEvasion = 0.0
		private set
	
	val airTargetedMult: Double
		get() = 1.5 - airEvasion
	
	fun undoMove() {
		val (newLocation, newFacing, newAction) = Triple(prevLocation, prevFacing, prevAction)
		location = newLocation
		facing = newFacing
		action = newAction
	}
	
	fun lockUndo() {
		prevLocation = location
		prevFacing = facing
		prevAction = action
	}
	
	var health = 1.0
	
	var hasAttacked = false
	
	var isCloaked = false
	var isCloakRevealed = false
	var heavyWeaponCharged = false
	
	fun attack(damage: Double, source: DamageSource) {
		health -= damage / type.stats.maxHealth
		
		if (health <= 0.0)
			GameSessionData.currentSession!!.removeById(id)
		else
			GameSessionData.currentSession!!.markDirty(id)
		
		ChatBox.notifyAttack(source, this, damage)
	}
	
	fun doNextTurn() {
		if (type.layer == PieceLayer.AIR) {
			if (action > AIR_PIECES_MAX_ACTION_REMAINING)
				attack(1_000_000.0, DamageSource.AirPieceCrash)
			else
				airEvasion = (AIR_PIECES_MAX_ACTION_REMAINING - action) / AIR_PIECES_MAX_ACTION_REMAINING
		}
		
		action = 1.0
		hasAttacked = false
		
		currentTerrainBlob?.let { blob ->
			val takenDamage = blob.type.stats.damagePerTurn
			attack(takenDamage, DamageSource.Terrain(blob.type))
		}
	}
	
	val canBeRendered: Boolean
		get() = Game.currentSide == owner || canBeRenderedByEnemy
	
	val canBeRenderedByEnemy: Boolean
		get() = (!isCloaked || isCloakRevealed) && !isHiddenByTerrain
	
	val currentTerrainBlob: TerrainBlob?
		get() = if (type.layer == PieceLayer.AIR)
			null
		else
			GameSessionData.currentSession!!.gameMap.terrainBlobs.singleOrNull { (it.center - location).magnitude < it.radius }
	
	val isHiddenByTerrain: Boolean
		get() = currentTerrainBlob?.let { blob ->
			val hideRange = blob.type.stats.hideEnemyUnitRange
			hideRange != null && GameSessionData.currentSession!!.allPiecesWithOwner(owner.other).none { enemyPiece ->
				val rangeMult = if (enemyPiece.type.layer == PieceLayer.AIR) 2.0 else 1.0
				(enemyPiece.location - location).magnitude < hideRange * rangeMult
			}
		} ?: false
	
	val visionRange: Double
		get() = when (type.layer) {
			PieceLayer.LAND -> 500.0
			PieceLayer.AIR -> 1000.0
		}
	
	val canBeIdentified: Boolean
		get() = Game.currentSide == owner || canBeIdentifiedByEnemy
	
	val canBeIdentifiedByEnemy: Boolean
		get() = GameSessionData.currentSession!!.allPiecesWithOwner(owner.other).any { otherPiece ->
			(location - otherPiece.location).magnitude < otherPiece.visionRange
		}
	
	val imagePath: String
		get() = if (canBeIdentified)
			getImagePath(owner != Game.currentSide!!, type)
		else
			getUnknownImagePath(owner != Game.currentSide!!, type.layer)
	
	val pieceRadius: Double
		get() = type.imageRadius + PIECE_RADIUS_OUTLINE
	
	val healthBarColor: String
		get() = if (health > 0.5)
			"rgb(${((1.0 - health) * 510).toInt()}, 255, 0)"
		else
			"rgb(255, ${(health * 510).toInt()}, 0)"
	
	companion object {
		const val AIR_PIECES_MAX_ACTION_REMAINING = 0.6
		
		const val PIECE_RADIUS_OUTLINE = 15.0
		
		private suspend fun preloadImage(path: String) {
			val img = Image()
			img.src = path
			img.awaitEvent("load")
		}
		
		suspend fun preloadAllPieceImages() {
			coroutineScope {
				val urls = PieceType.values().flatMap { pieceType ->
					listOf(
						getImagePath(true, pieceType),
						getImagePath(false, pieceType)
					)
				} + PieceLayer.values().flatMap { pieceLayer ->
					listOf(
						getUnknownImagePath(true, pieceLayer),
						getUnknownImagePath(false, pieceLayer)
					)
				}
				
				urls.map { url ->
					launch {
						preloadImage(url)
					}
				}.joinAll()
			}
		}
		
		fun getImagePath(isOpponent: Boolean, pieceType: PieceType): String {
			return "uniticons/${if (isOpponent) "opponent" else "player"}/${pieceType.name.lowercase()}.png"
		}
		
		fun getUnknownImagePath(isOpponent: Boolean, layer: PieceLayer): String {
			return "uniticons/${if (isOpponent) "opponent" else "player"}/${layer.name.lowercase()}_unknown.png"
		}
	}
}
