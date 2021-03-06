import kotlinx.serialization.Serializable

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
	val type: PieceType,
	val owner: GameServerSide,
	var location: Vec2,
	var facing: Double
) {
	var health = 1.0
	
	var shield = 1.0
	var shieldDepleted = false
	
	var action = 1.0
	var hasAttacked = false
	
	var isCloaked = false
	var isCloakRevealed = false
	var heavyWeaponCharged = false
	
	val canUseShield: Boolean
		get() = type.stats is SpacePieceStats && !shieldDepleted && !isCloaked && !(currentTerrainBlob?.let { blob ->
			blob.type.stats is TerrainStats.Space && blob.type.stats.forcesShieldsDown
		} ?: false)
	
	fun attack(damage: Double) {
		if (canUseShield) {
			val stats = type.stats as SpacePieceStats
			
			val dShield = damage / stats.maxShield
			if (dShield >= shield) {
				val dHealth = (damage - shield * stats.maxShield) / stats.maxHealth
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
		hasAttacked = false
		
		currentTerrainBlob?.let { blob ->
			val takenDamage = blob.type.stats.damagePerTurn
			if (blob.type.stats is TerrainStats.Space && blob.type.stats.dptIgnoresShields) {
				health -= takenDamage / type.stats.maxHealth
				
				if (health <= 0.0)
					GameSessionData.currentSession!!.removeById(id)
				else
					GameSessionData.currentSession!!.markDirty(id)
			} else {
				attack(takenDamage)
			}
		}
		
		if (type.stats is SpacePieceStats) {
			if (shieldDepleted && !isCloaked) {
				shield += SHIELD_RECHARGE_PER_TURN / type.stats.maxShield
				
				if (shield >= 1.0) {
					shield = 1.0
					shieldDepleted = false
				}
			}
		}
	}
	
	val canBeRendered: Boolean
		get() = Game.currentSide == owner || canBeRenderedByEnemy
	
	val canBeRenderedByEnemy: Boolean
		get() = (!isCloaked || isCloakRevealed) && !isHiddenByTerrain
	
	val currentTerrainBlob: TerrainBlob?
		get() = GameSessionData.currentSession!!.gameMap.terrainBlobs.singleOrNull { (it.center - location).magnitude < it.radius }
	
	val isHiddenByTerrain: Boolean
		get() = currentTerrainBlob?.let { blob ->
			val hideRange = blob.type.stats.hideEnemyUnitRange
			hideRange != null && GameSessionData.currentSession!!.allPiecesWithOwner(owner.other).none { enemyPiece ->
				(enemyPiece.location - location).magnitude < hideRange
			}
		} ?: false
	
	val visionRange: Double
		get() = if (type.requiredBattleType == BattleType.SPACE_BATTLE) 1000.0 else 500.0
	
	val canBeIdentified: Boolean
		get() = Game.currentSide == owner || canBeIdentifiedByEnemy
	
	val canBeIdentifiedByEnemy: Boolean
		get() = GameSessionData.currentSession!!.allPiecesWithOwner(owner.other).any { otherPiece ->
			(location - otherPiece.location).magnitude < otherPiece.visionRange
		}
	
	val imagePath: String
		get() = "uniticons/${if (owner == Game.currentSide) "player" else "opponent"}/${
			if (canBeIdentified) type.name.lowercase() else (when (type.requiredBattleType) {
				BattleType.LAND_BATTLE -> "land"
				BattleType.SPACE_BATTLE -> "space"
			} + when (type.factionSkin) {
				BattleFactionSkin.EMPIRE -> "_in"
				BattleFactionSkin.SPACE_MARINES -> "_sm"
				BattleFactionSkin.STAR_FLEET -> "_sf"
				BattleFactionSkin.KDF -> "_ke"
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
		get() {
			val alpha = if (isCloaked) "0.4" else "1.0"
			
			return if (shieldDepleted)
				"rgba(170, 85, 255, $alpha)"
			else
				"rgba(85, 170, 255, $alpha)"
		}
	
	companion object {
		const val SHIELD_RECHARGE_PER_TURN = 75.0
	}
}
