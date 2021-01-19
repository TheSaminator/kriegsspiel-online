# Kriegsspiel Online

*the simple WebRTC battle game*

## Where to Play

Kriegsspiel Online can be played on the Netlify instance at https://kriegsspiel-online.netlify.app/

## Who to Play

Kriegsspiel Online is played with two players.

## How to Play (Land Battles)

### Step 1: Host or Join

Hosting a game will give you a game ID that you can share with a friend.

To join a game, you must select your friend's game ID from a dropdown box.

### Step 2: Unit Deployment

Both players start out with 1000 points to spend on an army. Each unit has a varying point cost.

To deploy a unit, click on the button with the unit type's name and select a position on the map.

Both sides deploy units simultaneously.

### Step 3: FIGHT!

The three orders that most units can do are Move, Turn, and Attack.

Unlike in some other strategy games, in Kriegsspiel Online, unit facing matters for movement, attacks, and flanking.

Units have an Action bar that controls how much they can do in one round.

* Move:
    * Uses `moveSpeedPerRound` stat.
    * Unit moves up to `moveSpeedPerRound` away from starting position, turning up to 22.5 degrees away from its original facing.
    * The DistanceCovered is divided by `moveSpeedPerRound` to get the percentage of Action used up.
* Turn:
    * Uses `turnSpeedPerRound` stat.
    * Unit turns up to 180 degrees (or an angle determined by action remaining) away from starting facing.
    * The AngleTurned is divided by `turnSpeedPerRound` to get the percentage of Action used up.
* Attack
    * Targets a single unit
    * Uses attacker's `maxAttackAngle`, `minAttackDistance`, `maxAttackDistance`, `softAttack`, `hardAttack`, `attackActionConsumed`, and `canMoveAfterAttacking` stats.
    * Uses target's `maxHealth` and `hardness` stats.
    * Attacker's Action must be at least `attackActionConsumed`.
    * `softAttackStrength` is `(attacker.softAttack * (1 - target.Hardness) * SoftFlankingMult)`
    * `hardAttackStrength` is `(attacker.hardAttack * target.Hardness * HardFlankingMult)`
    * Flanking multiplier is calculated as `((attacker.facingNormal dot target.facingNormal) + flankWeight) / (flankWeight - 1)`
        * Facing normal is just the unit vector of the unit's facing, equal to î cos θ + ĵ sin θ, where θ is the unit's facing angle
        * Flank weight for soft attack is 3, resulting in a flank multiplier from 1 (minimum) to 2 (maximum)
        * Flank weight for hard attack is 9, resulting in a flank multiplier from 1 (minimum) to 1.125 (maximum)
    * Total reduction of target's health is `(softAttackStrength + hardAttackStrength) / target.maxHealth`
    * Reduction of attacker's Action is either:
        * If attacker `canMoveAfterAttacking`, then `attackActionConsumed`
        * Otherwise, all remaining Action

Unit stats can be viewed in [src/main/kotlin/game_data.kt](./src/main/kotlin/game_data.kt) in the enum class `PieceType`.

The game is turn-based, so each side has to wait until the other side is done its turn.

## How to Play (Space Battles)

Space battles are much like land battles, except in SPACE!

Imperial Navy ships' main armaments are broadside cannon arrays.
Their auxiliary armaments are things like torpedoes or the Cruiser's Nova Lance.
Space Marine Corps ships are similar. Starfleet ships are different,
using beam arrays to fire in a wide range.

## To Be Implemented

* [x] Fog of war
* [x] Medic units
* [x] ~~Supply system~~
    * After playtesting, I have found that this makes the game less fun
* [x] More ships for space warfare
