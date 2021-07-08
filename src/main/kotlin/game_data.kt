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
	
	// Same for both battle types
	
	@Serializable
	data class Move(val distancePerAction: Double) : Ability() {
		private val ANGLE_BUFFER get() = PI / 8
		private val DISTANCE_BUFFER get() = 50.0
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.pieceRadius / distancePerAction < currentPiece.action
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val moveMult = currentPiece.currentTerrainBlob?.type?.stats?.moveSpeedMult ?: 1.0
			val moveRange = (distancePerAction * currentPiece.action + DISTANCE_BUFFER) * moveMult
			
			val pickReq = PickRequest.PickPosition(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius,
					moveRange,
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
			val newAction = (currentPiece.action - (dLocation.magnitude / moveMult) / distancePerAction).coerceAtLeast(0.0)
			
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
	data class ChargeHeavyWeapon(val actionConsumed: Double) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action > actionConsumed && !currentPiece.heavyWeaponCharged
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			currentPiece.action -= actionConsumed
			
			currentPiece.heavyWeaponCharged = true
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
		val requiresCharge: Boolean,
		val actionConsumed: Double,
		val canMoveAfterAttacking: Boolean
	) : Ability() {
		// Flanking multiplier is calculated as ((attacker.facingNormal dot target.facingNormal) + flankWeight) / (flankWeight - 1)
		// For the soft flank weight of 3, the flanking multiplier ranges from 1 at minimum to 2 at maximum.
		// For the hard flank weight of 9, the flanking multiplier ranges from 1 at minimum to 1.25 at maximum.
		// Higher flank weight reduces the effect of flanking. Numbers less than or equal to 1 should NEVER be used.
		
		private val SOFT_FLANK_WEIGHT get() = 3.0
		private val HARD_FLANK_WEIGHT get() = 9.0
		
		private fun getFlankingMultiplier(dotProduct: Double, flankWeight: Double): Double {
			return (dotProduct + flankWeight) / (flankWeight - 1)
		}
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.hasAttacked
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
			
			val terrainBlob = currentPiece.currentTerrainBlob
			val terrainStats = terrainBlob?.type?.stats as? TerrainStats.Land
			val softMult = terrainStats?.softAttackMult ?: 1.0
			val hardMult = terrainStats?.hardAttackMult ?: 1.0
			
			val dotProduct = cos(attackFacing - targetPiece.facing)
			val softAttack = softAttackPower * softMult * targetSoftness * getFlankingMultiplier(dotProduct, SOFT_FLANK_WEIGHT)
			val hardAttack = hardAttackPower * hardMult * targetHardness * getFlankingMultiplier(dotProduct, HARD_FLANK_WEIGHT)
			
			val totalMult = if (terrainStats?.isHill == true) {
				val hillAngle = (currentPiece.location - terrainBlob.center).angle
				val hillDot = cos(attackFacing - hillAngle)
				getFlankingMultiplier(hillDot, 3.0) - 0.5
			} else 1.0
			
			val totalAttack = (softAttack + hardAttack) * totalMult
			targetPiece.attack(totalAttack)
			
			currentPiece.hasAttacked = true
			if (requiresCharge)
				currentPiece.heavyWeaponCharged = false
			
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
			return currentPiece.action >= actionConsumed && !currentPiece.hasAttacked
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
			
			currentPiece.hasAttacked = true
			
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
		val maxAngle: Double?,
		val invertAngle: Boolean,
		val minDistance: Double,
		val maxDistance: Double,
		val attackPower: Double,
		val requiresCharge: Boolean,
		val actionConsumed: Double,
		val canMoveAfterAttacking: Boolean
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.hasAttacked && !currentPiece.isCloaked && (currentPiece.heavyWeaponCharged || !requiresCharge)
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
					currentPiece.facing.asAngle(flipX = invertAngle, flipY = invertAngle),
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
			
			val attackMult = (currentPiece.currentTerrainBlob?.type?.stats as? TerrainStats.Space)?.attackMult ?: 1.0
			
			val totalAttack = attackPower * attackMult * getFlankingMultiplier(dotProduct, SPACE_FLANK_WEIGHT)
			targetPiece.attack(totalAttack)
			
			currentPiece.hasAttacked = true
			if (requiresCharge)
				currentPiece.heavyWeaponCharged = false
			
			if (canMoveAfterAttacking)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
		}
	}
	
	@Serializable
	data class AttackAreaSpace(
		val minAngle: Double?,
		val maxAngle: Double?,
		val invertAngle: Boolean,
		val minDistance: Double,
		val maxDistance: Double,
		val aoeRadius: Double,
		val attackPower: Double,
		val requiresCharge: Boolean,
		val actionConsumed: Double,
		val canMoveAfterAttacking: Boolean
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.hasAttacked && (currentPiece.heavyWeaponCharged || !requiresCharge)
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
					currentPiece.facing.asAngle(flipX = invertAngle, flipY = invertAngle),
					minAngle,
					maxAngle
				),
				currentPiece.location,
				null,
				aoeRadius
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPosition ?: return
			
			val attackMult = (currentPiece.currentTerrainBlob?.type?.stats as? TerrainStats.Space)?.attackMult ?: 1.0
			
			val piecesInAoe = GameSessionData.currentSession!!.allPieces().filter { (it.location - pickRes.pos).magnitude <= aoeRadius }
			piecesInAoe.forEach { targetPiece ->
				targetPiece.type.stats as SpacePieceStats
				
				val attackFacing = (targetPiece.location - currentPiece.location).angle
				val dotProduct = cos(attackFacing - targetPiece.facing)
				
				val totalAttack = attackPower * attackMult * getFlankingMultiplier(dotProduct, SPACE_FLANK_WEIGHT)
				targetPiece.attack(totalAttack)
			}
			
			currentPiece.hasAttacked = true
			if (requiresCharge)
				currentPiece.heavyWeaponCharged = false
			
			if (canMoveAfterAttacking)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
		}
	}
	
	@Serializable
	data class Cloak(val actionConsumed: Double) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action > actionConsumed && !currentPiece.isCloaked
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			currentPiece.action -= actionConsumed
			
			currentPiece.isCloaked = true
		}
	}
	
	@Serializable
	data class Decloak(val actionConsumed: Double) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action > actionConsumed && currentPiece.isCloaked
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			currentPiece.action -= actionConsumed
			
			currentPiece.isCloaked = false
			currentPiece.isCloakRevealed = false
		}
	}
	
	@Serializable
	data class RevealCloak(val revealRange: Double, val actionConsumed: Double) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action > actionConsumed && currentPiece.isCloaked
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			currentPiece.action -= actionConsumed
			
			currentPiece.isCloaked = false
			
			GameSessionData.currentSession!!.allPiecesWithOwner(currentPiece.owner.other).forEach { otherPiece ->
				val inRange = (currentPiece.location - otherPiece.location).magnitude < revealRange
				if (inRange && otherPiece.isCloaked)
					otherPiece.isCloakRevealed = true
			}
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
		false,
		attackActionConsumed,
		canMoveAfterAttacking
	)
) + extraAbilities

