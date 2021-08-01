import kotlinx.coroutines.channels.Channel

sealed class Player(val side: GameServerSide) {
	abstract fun deployPiece(pieceType: PieceType, pos: Vec2, facing: Double)
	abstract fun clearDeploying()
	abstract fun finishDeploying()
	
	abstract suspend fun doTurn()
	abstract suspend fun endTurn()
	
	abstract suspend fun pick(pickRequest: PickRequest): PickResponse
	abstract suspend fun useAbility(pieceId: String, abilityName: String)
	
	object HostPlayer : Player(GameServerSide.HOST) {
		override fun deployPiece(pieceType: PieceType, pos: Vec2, facing: Double) {
			GameSessionData.currentSession!!.addOrReplace(
				GamePiece(
					id = newGamePieceId(),
					type = pieceType,
					owner = side,
					initialLocation = pos,
					initialFacing = facing
				)
			)
		}
		
		override fun clearDeploying() {
			GameSessionData.currentSession!!.removeAllByOwner(GameServerSide.HOST)
		}
		
		override fun finishDeploying() {
			GamePhase.Deployment.hostIsDone = true
			
			GamePacket.send(GamePacket.DoneDeploying)
		}
		
		private val turnEnds = Channel<Unit>()
		
		override suspend fun doTurn() {
			GamePhase.currentPhase = GamePhase.PlayTurn(side)
			
			turnEnds.receive()
		}
		
		override suspend fun endTurn() {
			turnEnds.send(Unit)
			
			GamePhase.currentPhase = GamePhase.PlayTurn(side.other)
		}
		
		override suspend fun pick(pickRequest: PickRequest) = PickHandler.pickLocal(pickRequest)
		
		override suspend fun useAbility(pieceId: String, abilityName: String) {
			val piece = GameSessionData.currentSession!!.pieceById(pieceId)
			val ability = piece.type.stats.abilities.getValue(abilityName)
			
			if (ability.canUse(piece)) {
				ability.use(piece)
				
				GameSessionData.currentSession!!.markDirty(piece.id)
			}
		}
	}
	
	object GuestPlayer : Player(GameServerSide.GUEST) {
		override fun deployPiece(pieceType: PieceType, pos: Vec2, facing: Double) {
			GamePacket.send(GamePacket.PieceDeployed(pieceType, pos, facing))
		}
		
		override fun clearDeploying() {
			GamePacket.send(GamePacket.DeployCleared)
		}
		
		override fun finishDeploying() {
			GamePhase.Deployment.guestIsDone = true
			
			GamePacket.send(GamePacket.DoneDeploying)
		}
		
		override suspend fun doTurn() {
			GamePhase.currentPhase = GamePhase.PlayTurn(side)
			
			GamePacket.awaitOpponentTurnEnd()
		}
		
		override suspend fun endTurn() {
			GamePacket.send(GamePacket.TurnEnded)
			
			GamePhase.awaitPhase { it != GamePhase.PlayTurn(side) }
		}
		
		override suspend fun pick(pickRequest: PickRequest) = PickHandler.pickRemote(pickRequest)
		
		override suspend fun useAbility(pieceId: String, abilityName: String) {
			GamePacket.send(GamePacket.PieceAbilityUsed(pieceId, abilityName))
			GamePacket.awaitAbilityDone()
		}
	}
	
	companion object {
		fun getPlayer(side: GameServerSide) = when (side) {
			GameServerSide.HOST -> HostPlayer
			GameServerSide.GUEST -> GuestPlayer
		}
		
		val currentPlayer: Player?
			get() = Game.currentSide?.let { getPlayer(it) }
	}
}

val Player.other: Player
	get() = Player.getPlayer(side.other)
