@file:Suppress("DuplicatedCode")

import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlinx.html.*
import org.w3c.dom.HTMLAnchorElement
import kotlin.math.PI
import kotlin.math.roundToInt

object Kriegspedia {
	private fun P.explainMechanics() {
		+"There are several main mechanics that govern battles in Kriegsspiel Online, such as Action, Hardness or Flanking."
		br
		br
		br
		+"Action is a measure of how much each piece can do in a single turn. Abilities such as Move, Rotate, etc. use up different amounts of Action depending on how far the piece moved or rotated."
		+" Abilities such as Attack will usually take up all of the piece's remaining Action, with the exception of Cavalry, Winged Hussar, Light Tank, and Heavy Tank pieces:"
		+" while they cannot attack multiple times in a turn, they "
		em { +"can" }
		+" move after attacking."
		br
		br
		+"The two types of air pieces are Fighter Wings and Bomber Wings; when in flight, they must use at least 40% of their Action in a turn, or else they will crash and "
		strong { +"die instantly" }
		+"."
		br
		br
		br
		+"Hardness is a measure that each piece type has of how \"hard\" or armored that piece is. The hardness of a piece determines how much hard attack it receives vs how much soft attack it receives."
		br
		br
		+"The precise calculation is: "
		code { +"(attacker.hardAttack * target.hardness) + (attacker.softAttack * (1 - target.hardness))" }
		+", where "
		code { +"hardness" }
		+" ranges from 0.0 to 1.0, with 0% being totally soft and 100% being totally hard."
		br
		br
		+"Pieces that are hard, such as light tanks and heavy tanks, tend to have high soft attack, while pieces that are soft, such as infantry and cavalry, tend to have low hard attack. This makes anti-tank guns and artillery an invaluable purchase when deploying."
		br
		br
		br
		+"Flanking is another mechanic that governs land battles. Piece facing matters a lot in Kriegsspiel Online, and attacking a piece from the rear deals a lot more damage than attacking it from the front."
		br
		br
		+"The flanking bonus is given by the formula "
		code { +"(flankWeight + (attacker.facing dot target.facing)) / (flankWeight - 1)" }
		+", where "
		code { +"dot" }
		+" calculates the dot product of two angles' normal vectors. Since these are normal vectors, the calculation is simplified to the cosine of the angular difference between the two pieces' facings."
		br
		br
		code { +"flankWeight" }
		+" determines how powerful the flanking bonus is. For soft attack, the flank weight is three, resulting in a flank multiplier that ranges from 100% minimum to 200% maximum."
		+" Hard attack, on the other hand, has a flank weight of 9, resulting in a flank multiplier ranging from 100% minimum to 125% maximum."
	}
	
	private fun TABLE.explainTerrainType(type: TerrainType) {
		tr {
			th {
				colSpan = "2"
				style = "background-color: ${GameMap.defaultColor}; color: ${type.color}; font-weight: bold;"
				+type.displayName
			}
		}
		
		if (type.stats.isImpassible) {
			tr {
				td {
					colSpan = "2"
					style = "text-align: center"
					+"This terrain is impassible. Pieces may not enter it at all."
				}
			}
			
			return
		}
		
		if (type.stats.isHill) {
			tr {
				td {
					colSpan = "2"
					style = "text-align: center"
					+"This terrain has special hill mechanics. Pieces have an attack buff when attacking downhill and an attack debuff when attacking uphill. Also, units have slower movement when facing towards or away from the center of the hill; their movement isn't as slow when the unit is facing with its side to the center of the hill."
				}
			}
			
			return
		}
		
		type.stats.hideEnemyUnitRange?.let { range ->
			tr {
				th {
					+"Hides pieces from the enemy outside range"
				}
				td {
					+"$range lengths"
				}
			}
		}
		
		type.stats.damagePerTurn.takeUnless { it == 0.0 }?.let { dmg ->
			tr {
				th {
					+"Pieces ending their turns in this terrain receive damage"
				}
				td {
					+"$dmg HP"
				}
			}
		}
		
		type.stats.moveSpeedMult.takeUnless { it == 1.0 }?.let { mult ->
			tr {
				th {
					+"Pieces in this terrain have their move speed modified by"
				}
				td {
					+((mult * 100).roundToInt().toString() + "%")
				}
			}
		}
		
		type.stats.softAttackMult.takeUnless { it == 1.0 }?.let { mult ->
			tr {
				th {
					+"Pieces in this terrain have their soft attack power modified by"
				}
				td {
					+((mult * 100).roundToInt().toString() + "%")
				}
			}
		}
		
		type.stats.hardAttackMult.takeUnless { it == 1.0 }?.let { mult ->
			tr {
				th {
					+"Pieces in this terrain have their hard attack power modified by"
				}
				td {
					+((mult * 100).roundToInt().toString() + "%")
				}
			}
		}
	}
	