fun broadsideSpacePieceAbilities(
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
		false,
		minAttackDistance,
		maxAttackDistance,
		attackStrength,
		false,
		attackActionConsumed,
		canMoveAfterAttacking
	)
) + extraAbilities

fun arrayedSpacePieceAbilities(
	moveSpeedPerRound: Double,
	turnSpeedPerRound: Double,
	
	maxArrayAngle: Double,
	maxArrayDistance: Double,
	arrayStrength: Double,
	arrayActionConsumed: Double,
	canMoveAfterFiringArray: Boolean,
	
	maxTorpedoAngle: Double,
	maxTorpedoDistance: Double,
	torpedoStrength: Double,
	torpedoLoadActionConsumed: Double,
	torpedoFireActionConsumed: Double,
	canMoveAfterFiringTorpedo: Boolean,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Move" to Ability.Move(moveSpeedPerRound),
	"Turn" to Ability.Rotate(turnSpeedPerRound),
	"Fire Fore Arrays" to Ability.AttackSpace(
		null,
		maxArrayAngle,
		false,
		0.0,
		maxArrayDistance,
		arrayStrength,
		false,
		arrayActionConsumed,
		canMoveAfterFiringArray
	),
	"Fire Aft Arrays" to Ability.AttackSpace(
		null,
		maxArrayAngle,
		true,
		0.0,
		maxArrayDistance,
		arrayStrength,
		false,
		arrayActionConsumed,
		canMoveAfterFiringArray
	),
) + (if (maxArrayAngle > PI / 2) {
	mapOf(
		"Fire Both Arrays" to Ability.AttackSpace(
			PI - maxArrayAngle,
			maxArrayAngle,
			false,
			0.0,
			maxArrayDistance,
			arrayStrength * 2,
			false,
			arrayActionConsumed * 1.5,
			canMoveAfterFiringArray
		)
	)
} else emptyMap()) + mapOf(
	"Load Torpedo" to Ability.ChargeHeavyWeapon(
		torpedoLoadActionConsumed
	),
	"Fire Torpedo" to Ability.AttackSpace(
		null,
		maxTorpedoAngle,
		false,
		0.0,
		maxTorpedoDistance,
		torpedoStrength,
		true,
		torpedoFireActionConsumed,
		canMoveAfterFiringTorpedo
	)
) + extraAbilities

