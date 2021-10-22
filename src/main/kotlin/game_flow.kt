import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Suppress("DuplicatedCode")
object Game {
	private suspend fun beginWith(side: GameServerSide) {
		currentSide = side
		
		GamePacket.initReception()
		
		ChatBox.enable()
	}
	
	suspend fun doLocal(): GameServerSide {
		beginWith(GameServerSide.HOST)
		
		if (!GamePacket.awaitJoinAccept()) {
			end()
			awaitCancellation()
		}
		
		val battleSize = Popup.NameableChoice("Select battle size", DeployConstants.pointLevels.keys.toList()) {
			DeployConstants.pointLevels.getValue(it) + " ($it)"
		}.display()
		
		GameSessionData.currentSession = GameSessionData(GameMap.generateMap(), battleSize).also { gsd ->
			GamePacket.send(GamePacket.MapLoaded(gsd.gameMap, battleSize))
			GameField.drawEverything(gsd)
		}
		
		GameSidebar.beginDeploy()
		GameSidebar.deployMenu()
		
		GamePhase.Deployment.awaitBothDone()
		
		GameSidebar.updateSidebar()
		
		var currentPlayer: Player = Player.HostPlayer
		while (true) {
			currentPlayer.doTurn()
			
			GameSessionData.currentSession!!.endTurn(currentPlayer.side)
			
			val winner = GameSessionData.currentSession!!.winner
			if (winner != null) {
				GamePacket.send(GamePacket.GameEnded(winner))
				return winner
			}
			
			currentPlayer = currentPlayer.other
		}
	}
	
	suspend fun doRemote(): GameServerSide {
		beginWith(GameServerSide.GUEST)
		
		if (!Popup.TryLoadingScreen("Awaiting connection acceptance...", "Connection accepted!", "Connection rejected.") { GamePacket.awaitJoinAccept() }.display()) {
			end()
			awaitCancellation()
		}
		
		Popup.LoadingScreen("Awaiting battle parameters from host...") {
			while (GameSessionData.currentSession == null)
				delay(100)
		}.display()
		
		GameSidebar.beginDeploy()
		GameSidebar.deployMenu()
		
		GamePhase.Deployment.awaitBothDone()
		
		GameSidebar.updateSidebar()
		
		return GamePacket.awaitGameWon()
	}
	
	fun end() {
		currentSide = null
		
		ExitHandler.detach()
		
		GameSessionData.currentSession = null
		
		GameSidebar.clearSidebar()
		
		GameField.eraseEverything()
		
		GamePhase.Deployment.reset()
		
		ChatBox.disable()
		
		WebRTC.closeConn()
	}
	
	var currentSide: GameServerSide? = null
		private set
}

@Serializable
enum class GameServerSide {
	HOST, GUEST;
	
	val clientSide: GameClientSide?
		get() = when (Game.currentSide) {
			this -> GameClientSide.PLAYER
			this.other -> GameClientSide.OPPONENT
			else -> null
		}
	
	val other: GameServerSide
		get() = when (this) {
			HOST -> GUEST
			GUEST -> HOST
		}
}

enum class GameClientSide {
	PLAYER, OPPONENT;
	
	val other: GameClientSide
		get() = when (this) {
			PLAYER -> OPPONENT
			OPPONENT -> PLAYER
		}
}