	private fun TABLE.explainPieceType(type: PieceType) {
		tr {
			th {
				colSpan = "2"
				+type.displayName
				if (type.layer == PieceLayer.AIR)
					+" (Flying)"
			}
		}
		tr {
			th {
				img(src = "uniticons/player/${type.name.lowercase()}.png") {
					style = "max-width: 75%; max-height: 7.5em"
				}
				br
				+"Icon (Player)"
			}
			th {
				img(src = "uniticons/opponent/${type.name.lowercase()}.png") {
					style = "max-width: 75%; max-height: 7.5em"
				}
				br
				+"Icon (Opponent)"
			}
		}
		tr {
			th {
				+"Size on Map"
			}
			td {
				+(((type.imageRadius + GamePiece.PIECE_RADIUS_OUTLINE) * 2).roundToInt().toString() + " lengths")
			}
		}
		tr {
			th {
				+"Point Cost"
			}
			td {
				+(type.pointCost?.toString() ?: "Cannot be purchased")
			}
		}
		tr {
			th {
				+"Max Health"
			}
			td {
				+(type.stats.maxHealth.roundToInt().toString() + " HP")
			}
		}
		tr {
			th {
				+"Hardness"
			}
			td {
				+((type.stats.hardness * 100).roundToInt().toString() + "%")
			}
		}
		type.stats.abilities.forEach { (name, data) ->
			tr {
				th {
					colSpan = "2"
					+"Ability: $name"
				}
			}
			
			when (data) {
				is Ability.Move -> {
					tr {
						th {
							+"Uses 100% of Action to move"
						}
						td {
							+(data.distancePerAction.roundToInt().toString() + " lengths")
						}
					}
				}
				is Ability.Rotate -> {
					tr {
						th {
							+"Uses 100% of Action to turn"
						}
						td {
							+((data.anglePerAction * 180 / PI).roundToInt().toString() + " degrees")
						}
					}
				}
				Ability.UndoMove -> {
					tr {
						th {
							+"Can undo previous move"
						}
						td {
							+"Yes"
						}
					}
				}
				is Ability.AttackLand -> {
					tr {
						th {
							+"Side-to-side range"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Minimum distance"
						}
						td {
							+(data.minDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Maximum distance"
						}
						td {
							+(data.maxDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Soft attack"
						}
						td {
							+(data.softAttackPower.roundToInt().toString() + " HP")
						}
					}
					tr {
						th {
							+"Hard attack"
						}
						td {
							+(data.hardAttackPower.roundToInt().toString() + " HP")
						}
					}
					tr {
						th {
							+(if (data.canMoveAfterAttacking) "Consumes Action" else "Requires minimum Action")
						}
						td {
							+((data.actionConsumed * 100).roundToInt().toString() + "%")
						}
					}
					tr {
						th {
							+"Requires preparation"
						}
						td {
							+(if (data.requiresLoading) "Yes" else "No")
						}
					}
				}
				is Ability.HealLand -> {
					tr {
						th {
							+"Side-to-side range"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Minimum distance"
						}
						td {
							+(data.minDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Maximum distance"
						}
						td {
							+(data.maxDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Restores target health"
						}
						td {
							+(data.healthRestored.roundToInt().toString() + " HP")
						}
					}
					tr {
						th {
							+(if (data.canMoveAfterHealing) "Consumes Action" else "Requires minimum Action")
						}
						td {
							+((data.actionConsumed * 100).roundToInt().toString() + "%")
						}
					}
				}
				is Ability.LoadHeavyWeapon -> {
					tr {
						th {
							+"Consumes Action"
						}
						td {
							+((data.actionConsumed * 100).roundToInt().toString() + "%")
						}
					}
				}
				is Ability.LandAttackAir -> {
					tr {
						th {
							+"Side-to-side range"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Minimum distance"
						}
						td {
							+(data.minDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Maximum distance"
						}
						td {
							+(data.maxDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Attack power"
						}
						td {
							+(data.attackPower.roundToInt().toString() + " HP")
						}
					}
					tr {
						th {
							+(if (data.canMoveAfterAttacking) "Consumes Action" else "Requires minimum Action")
						}
						td {
							+((data.actionConsumed * 100).roundToInt().toString() + "%")
						}
					}
					tr {
						th {
							+"Requires preparation"
						}
						td {
							+(if (data.requiresLoading) "Yes" else "No")
						}
					}
				}
				is Ability.TakeOff -> {
					tr {
						th {
							+"Side-to-side range"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Minimum distance"
						}
						td {
							+(data.minDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Maximum distance"
						}
						td {
							+(data.maxDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Consumes Action"
						}
						td {
							+((data.minimumAction * 100).roundToInt().toString() + "%")
						}
					}
				}
				is Ability.Fly -> {
					tr {
						th {
							+"Side-to-side range"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Minimum distance"
						}
						td {
							+(data.minDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Uses 100% of Action to move"
						}
						td {
							+(data.distancePerAction.roundToInt().toString() + " lengths")
						}
					}
				}
				is Ability.AttackAir -> {
					tr {
						th {
							+"Side-to-side range"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Fired from"
						}
						td {
							+(if (data.invertAngle) "Rear" else "Fore")
						}
					}
					tr {
						th {
							+"Minimum distance"
						}
						td {
							+(data.minDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Maximum distance"
						}
						td {
							+(data.maxDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Attack power"
						}
						td {
							+(data.attackPower.roundToInt().toString() + " HP")
						}
					}
				}
				is Ability.AirAttackLand -> {
					tr {
						th {
							+"Side-to-side range"
						}
						td {
							+(data.maxAngle?.let { angle ->
								(angle * 360 / PI).roundToInt().toString() + " degrees"
							} ?: "360 degrees")
						}
					}
					data.minDistance?.let { minRange ->
						tr {
							th {
								+"Minimum distance"
							}
							td {
								+(minRange.roundToInt().toString() + " lengths")
							}
						}
					}
					tr {
						th {
							+"Maximum distance"
						}
						td {
							+(data.maxDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Soft attack power"
						}
						td {
							+(data.softAttackPower.roundToInt().toString() + " HP")
						}
					}
					tr {
						th {
							+"Hard attack power"
						}
						td {
							+(data.hardAttackPower.roundToInt().toString() + " HP")
						}
					}
				}
				is Ability.LandOnGround -> {
					tr {
						th {
							+"Side-to-side range"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Minimum distance"
						}
						td {
							+(data.minDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Maximum distance"
						}
						td {
							+(data.maxDistance.roundToInt().toString() + " lengths")
						}
					}
					tr {
						th {
							+"Consumes Action"
						}
						td {
							+((data.minimumAction * 100).roundToInt().toString() + "%")
						}
					}
				}
			}
		}
	}
	
	private var kriegspediaButtonAttached = false
	fun attachKriegspediaButton() {
		if (kriegspediaButtonAttached)
			return
		
		val aElement = document.getElementById("kriegspedia-button").unsafeCast<HTMLAnchorElement>()
		aElement.onclick = { e ->
			e.preventDefault()
			
			GameScope.launch {
				viewKriegspedia()
			}
			
			Unit
		}
		
		kriegspediaButtonAttached = true
	}
	
	suspend fun viewKriegspedia() {
		when (Popup.KriegspediaStart.display()) {
			KriegspediaSection.MECHANICS -> mechanics()
			KriegspediaSection.PIECES -> pieces()
			KriegspediaSection.TERRAINS -> terrains()
			null -> return
		}
	}
	
	private suspend fun mechanics() {
		Popup.KriegspediaExplanation { explainMechanics() }.display()
		viewKriegspedia()
	}
	
	private suspend fun pieces() {
		val piece = Popup.KriegspediaPieceList.display()
		if (piece == null)
			viewKriegspedia()
		else {
			Popup.KriegspediaDataTable { explainPieceType(piece) }.display()
			pieces()
		}
	}
	
	private suspend fun terrains() {
		val terrain = Popup.KriegspediaTerrainList.display()
		if (terrain == null)
			viewKriegspedia()
		else {
			Popup.KriegspediaDataTable { explainTerrainType(terrain) }.display()
			terrains()
		}
	}
}

enum class KriegspediaSection {
	MECHANICS, PIECES, TERRAINS;
}