fun cannonedSpacePieceAbilities(
	moveSpeedPerRound: Double,
	turnSpeedPerRound: Double,
	
	maxCannonAngle: Double,
	maxCannonDistance: Double,
	cannonStrength: Double,
	cannonActionConsumed: Double,
	canMoveAfterFiringCannons: Boolean,
	
	maxTurretDistance: Double,
	turretStrength: Double,
	turretActionConsumed: Double,
	canMoveAfterFiringTurret: Boolean,
	
	maxTorpedoAngle: Double,
	maxTorpedoDistance: Double,
	torpedoStrength: Double,
	torpedoLoadActionConsumed: Double,
	torpedoFireActionConsumed: Double,
	canMoveAfterFiringTorpedo: Boolean,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Move" to Ability.Move(moveSpeedPerRound),
	"Turn" to Ability.Rotate(turnSpeedPerRound),
	"Fire Cannons" to Ability.AttackSpace(
		null,
		maxCannonAngle,
		false,
		0.0,
		maxCannonDistance,
		cannonStrength,
		false,
		cannonActionConsumed,
		canMoveAfterFiringCannons
	),
	"Fire Turrets" to Ability.AttackSpace(
		null,
		null,
		false,
		0.0,
		maxTurretDistance,
		turretStrength,
		false,
		turretActionConsumed,
		canMoveAfterFiringTurret
	),
	"Load Torpedo" to Ability.ChargeHeavyWeapon(
		torpedoLoadActionConsumed
	),
	"Fire Torpedo" to Ability.AttackSpace(
		null,
		maxTorpedoAngle,
		false,
		0.0,
		maxTorpedoDistance,
		torpedoStrength,
		true,
		torpedoFireActionConsumed,
		canMoveAfterFiringTorpedo
	)
) + extraAbilities

