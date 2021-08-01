import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.joinAll
import kotlinx.serialization.Serializable

@Serializable
sealed class GamePhase {
	@Serializable
	object Deployment : GamePhase() {
		private var hostJob = Job()
		private var guestJob = Job()
		
		var hostIsDone: Boolean
			get() = hostJob.isCompleted
			set(value) {
				if (value)
					hostJob.complete()
				else
					hostJob = Job()
			}
		
		var guestIsDone: Boolean
			get() = guestJob.isCompleted
			set(value) {
				if (value)
					guestJob.complete()
				else
					guestJob = Job()
			}
		
		val localIsDone
			get() = when (Game.currentSide!!) {
				GameServerSide.HOST -> hostIsDone
				GameServerSide.GUEST -> guestIsDone
			}
		
		val bothAreDone get() = hostIsDone && guestIsDone
		
		suspend fun awaitBothDone() {
			listOf(hostJob, guestJob).joinAll()
		}
		
		fun reset() {
			hostIsDone = false
			guestIsDone = false
		}
	}
	
	@Serializable
	data class PlayTurn(val whoseTurn: GameServerSide) : GamePhase()
	
	companion object {
		private val phaseChanges = MutableStateFlow<GamePhase>(Deployment)
		
		suspend fun awaitPhase(waitUntil: suspend (GamePhase) -> Boolean) {
			if (waitUntil(phaseChanges.value))
				return
			phaseChanges.takeWhile { !waitUntil(it) }.collect { /* no-op */ }
		}
		
		var currentPhase: GamePhase
			get() = phaseChanges.value
			set(value) {
				phaseChanges.value = value
				
				GameSidebar.updateSidebar()
				if (Game.currentSide == GameServerSide.HOST)
					GamePacket.send(GamePacket.GamePhaseChanged(GamePhase.currentPhase))
			}
	}
}
