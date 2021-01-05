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
	
	@Serializable
	data class Attack(
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
					maxAngle
				),
				currentPiece.owner.other
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPiece ?: return
			val targetPiece = GameSessionData.currentSession!!.pieceById(pickRes.pieceId)
			
			val targetHardness = targetPiece.type.stats.hardness
			val targetSoftness = 1 - targetHardness
			
			val attackFacing = (targetPiece.location - currentPiece.location).angle
			
			val dotProduct = cos(attackFacing - targetPiece.facing)
			val softAttack = softAttackPower * targetSoftness * getFlankingMultiplier(dotProduct, SOFT_FLANK_WEIGHT)
			val hardAttack = hardAttackPower * targetHardness * getFlankingMultiplier(dotProduct, HARD_FLANK_WEIGHT)
			
			val totalAttack = softAttack + hardAttack
			val deltaHealth = totalAttack / targetPiece.type.stats.maxHealth
			targetPiece.health -= deltaHealth
			
			if (targetPiece.health <= 0.0)
				GameSessionData.currentSession!!.removeById(targetPiece.id)
			else
				GameSessionData.currentSession!!.markDirty(targetPiece.id)
			
			currentPiece.attacked = true
			
			if (canMoveAfterAttacking)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
		}
	}
	
	@Serializable
	data class Heal(
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
}

fun standardPieceAbilities(
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
	"Attack" to Ability.Attack(
		maxAttackAngle,
		minAttackDistance,
		maxAttackDistance,
		softAttack,
		hardAttack,
		attackActionConsumed,
		canMoveAfterAttacking
	)
) + extraAbilities

@Serializable
data class PieceStats(
	val maxHealth: Double,
	val hardness: Double,
	val abilities: Map<String, Ability>
)

@Serializable
enum class PieceType(
	val displayName: String,
	val pointCost: Int,
	val stats: PieceStats
) {
	INFANTRY(
		"Infantry",
		50,
		PieceStats(
			maxHealth = 2000.0,
			hardness = 0.0,
			abilities = standardPieceAbilities(
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
	ELITE_INFANTRY(
		"Stormtroopers",
		100,
		PieceStats(
			maxHealth = 3000.0,
			hardness = 0.1,
			abilities = standardPieceAbilities(
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
	MEDIC(
		"Combat Medic",
		150,
		PieceStats(
			maxHealth = 1500.0,
			hardness = 0.05,
			abilities = standardPieceAbilities(
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
					"Heal" to Ability.Heal(
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
	CAVALRY(
		"Cavalry",
		75,
		PieceStats(
			maxHealth = 1500.0,
			hardness = 0.0,
			abilities = standardPieceAbilities(
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
	ELITE_CAVALRY(
		"Winged Hussars",
		125,
		PieceStats(
			maxHealth = 2500.0,
			hardness = 0.1,
			abilities = standardPieceAbilities(
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
	TANKS(
		"Light Tanks",
		80,
		PieceStats(
			maxHealth = 2500.0,
			hardness = 0.85,
			abilities = standardPieceAbilities(
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
	HEAVY_TANKS(
		"Heavy Tanks",
		120,
		PieceStats(
			maxHealth = 3500.0,
			hardness = 0.95,
			abilities = standardPieceAbilities(
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
	ARTILLERY(
		"Artillery",
		60,
		PieceStats(
			maxHealth = 1000.0,
			hardness = 0.05,
			abilities = standardPieceAbilities(
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
	ROCKET_ARTILLERY(
		"Rocket Artillery",
		120,
		PieceStats(
			maxHealth = 1500.0,
			hardness = 0.15,
			abilities = standardPieceAbilities(
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
	ANTI_TANK(
		"Anti-Tank Guns",
		100,
		PieceStats(
			maxHealth = 2000.0,
			hardness = 0.10,
			abilities = standardPieceAbilities(
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
	);
	
	fun getImagePath(side: GameServerSide, identified: Boolean): String {
		return "uniticons/${if (side == Game.currentSide) "player" else "opponent"}/${if (identified) name.toLowerCase() else "unknown"}.png"
	}
	
	val imageWidth: Double
		get() = when (this) {
			INFANTRY -> 500.0
			ELITE_INFANTRY -> 500.0
			MEDIC -> 500.0
			CAVALRY -> 400.0
			ELITE_CAVALRY -> 400.0
			TANKS -> 500.0
			HEAVY_TANKS -> 500.0
			ARTILLERY -> 400.0
			ROCKET_ARTILLERY -> 400.0
			ANTI_TANK -> 400.0
		} * IMAGE_SCALING
	
	val imageHeight: Double
		get() = when (this) {
			INFANTRY -> 300.0
			ELITE_INFANTRY -> 300.0
			MEDIC -> 300.0
			CAVALRY -> 400.0
			ELITE_CAVALRY -> 400.0
			TANKS -> 400.0
			HEAVY_TANKS -> 400.0
			ARTILLERY -> 300.0
			ROCKET_ARTILLERY -> 300.0
			ANTI_TANK -> 300.0
		} * IMAGE_SCALING
	
	val imageRadius: Double
		get() = Vec2(imageWidth, imageHeight).magnitude / 2
	
	companion object {
		val IMAGE_SCALING = 0.1
	}
}
