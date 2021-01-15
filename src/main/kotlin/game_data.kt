import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

@Suppress("DuplicatedCode")
@Serializable
sealed class Ability {
	protected val GamePiece.player: Player
		get() = Player.getPlayer(owner)
	
	abstract fun canUse(currentPiece: GamePiece): Boolean
	abstract suspend fun use(currentPiece: GamePiece)
	
	// Same for both battle type
	
	@Serializable
	data class Move(val distancePerAction: Double) : Ability() {
		private val ANGLE_BUFFER get() = PI / 8
		private val DISTANCE_BUFFER get() = 50.0
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.pieceRadius / distancePerAction < currentPiece.action
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickPosition(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius,
					distancePerAction * currentPiece.action + DISTANCE_BUFFER,
					currentPiece.facing,
					null,
					ANGLE_BUFFER
				),
				currentPiece.location,
				currentPiece.pieceRadius,
				currentPiece.pieceRadius
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPosition ?: return
			
			val newLocation = pickRes.pos
			val dLocation = newLocation - currentPiece.location
			val newFacing = dLocation.angle.asAngle()
			val newAction = (currentPiece.action - dLocation.magnitude / distancePerAction).coerceAtLeast(0.0)
			
			currentPiece.location = newLocation
			currentPiece.facing = newFacing
			currentPiece.action = newAction
		}
	}
	
	@Serializable
	data class Rotate(val anglePerAction: Double) : Ability() {
		private val ANGLE_BUFFER get() = PI / 12
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action > 0.0
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickAngle(
				currentPiece.location,
				currentPiece.facing,
				currentPiece.action * anglePerAction + ANGLE_BUFFER,
				currentPiece.pieceRadius + 10
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedAngle ?: return
			
			val newFacing = pickRes.newAngle
			val deltaAngle = abs(currentPiece.facing - newFacing)
			val newAction = (currentPiece.action - deltaAngle / anglePerAction).coerceAtLeast(0.0)
			
			currentPiece.facing = newFacing
			currentPiece.action = newAction
		}
	}
	
	// Land Battle abilities
	
	@Serializable
	data class AttackLand(
		val maxAngle: Double,
		val minDistance: Double,
		val maxDistance: Double,
		val softAttackPower: Double,
		val hardAttackPower: Double,
		val actionConsumed: Double,
		val canMoveAfterAttacking: Boolean
	) : Ability() {
		// Flanking multiplier is calculated as ((attacker.facingNormal dot target.facingNormal) + flankWeight) / (flankWeight - 1)
		// For the soft attack weight of 3, the flanking multiplier ranges from 1 at minimum to 2 at maximum.
		// For the hard attack weight of 9, the flanking multiplier ranges from 1 at minimum to 1.25 at maximum.
		// Higher flank weight reduces the effect of flanking. Numbers less than or equal to 1 should NEVER be used.
		
		private val SOFT_FLANK_WEIGHT get() = 3.0
		private val HARD_FLANK_WEIGHT get() = 9.0
		
		private fun getFlankingMultiplier(dotProduct: Double, flankWeight: Double): Double {
			return (dotProduct + flankWeight) / (flankWeight - 1)
		}
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.attacked
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickPiece(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius + minDistance,
					currentPiece.pieceRadius + maxDistance,
					currentPiece.facing,
					null,
					maxAngle
				),
				currentPiece.owner.other
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPiece ?: return
			val targetPiece = GameSessionData.currentSession!!.pieceById(pickRes.pieceId)
			targetPiece.type.stats as LandPieceStats
			
			val targetHardness = targetPiece.type.stats.hardness
			val targetSoftness = 1 - targetHardness
			
			val attackFacing = (targetPiece.location - currentPiece.location).angle
			
			val dotProduct = cos(attackFacing - targetPiece.facing)
			val softAttack = softAttackPower * targetSoftness * getFlankingMultiplier(dotProduct, SOFT_FLANK_WEIGHT)
			val hardAttack = hardAttackPower * targetHardness * getFlankingMultiplier(dotProduct, HARD_FLANK_WEIGHT)
			
			val totalAttack = softAttack + hardAttack
			targetPiece.attack(totalAttack)
			
			currentPiece.attacked = true
			
			if (canMoveAfterAttacking)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
		}
	}
	
	@Serializable
	data class HealLand(
		val maxAngle: Double,
		val minDistance: Double,
		val maxDistance: Double,
		val healthRestored: Double,
		val actionConsumed: Double,
		val canMoveAfterHealing: Boolean
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.attacked
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickPiece(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius + minDistance,
					currentPiece.pieceRadius + maxDistance,
					currentPiece.facing,
					null,
					maxAngle
				),
				currentPiece.owner
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPiece ?: return
			val targetPiece = GameSessionData.currentSession!!.pieceById(pickRes.pieceId)
			
			val deltaHealth = healthRestored / targetPiece.type.stats.maxHealth
			targetPiece.health += deltaHealth
			
			if (targetPiece.health > 1.0)
				targetPiece.health = 1.0
			
			GameSessionData.currentSession!!.markDirty(targetPiece.id)
			
			currentPiece.attacked = true
			
			if (canMoveAfterHealing)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
		}
	}
	
	// Space Battle abilities
	
	@Serializable
	data class AttackSpace(
		val minAngle: Double?,
		val maxAngle: Double,
		val minDistance: Double,
		val maxDistance: Double,
		val attackPower: Double,
		val actionConsumed: Double,
		val canMoveAfterAttacking: Boolean
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.attacked
		}
		
		private val SPACE_FLANK_WEIGHT get() = 5.0
		
		private fun getFlankingMultiplier(dotProduct: Double, flankWeight: Double): Double {
			return (dotProduct + flankWeight) / (flankWeight - 1)
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickPiece(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius + minDistance,
					currentPiece.pieceRadius + maxDistance,
					currentPiece.facing,
					minAngle,
					maxAngle
				),
				currentPiece.owner.other
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPiece ?: return
			val targetPiece = GameSessionData.currentSession!!.pieceById(pickRes.pieceId)
			targetPiece.type.stats as SpacePieceStats
			
			val attackFacing = (targetPiece.location - currentPiece.location).angle
			val dotProduct = cos(attackFacing - targetPiece.facing)
			
			val totalAttack = attackPower * getFlankingMultiplier(dotProduct, SPACE_FLANK_WEIGHT)
			targetPiece.attack(totalAttack)
			
			currentPiece.attacked = true
			
			if (canMoveAfterAttacking)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
		}
	}
	
	@Serializable
	data class AttackAreaSpace(
		val minAngle: Double?,
		val maxAngle: Double,
		val minDistance: Double,
		val maxDistance: Double,
		val aoeRadius: Double,
		val attackPower: Double,
		val actionConsumed: Double,
		val canMoveAfterAttacking: Boolean
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.attacked
		}
		
		private val SPACE_FLANK_WEIGHT get() = 5.0
		
		private fun getFlankingMultiplier(dotProduct: Double, flankWeight: Double): Double {
			return (dotProduct + flankWeight) / (flankWeight - 1)
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickPosition(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius + minDistance,
					currentPiece.pieceRadius + maxDistance,
					currentPiece.facing,
					minAngle,
					maxAngle
				),
				currentPiece.location,
				null,
				aoeRadius
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPosition ?: return
			
			val piecesInAoe = GameSessionData.currentSession!!.allPieces().filter { (it.location - pickRes.pos).magnitude <= aoeRadius }
			piecesInAoe.forEach { targetPiece ->
				targetPiece.type.stats as SpacePieceStats
				
				val attackFacing = (targetPiece.location - currentPiece.location).angle
				val dotProduct = cos(attackFacing - targetPiece.facing)
				
				val totalAttack = attackPower * getFlankingMultiplier(dotProduct, SPACE_FLANK_WEIGHT)
				targetPiece.attack(totalAttack)
			}
			
			currentPiece.attacked = true
			
			if (canMoveAfterAttacking)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
		}
	}
}

