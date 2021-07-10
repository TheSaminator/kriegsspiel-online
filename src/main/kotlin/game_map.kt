import kotlinx.serialization.Serializable

@Serializable
sealed class TerrainStats {
	abstract val hideEnemyUnitRange: Double?
	abstract val damagePerTurn: Double
	abstract val moveSpeedMult: Double
	
	@Serializable
	data class Land(
		override val hideEnemyUnitRange: Double?,
		override val damagePerTurn: Double,
		override val moveSpeedMult: Double,
		val softAttackMult: Double,
		val hardAttackMult: Double,
		val isImpassible: Boolean,
		val isHill: Boolean // Hills have special behavior when units move or attack
	) : TerrainStats()
	
	@Serializable
	data class Space(
		override val hideEnemyUnitRange: Double?,
		override val damagePerTurn: Double,
		override val moveSpeedMult: Double,
		val dptIgnoresShields: Boolean,
		val forcesShieldsDown: Boolean,
		val attackMult: Double,
	) : TerrainStats()
}

@Serializable
enum class TerrainType(val color: String, val stats: TerrainStats) {
	// Land terrain
	DESERT(
		"#ed9",
		TerrainStats.Land(
			hideEnemyUnitRange = null,
			damagePerTurn = 100.0,
			moveSpeedMult = 1.0,
			softAttackMult = 1.0,
			hardAttackMult = 1.0,
			isImpassible = false,
			isHill = false
		)
	),
	FOREST(
		"#062",
		TerrainStats.Land(
			hideEnemyUnitRange = 160.0,
			damagePerTurn = 0.0,
			moveSpeedMult = 0.75,
			softAttackMult = 0.9,
			hardAttackMult = 0.6,
			isImpassible = false,
			isHill = false
		)
	),
	WATER(
		"#7ad",
		TerrainStats.Land(
			hideEnemyUnitRange = null,
			damagePerTurn = 0.0,
			moveSpeedMult = 0.0,
			softAttackMult = 0.0,
			hardAttackMult = 0.0,
			isImpassible = true,
			isHill = false
		)
	),
	HILL(
		"#2c6",
		TerrainStats.Land(
			hideEnemyUnitRange = null,
			damagePerTurn = 0.0,
			moveSpeedMult = 1.0,
			softAttackMult = 1.0,
			hardAttackMult = 1.0,
			isImpassible = false,
			isHill = true
		)
	),
	
	// Space terrain
	ASTEROID_THICKET(
		"#876",
		TerrainStats.Space(
			hideEnemyUnitRange = null,
			damagePerTurn = 30.0,
			moveSpeedMult = 0.85,
			dptIgnoresShields = false,
			forcesShieldsDown = false,
			attackMult = 1.0
		)
	),
	GAS_CLOUD(
		"#999",
		TerrainStats.Space(
			hideEnemyUnitRange = 240.0,
			damagePerTurn = 0.0,
			moveSpeedMult = 0.8,
			dptIgnoresShields = false,
			forcesShieldsDown = false,
			attackMult = 1.0
		)
	),
	ELECTROPLASMA_CLOUD(
		"#86a",
		TerrainStats.Space(
			hideEnemyUnitRange = 200.0,
			damagePerTurn = 0.0,
			moveSpeedMult = 0.75,
			dptIgnoresShields = false,
			forcesShieldsDown = true,
			attackMult = 1.0
		)
	),
	RADIOPLASMA_CLOUD(
		"#6a8",
		TerrainStats.Space(
			hideEnemyUnitRange = 160.0,
			damagePerTurn = 20.0,
			moveSpeedMult = 0.7,
			dptIgnoresShields = true,
			forcesShieldsDown = false,
			attackMult = 1.0
		)
	),
	;
	
	val requiredBattleType: BattleType
		get() = when (stats) {
			is TerrainStats.Land -> BattleType.LAND_BATTLE
			is TerrainStats.Space -> BattleType.SPACE_BATTLE
		}
	
	val minRadius: Double
		get() = when (requiredBattleType) {
			BattleType.LAND_BATTLE -> 150.0
			BattleType.SPACE_BATTLE -> 100.0
		}
	
	val maxRadius: Double
		get() = when (requiredBattleType) {
			BattleType.LAND_BATTLE -> 400.0
			BattleType.SPACE_BATTLE -> 300.0
		}
	
	val radiusRange: ClosedFloatingPointRange<Double>
		get() = minRadius..maxRadius
	
	fun getRandomRadius(): Double = radiusRange.random()
}

@Serializable
data class TerrainBlob(
	val type: TerrainType,
	val center: Vec2,
	val radius: Double
)

@Serializable
data class GameMap(
	val size: Vec2,
	val gameType: BattleType,
	val terrainBlobs: Set<TerrainBlob>
) {
	companion object {
		val LAND_WIDTH_RANGE = 3500.0..5500.0
		val LAND_HEIGHT_RANGE = 2500.0..4500.0
		
		val SPACE_WIDTH_RANGE = 3000.0..5000.0
		val SPACE_HEIGHT_RANGE = 3000.0..5000.0
		
		fun randomSize(battleType: BattleType) = when (battleType) {
			BattleType.LAND_BATTLE -> Vec2(LAND_WIDTH_RANGE.random(), LAND_HEIGHT_RANGE.random())
			BattleType.SPACE_BATTLE -> Vec2(SPACE_WIDTH_RANGE.random(), SPACE_HEIGHT_RANGE.random())
		}
		
		val LAND_MIN_DISTANCE_BETWEEN_BLOBS = 200.0
		val SPACE_MIN_DISTANCE_BETWEEN_BLOBS = 300.0
		
		val MAX_TERRAIN_BLOBS = 20
		
		fun generateMap(battleType: BattleType): GameMap {
			val minDistanceBetweenBlobs = when (battleType) {
				BattleType.LAND_BATTLE -> LAND_MIN_DISTANCE_BETWEEN_BLOBS
				BattleType.SPACE_BATTLE -> SPACE_MIN_DISTANCE_BETWEEN_BLOBS
			}
			
			val size = randomSize(battleType)
			
			val blobs = mutableSetOf<TerrainBlob>()
			val blobTypes = TerrainType.values().filter { it.requiredBattleType == battleType }.toList()
			repeat(MAX_TERRAIN_BLOBS) { i ->
				val type = blobTypes[i % blobTypes.size]
				val radius = type.getRandomRadius()
				val center = Vec2((radius..(size.x - radius)).random(), (radius..(size.y - radius)).random())
				
				if (blobs.none { (it.center - center).magnitude < (it.radius + radius + minDistanceBetweenBlobs) }) {
					blobs.add(TerrainBlob(type, center, radius))
				}
			}
			
			return GameMap(size, battleType, blobs)
		}
	}
}
