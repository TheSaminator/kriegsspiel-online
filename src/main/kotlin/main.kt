import kotlinx.browser.window
import kotlinx.coroutines.*

const val GAME_NAME = "kriegsspiel-online"

private const val playerNameStorageKey = "playerName"
var playerName: String? = null
	private set

private suspend fun choosePlayerName(): String? {
	// have to use try-catch in case localStorage is disabled
	return try {
		window.localStorage.getItem(playerNameStorageKey)!!
	} catch (ex: dynamic) {
		Popup.ChooseNameScreen.display()?.also {
			try {
				window.localStorage.setItem(playerNameStorageKey, it)
			} catch (ex: dynamic) {
				// cannot put stuff into local storage, whatever
			}
		}
	}
}

private val AppScope = MainScope() + CoroutineExceptionHandler { _, error ->
	console.error("Unhandled coroutine exception", error.stackTraceToString())
}

private var mainJob: Job? = null

val GameScope: CoroutineScope
	get() = mainJob?.let { AppScope + it } ?: AppScope

fun main() {
	AppScope.launch {
		suspendMain()
	}
}

suspend fun suspendMain() {
	Kriegspedia.attachKriegspediaButton()
	PieceLayer.attachToggleAirUnitsButton()
	
	Popup.LoadingScreen("Loading assets...") {
		GamePiece.preloadAllImages()
	}.display()
	
	gameMain()
}

fun gameMain() {
	mainJob?.cancel()
	mainJob = AppScope.launch {
		val winner = playMain()
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

suspend fun playMain(): GameServerSide {
	if (ExitHandler.isAttached)
		ExitHandler.detach()
	
	return when (val action = Popup.MainMenu.display()) {
		is MainMenuAction.Play -> {
			when (action.side) {
				GameServerSide.HOST -> hostGame()
				GameServerSide.GUEST -> joinGame()
			}
		}
		MainMenuAction.ViewKriegspedia -> {
			Kriegspedia.viewKriegspedia()
			playMain()
		}
	}
}

suspend fun hostGame(): GameServerSide {
	ExitHandler.attach()
	
	playerName = playerName ?: choosePlayerName() ?: return playMain()
	
	if (!WebRTCSignalling.host { Popup.HostScreen(it).display() }) {
		return playMain()
	}
	
	Popup.LoadingScreen("Establishing connection...") {
		WebRTCSignalling.exchangeIce()
	}.display()
	
	return Game.doLocal()
}

suspend fun joinGame(): GameServerSide {
	ExitHandler.attach()
	
	playerName = playerName ?: choosePlayerName() ?: return playMain()
	
	if (!WebRTCSignalling.join { Popup.JoinScreen(it).display() }) {
		return playMain()
	}
	
	Popup.LoadingScreen("Establishing connection...") {
		WebRTCSignalling.exchangeIce()
	}.display()
	
	return Game.doRemote()
}