fun standardLandPieceAbilities(
	moveSpeedPerRound: Double,
	turnSpeedPerRound: Double,
	
	maxAttackAngle: Double,
	minAttackDistance: Double,
	maxAttackDistance: Double,
	softAttack: Double,
	hardAttack: Double,
	attackActionConsumed: Double,
	canMoveAfterAttacking: Boolean,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Move" to Ability.Move(moveSpeedPerRound),
	"Turn" to Ability.Rotate(turnSpeedPerRound),
	"Attack" to Ability.AttackLand(
		maxAttackAngle,
		minAttackDistance,
		maxAttackDistance,
		softAttack,
		hardAttack,
		attackActionConsumed,
		canMoveAfterAttacking
	)
) + extraAbilities

fun standardSpacePieceAbilities(
	moveSpeedPerRound: Double,
	turnSpeedPerRound: Double,
	
	minAttackAngle: Double?,
	maxAttackAngle: Double,
	minAttackDistance: Double,
	maxAttackDistance: Double,
	attackStrength: Double,
	attackActionConsumed: Double,
	canMoveAfterAttacking: Boolean,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Move" to Ability.Move(moveSpeedPerRound),
	"Turn" to Ability.Rotate(turnSpeedPerRound),
	"Fire Main Batteries" to Ability.AttackSpace(
		minAttackAngle,
		maxAttackAngle,
		minAttackDistance,
		maxAttackDistance,
		attackStrength,
		attackActionConsumed,
		canMoveAfterAttacking
	)
) + extraAbilities

