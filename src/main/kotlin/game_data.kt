import kotlinx.browser.document
import kotlinx.serialization.Serializable
import org.w3c.dom.HTMLAnchorElement
import kotlin.math.*

@Suppress("DuplicatedCode")
@Serializable
sealed class Ability {
	protected val GamePiece.player: Player
		get() = Player.getPlayer(owner)
	
	abstract fun canUse(currentPiece: GamePiece): Boolean
	abstract suspend fun use(currentPiece: GamePiece)
	
	// Land unit abilities
	
	@Serializable
	data class Move(val distancePerAction: Double) : Ability() {
		private val angleBuffer get() = PI / 8
		private val distanceBuffer get() = 50.0
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.pieceRadius / distancePerAction < currentPiece.action
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val terrainBlob = currentPiece.currentTerrainBlob
			val terrainStats = terrainBlob?.type?.stats
			
			// Hills change speed multiplier depending on the direction of the slope
			// When a unit is facing parallel to (towards or away from) the peak, then the speed mult is 0.6
			// When a unit is facing perpendicular to the peak, then the speed multiplier is 0.9
			val hillMult = if (terrainStats != null && terrainStats.isHill) {
				val hillAngle = (currentPiece.location - terrainBlob.center).angle
				val hillCross = sin(currentPiece.facing - hillAngle)
				val hillX2 = hillCross * hillCross
				(hillX2 * 0.3) + 0.6
			} else 1.0
			
			val terrainMult = currentPiece.currentTerrainBlob?.type?.stats?.moveSpeedMult ?: 1.0
			
			val moveMult = hillMult * terrainMult
			val moveRange = (distancePerAction * currentPiece.action + distanceBuffer) * moveMult
			
			val pickReq = PickRequest.PickPosition(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius,
					currentPiece.pieceRadius + moveRange,
					currentPiece.facing,
					null,
					angleBuffer
				),
				currentPiece.location,
				currentPiece.pieceRadius,
				currentPiece.type.layer,
				TerrainRequirement.DEFAULT,
				currentPiece.pieceRadius
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPosition ?: return
			
			val newLocation = pickRes.pos
			val dLocation = newLocation - currentPiece.location
			val newFacing = dLocation.angle
			val newAction = (currentPiece.action - (dLocation.magnitude / moveMult) / distancePerAction).coerceAtLeast(0.0)
			