fun arrayedAndCannonedSpacePieceAbilities(
	moveSpeedPerRound: Double,
	turnSpeedPerRound: Double,
	
	maxArrayAngle: Double,
	maxArrayDistance: Double,
	arrayStrength: Double,
	arrayActionConsumed: Double,
	canMoveAfterFiringArray: Boolean,
	
	maxCannonAngle: Double,
	maxCannonDistance: Double,
	cannonStrength: Double,
	cannonActionConsumed: Double,
	canMoveAfterFiringCannons: Boolean,
	
	maxTorpedoAngle: Double,
	maxTorpedoDistance: Double,
	torpedoStrength: Double,
	torpedoLoadActionConsumed: Double,
	torpedoFireActionConsumed: Double,
	canMoveAfterFiringTorpedo: Boolean,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Move" to Ability.Move(moveSpeedPerRound),
	"Turn" to Ability.Rotate(turnSpeedPerRound),
	"Fire Fore Arrays" to Ability.AttackSpace(
		null,
		maxArrayAngle,
		false,
		0.0,
		maxArrayDistance,
		arrayStrength,
		false,
		arrayActionConsumed,
		canMoveAfterFiringArray
	),
	"Fire Aft Arrays" to Ability.AttackSpace(
		null,
		maxArrayAngle,
		true,
		0.0,
		maxArrayDistance,
		arrayStrength,
		false,
		arrayActionConsumed,
		canMoveAfterFiringArray
	),
) + (if (maxArrayAngle > PI / 2) {
	mapOf(
		"Fire Both Arrays" to Ability.AttackSpace(
			PI - maxArrayAngle,
			maxArrayAngle,
			false,
			0.0,
			maxArrayDistance,
			arrayStrength * 2,
			false,
			arrayActionConsumed * 1.5,
			canMoveAfterFiringArray
		)
	)
} else emptyMap()) + mapOf(
	"Fire Cannons" to Ability.AttackSpace(
		null,
		maxCannonAngle,
		false,
		0.0,
		maxCannonDistance,
		cannonStrength,
		false,
		cannonActionConsumed,
		canMoveAfterFiringCannons
	),
	"Load Torpedo" to Ability.ChargeHeavyWeapon(
		torpedoLoadActionConsumed
	),
	"Fire Torpedo" to Ability.AttackSpace(
		null,
		maxTorpedoAngle,
		false,
		0.0,
		maxTorpedoDistance,
		torpedoStrength,
		true,
		torpedoFireActionConsumed,
		canMoveAfterFiringTorpedo
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

@Serializable
enum class BattleType(
	val displayName: String,
	val defaultMapColor: String
) {
	LAND_BATTLE("Land Battle", "#194"),
	SPACE_BATTLE("Space Battle", "#444");
	
	val usesSkins: Boolean
		get() = BattleFactionSkin.values().any { it.forBattleType == this }
}

@Serializable
enum class BattleFactionSkin(
	val displayName: String,
	val forBattleType: BattleType
) {
	EMPIRE("Imperial Navy", BattleType.SPACE_BATTLE),
	SPACE_MARINES("Space Marine Corps", BattleType.SPACE_BATTLE),
	STAR_FLEET("Star Fleet", BattleType.SPACE_BATTLE),
	KDF("K.D.F.", BattleType.SPACE_BATTLE)
}

@Serializable
enum class PieceType(
	val displayName: String,
	val pointCost: Int,
	val stats: PieceStats,
	val factionSkin: BattleFactionSkin?
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
		),
		null
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
		),
		null
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
		),
		null
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
		),
		null
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
		),
		null
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
		),
		null
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
		),
		null
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
		),
		null
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
		),
		null
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
		),
		null
	),
	
	// Space Battle pieces
	
	SPACE_FRIGATE(
		"Frigate",
		40,
		SpacePieceStats(
			maxHealth = 750.0,
			maxShield = 400.0,
			abilities = broadsideSpacePieceAbilities(
				moveSpeedPerRound = 400.0,
				turnSpeedPerRound = 1.4 * PI,
				
				minAttackAngle = PI / 4,
				maxAttackAngle = 3 * PI / 4,
				minAttackDistance = 0.0,
				maxAttackDistance = 300.0,
				attackStrength = 50.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false,
				
				extraAbilities = mapOf(
					"Reveal Cloaked Ships" to Ability.RevealCloak(250.0, 0.4)
				)
			)
		),
		BattleFactionSkin.EMPIRE
	),
	SPACE_LIGHT_CRUISER(
		"Light Cruiser",
		90,
		SpacePieceStats(
			maxHealth = 875.0,
			maxShield = 500.0,
			abilities = broadsideSpacePieceAbilities(
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
						invertAngle = false,
						minDistance = 200.0,
						maxDistance = 800.0,
						aoeRadius = 150.0,
						attackPower = 50.0,
						requiresCharge = false,
						actionConsumed = 0.5,
						canMoveAfterAttacking = true
					)
				)
			)
		),
		BattleFactionSkin.EMPIRE
	),
	SPACE_CRUISER(
		"Cruiser",
		160,
		SpacePieceStats(
			maxHealth = 1000.0,
			maxShield = 600.0,
			abilities = broadsideSpacePieceAbilities(
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
					"Charge Plasma Lance" to Ability.ChargeHeavyWeapon(
						actionConsumed = 0.5
					),
					"Fire Plasma Lance" to Ability.AttackAreaSpace(
						minAngle = null,
						maxAngle = PI / 8,
						invertAngle = false,
						minDistance = 200.0,
						maxDistance = 800.0,
						aoeRadius = 100.0,
						attackPower = 150.0,
						requiresCharge = true,
						actionConsumed = 1.0,
						canMoveAfterAttacking = false
					)
				)
			)
		),
		BattleFactionSkin.EMPIRE
	),
	SPACE_BATTLESHIP(
		"Battleship",
		250,
		SpacePieceStats(
			maxHealth = 1250.0,
			maxShield = 800.0,
			abilities = broadsideSpacePieceAbilities(
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
						invertAngle = false,
						minDistance = 300.0,
						maxDistance = 900.0,
						aoeRadius = 150.0,
						attackPower = 150.0,
						requiresCharge = false,
						actionConsumed = 0.5,
						canMoveAfterAttacking = true
					)
				)
			)
		),
		BattleFactionSkin.EMPIRE
	),
	
	SPACE_ESCORT(
		"Escort",
		40,
		SpacePieceStats(
			maxHealth = 750.0,
			maxShield = 400.0,
			abilities = broadsideSpacePieceAbilities(
				moveSpeedPerRound = 400.0,
				turnSpeedPerRound = 1.4 * PI,
				
				minAttackAngle = PI / 4,
				maxAttackAngle = 3 * PI / 4,
				minAttackDistance = 0.0,
				maxAttackDistance = 300.0,
				attackStrength = 50.0,
				attackActionConsumed = 0.25,
				canMoveAfterAttacking = false,
				
				extraAbilities = mapOf(
					"Fire Torpedoes" to Ability.AttackAreaSpace(
						minAngle = null,
						maxAngle = PI / 8,
						invertAngle = false,
						minDistance = 200.0,
						maxDistance = 800.0,
						aoeRadius = 150.0,
						attackPower = 50.0,
						requiresCharge = false,
						actionConsumed = 0.5,
						canMoveAfterAttacking = true
					)
				)
			)
		),
		BattleFactionSkin.SPACE_MARINES
	),
	SPACE_STRIKE_CRUISER(
		"Strike Cruiser",
		90,
		SpacePieceStats(
			maxHealth = 875.0,
			maxShield = 500.0,
			abilities = broadsideSpacePieceAbilities(
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
						maxAngle = PI / 8,
						invertAngle = false,
						minDistance = 200.0,
						maxDistance = 800.0,
						aoeRadius = 150.0,
						attackPower = 50.0,
						requiresCharge = false,
						actionConsumed = 0.5,
						canMoveAfterAttacking = true
					),
					"Reveal Cloaked Ships" to Ability.RevealCloak(250.0, 0.4)
				)
			)
		),
		BattleFactionSkin.SPACE_MARINES
	),
	SPACE_BATTLE_BARGE(
		"Battle Barge",
		160,
		SpacePieceStats(
			maxHealth = 1000.0,
			maxShield = 600.0,
			abilities = broadsideSpacePieceAbilities(
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
					"Fire Torpedoes" to Ability.AttackAreaSpace(
						minAngle = null,
						maxAngle = PI / 8,
						invertAngle = false,
						minDistance = 300.0,
						maxDistance = 900.0,
						aoeRadius = 150.0,
						attackPower = 150.0,
						requiresCharge = false,
						actionConsumed = 0.5,
						canMoveAfterAttacking = true
					)
				)
			)
		),
		BattleFactionSkin.SPACE_MARINES
	),
	SPACE_CAPITAL_SHIP(
		"Capital Ship",
		250,
		SpacePieceStats(
			maxHealth = 1250.0,
			maxShield = 800.0,
			abilities = broadsideSpacePieceAbilities(
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
						maxAngle = PI / 8,
						invertAngle = false,
						minDistance = 300.0,
						maxDistance = 900.0,
						aoeRadius = 150.0,
						attackPower = 150.0,
						requiresCharge = false,
						actionConsumed = 0.5,
						canMoveAfterAttacking = true
					)
				)
			)
		),
		BattleFactionSkin.SPACE_MARINES
	),
	
	SPACE_UTILITY_CRUISER(
		"Utility Cruiser",
		40,
		SpacePieceStats(
			maxHealth = 750.0,
			maxShield = 400.0,
			abilities = arrayedSpacePieceAbilities(
				moveSpeedPerRound = 400.0,
				turnSpeedPerRound = 1.4 * PI,
				
				maxArrayAngle = 2 * PI / 3,
				maxArrayDistance = 300.0,
				arrayStrength = 35.0,
				arrayActionConsumed = 0.25,
				canMoveAfterFiringArray = false,
				
				maxTorpedoAngle = PI / 4,
				maxTorpedoDistance = 400.0,
				torpedoStrength = 85.0,
				torpedoLoadActionConsumed = 0.501,
				torpedoFireActionConsumed = 0.501,
				canMoveAfterFiringTorpedo = false,
				
				extraAbilities = mapOf(
					"Reveal Cloaked Ships" to Ability.RevealCloak(250.0, 0.4)
				)
			)
		),
		BattleFactionSkin.STAR_FLEET
	),
	SPACE_WARSHIP(
		"Warship",
		90,
		SpacePieceStats(
			maxHealth = 875.0,
			maxShield = 500.0,
			abilities = cannonedSpacePieceAbilities(
				moveSpeedPerRound = 640.0,
				turnSpeedPerRound = 3 * PI,
				
				maxCannonAngle = PI / 6,
				maxCannonDistance = 400.0,
				cannonStrength = 125.0,
				cannonActionConsumed = 0.35,
				canMoveAfterFiringCannons = true,
				
				maxTurretDistance = 300.0,
				turretStrength = 65.0,
				turretActionConsumed = 0.25,
				canMoveAfterFiringTurret = true,
				
				maxTorpedoAngle = PI / 4,
				maxTorpedoDistance = 400.0,
				torpedoStrength = 100.0,
				torpedoLoadActionConsumed = 0.501,
				torpedoFireActionConsumed = 0.501,
				canMoveAfterFiringTorpedo = false,
				
				extraAbilities = mapOf(
					"Cloak" to Ability.Cloak(0.6),
					"Decloak" to Ability.Decloak(0.3),
				)
			)
		),
		BattleFactionSkin.STAR_FLEET
	),
	SPACE_ADVANCED_CRUISER(
		"Advanced Cruiser",
		160,
		SpacePieceStats(
			maxHealth = 1000.0,
			maxShield = 600.0,
			abilities = arrayedSpacePieceAbilities(
				moveSpeedPerRound = 300.0,
				turnSpeedPerRound = 1.2 * PI,
				
				maxArrayAngle = 2 * PI / 3,
				maxArrayDistance = 350.0,
				arrayStrength = 85.0,
				arrayActionConsumed = 0.25,
				canMoveAfterFiringArray = false,
				
				maxTorpedoAngle = PI / 4,
				maxTorpedoDistance = 400.0,
				torpedoStrength = 125.0,
				torpedoLoadActionConsumed = 0.501,
				torpedoFireActionConsumed = 0.501,
				canMoveAfterFiringTorpedo = false
			)
		),
		BattleFactionSkin.STAR_FLEET
	),
	SPACE_EXPLORATION_CRUISER(
		"Exploration Cruiser",
		250,
		SpacePieceStats(
			maxHealth = 1250.0,
			maxShield = 800.0,
			abilities = arrayedSpacePieceAbilities(
				moveSpeedPerRound = 250.0,
				turnSpeedPerRound = 1.1 * PI,
				
				maxArrayAngle = 2 * PI / 3,
				maxArrayDistance = 350.0,
				arrayStrength = 175.0,
				arrayActionConsumed = 0.25,
				canMoveAfterFiringArray = false,
				
				maxTorpedoAngle = PI / 4,
				maxTorpedoDistance = 400.0,
				torpedoStrength = 125.0,
				torpedoLoadActionConsumed = 0.501,
				torpedoFireActionConsumed = 0.501,
				canMoveAfterFiringTorpedo = false
			)
		),
		BattleFactionSkin.STAR_FLEET
	),
	
	SPACE_BIRD_OF_PREY(
		"Bird-of-Prey",
		40,
		SpacePieceStats(
			maxHealth = 750.0,
			maxShield = 400.0,
			abilities = cannonedSpacePieceAbilities(
				moveSpeedPerRound = 640.0,
				turnSpeedPerRound = 3 * PI,
				
				maxCannonAngle = PI / 6,
				maxCannonDistance = 400.0,
				cannonStrength = 125.0,
				cannonActionConsumed = 0.35,
				canMoveAfterFiringCannons = true,
				
				maxTurretDistance = 300.0,
				turretStrength = 65.0,
				turretActionConsumed = 0.25,
				canMoveAfterFiringTurret = true,
				
				maxTorpedoAngle = PI / 4,
				maxTorpedoDistance = 400.0,
				torpedoStrength = 100.0,
				torpedoLoadActionConsumed = 0.501,
				torpedoFireActionConsumed = 0.501,
				canMoveAfterFiringTorpedo = false,
				
				extraAbilities = mapOf(
					"Cloak" to Ability.Cloak(0.6),
					"Decloak" to Ability.Decloak(0.3),
				)
			)
		),
		BattleFactionSkin.KDF
	),
	SPACE_RAPTOR(
		"Raptor",
		90,
		SpacePieceStats(
			maxHealth = 875.0,
			maxShield = 500.0,
			abilities = cannonedSpacePieceAbilities(
				moveSpeedPerRound = 480.0,
				turnSpeedPerRound = 2.5 * PI,
				
				maxCannonAngle = PI / 6,
				maxCannonDistance = 300.0,
				cannonStrength = 175.0,
				cannonActionConsumed = 0.35,
				canMoveAfterFiringCannons = true,
				
				maxTurretDistance = 200.0,
				turretStrength = 105.0,
				turretActionConsumed = 0.25,
				canMoveAfterFiringTurret = true,
				
				maxTorpedoAngle = PI / 4,
				maxTorpedoDistance = 400.0,
				torpedoStrength = 100.0,
				torpedoLoadActionConsumed = 0.501,
				torpedoFireActionConsumed = 0.501,
				canMoveAfterFiringTorpedo = false,
				
				extraAbilities = mapOf(
					"Cloak" to Ability.Cloak(0.6),
					"Decloak" to Ability.Decloak(0.3),
				)
			)
		),
		BattleFactionSkin.KDF
	),
	SPACE_BATTLECRUISER(
		"Battlecruiser",
		160,
		SpacePieceStats(
			maxHealth = 1000.0,
			maxShield = 600.0,
			abilities = arrayedAndCannonedSpacePieceAbilities(
				moveSpeedPerRound = 360.0,
				turnSpeedPerRound = 2 * PI,
				
				maxArrayAngle = 2 * PI / 3,
				maxArrayDistance = 350.0,
				arrayStrength = 85.0,
				arrayActionConsumed = 0.25,
				canMoveAfterFiringArray = false,
				
				maxCannonAngle = PI / 6,
				maxCannonDistance = 300.0,
				cannonStrength = 155.0,
				cannonActionConsumed = 0.35,
				canMoveAfterFiringCannons = true,
				
				maxTorpedoAngle = PI / 4,
				maxTorpedoDistance = 400.0,
				torpedoStrength = 125.0,
				torpedoLoadActionConsumed = 0.501,
				torpedoFireActionConsumed = 0.501,
				canMoveAfterFiringTorpedo = false,
				
				extraAbilities = mapOf(
					"Cloak" to Ability.Cloak(0.6),
					"Decloak" to Ability.Decloak(0.3),
				)
			)
		),
		BattleFactionSkin.KDF
	),
	SPACE_HEAVY_BATTLECRUISER(
		"Heavy Battlecruiser",
		250,
		SpacePieceStats(
			maxHealth = 1250.0,
			maxShield = 800.0,
			abilities = arrayedAndCannonedSpacePieceAbilities(
				moveSpeedPerRound = 270.0,
				turnSpeedPerRound = 1.75 * PI,
				
				maxArrayAngle = 2 * PI / 3,
				maxArrayDistance = 350.0,
				arrayStrength = 125.0,
				arrayActionConsumed = 0.25,
				canMoveAfterFiringArray = false,
				
				maxCannonAngle = PI / 6,
				maxCannonDistance = 300.0,
				cannonStrength = 145.0,
				cannonActionConsumed = 0.35,
				canMoveAfterFiringCannons = true,
				
				maxTorpedoAngle = PI / 4,
				maxTorpedoDistance = 400.0,
				torpedoStrength = 125.0,
				torpedoLoadActionConsumed = 0.501,
				torpedoFireActionConsumed = 0.501,
				canMoveAfterFiringTorpedo = false,
				
				extraAbilities = mapOf(
					"Cloak" to Ability.Cloak(0.6),
					"Decloak" to Ability.Decloak(0.3),
				)
			)
		),
		BattleFactionSkin.KDF
	),
	;
	
	val requiredBattleType: BattleType
		get() = when (stats) {
			is LandPieceStats -> BattleType.LAND_BATTLE
			is SpacePieceStats -> BattleType.SPACE_BATTLE
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
			
			SPACE_ESCORT -> 260.0
			SPACE_STRIKE_CRUISER -> 260.0
			SPACE_BATTLE_BARGE -> 340.0
			SPACE_CAPITAL_SHIP -> 480.0
			
			SPACE_UTILITY_CRUISER -> 400.0
			SPACE_WARSHIP -> 500.0
			SPACE_ADVANCED_CRUISER -> 400.0
			SPACE_EXPLORATION_CRUISER -> 600.0
			
			SPACE_BIRD_OF_PREY -> 700.0
			SPACE_RAPTOR -> 750.0
			SPACE_BATTLECRUISER -> 750.0
			SPACE_HEAVY_BATTLECRUISER -> 710.0
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
			
			SPACE_ESCORT -> 600.0
			SPACE_STRIKE_CRUISER -> 660.0
			SPACE_BATTLE_BARGE -> 820.0
			SPACE_CAPITAL_SHIP -> 950.0
			
			SPACE_UTILITY_CRUISER -> 620.0
			SPACE_WARSHIP -> 570.0
			SPACE_ADVANCED_CRUISER -> 960.0
			SPACE_EXPLORATION_CRUISER -> 680.0
			
			SPACE_BIRD_OF_PREY -> 640.0
			SPACE_RAPTOR -> 780.0
			SPACE_BATTLECRUISER -> 750.0
			SPACE_HEAVY_BATTLECRUISER -> 730.0
		} * imageScaling
	
	val imageRadius: Double
		get() = Vec2(imageWidth, imageHeight).magnitude / 2
	
	val imageScaling: Double
		get() = when (requiredBattleType) {
			BattleType.LAND_BATTLE -> 0.1
			BattleType.SPACE_BATTLE -> 0.05
		}
}