@Serializable
sealed class PieceStats {
	abstract val maxHealth: Double
	abstract val abilities: Map<String, Ability>
}

@Serializable
data class LandPieceStats(
	override val maxHealth: Double,
	val hardness: Double,
	override val abilities: Map<String, Ability>
) : PieceStats()

@Serializable
data class SpacePieceStats(
	override val maxHealth: Double,
	val maxShield: Double,
	override val abilities: Map<String, Ability>
) : PieceStats()

enum class BattleType(
	val displayName: String,
	val mapColor: String
) {
	LAND_BATTLE("Land Battle", "#194"),
	SPACE_BATTLE("Space Battle", "#444");
}

@Serializable
enum class PieceType(
	val displayName: String,
	val pointCost: Int,
	val stats: PieceStats
) {
	// Land Battle pieces
	
	LAND_INFANTRY(
		"Infantry",
		50,
		LandPieceStats(
			maxHealth = 2000.0,
			hardness = 0.0,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 3 * PI,
				
				maxAttackAngle = PI / 8,
				minAttackDistance = 0.0,
				maxAttackDistance = 300.0,
				softAttack = 300.0,
				hardAttack = 50.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_ELITE_INFANTRY(
		"Stormtroopers",
		100,
		LandPieceStats(
			maxHealth = 3000.0,
			hardness = 0.1,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 3 * PI,
				
				maxAttackAngle = PI / 8,
				minAttackDistance = 0.0,
				maxAttackDistance = 400.0,
				softAttack = 450.0,
				hardAttack = 75.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_MEDIC(
		"Combat Medic",
		150,
		LandPieceStats(
			maxHealth = 1500.0,
			hardness = 0.05,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 3 * PI,
				
				maxAttackAngle = PI / 8,
				minAttackDistance = 0.0,
				maxAttackDistance = 300.0,
				softAttack = 150.0,
				hardAttack = 25.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false,
				
				extraAbilities = mapOf(
					"Heal" to Ability.HealLand(
						maxAngle = PI / 4,
						minDistance = 0.0,
						maxDistance = 200.0,
						healthRestored = 800.0,
						actionConsumed = 0.5,
						canMoveAfterHealing = false
					)
				)
			)
		)
	),
	LAND_CAVALRY(
		"Cavalry",
		75,
		LandPieceStats(
			maxHealth = 1500.0,
			hardness = 0.0,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 1200.0,
				turnSpeedPerRound = 6 * PI,
				
				maxAttackAngle = PI / 6,
				minAttackDistance = 0.0,
				maxAttackDistance = 300.0,
				softAttack = 300.0,
				hardAttack = 30.0,
				attackActionConsumed = 0.125,
				canMoveAfterAttacking = true
			)
		)
	),
	LAND_ELITE_CAVALRY(
		"Winged Hussars",
		125,
		LandPieceStats(
			maxHealth = 2500.0,
			hardness = 0.1,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 1200.0,
				turnSpeedPerRound = 6 * PI,
				
				maxAttackAngle = PI / 6,
				minAttackDistance = 0.0,
				maxAttackDistance = 400.0,
				softAttack = 450.0,
				hardAttack = 45.0,
				attackActionConsumed = 0.125,
				canMoveAfterAttacking = true
			)
		)
	),
	LAND_TANKS(
		"Light Tanks",
		80,
		LandPieceStats(
			maxHealth = 2500.0,
			hardness = 0.85,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 900.0,
				turnSpeedPerRound = 4.5 * PI,
				
				maxAttackAngle = PI / 4,
				minAttackDistance = 0.0,
				maxAttackDistance = 400.0,
				softAttack = 800.0,
				hardAttack = 400.0,
				attackActionConsumed = 0.125,
				canMoveAfterAttacking = true
			)
		)
	),
	LAND_HEAVY_TANKS(
		"Heavy Tanks",
		120,
		LandPieceStats(
			maxHealth = 3500.0,
			hardness = 0.95,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 3 * PI,
				
				maxAttackAngle = PI / 4,
				minAttackDistance = 0.0,
				maxAttackDistance = 400.0,
				softAttack = 900.0,
				hardAttack = 700.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = true
			)
		)
	),
	LAND_ARTILLERY(
		"Artillery",
		60,
		LandPieceStats(
			maxHealth = 1000.0,
			hardness = 0.05,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 2 * PI,
				
				maxAttackAngle = PI / 6,
				minAttackDistance = 400.0,
				maxAttackDistance = 1200.0,
				softAttack = 1500.0,
				hardAttack = 500.0,
				attackActionConsumed = 0.5,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_ROCKET_ARTILLERY(
		"Rocket Artillery",
		120,
		LandPieceStats(
			maxHealth = 1500.0,
			hardness = 0.15,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 2 * PI,
				
				maxAttackAngle = PI / 4,
				minAttackDistance = 500.0,
				maxAttackDistance = 1500.0,
				softAttack = 2000.0,
				hardAttack = 300.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_ANTI_TANK(
		"Anti-Tank Guns",
		100,
		LandPieceStats(
			maxHealth = 2000.0,
			hardness = 0.10,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 2 * PI,
				
				maxAttackAngle = PI / 6,
				minAttackDistance = 0.0,
				maxAttackDistance = 500.0,
				softAttack = 500.0,
				hardAttack = 1500.0,
				attackActionConsumed = 0.375,
				canMoveAfterAttacking = false
			)
		)
	),
	
	// Space Battle pieces
	
	SPACE_FRIGATE(
		"Frigate",
		50,
		SpacePieceStats(
			maxHealth = 500.0,
			maxShield = 200.0,
			abilities = standardSpacePieceAbilities(
				moveSpeedPerRound = 400.0,
				turnSpeedPerRound = 1.4 * PI,
				
				minAttackAngle = PI / 4,
				maxAttackAngle = 3 * PI / 4,
				minAttackDistance = 0.0,
				maxAttackDistance = 300.0,
				attackStrength = 50.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false,
			)
		)
	),
	SPACE_LIGHT_CRUISER(
		"Light Cruiser",
		90,
		SpacePieceStats(
			maxHealth = 750.0,
			maxShield = 300.0,
			abilities = standardSpacePieceAbilities(
				moveSpeedPerRound = 350.0,
				turnSpeedPerRound = 1.3 * PI,
				
				minAttackAngle = PI / 4,
				maxAttackAngle = 3 * PI / 4,
				minAttackDistance = 0.0,
				maxAttackDistance = 350.0,
				attackStrength = 75.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false,
				
				extraAbilities = mapOf(
					"Fire Torpedoes" to Ability.AttackAreaSpace(
						minAngle = null,
						maxAngle = PI / 6,
						minDistance = 200.0,
						maxDistance = 800.0,
						aoeRadius = 150.0,
						attackPower = 50.0,
						actionConsumed = 0.5,
						canMoveAfterAttacking = true
					)
				)
			)
		)
	),
	SPACE_CRUISER(
		"Cruiser",
		120,
		SpacePieceStats(
			maxHealth = 1000.0,
			maxShield = 400.0,
			abilities = standardSpacePieceAbilities(
				moveSpeedPerRound = 300.0,
				turnSpeedPerRound = 1.2 * PI,
				
				minAttackAngle = PI / 3,
				maxAttackAngle = 2 * PI / 3,
				minAttackDistance = 0.0,
				maxAttackDistance = 350.0,
				attackStrength = 100.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false,
				
				extraAbilities = mapOf(
					"Fire Nova Lance" to Ability.AttackAreaSpace(
						minAngle = null,
						maxAngle = PI / 6,
						minDistance = 400.0,
						maxDistance = 1200.0,
						aoeRadius = 200.0,
						attackPower = 250.0,
						actionConsumed = 1.0,
						canMoveAfterAttacking = false
					)
				)
			)
		)
	),
	SPACE_BATTLESHIP(
		"Battleship",
		140,
		SpacePieceStats(
			maxHealth = 1500.0,
			maxShield = 750.0,
			abilities = standardSpacePieceAbilities(
				moveSpeedPerRound = 250.0,
				turnSpeedPerRound = 1.1 * PI,
				
				minAttackAngle = PI / 3,
				maxAttackAngle = 2 * PI / 3,
				minAttackDistance = 0.0,
				maxAttackDistance = 400.0,
				attackStrength = 200.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false,
				
				extraAbilities = mapOf(
					"Fire Torpedoes" to Ability.AttackAreaSpace(
						minAngle = null,
						maxAngle = PI / 6,
						minDistance = 300.0,
						maxDistance = 900.0,
						aoeRadius = 150.0,
						attackPower = 150.0,
						actionConsumed = 0.5,
						canMoveAfterAttacking = true
					)
				)
			)
		)
	),
	;
	
	val requiredBattleType: BattleType
		get() = when (stats) {
			is LandPieceStats -> BattleType.LAND_BATTLE
			is SpacePieceStats -> BattleType.SPACE_BATTLE
		}
	
	fun getImagePath(side: GameServerSide, identified: Boolean): String {
		return "uniticons/${if (side == Game.currentSide) "player" else "opponent"}/${
			if (identified) name.toLowerCase() else (when (requiredBattleType) {
				BattleType.LAND_BATTLE -> "land"
				BattleType.SPACE_BATTLE -> "space"
			} + "_unknown")
		}.png"
	}
	
	val imageWidth: Double
		get() = when (this) {
			LAND_INFANTRY -> 500.0
			LAND_ELITE_INFANTRY -> 500.0
			LAND_MEDIC -> 500.0
			LAND_CAVALRY -> 400.0
			LAND_ELITE_CAVALRY -> 400.0
			LAND_TANKS -> 500.0
			LAND_HEAVY_TANKS -> 500.0
			LAND_ARTILLERY -> 400.0
			LAND_ROCKET_ARTILLERY -> 400.0
			LAND_ANTI_TANK -> 400.0
			
			SPACE_FRIGATE -> 260.0
			SPACE_LIGHT_CRUISER -> 260.0
			SPACE_CRUISER -> 340.0
			SPACE_BATTLESHIP -> 480.0
		} * imageScaling
	
	val imageHeight: Double
		get() = when (this) {
			LAND_INFANTRY -> 300.0
			LAND_ELITE_INFANTRY -> 300.0
			LAND_MEDIC -> 300.0
			LAND_CAVALRY -> 400.0
			LAND_ELITE_CAVALRY -> 400.0
			LAND_TANKS -> 400.0
			LAND_HEAVY_TANKS -> 400.0
			LAND_ARTILLERY -> 300.0
			LAND_ROCKET_ARTILLERY -> 300.0
			LAND_ANTI_TANK -> 300.0
			
			SPACE_FRIGATE -> 640.0
			SPACE_LIGHT_CRUISER -> 750.0
			SPACE_CRUISER -> 960.0
			SPACE_BATTLESHIP -> 920.0
		} * imageScaling
	
	val imageRadius: Double
		get() = Vec2(imageWidth, imageHeight).magnitude / 2
	
	val imageScaling = when (requiredBattleType) {
		BattleType.LAND_BATTLE -> 0.1
		BattleType.SPACE_BATTLE -> 0.05
	}
}
