import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

object Game {
	private suspend fun beginWith(side: GameServerSide) {
		currentSide = side
		
		GamePacket.initReception()
		
		ChatBox.enable()
	}
	
	suspend fun beginLocal() = beginWith(GameServerSide.HOST)
	suspend fun beginRemote() = beginWith(GameServerSide.GUEST)
	
	suspend fun doLocal(): GameServerSide {
		val battleType = Popup.NameableChoice(BattleType.values().toList(), BattleType::displayName).display()
		
		GameSessionData.currentSession = GameSessionData(GameSessionData.randomSize(battleType), battleType).also { gsd ->
			GamePacket.send(GamePacket.MapLoaded(gsd.mapSize, gsd.battleType))
			GameField.drawEverything(gsd)
		}
		
		if (battleType.usesSkins)
			GamePhase.Deployment.chosenSkin = Popup.NameableChoice(BattleFactionSkin.values().filter {
				it.forBattleType == battleType
			}, BattleFactionSkin::displayName).display()
		
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
		while (GameSessionData.currentSession == null)
			delay(100)
		
		val battleType = GameSessionData.currentSession!!.battleType
		if (battleType.usesSkins)
			GamePhase.Deployment.chosenSkin = Popup.NameableChoice(BattleFactionSkin.values().filter {
				it.forBattleType == battleType
			}, BattleFactionSkin::displayName).display()
		
		GameSidebar.deployMenu()
		
		GamePhase.Deployment.awaitBothDone()
		
		GameSidebar.updateSidebar()
		
		return GamePacket.awaitGameWon()
	}
	
	fun end() {
		if (currentSide == null)
			throw IllegalStateException("Game not initialized yet!")
		
		currentSide = null
		
		ExitHandler.detach()
		
		GameSessionData.currentSession = null
		
		GameSidebar.clearSidebar()
		
		GameField.eraseEverything()
		
		ChatBox.disable()
	}
	
	var currentSide: GameServerSide? = null
		private set
}

@Serializable
enum class GameServerSide {
	HOST, GUEST;
	
	val other: GameServerSide
		get() = when (this) {
			HOST -> GUEST
			GUEST -> HOST
		}
}
