import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Suppress("DuplicatedCode")
object Game {
	private suspend fun beginWith(side: GameServerSide) {
		currentSide = side
		
		GamePacket.initReception()
		
		ChatBox.enable()
	}
	
	suspend fun beginLocal() = beginWith(GameServerSide.HOST)
	suspend fun beginRemote() = beginWith(GameServerSide.GUEST)
	
	suspend fun doLocal(): GameServerSide {
		if (!GamePacket.awaitJoinAccept()) {
			end()
			awaitCancellation()
		}
		
		val battleType = Popup.NameableChoice("Select battle type", BattleType.values().toList(), BattleType::displayName).display()
		
		val battleSize = Popup.NameableChoice("Select battle size", DeployConstants.pointLevels.keys.toList()) {
			DeployConstants.pointLevels.getValue(it) + " ($it)"
		}.display()
		
		GameSessionData.currentSession = GameSessionData(GameSessionData.randomSize(battleType), battleType, battleSize).also { gsd ->
			GamePacket.send(GamePacket.MapLoaded(gsd.mapSize, gsd.battleType, battleSize))
			GameField.drawEverything(gsd)
		}
		
		if (battleType.usesSkins)
			GamePhase.Deployment.chosenSkin = Popup.NameableChoice("Select your faction skin", BattleFactionSkin.values().filter {
				it.forBattleType == battleType
			}, BattleFactionSkin::displayName).display()
		
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
		if (!Popup.TryLoadingScreen("Awaiting connection acceptance...", "Connection accepted!", "Connection rejected.") { GamePacket.awaitJoinAccept() }.display()) {
			end()
			awaitCancellation()
		}
		
		Popup.LoadingScreen("Awaiting battle parameters from host...", "Battle parameters received!") {
			while (GameSessionData.currentSession == null)
				delay(100)
		}.display()
		
		val battleType = GameSessionData.currentSession!!.battleType
		if (battleType.usesSkins)
			GamePhase.Deployment.chosenSkin = Popup.NameableChoice("Select your faction skin", BattleFactionSkin.values().filter {
				it.forBattleType == battleType
			}, BattleFactionSkin::displayName).display()
		
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
		
		ChatBox.disable()
		
		WebRTC.closeConn()
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