			currentPiece.location = newLocation
			currentPiece.facing = newFacing
			currentPiece.action = newAction
		}
	}
	
	@Serializable
	data class Rotate(val anglePerAction: Double) : Ability() {
		private val angleBuffer get() = PI / 12
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action > 0.0
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickAngle(
				currentPiece.location,
				currentPiece.facing,
				currentPiece.action * anglePerAction + angleBuffer,
				currentPiece.pieceRadius + 10
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedAngle ?: return
			
			val newLocation = currentPiece.location
			val newFacing = pickRes.newAngle
			val deltaAngle = abs(currentPiece.facing - newFacing)
			val newAction = (currentPiece.action - deltaAngle / anglePerAction).coerceAtLeast(0.0)
			
			currentPiece.location = newLocation // prevent the Undo Move from messing up
			currentPiece.facing = newFacing
			currentPiece.action = newAction
		}
	}
	
	object UndoMove : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.canUndoMove
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			currentPiece.undoMove()
		}
	}
	
	@Serializable
	data class AttackLand(
		val maxAngle: Double,
		val minDistance: Double,
		val maxDistance: Double,
		val softAttackPower: Double,
		val hardAttackPower: Double,
		val actionConsumed: Double,
		val requiresLoading: Boolean,
		val canMoveAfterAttacking: Boolean
	) : Ability() {
		// Flanking multiplier is calculated as ((attacker.facingNormal dot target.facingNormal) + flankWeight) / (flankWeight - 1)
		// For the soft flank weight of 3, the flanking multiplier ranges from 1 at minimum to 2 at maximum.
		// For the hard flank weight of 9, the flanking multiplier ranges from 1 at minimum to 1.25 at maximum.
		// Higher flank weight reduces the effect of flanking. Numbers less than or equal to 1 should NEVER be used.
		
		private val softFlankWeight get() = 3.0
		private val hardFlankWeight get() = 9.0
		
		private fun getFlankingMultiplier(dotProduct: Double, flankWeight: Double): Double {
			return (dotProduct + flankWeight) / (flankWeight - 1)
		}
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.hasAttacked && (currentPiece.heavyWeaponCharged || !requiresLoading)
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
				currentPiece.owner.other,
				PieceLayer.LAND
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPiece ?: return
			val targetPiece = GameSessionData.currentSession!!.pieceById(pickRes.pieceId)
			
			val targetHardness = targetPiece.type.stats.hardness
			val targetSoftness = 1 - targetHardness
			
			val attackFacing = (targetPiece.location - currentPiece.location).angle
			
			val terrainBlob = currentPiece.currentTerrainBlob
			val terrainStats = terrainBlob?.type?.stats
			val softMult = terrainStats?.softAttackMult ?: 1.0
			val hardMult = terrainStats?.hardAttackMult ?: 1.0
			
			val dotProduct = cos(attackFacing - targetPiece.facing)
			val softAttack = softAttackPower * softMult * targetSoftness * getFlankingMultiplier(dotProduct, softFlankWeight)
			val hardAttack = hardAttackPower * hardMult * targetHardness * getFlankingMultiplier(dotProduct, hardFlankWeight)
			
			// Hills change attack power depending on uphill or downhill
			// Attacking uphill has a damage multiplier of 0.5
			// Attacking downhill has a damage mult of 1.5
			val totalMult = if (terrainStats?.isHill == true) {
				val hillAngle = (currentPiece.location - terrainBlob.center).angle
				val hillDot = cos(attackFacing - hillAngle)
				
				// A flanking weight of 3.0 results in a range of 1.0 to 2.0
				// Subtract 0.5 to get a range of 0.5 to 1.5
				getFlankingMultiplier(hillDot, 3.0) - 0.5
			} else 1.0
			
			val totalAttack = (softAttack + hardAttack) * totalMult
			targetPiece.attack(totalAttack, DamageSource.Piece(currentPiece))
			currentPiece.hasAttacked = true
			
			if (canMoveAfterAttacking)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
			
			currentPiece.lockUndo()
		}
	}
	
	@Serializable
	data class HealLand(
		val maxAngle: Double,
		val minDistance: Double,
		val maxDistance: Double,
		val healthRestored: Double,
		val actionConsumed: Double,
		val requiresLoading: Boolean,
		val canMoveAfterHealing: Boolean
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.hasAttacked && (currentPiece.heavyWeaponCharged || !requiresLoading)
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
				currentPiece.owner,
				PieceLayer.LAND
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
			
			currentPiece.lockUndo()
		}
	}
	
	@Serializable
	data class LoadHeavyWeapon(val actionConsumed: Double) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action > actionConsumed && !currentPiece.heavyWeaponCharged
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			currentPiece.action -= actionConsumed
			
			currentPiece.hasAttacked = true
			currentPiece.heavyWeaponCharged = true
			
			currentPiece.lockUndo()
		}
	}
	
	// Anti-air unit abilities
	
	class LandAttackAir(
		val maxAngle: Double,
		val minDistance: Double,
		val maxDistance: Double,
		val attackPower: Double,
		val actionConsumed: Double,
		val requiresLoading: Boolean,
		val canMoveAfterAttacking: Boolean
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action >= actionConsumed && !currentPiece.hasAttacked && (currentPiece.heavyWeaponCharged || !requiresLoading)
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
				currentPiece.owner.other,
				PieceLayer.AIR
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPiece ?: return
			val targetPiece = GameSessionData.currentSession!!.pieceById(pickRes.pieceId)
			
			val totalAttack = attackPower * targetPiece.airTargetedMult
			targetPiece.attack(totalAttack, DamageSource.Piece(currentPiece))
			currentPiece.hasAttacked = true
			
			if (canMoveAfterAttacking)
				currentPiece.action -= actionConsumed
			else
				currentPiece.action = 0.0
			
			currentPiece.lockUndo()
		}
	}
	
	// Air unit abilities
	
	class TakeOff(
		val minDistance: Double,
		val maxDistance: Double,
		val maxAngle: Double,
		val minimumAction: Double,
		private val airUnitEquiv: String
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.currentTerrainBlob == null && currentPiece.action > minimumAction
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val turnsInto = PieceType.valueOf(airUnitEquiv)
			
			currentPiece.heavyWeaponCharged = true
			
			val pickReq = PickRequest.PickPosition(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius + minDistance,
					currentPiece.pieceRadius + maxDistance,
					currentPiece.facing,
					null,
					maxAngle
				),
				currentPiece.location,
				turnsInto.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE,
				PieceLayer.AIR,
				TerrainRequirement.ALLOW_ANY,
				turnsInto.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPosition ?: return
			
			val newLocation = pickRes.pos
			val dLocation = newLocation - currentPiece.location
			val newFacing = dLocation.angle
			
			currentPiece.location = newLocation
			currentPiece.facing = newFacing
			currentPiece.action = 0.0
			currentPiece.type = turnsInto
			
			currentPiece.lockUndo()
		}
	}
	
	class Fly(
		val maxAngle: Double,
		val minDistance: Double,
		val distancePerAction: Double
	) : Ability() {
		private val distanceBuffer get() = 50.0
		
		override fun canUse(currentPiece: GamePiece): Boolean {
			return (currentPiece.pieceRadius + minDistance) / distancePerAction < currentPiece.action
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val moveRange = distancePerAction * currentPiece.action + distanceBuffer
			
			val pickReq = PickRequest.PickPosition(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius + minDistance,
					currentPiece.pieceRadius + moveRange,
					currentPiece.facing,
					null,
					maxAngle
				),
				currentPiece.location,
				currentPiece.pieceRadius,
				currentPiece.type.layer,
				TerrainRequirement.ALLOW_ANY,
				currentPiece.pieceRadius
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPosition ?: return
			
			val newLocation = pickRes.pos
			val dLocation = newLocation - currentPiece.location
			val newFacing = (dLocation.angle * 2) - currentPiece.facing
			val newAction = (currentPiece.action - dLocation.magnitude / distancePerAction).coerceAtLeast(0.0)
			
			currentPiece.location = newLocation
			currentPiece.facing = newFacing.asAngle()
			currentPiece.action = newAction
		}
	}
	
	class AttackAir(
		val maxAngle: Double,
		val invertAngle: Boolean,
		val minDistance: Double,
		val maxDistance: Double,
		val attackPower: Double
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return !currentPiece.hasAttacked
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickPiece(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius + minDistance,
					currentPiece.pieceRadius + maxDistance,
					currentPiece.facing.asAngle(
						flipX = invertAngle,
						flipY = invertAngle
					),
					null,
					maxAngle
				),
				currentPiece.owner.other,
				PieceLayer.AIR
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPiece ?: return
			val targetPiece = GameSessionData.currentSession!!.pieceById(pickRes.pieceId)
			
			val attackDistance = (targetPiece.location - currentPiece.location).magnitude
			val totalMultiplier = sqrt((maxDistance - attackDistance) / (maxDistance - minDistance))
			
			val totalAttack = attackPower * totalMultiplier * targetPiece.airTargetedMult
			targetPiece.attack(totalAttack, DamageSource.Piece(currentPiece))
			
			currentPiece.hasAttacked = true
			currentPiece.lockUndo()
		}
	}
	
	class AirAttackLand(
		val maxAngle: Double?,
		val minDistance: Double?,
		val maxDistance: Double,
		val softAttackPower: Double,
		val hardAttackPower: Double,
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return !currentPiece.hasAttacked
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val pickReq = PickRequest.PickPiece(
				PickBoundaryUnitBased(
					currentPiece.location,
					minDistance?.let { range -> currentPiece.pieceRadius + range },
					currentPiece.pieceRadius + maxDistance,
					currentPiece.facing,
					null,
					maxAngle
				),
				currentPiece.owner.other,
				PieceLayer.LAND
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPiece ?: return
			val targetPiece = GameSessionData.currentSession!!.pieceById(pickRes.pieceId)
			
			val attackDistance = (targetPiece.location - currentPiece.location).magnitude
			val totalMultiplier = sqrt((maxDistance - attackDistance) / (maxDistance - (minDistance ?: 0.0)))
			
			val targetHardness = targetPiece.type.stats.hardness
			val targetSoftness = 1 - targetHardness
			
			val softAttack = softAttackPower * targetSoftness
			val hardAttack = hardAttackPower * targetHardness
			
			val totalAttack = (softAttack + hardAttack) * totalMultiplier
			targetPiece.attack(totalAttack, DamageSource.Piece(currentPiece))
			
			currentPiece.hasAttacked = true
			currentPiece.lockUndo()
		}
	}
	
	class LandOnGround(
		val minDistance: Double,
		val maxDistance: Double,
		val maxAngle: Double,
		val minimumAction: Double,
		private val landUnitEquiv: String
	) : Ability() {
		override fun canUse(currentPiece: GamePiece): Boolean {
			return currentPiece.action > minimumAction
		}
		
		override suspend fun use(currentPiece: GamePiece) {
			val turnsInto = PieceType.valueOf(landUnitEquiv)
			
			currentPiece.heavyWeaponCharged = true
			
			val pickReq = PickRequest.PickPosition(
				PickBoundaryUnitBased(
					currentPiece.location,
					currentPiece.pieceRadius + minDistance,
					currentPiece.pieceRadius + maxDistance,
					currentPiece.facing,
					null,
					maxAngle
				),
				currentPiece.location,
				turnsInto.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE,
				PieceLayer.LAND,
				TerrainRequirement.REQ_NONE,
				turnsInto.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE
			)
			val pickRes = currentPiece.player.pick(pickReq) as? PickResponse.PickedPosition ?: return
			
			val newLocation = pickRes.pos
			val dLocation = newLocation - currentPiece.location
			val newFacing = dLocation.angle
			
			currentPiece.location = newLocation
			currentPiece.facing = newFacing
			currentPiece.action = 0.0
			currentPiece.type = turnsInto
			
			currentPiece.lockUndo()
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
	attackRequiresLoading: Double?,
	canMoveAfterAttacking: Boolean,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Move" to Ability.Move(moveSpeedPerRound),
	"Rotate" to Ability.Rotate(turnSpeedPerRound),
	"Undo Move" to Ability.UndoMove,
	"Attack" to Ability.AttackLand(
		maxAttackAngle,
		minAttackDistance,
		maxAttackDistance,
		softAttack,
		hardAttack,
		attackActionConsumed,
		attackRequiresLoading != null,
		canMoveAfterAttacking
	)
) + (if (attackRequiresLoading != null)
	mapOf(
		"Prepare Firing" to Ability.LoadHeavyWeapon(attackRequiresLoading)
	)
else emptyMap()) + extraAbilities

fun standardAntiAirPieceAbilities(
	moveSpeedPerRound: Double,
	turnSpeedPerRound: Double,
	
	maxAttackAngle: Double,
	minAttackDistance: Double,
	maxAttackDistance: Double,
	attackPower: Double,
	attackActionConsumed: Double,
	attackRequiresLoading: Double?,
	canMoveAfterAttacking: Boolean,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Move" to Ability.Move(moveSpeedPerRound),
	"Rotate" to Ability.Rotate(turnSpeedPerRound),
	"Undo Move" to Ability.UndoMove,
	"Anti-Air Attack" to Ability.LandAttackAir(
		maxAttackAngle,
		minAttackDistance,
		maxAttackDistance,
		attackPower,
		attackActionConsumed,
		attackRequiresLoading != null,
		canMoveAfterAttacking
	)
) + (if (attackRequiresLoading != null)
	mapOf(
		"Prepare Firing" to Ability.LoadHeavyWeapon(attackRequiresLoading)
	)
else emptyMap()) + extraAbilities

fun standardLandedAirPieceAbilities(
	moveSpeedPerRound: Double,
	turnSpeedPerRound: Double,
	
	minTakeoffRange: Double,
	maxTakeoffRange: Double,
	maxTakeoffAngle: Double,
	minTakeoffAction: Double,
	turnsInto: String,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Move" to Ability.Move(moveSpeedPerRound),
	"Rotate" to Ability.Rotate(turnSpeedPerRound),
	"Undo Move" to Ability.UndoMove,
	"Take Off" to Ability.TakeOff(
		minTakeoffRange,
		maxTakeoffRange,
		maxTakeoffAngle,
		minTakeoffAction,
		turnsInto
	)
) + extraAbilities

fun standardAirFighterAbilities(
	maxFlightTurn: Double,
	minFlightDist: Double,
	maxFlightDist: Double,
	
	airAttackLabel: String,
	airMaxAttackAngle: Double,
	airAttackFromRear: Boolean,
	airMinAttackRange: Double,
	airMaxAttackRange: Double,
	airAttackStrength: Double,
	
	landAttackLabel: String,
	landMaxAttackAngle: Double?,
	landMinAttackRange: Double?,
	landMaxAttackRange: Double,
	landSoftAttackPower: Double,
	landHardAttackPower: Double,
	
	minLandingRange: Double,
	maxLandingRange: Double,
	maxLandingAngle: Double,
	minLandingAction: Double,
	turnsInto: String,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Fly" to Ability.Fly(
		maxFlightTurn,
		minFlightDist,
		maxFlightDist
	),
	"Undo Move" to Ability.UndoMove,
	airAttackLabel to Ability.AttackAir(
		airMaxAttackAngle,
		airAttackFromRear,
		airMinAttackRange,
		airMaxAttackRange,
		airAttackStrength
	),
	landAttackLabel to Ability.AirAttackLand(
		landMaxAttackAngle,
		landMinAttackRange,
		landMaxAttackRange,
		landSoftAttackPower,
		landHardAttackPower
	),
	"Land on Ground" to Ability.LandOnGround(
		minLandingRange,
		maxLandingRange,
		maxLandingAngle,
		minLandingAction,
		turnsInto
	)
) + extraAbilities

