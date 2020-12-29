# Kriegsspiel Online

*the simple WebRTC battle game*

## Where to Play

Kriegsspiel Online can be played on the Github Pages instance at https://thesaminator.github.io/kriegsspiel-online/

## Who to Play

Kriegsspiel Online is played with two players.

## How to Play

### Step 1: Host or Join

Hosting a game will give you a game ID that you can share with a friend.

To join a game, you must select your friend's game ID from a dropdown box.

### Step 2: Unit Deployment

Both players start out with 1000 points to spend on an army. The following table details unit point costs:

| Unit             | Point Cost |
| :--------------: | ---------: |
| Infantry         |         50 |
| Stormtroopers    |        100 |
| Cavalry          |         75 |
| Winged Hussars   |        125 |
| Light Tanks      |         80 |
| Heavy Tanks      |        120 |
| Artillery        |         60 |
| Rocket Artillery |        120 |
| Anti-Tank Guns   |        100 |

To deploy a unit, click on the button with the unit type's name and select a position on the map.

Both sides deploy units simultaneously.

### Step 3: FIGHT!

The three orders that units can do are Move, Turn, and Attack.

Unlike in some other strategy games, in Kriegsspiel Online, unit facing matters for movement, attacks, and flanking.

Units have an Action bar that controls how much they can do in one round.

* Move:
    * Uses MaxDistance stat.
    * Unit moves up to MaxDistance away from starting position, turning up to 22.5 degrees away from its original facing.
    * The DistanceCovered is divided by MaxDistance to get the percentage of Action used up.
* Turn:
    * Uses MaxTurn stat.
    * Unit turns up to 180 degrees (or an angle determined by action remaining) away from starting facing.
    * The AngleTurned is divided by MaxTurn to get the percentage of Action used up.
* Attack
    * Targets a single unit
    * Uses attacker's MaxAttackTurn, MaxAttackDistance, SoftAttack, HardAttack, AttackActionConsumed, and CanMoveAfterAttacking stats.
    * Uses target's MaxHealth and Hardness stats.
    * Attacker's Action must be at least AttackActionConsumed.
    * SoftAttackStrength is (attacker.SoftAttack * (1 - target.Hardness) * SoftFlankingMult)
    * HardAttackStrength is (attacker.HardAttack * target.Hardness * HardFlankingMult)
    * Flanking multiplier is calculated as ((attacker.facingNormal dot target.facingNormal) + flankWeight) / (flankWeight - 1)
        * Flank weight for soft attack is 3, resulting in a flank multiplier from 1 (minimum) to 2 (maximum)
        * Flank weight for hard attack is 9, resulting in a flank multiplier from 1 (minimum) to 1.125 (maximum)
    * Total reduction of target's health is (SoftAttackStrength + HardAttackStrength) / target.MaxHealth
    * Reduction of attacker's Action is either:
        * If attacker can't move after attacking, then all remaining Action
        * Otherwise, AttackActionConsumed

Units can also Skip Turn, if not all of their Action is consumed, this lets them end the turn anyway.

Unit stats can be viewed in [src/main/kotlin/game_data.kt](./src/main/kotlin/game_data.kt) in the enum class `PieceType`.

The game is turn-based, so each side has to wait until the other side is done its turn.
