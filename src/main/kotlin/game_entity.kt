import kotlinx.serialization.Serializable

@Serializable
class GameSessionData(val mapSize: Vec2) {
	private val pieces = mutableSetOf<GamePiece>()
	
	fun allPieces() = setOf<GamePiece>() + pieces
	
	fun allPiecesWithOwner(owner: GameServerSide) = pieces.filter { it.owner == owner }.toSet()
	
	fun anyPiecesWithOwner(owner: GameServerSide) = pieces.any { it.owner == owner }
	
	fun pieceById(id: String) = pieces.single { it.id == id }
	fun pieceByIdOrNull(id: String) = pieces.singleOrNull { it.id == id }
	
	fun markDirty(id: String) {
		val dirty = pieceByIdOrNull(id)
		
		if (dirty == null) {
			GameField.undrawPiece(id)
			
			if (Game.currentSide == GameServerSide.HOST)
				GamePacket.send(GamePacket.PieceDeleted(id))
		} else {
			GameField.drawPiece(dirty)
			
			if (Game.currentSide == GameServerSide.HOST)
				GamePacket.send(GamePacket.PieceAddedOrChanged(dirty))
		}
		
		if (GamePhase.currentPhase != GamePhase.Deployment)
			GameSidebar.updateSidebar()
	}
	
	fun addOrReplace(piece: GamePiece) {
		pieces.removeAll { it.id == piece.id }
		pieces.add(piece)
		
		GameField.drawPiece(piece)
		
		if (Game.currentSide == GameServerSide.HOST)
			GamePacket.send(GamePacket.PieceAddedOrChanged(piece))
		
		if (GamePhase.currentPhase == GamePhase.Deployment)
			GameSidebar.deployMenu()
		else
			GameSidebar.updateSidebar()
	}
	
	fun removeById(id: String) {
		if (pieces.removeAll { it.id == id }) {
			GameField.undrawPiece(id)
			
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
			removeById(id)
		}
	}
	
	val canEndTurn: Boolean
		get() = pieces.any { it.owner == Game.currentSide!! } && pieces.filter { it.owner == Game.currentSide!! }.all { it.isDoneTurn }
	
	fun endTurn(player: GameServerSide) {
		pieces.filter { it.owner == player }.forEach { piece ->
			piece.doNextTurn()
			markDirty(piece.id)
		}
	}
	
	val winner: GameServerSide?
		get() = when {
			pieces.all { it.owner == GameServerSide.HOST } -> GameServerSide.HOST
			pieces.all { it.owner == GameServerSide.GUEST } -> GameServerSide.GUEST
			else -> null
		}
	
	companion object {
		var currentSession: GameSessionData? = null
		
		val SIZE_RANGE = 1500.0..2500.0
		
		fun randomSize() = Vec2(SIZE_RANGE.random(), SIZE_RANGE.random())
	}
}

private var idCount = 0
fun newGamePieceId() = "game-piece-${++idCount}"

@Serializable
data class GamePiece(
	val id: String,
	val type: PieceType,
	val owner: GameServerSide,
	var location: Vec2,
	var facing: Double
) {
	var health = 1.0
	
	var action = 1.0
	var skipTurn = false
	var attacked = false
	
	val isDoneTurn: Boolean
		get() = action <= 0.0 || skipTurn
	
	fun doNextTurn() {
		action = 1.0
		skipTurn = false
		attacked = false
	}
	
	val pieceRadius: Double
		get() = type.imageRadius + 15
	
	val healthBarColor: String
		get() = if (health > 0.5)
			"rgb(${((1.0 - health) * 510).toInt()}, 255, 0)"
		else
			"rgb(255, ${(health * 510).toInt()}, 0)"
}
