import kotlinx.serialization.Serializable

@Serializable
class GameSessionData(val mapSize: Vec2, val battleType: BattleType) {
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
		
		val LAND_WIDTH_RANGE = 3500.0..4500.0
		val LAND_HEIGHT_RANGE = 1500.0..2500.0
		
		val SPACE_WIDTH_RANGE = 3000.0..5000.0
		val SPACE_HEIGHT_RANGE = 2000.0..4000.0
		
		fun randomSize(battleType: BattleType) = when (battleType) {
			BattleType.LAND_BATTLE -> Vec2(LAND_WIDTH_RANGE.random(), LAND_HEIGHT_RANGE.random())
			BattleType.SPACE_BATTLE -> Vec2(SPACE_WIDTH_RANGE.random(), SPACE_HEIGHT_RANGE.random())
		}
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
	
	var shield = 1.0
	var shieldDepleted = false
	
	var action = 1.0
	var attacked = false
	
	var heavyWeaponCharged = false
	
	fun attack(damage: Double) {
		if (type.stats is SpacePieceStats && !shieldDepleted) {
			val dShield = damage / type.stats.maxShield
			if (dShield >= shield) {
				val dHealth = (damage - shield * type.stats.maxShield) / type.stats.maxHealth
				health -= dHealth
				
				shield = 0.0
				shieldDepleted = true
				
				if (health <= 0.0)
					GameSessionData.currentSession!!.removeById(id)
				else
					GameSessionData.currentSession!!.markDirty(id)
			} else {
				shield -= dShield
				
				GameSessionData.currentSession!!.markDirty(id)
			}
		} else {
			health -= damage / type.stats.maxHealth
			
			if (health <= 0.0)
				GameSessionData.currentSession!!.removeById(id)
			else
				GameSessionData.currentSession!!.markDirty(id)
		}
	}
	
	fun doNextTurn() {
		action = 1.0
		attacked = false
		
		if (type.stats is SpacePieceStats) {
			if (shieldDepleted) {
				shield += SHIELD_RECHARGE_PER_TURN / type.stats.maxShield
				
				if (shield >= 1.0) {
					shield = 1.0
					shieldDepleted = false
				}
			}
		}
	}
	
	val visionRange: Double
		get() = if (type.requiredBattleType == BattleType.SPACE_BATTLE) 1000.0 else 500.0
	
	val canBeIdentified: Boolean
		get() = Game.currentSide == owner || canBeIdentifiedByEnemy
	
	val canBeIdentifiedByEnemy: Boolean
		get() = GameSessionData.currentSession!!.allPiecesWithOwner(owner.other).any { otherPiece ->
			(location - otherPiece.location).magnitude < visionRange
		}
	
	val imagePath: String
		get() = "uniticons/${if (owner == Game.currentSide) "player" else "opponent"}/${
			if (canBeIdentified) type.name.toLowerCase() else (when (type.requiredBattleType) {
				BattleType.LAND_BATTLE -> "land"
				BattleType.SPACE_BATTLE -> "space"
			} + when (type.factionSkin) {
				BattleFactionSkin.EMPIRE -> "_in"
				BattleFactionSkin.SPACE_MARINES -> "_sm"
				BattleFactionSkin.STAR_FLEET -> "_sf"
				null -> ""
			} + "_unknown")
		}.png"
	
	val pieceRadius: Double
		get() = type.imageRadius + 15
	
	val healthBarColor: String
		get() = if (health > 0.5)
			"rgb(${((1.0 - health) * 510).toInt()}, 255, 0)"
		else
			"rgb(255, ${(health * 510).toInt()}, 0)"
	
	val shieldBarColor: String
		get() = if (shieldDepleted)
			"rgb(170, 85, 255)"
		else
			"rgb(85, 170, 255)"
	
	companion object {
		const val SHIELD_RECHARGE_PER_TURN = 75.0
	}
}
