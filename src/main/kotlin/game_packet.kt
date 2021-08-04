import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed class GamePacket {
	companion object {
		suspend fun initReception() {
			val receiveJob = GameScope.launch {
				for (jsonText in WebRTC.messageChannel) {
					if (isDevEnv)
						console.log("Received packet $jsonText")
					
					launch {
						when (val packet = jsonSerializer.decodeFromString(serializer(), jsonText)) {
							is GuestReady -> {
								if (Game.currentSide != GameServerSide.HOST)
									throw IllegalStateException("Remote game must not receive guest ready!")
								
								guestReadyChannel.send(Unit)
							}
							is HostReady -> {
								if (Game.currentSide != GameServerSide.GUEST)
									throw IllegalStateException("Local game must not receive host ready!")
								
								send(JoinRequest(playerName!!))
							}
							is JoinRequest -> {
								if (Game.currentSide != GameServerSide.HOST)
									throw IllegalStateException("Remote game must not receive join request!")
								
								val accepted = Popup.YesNoDialogue {
									+"The player ${packet.name} has requested to join your game."
								}.display()
								
								send(JoinResponse(accepted))
								
								joinAcceptChannel.send(accepted)
							}
							is JoinResponse -> {
								if (Game.currentSide != GameServerSide.GUEST)
									throw IllegalStateException("Local game must not receive join response!")
								
								joinAcceptChannel.send(packet.accepted)
							}
							is ChatMessage -> {
								ChatBox.addChatMessage("Opponent", packet.text)
							}
							is AttackMessage -> {
								if (Game.currentSide != GameServerSide.GUEST)
									throw IllegalStateException("Local game must not receive attack notification!")
								
								ChatBox.notifyAttack(packet.source, packet.target, packet.amount)
							}
							is MapLoaded -> {
								if (Game.currentSide != GameServerSide.GUEST)
									throw IllegalStateException("Local game must not receive map data!")
								
								GameSessionData.currentSession = GameSessionData(packet.map, packet.battleSize).also { gsd ->
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
										initialLocation = packet.location,
										initialFacing = packet.facing
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
								
								abilityDoneChannel.send(packet.successful)
							}
							is PieceAddedOrChanged -> {
								if (Game.currentSide != GameServerSide.GUEST)
									throw IllegalStateException("Local game must not receive piece notification!")
								
								GameSessionData.currentSession!!.addOrReplace(packet.piece)
								GameField.drawPiece(packet.piece, true)
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
								
								turnEndChannel.send(Unit)
							}
							is PickReq -> {
								if (Game.currentSide != GameServerSide.GUEST)
									throw IllegalStateException("Local game must not receive pick request!")
								
								send(PickRes(PickHandler.pickLocal(packet.pickRequest)))
							}
							is PickRes -> {
								if (Game.currentSide != GameServerSide.HOST)
									throw IllegalStateException("Remote game must not receive pick response!")
								
								pickResponseChannel.send(packet.pickResponse)
							}
							is GameEnded -> {
								if (Game.currentSide != GameServerSide.GUEST)
									throw IllegalStateException("Local game must not receive game end!")
								
								gameWonChannel.send(packet.winner)
							}
						}
					}
				}
			}
			
			WebRTC.makeDataChannel {
				receiveJob.join()
				
				if (Game.currentSide != null)
					Game.end()
				
				Popup.Message("Connection closed.", true, "Return to Main Menu").display()
				
				gameMain()
			}
			
			Popup.LoadingScreen("Processing handshake protocol...") {
				when (Game.currentSide!!) {
					GameServerSide.HOST -> {
						guestReadyChannel.receive()
						send(HostReady)
					}
					
					GameServerSide.GUEST -> {
						delay(100L) // Give host time to be ready
						send(GuestReady)
					}
				}
			}.display()
		}
		
		fun send(packet: GamePacket) {
			val jsonText = jsonSerializer.encodeToString(serializer(), packet)
			WebRTC.sendData(jsonText)
			
			if (isDevEnv)
				console.log("Sent packet $jsonText")
		}
		
		private val guestReadyChannel = Channel<Unit>()
		
		private val joinAcceptChannel = Channel<Boolean>()
		suspend fun awaitJoinAccept() = joinAcceptChannel.receive()
		
		private val abilityDoneChannel = Channel<Boolean>()
		suspend fun awaitAbilityDone() = abilityDoneChannel.receive()
		
		private val turnEndChannel = Channel<Unit>()
		suspend fun awaitOpponentTurnEnd() = turnEndChannel.receive()
		
		private val pickResponseChannel = Channel<PickResponse>()
		suspend fun awaitPickResponse() = pickResponseChannel.receive()
		
		private val gameWonChannel = Channel<GameServerSide>()
		suspend fun awaitGameWon() = gameWonChannel.receive()
	}
	
	@Serializable
	object GuestReady : GamePacket()
	
	@Serializable
	object HostReady : GamePacket()
	
	@Serializable
	data class JoinRequest(val name: String) : GamePacket()
	
	@Serializable
	data class JoinResponse(val accepted: Boolean) : GamePacket()
	
	@Serializable
	data class ChatMessage(val text: String) : GamePacket()
	
	@Serializable
	data class AttackMessage(val source: DamageSource, val target: GamePiece, val amount: Double) : GamePacket()
	
	@Serializable
	data class MapLoaded(val map: GameMap, val battleSize: Int) : GamePacket()
	
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
