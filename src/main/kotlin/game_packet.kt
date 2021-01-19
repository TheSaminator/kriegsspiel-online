import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed class GamePacket {
	companion object {
		suspend fun initReception() {
			WebRTC.messageHandler = { jsonText ->
				val packet = jsonSerializer.decodeFromString(serializer(), jsonText)
				
				GlobalScope.launch {
					when (packet) {
						is HostReady -> {
							send(JoinRequest(playerName!!))
						}
						is JoinRequest -> {
							if (Game.currentSide != GameServerSide.HOST)
								throw IllegalStateException("Remote game must not receive join request!")
							
							GlobalScope.launch {
								Popup.awaitPopupClose()
								
								val accepted = Popup.YesNoDialogue {
									+"The player ${packet.name} has requested to join your game."
								}.display()
								
								send(JoinResponse(accepted))
								
								if (accepted)
									joinAcceptHandler?.invoke(Unit)
								else
									Game.end()
							}
						}
						is JoinResponse -> {
							if (Game.currentSide != GameServerSide.GUEST)
								throw IllegalStateException("Local game must not receive join response!")
							
							if (packet.accepted)
								joinAcceptHandler?.invoke(Unit)
							else
								Game.end()
						}
						is ChatMessage -> {
							ChatBox.addMessage("Opponent", packet.text)
						}
						is MapLoaded -> {
							if (Game.currentSide != GameServerSide.GUEST)
								throw IllegalStateException("Local game must not receive map data!")
							
							GameSessionData.currentSession = GameSessionData(packet.size, packet.battleType).also { gsd ->
								GameField.drawEverything(gsd)
							}
						}
						is PieceDeployed -> {
							if (Game.currentSide != GameServerSide.HOST)
								throw IllegalStateException("Remote game must not receive piece deployment!")
							
							GameSessionData.currentSession!!.addOrReplace(
								GamePiece(
									id = newGamePieceId(),
									type = packet.pieceType,
									owner = GameServerSide.GUEST,
									location = packet.location,
									facing = packet.facing
								)
							)
						}
						is DeployCleared -> {
							if (Game.currentSide != GameServerSide.HOST)
								throw IllegalStateException("Remote game must not receive deployment erasure!")
							
							GameSessionData.currentSession!!.removeAllByOwner(GameServerSide.GUEST)
						}
						is DoneDeploying -> {
							when (Game.currentSide!!) {
								GameServerSide.HOST -> GamePhase.Deployment.guestIsDone = true
								GameServerSide.GUEST -> GamePhase.Deployment.hostIsDone = true
							}
						}
						is GamePhaseChanged -> {
							if (Game.currentSide != GameServerSide.GUEST)
								throw IllegalStateException("Local game must not receive phase change!")
							
							GamePhase.currentPhase = packet.newPhase
							
							GameSidebar.updateSidebar()
						}
						is PieceAbilityUsed -> {
							if (Game.currentSide != GameServerSide.HOST)
								throw IllegalStateException("Remote game must not receive piece ability usage!")
							
							val piece = GameSessionData.currentSession!!.pieceById(packet.pieceId)
							val ability = piece.type.stats.abilities.getValue(packet.abilityName)
							
							if (ability.canUse(piece)) {
								ability.use(piece)
								
								send(PieceAbilityDone(true))
								
								GameSessionData.currentSession!!.markDirty(piece.id)
							} else
								send(PieceAbilityDone(false))
						}
						is PieceAbilityDone -> {
							if (Game.currentSide != GameServerSide.GUEST)
								throw IllegalStateException("Local game must not receive piece ability completion!")
							
							abilityDoneHandler?.invoke(packet.successful)
						}
						is PieceAddedOrChanged -> {
							if (Game.currentSide != GameServerSide.GUEST)
								throw IllegalStateException("Local game must not receive piece notification!")
							
							GameSessionData.currentSession!!.addOrReplace(packet.piece)
							GameField.drawPiece(packet.piece)
						}
						is PieceDeleted -> {
							if (Game.currentSide != GameServerSide.GUEST)
								throw IllegalStateException("Local game must not receive piece notification!")
							
							GameSessionData.currentSession!!.removeById(packet.pieceId)
							GameField.undrawPiece(packet.pieceId)
						}
						is TurnEnded -> {
							if (Game.currentSide != GameServerSide.HOST)
								throw IllegalStateException("Remote game must not receive turn end!")
							
							turnEndHandler?.invoke(Unit)
						}
						is PickReq -> {
							if (Game.currentSide != GameServerSide.GUEST)
								throw IllegalStateException("Local game must not receive pick request!")
							
							send(PickRes(PickHandler.pickLocal(packet.pickRequest)))
						}
						is PickRes -> {
							if (Game.currentSide != GameServerSide.HOST)
								throw IllegalStateException("Remote game must not receive pick response!")
							
							pickResponseHandler?.invoke(packet.pickResponse)
						}
						is GameEnded -> {
							if (Game.currentSide != GameServerSide.GUEST)
								throw IllegalStateException("Local game must not receive game end!")
							
							gameWonHandler?.invoke(packet.winner)
						}
					}
				}
			}
			
			WebRTC.channelCloseHandler = {
				GlobalScope.launch {
					Game.end()
					
					Popup.Message("Connection cancelled.", true, "Return to Main Menu").display()
					
					main()
				}
			}
			
			WebRTC.makeDataChannel()
		}
		
		fun send(packet: GamePacket) {
			val jsonText = jsonSerializer.encodeToString(serializer(), packet)
			WebRTC.sendData(jsonText)
		}
		
		private var joinAcceptHandler: ((Unit) -> Unit)? = null
		suspend fun awaitJoinAccept() = this::joinAcceptHandler.await()
		
		private var abilityDoneHandler: ((Boolean) -> Unit)? = null
		suspend fun awaitAbilityDone() = this::abilityDoneHandler.await()
		
		private var turnEndHandler: ((Unit) -> Unit)? = null
		suspend fun awaitOpponentTurnEnd() = this::turnEndHandler.await()
		
		private var pickResponseHandler: ((PickResponse) -> Unit)? = null
		suspend fun awaitPickResponse() = this::pickResponseHandler.await()
		
		private var gameWonHandler: ((GameServerSide) -> Unit)? = null
		suspend fun awaitGameWon() = this::gameWonHandler.await()
	}
	
	@Serializable
	object HostReady : GamePacket()
	
	@Serializable
	data class JoinRequest(val name: String) : GamePacket()
	
	@Serializable
	data class JoinResponse(val accepted: Boolean) : GamePacket()
	
	@Serializable
	data class ChatMessage(val text: String) : GamePacket()
	
	@Serializable
	data class MapLoaded(val size: Vec2, val battleType: BattleType) : GamePacket()
	
	@Serializable
	data class PieceDeployed(val pieceType: PieceType, val location: Vec2, val facing: Double) : GamePacket()
	
	@Serializable
	object DeployCleared : GamePacket()
	
	@Serializable
	object DoneDeploying : GamePacket()
	
	@Serializable
	data class GamePhaseChanged(val newPhase: GamePhase) : GamePacket()
	
	@Serializable
	data class PieceAbilityUsed(val pieceId: String, val abilityName: String) : GamePacket()
	
	@Serializable
	data class PieceAbilityDone(val successful: Boolean) : GamePacket()
	
	@Serializable
	data class PieceAddedOrChanged(val piece: GamePiece) : GamePacket()
	
	@Serializable
	data class PieceDeleted(val pieceId: String) : GamePacket()
	
	@Serializable
	object TurnEnded : GamePacket()
	
	@Serializable
	data class PickReq(val pickRequest: PickRequest) : GamePacket()
	
	@Serializable
	data class PickRes(val pickResponse: PickResponse) : GamePacket()
	
	@Serializable
	data class GameEnded(val winner: GameServerSide) : GamePacket()
}