fun standardAirBomberAbilities(
	maxFlightTurn: Double,
	minFlightDist: Double,
	maxFlightDist: Double,
	
	airAttackLabel: String,
	airMaxAttackAngle: Double,
	airAttackFromRear: Boolean,
	airMinAttackRange: Double,
	airMaxAttackRange: Double,
	airAttackStrength: Double,
	
	landAttackLabel: String,
	landMaxAttackAngle: Double?,
	landMinAttackRange: Double?,
	landMaxAttackRange: Double,
	landSoftAttackPower: Double,
	landHardAttackPower: Double,
	
	minLandingRange: Double,
	maxLandingRange: Double,
	maxLandingAngle: Double,
	minLandingAction: Double,
	turnsInto: String,
	
	extraAbilities: Map<String, Ability> = emptyMap()
): Map<String, Ability> = mapOf(
	"Fly" to Ability.Fly(
		maxFlightTurn,
		minFlightDist,
		maxFlightDist
	),
	"Undo Move" to Ability.UndoMove,
	landAttackLabel to Ability.AirAttackLand(
		landMaxAttackAngle,
		landMinAttackRange,
		landMaxAttackRange,
		landSoftAttackPower,
		landHardAttackPower
	),
	airAttackLabel to Ability.AttackAir(
		airMaxAttackAngle,
		airAttackFromRear,
		airMinAttackRange,
		airMaxAttackRange,
		airAttackStrength
	),
	"Land on Ground" to Ability.LandOnGround(
		minLandingRange,
		maxLandingRange,
		maxLandingAngle,
		minLandingAction,
		turnsInto
	)
) + extraAbilities

