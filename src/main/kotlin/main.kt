import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

var playerName: String? = null
lateinit var mainJob: Job

fun main() {
	if (::mainJob.isInitialized)
		mainJob.cancel()
	
	mainJob = MainScope().launch {
		GamePhase.Deployment.reset()
		
		val winner = gameMain()
		val message = if (winner == Game.currentSide!!)
			"YOU HAVE WON THE BATTLE!"
		else
			"YOU HAVE BEEN DEFEATED!"
		
		Popup.UncloseableMessage(message, true).display()
	}
}

suspend fun gameMain(): GameServerSide {
	if (ExitHandler.isAttached)
		ExitHandler.detach()
	
	return when (Popup.MainMenu.display()) {
		GameServerSide.HOST -> hostGame()
		GameServerSide.GUEST -> joinGame()
	}
}

suspend fun hostGame(): GameServerSide {
	ExitHandler.attach()
	
	playerName = playerName ?: Popup.ChooseNameScreen.display() ?: return gameMain()
	
	if (!WebRTCSignalling.host { Popup.HostScreen(it).display() }) {
		return gameMain()
	}
	
	Popup.LoadingScreen("CONNECTING...", "CONNECTED, CLICK TO CONTINUE") {
		WebRTCSignalling.exchangeIce()
	}
	
	Game.beginLocal()
	return Game.doLocal()
}

suspend fun joinGame(): GameServerSide {
	ExitHandler.attach()
	
	playerName = playerName ?: Popup.ChooseNameScreen.display() ?: return gameMain()
	
	if (!WebRTCSignalling.join { Popup.JoinScreen(it).display() }) {
		return gameMain()
	}
	
	Popup.LoadingScreen("CONNECTING...", "CONNECTED, CLICK TO CONTINUE") {
		WebRTCSignalling.exchangeIce()
	}
	
	Game.beginRemote()
	return Game.doRemote()
}
