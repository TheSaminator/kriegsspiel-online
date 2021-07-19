import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.html.*
import org.w3c.dom.HTMLAnchorElement
import kotlin.math.PI
import kotlin.math.roundToInt

private fun P.explainLand() {
	+"There are two main mechanics that govern land battles: Hardness and Flanking."
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
	+"Flanking is the other mechanic that governs land battles. Piece facing matters a lot in Kriegsspiel Online, and attacking a piece from the rear deals a lot more damage than attacking it from the front."
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

private fun P.explainSpace() {
	+"Space battles, unlike land battles, don't have any Hardness mechanic. However, it does keep the flanking mechanic. Attacking starships from their rear deals more damage than attacking them from their fore. "
	+"The flank weight in space in 5, resulting in a flank multiplier that ranges from a minimum of 100% to a maximum of 150%."
	br
	br
	+"Starships, unlike land pieces, have shields that absorb a certain amount of damage before falling and needing to recharge. "
	+"When a ship's shield bar is blue, that means its shields are up. When the bar is purple, that means its shields are recharging."
	br
	br
	+"Unlike land battles, which use a single style for all pieces, space battles have different faction skins with different starship types available to them. These factions are the Imperial Navy, the Space Marine Corps, the Star Fleet, and the K.D.F."
}

private fun TABLE.explainTerrainType(type: TerrainType) {
	tr {
		th {
			colSpan = "2"
			+type.displayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
		}
	}
	tr {
		th {
			colSpan = "2"
			span {
				style = "background-color: ${type.requiredBattleType.defaultMapColor}; color: ${type.color}; font-weight: bold;"
				+"Looks like this on the map"
			}
		}
	}
	
	when (val stats = type.stats) {
		is TerrainStats.Land -> {
			if (stats.isImpassible) {
				tr {
					td {
						colSpan = "2"
						style = "text-align: center"
						+"This terrain is impassible. Units may not enter it at all."
					}
				}
				
				return
			}
			
			tr {
				th {
					+"Hides pieces from the enemy outside range"
				}
				td {
					+(stats.hideEnemyUnitRange?.let { "$it lengths" } ?: "Doesn't hide pieces")
				}
			}
			tr {
				th {
					+"Pieces ending their turns in this terrain receive damage"
				}
				td {
					+(stats.damagePerTurn.toString() + " HP")
				}
			}
			
			if (stats.isHill) {
				tr {
					th {
						+"This terrain uses special hill mechanics"
					}
					td {
						+"Yes"
					}
				}
			} else {
				tr {
					th {
						+"Pieces in this terrain have their move speed modified by"
					}
					td {
						+((stats.moveSpeedMult * 100).roundToInt().toString() + "%")
					}
				}
				tr {
					th {
						+"Pieces in this terrain have their soft attack power modified by"
					}
					td {
						+((stats.softAttackMult * 100).roundToInt().toString() + "%")
					}
				}
				tr {
					th {
						+"Pieces in this terrain have their hard attack power modified by"
					}
					td {
						+((stats.hardAttackMult * 100).roundToInt().toString() + "%")
					}
				}
			}
		}
		is TerrainStats.Space -> {
			tr {
				th {
					+"Hides pieces from the enemy outside range"
				}
				td {
					+(stats.hideEnemyUnitRange?.let { "$it lengths" } ?: "Doesn't hide pieces")
				}
			}
			tr {
				th {
					+"Pieces ending their turns in this terrain receive damage"
				}
				td {
					+(stats.damagePerTurn.toString() + " HP")
				}
			}
			tr {
				th {
					+"Damage done by this terrain ignores shields"
				}
				td {
					+(if (stats.dptIgnoresShields) "Yes" else "No")
				}
			}
			tr {
				th {
					+"Pieces in this terrain have their move speed modified by"
				}
				td {
					+((stats.moveSpeedMult * 100).roundToInt().toString() + "%")
				}
			}
			tr {
				th {
					+"This terrain renders pieces' shields inoperable"
				}
				td {
					+(if (stats.forcesShieldsDown) "Yes" else "No")
				}
			}
			tr {
				th {
					+"Pieces in this terrain have their attack power modified by"
				}
				td {
					+((stats.attackMult * 100).roundToInt().toString() + "%")
				}
			}
		}
	}
}

private fun TABLE.explainPieceType(type: PieceType) {
	tr {
		th {
			colSpan = "2"
			+type.displayName
		}
	}
	tr {
		th {
			img(src = "uniticons/player/${type.name.lowercase()}.png") {
				width = "50%"
			}
			br
			+"Icon (Player)"
		}
		th {
			img(src = "uniticons/opponent/${type.name.lowercase()}.png") {
				width = "50%"
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
			+((type.imageRadius * 2).roundToInt().toString() + " lengths")
		}
	}
	tr {
		th {
			+"Point Cost"
		}
		td {
			+type.pointCost.toString()
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
	when (val stats = type.stats) {
		is LandPieceStats -> tr {
			th {
				+"Hardness"
			}
			td {
				+((stats.hardness * 100).roundToInt().toString() + "%")
			}
		}
		is SpacePieceStats -> tr {
			th {
				+"Max Shield"
			}
			td {
				+(stats.maxShield.roundToInt().toString() + " HP")
			}
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
			is Ability.ChargeHeavyWeapon -> {
				tr {
					th {
						+"Consumes Action"
					}
					td {
						+((data.actionConsumed * 100).roundToInt().toString() + "%")
					}
				}
			}
			is Ability.AttackSpace -> {
				if (data.minAngle != null && data.maxAngle != null) {
					tr {
						th {
							+"Minimum angle from fore"
						}
						td {
							+((data.minAngle * 180 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Maximum angle from fore"
						}
						td {
							+((data.maxAngle * 180 / PI).roundToInt().toString() + " degrees")
						}
					}
				} else if (data.maxAngle != null) {
					tr {
						th {
							+"Side-to-side range"
							if (data.invertAngle)
								+" (from rear)"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
				} else {
					tr {
						th {
							+"All-directional fire"
						}
						td {
							+"Yes"
						}
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
						+"Attack strength"
					}
					td {
						+(data.attackPower.roundToInt().toString() + " HP")
					}
				}
				tr {
					th {
						+"Requires preparation"
					}
					td {
						+(if (data.requiresCharge) "Yes" else "No")
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
			}
			is Ability.AttackAreaSpace -> {
				if (data.minAngle != null && data.maxAngle != null) {
					tr {
						th {
							+"Minimum angle from fore"
						}
						td {
							+((data.minAngle * 180 / PI).roundToInt().toString() + " degrees")
						}
					}
					tr {
						th {
							+"Maximum angle from fore"
						}
						td {
							+((data.maxAngle * 180 / PI).roundToInt().toString() + " degrees")
						}
					}
				} else if (data.maxAngle != null) {
					tr {
						th {
							+"Side-to-side range"
							if (data.invertAngle)
								+" (from rear)"
						}
						td {
							+((data.maxAngle * 360 / PI).roundToInt().toString() + " degrees")
						}
					}
				} else {
					tr {
						th {
							+"All-directional fire"
						}
						td {
							+"Yes"
						}
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
						+"Attack area radius"
					}
					td {
						+(data.aoeRadius.roundToInt().toString() + " lengths")
					}
				}
				tr {
					th {
						+"Attack strength"
					}
					td {
						+(data.attackPower.roundToInt().toString() + " HP")
					}
				}
				tr {
					th {
						+"Requires preparation"
					}
					td {
						+(if (data.requiresCharge) "Yes" else "No")
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
			}
			is Ability.Cloak -> {
				tr {
					th {
						+"Consumes Action"
					}
					td {
						+((data.actionConsumed * 100).roundToInt().toString() + "%")
					}
				}
			}
			is Ability.Decloak -> {
				tr {
					th {
						+"Consumes Action"
					}
					td {
						+((data.actionConsumed * 100).roundToInt().toString() + "%")
					}
				}
			}
			is Ability.RevealCloak -> {
				tr {
					th {
						+"Reveals cloaked ships in range"
					}
					td {
						+(data.revealRange.roundToInt().toString() + " lengths")
					}
				}
				tr {
					th {
						+"Consumes Action"
					}
					td {
						+((data.actionConsumed * 100).roundToInt().toString() + "%")
					}
				}
			}
		}
	}
}

private var kriegspediaButtonEnabled = false
fun enableKriegspediaButton() {
	if (kriegspediaButtonEnabled)
		return
	
	val aElement = document.getElementById("kriegspedia-button").unsafeCast<HTMLAnchorElement>()
	aElement.onclick = { e ->
		e.preventDefault()
		
		GlobalScope.launch {
			viewKriegspedia()
		}
		
		Unit
	}
	
	kriegspediaButtonEnabled = true
}

suspend fun viewKriegspedia() {
	Popup.KriegspediaStart.display()?.let {
		index(it)
	}
}

private suspend fun index(type: BattleType) {
	when (Popup.KriegspediaIndex(type).display()) {
		KriegspediaSection.MECHANICS -> mechanics(type)
		KriegspediaSection.PIECES -> pieces(type)
		KriegspediaSection.TERRAINS -> terrains(type)
		null -> viewKriegspedia()
	}
}

private suspend fun mechanics(type: BattleType) {
	Popup.KriegspediaExplanation(
		when (type) {
			BattleType.LAND_BATTLE -> P::explainLand
			BattleType.SPACE_BATTLE -> P::explainSpace
		}
	).display()
	index(type)
}

private suspend fun pieces(type: BattleType) {
	val piece = Popup.KriegspediaPieceList(type).display()
	if (piece == null)
		index(type)
	else {
		Popup.KriegspediaDataTable { explainPieceType(piece) }.display()
		pieces(type)
	}
}

private suspend fun terrains(type: BattleType) {
	val terrain = Popup.KriegspediaTerrainList(type).display()
	if (terrain == null)
		index(type)
	else {
		Popup.KriegspediaDataTable { explainTerrainType(terrain) }.display()
		terrains(type)
	}
}

enum class KriegspediaSection {
	MECHANICS, PIECES, TERRAINS;
}