enum class PieceLayer {
	LAND, AIR;
	
	companion object {
		var viewAirUnits = true
			private set
		
		private var toggleAirUnitsButtonAttached = false
		fun attachToggleAirUnitsButton() {
			if (toggleAirUnitsButtonAttached)
				return
			
			val aElement = document.getElementById("toggle-air-pieces").unsafeCast<HTMLAnchorElement>()
			aElement.onclick = { e ->
				e.preventDefault()
				
				viewAirUnits = !viewAirUnits
				
				if (viewAirUnits)
					aElement.innerHTML = "Hide Air Units"
				else
					aElement.innerHTML = "Show Air Units"
				
				GameSessionData.currentSession?.let {
					GameField.redrawAllPieces(it.allPieces())
				}
				
				Unit
			}
			
			toggleAirUnitsButtonAttached = true
		}
	}
}

@Serializable
data class PieceStats(
	val maxHealth: Double,
	val hardness: Double,
	val abilities: Map<String, Ability>
)

@Serializable
enum class PieceType(
	val displayName: String,
	val pointCost: Int?,
	val layer: PieceLayer,
	val stats: PieceStats
) {
	// Land pieces
	
	LAND_INFANTRY(
		"Infantry",
		50,
		PieceLayer.LAND,
		PieceStats(
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
				attackRequiresLoading = null,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_ELITE_INFANTRY(
		"Stormtroopers",
		100,
		PieceLayer.LAND,
		PieceStats(
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
				attackRequiresLoading = null,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_MEDIC(
		"Combat Medic",
		150,
		PieceLayer.LAND,
		PieceStats(
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
				attackRequiresLoading = null,
				canMoveAfterAttacking = false,
				
				extraAbilities = mapOf(
					"Heal" to Ability.HealLand(
						maxAngle = PI / 4,
						minDistance = 0.0,
						maxDistance = 200.0,
						healthRestored = 800.0,
						actionConsumed = 0.5,
						requiresLoading = false,
						canMoveAfterHealing = false
					)
				)
			)
		)
	),
	LAND_CAVALRY(
		"Cavalry",
		75,
		PieceLayer.LAND,
		PieceStats(
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
				attackRequiresLoading = null,
				canMoveAfterAttacking = true
			)
		)
	),
	LAND_ELITE_CAVALRY(
		"Winged Hussars",
		125,
		PieceLayer.LAND,
		PieceStats(
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
				attackRequiresLoading = null,
				canMoveAfterAttacking = true
			)
		)
	),
	LAND_TANKS(
		"Light Tanks",
		160,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 2500.0,
			hardness = 0.85,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 700.0,
				turnSpeedPerRound = 4.5 * PI,
				
				maxAttackAngle = PI / 10,
				minAttackDistance = 0.0,
				maxAttackDistance = 400.0,
				softAttack = 600.0,
				hardAttack = 400.0,
				attackActionConsumed = 0.125,
				attackRequiresLoading = null,
				canMoveAfterAttacking = true
			)
		)
	),
	LAND_HEAVY_TANKS(
		"Heavy Tanks",
		250,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 3500.0,
			hardness = 0.95,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 500.0,
				turnSpeedPerRound = 3 * PI,
				
				maxAttackAngle = PI / 10,
				minAttackDistance = 0.0,
				maxAttackDistance = 400.0,
				softAttack = 700.0,
				hardAttack = 500.0,
				attackActionConsumed = 0.25,
				attackRequiresLoading = null,
				canMoveAfterAttacking = true
			)
		)
	),
	LAND_ARTILLERY(
		"Artillery",
		60,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 1000.0,
			hardness = 0.05,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 2 * PI,
				
				maxAttackAngle = PI / 6,
				minAttackDistance = 400.0,
				maxAttackDistance = 1200.0,
				softAttack = 1200.0,
				hardAttack = 600.0,
				attackActionConsumed = 0.5,
				attackRequiresLoading = 0.51,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_ROCKET_ARTILLERY(
		"Rocket Artillery",
		120,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 1500.0,
			hardness = 0.15,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 900.0,
				turnSpeedPerRound = 2.5 * PI,
				
				maxAttackAngle = PI / 4,
				minAttackDistance = 500.0,
				maxAttackDistance = 1500.0,
				softAttack = 1400.0,
				hardAttack = 700.0,
				attackActionConsumed = 0.25,
				attackRequiresLoading = 0.25,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_ANTI_TANK(
		"Anti-Tank Guns",
		100,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 2000.0,
			hardness = 0.10,
			abilities = standardLandPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 2 * PI,
				
				maxAttackAngle = PI / 6,
				minAttackDistance = 0.0,
				maxAttackDistance = 500.0,
				softAttack = 400.0,
				hardAttack = 1600.0,
				attackActionConsumed = 0.375,
				attackRequiresLoading = null,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_ANTI_AIR(
		"Flak Cannons",
		110,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 1625.0,
			hardness = 0.075,
			abilities = standardAntiAirPieceAbilities(
				moveSpeedPerRound = 600.0,
				turnSpeedPerRound = 2 * PI,
				
				maxAttackAngle = PI / 6,
				minAttackDistance = 0.0,
				maxAttackDistance = 500.0,
				attackPower = 800.0,
				attackActionConsumed = 0.375,
				attackRequiresLoading = 0.25,
				canMoveAfterAttacking = false
			)
		)
	),
	LAND_SAM_LAUNCHER(
		"Surface-to-Air Missiles",
		140,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 1875.0,
			hardness = 0.125,
			abilities = standardAntiAirPieceAbilities(
				moveSpeedPerRound = 900.0,
				turnSpeedPerRound = 2.5 * PI,
				
				maxAttackAngle = PI / 3,
				minAttackDistance = 10.0,
				maxAttackDistance = 600.0,
				attackPower = 1200.0,
				attackActionConsumed = 0.375,
				attackRequiresLoading = null,
				canMoveAfterAttacking = false
			)
		)
	),
	
	// Landed air pieces
	
	LAND_AIR_FIGHTERS(
		"Fighter Wing",
		160,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 500.0,
			hardness = 0.0,
			abilities = standardLandedAirPieceAbilities(
				moveSpeedPerRound = 400.0,
				turnSpeedPerRound = 2.5 * PI,
				
				minTakeoffRange = 600.0,
				maxTakeoffRange = 900.0,
				maxTakeoffAngle = PI / 4.5,
				minTakeoffAction = 0.4,
				turnsInto = "AIR_FIGHTERS"
			)
		)
	),
	LAND_AIR_BOMBERS(
		"Bomber Wing",
		190,
		PieceLayer.LAND,
		PieceStats(
			maxHealth = 500.0,
			hardness = 0.0,
			abilities = standardLandedAirPieceAbilities(
				moveSpeedPerRound = 300.0,
				turnSpeedPerRound = 2.0 * PI,
				
				minTakeoffRange = 400.0,
				maxTakeoffRange = 600.0,
				maxTakeoffAngle = PI / 6,
				minTakeoffAction = 0.6,
				turnsInto = "AIR_BOMBERS"
			)
		)
	),
	
	// Flying air pieces
	
	AIR_FIGHTERS(
		"Fighter Wing",
		null,
		PieceLayer.AIR,
		PieceStats(
			maxHealth = 2750.0,
			hardness = 0.0, // hardness is not used when attacking air units
			abilities = standardAirFighterAbilities(
				maxFlightTurn = PI / 2,
				minFlightDist = 240.0,
				maxFlightDist = 640.0,
				
				airAttackLabel = "Intercept",
				airMaxAttackAngle = PI / 5,
				airAttackFromRear = false,
				airMinAttackRange = 320.0,
				airMaxAttackRange = 640.0,
				airAttackStrength = 480.0,
				
				landAttackLabel = "Missile Strike",
				landMaxAttackAngle = PI / 7,
				landMinAttackRange = 360.0,
				landMaxAttackRange = 600.0,
				landSoftAttackPower = 750.0,
				landHardAttackPower = 250.0,
				
				minLandingRange = 600.0,
				maxLandingRange = 900.0,
				maxLandingAngle = PI / 4.5,
				minLandingAction = 0.4,
				
				turnsInto = "LAND_AIR_FIGHTERS"
			)
		)
	),
	AIR_BOMBERS(
		"Bomber Wing",
		null,
		PieceLayer.AIR,
		PieceStats(
			maxHealth = 2250.0,
			hardness = 0.0, // hardness is not used when attacking air units
			abilities = standardAirBomberAbilities(
				maxFlightTurn = PI / 2,
				minFlightDist = 180.0,
				maxFlightDist = 480.0,
				
				landAttackLabel = "Bomb",
				landMaxAttackAngle = null,
				landMinAttackRange = null,
				landMaxAttackRange = 250.0,
				landSoftAttackPower = 1650.0,
				landHardAttackPower = 1350.0,
				
				airAttackLabel = "Fire Tail Gun",
				airMaxAttackAngle = PI / 6,
				airAttackFromRear = true,
				airMinAttackRange = 120.0,
				airMaxAttackRange = 320.0,
				airAttackStrength = 240.0,
				
				minLandingRange = 400.0,
				maxLandingRange = 600.0,
				maxLandingAngle = PI / 6,
				minLandingAction = 0.6,
				
				turnsInto = "LAND_AIR_BOMBERS"
			)
		)
	),
	;
	
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
			LAND_ANTI_AIR -> 400.0
			LAND_SAM_LAUNCHER -> 400.0
			
			LAND_AIR_FIGHTERS -> 360.0
			LAND_AIR_BOMBERS -> 360.0
			
			AIR_FIGHTERS -> 360.0
			AIR_BOMBERS -> 360.0
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
			LAND_ANTI_AIR -> 300.0
			LAND_SAM_LAUNCHER -> 300.0
			
			LAND_AIR_FIGHTERS -> 360.0
			LAND_AIR_BOMBERS -> 360.0
			
			AIR_FIGHTERS -> 360.0
			AIR_BOMBERS -> 360.0
		} * imageScaling
	
	val imageRadius: Double
		get() = Vec2(imageWidth, imageHeight).magnitude / 2
	
	private val imageScaling: Double
		get() = 0.1
}
