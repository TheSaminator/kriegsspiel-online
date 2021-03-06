import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
sealed class GamePhase {
	@Serializable
	object Deployment : GamePhase() {
		var chosenSkin: BattleFactionSkin? = null
		var hostIsDone = false
		var guestIsDone = false
		
		val localIsDone
			get() = when (Game.currentSide!!) {
				GameServerSide.HOST -> hostIsDone
				GameServerSide.GUEST -> guestIsDone
			}
		
		val bothAreDone get() = hostIsDone && guestIsDone
		
		suspend fun awaitBothDone() {
			while (!bothAreDone)
				delay(100)
		}
		
		fun reset() {
			chosenSkin = null
			hostIsDone = false
			guestIsDone = false
		}
	}
	
	@Serializable
	data class PlayTurn(val whoseTurn: GameServerSide) : GamePhase()
	
	companion object {
		var currentPhase: GamePhase = Deployment
	}
}
