import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

var playerName: String? = null

private var mainJob: Job? = null
fun main() {
	mainJob?.cancel()
	mainJob = MainScope().launch {
		val winner = gameMain()
		val message = if (winner == Game.currentSide!!)
			"You have won the battle!"
		else
			"You have been defeated."
		
		Popup.UncloseableMessage(message, true).display()
	}
}

sealed class MainMenuAction {
	data class Play(val side: GameServerSide) : MainMenuAction()
	object ViewKriegspedia : MainMenuAction()
}

suspend fun gameMain(): GameServerSide {
	if (ExitHandler.isAttached)
		ExitHandler.detach()
	
	enableKriegspediaButton()
	
	return when (val action = Popup.MainMenu.display()) {
		is MainMenuAction.Play -> {
			when (action.side) {
				GameServerSide.HOST -> hostGame()
				GameServerSide.GUEST -> joinGame()
			}
		}
		MainMenuAction.ViewKriegspedia -> {
			viewKriegspedia()
			gameMain()
		}
	}
}

suspend fun hostGame(): GameServerSide {
	ExitHandler.attach()
	
	playerName = playerName ?: Popup.ChooseNameScreen.display() ?: return gameMain()
	
	if (!WebRTCSignalling.host { Popup.HostScreen(it).display() }) {
		return gameMain()
	}
	
	Popup.LoadingScreen("Establishing connection...") {
		WebRTCSignalling.exchangeIce()
	}.display()
	
	Game.beginLocal()
	return Game.doLocal()
}

suspend fun joinGame(): GameServerSide {
	ExitHandler.attach()
	
	playerName = playerName ?: Popup.ChooseNameScreen.display() ?: return gameMain()
	
	if (!WebRTCSignalling.join { Popup.JoinScreen(it).display() }) {
		return gameMain()
	}
	
	Popup.LoadingScreen("Establishing connection...") {
		WebRTCSignalling.exchangeIce()
	}.display()
	
	Game.beginRemote()
	return Game.doRemote()
}
