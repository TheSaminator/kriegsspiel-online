import kotlinx.serialization.Serializable

@Serializable
data class TerrainStats(
	val hideEnemyUnitRange: Double?,
	val damagePerTurn: Double,
	val moveSpeedMult: Double,
	val softAttackMult: Double,
	val hardAttackMult: Double,
	val isImpassible: Boolean,
	val isHill: Boolean // Hills have special behavior when units move or attack
)

@Serializable
enum class TerrainType(val displayName: String, val color: String, val stats: TerrainStats) {
	// Land terrain
	DESERT(
		"Desert",
		"#ed9",
		TerrainStats(
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
		"Forest",
		"#062",
		TerrainStats(
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
		"Water",
		"#7ad",
		TerrainStats(
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
		"Hill",
		"#2c6",
		TerrainStats(
			hideEnemyUnitRange = null,
			damagePerTurn = 0.0,
			moveSpeedMult = 1.0,
			softAttackMult = 1.0,
			hardAttackMult = 1.0,
			isImpassible = false,
			isHill = true
		)
	),
	;
	
	private val radiusRange: ClosedFloatingPointRange<Double>
		get() = 150.0..400.0
	
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
	val terrainBlobs: Set<TerrainBlob>
) {
	companion object {
		const val defaultColor = "#194"
		
		val WIDTH_RANGE = 3500.0..5500.0
		val HEIGHT_RANGE = 2500.0..4500.0
		
		fun randomSize() = Vec2(WIDTH_RANGE.random(), HEIGHT_RANGE.random())
		
		val MIN_DISTANCE_BETWEEN_BLOBS = 200.0
		
		val MAX_TERRAIN_BLOBS = 20
		
		fun generateMap(): GameMap {
			val minDistanceBetweenBlobs = MIN_DISTANCE_BETWEEN_BLOBS
			
			val size = randomSize()
			
			val blobs = mutableSetOf<TerrainBlob>()
			val blobTypes = TerrainType.values()
			repeat(MAX_TERRAIN_BLOBS) { i ->
				val type = blobTypes[i % blobTypes.size]
				val radius = type.getRandomRadius()
				val center = Vec2((radius..(size.x - radius)).random(), (radius..(size.y - radius)).random())
				
				if (blobs.none { (it.center - center).magnitude < (it.radius + radius + minDistanceBetweenBlobs) }) {
					blobs.add(TerrainBlob(type, center, radius))
				}
			}
			
			return GameMap(size, blobs)
		}
	}
}
