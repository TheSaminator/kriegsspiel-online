import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromDynamic
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

object WebRTCSignalling {
	private lateinit var ws: WebSocket
	
	private val offerIdChannel = Channel<WebRTCHostId>()
	private val listDataChannel = Channel<WebRTCHostList>()
	private val answerDataChannel = Channel<WebRTCHostAnswer>()
	
	@OptIn(ExperimentalSerializationApi::class)
	suspend fun initConnection() {
		if (this::ws.isInitialized)
			ws.close()
		
		ws = WebSocket("wss://franciscusrex.dev/rtc-signal/")
		
		ws.addEventListener("message", { e ->
			val packet = JSON.parse<dynamic>(e.unsafeCast<MessageEvent>().data as String)
			
			GameScope.launch {
				when (packet.type) {
					"offer-id" -> {
						val offerId = jsonSerializer.decodeFromDynamic(WebRTCHostId.serializer(), packet)
						offerIdChannel.send(offerId)
					}
					"list-data" -> {
						val listData = jsonSerializer.decodeFromDynamic(WebRTCHostList.serializer(), packet)
						listDataChannel.send(listData)
					}
					"answer-data" -> {
						val answerData = jsonSerializer.decodeFromDynamic(WebRTCHostAnswer.serializer(), packet)
						answerDataChannel.send(answerData)
					}
					"ice-candidate" -> {
						val iceCandidate = packet.candidate.unsafeCast<String>()
						
						GameScope.launch {
							WebRTC.receiveIceCandidate(JSON.parse(iceCandidate))
						}
					}
					"connection-error" -> {
						val errorMessage = packet.message.unsafeCast<String>()
						
						GameScope.launch {
							Popup.Message("Connection error: $errorMessage", true, "Return to Main Menu").display()
							
							gameMain()
						}
					}
				}
			}
		})
		
		WebRTC.iceCandidateHandler = { candidate ->
			val candidatePacket = jsonString {
				it.type = "ice-candidate"
				it.candidate = JSON.stringify(candidate)
			}
			
			ws.send(candidatePacket)
		}
		
		ws.awaitEvent("open")
	}
	
	suspend fun host(useId: suspend (String) -> Boolean): Boolean {
		initConnection()
		
		val offer = WebRTC.host1()
		
		val offerPacket = jsonString {
			it.type = "host-offer"
			it.name = playerName!!
			it.game = GAME_NAME
			it.offer = offer
		}
		
		val idDeferred = GameScope.async {
			offerIdChannel.receive().id
		}
		
		ws.send(offerPacket)
		
		val id = idDeferred.await()
		
		WebRTC.dumpGatheredIceCandidates()
		
		val awaitAnswerJob = GameScope.launch {
			val answer = answerDataChannel.receive().answer
			WebRTC.host2(answer)
		}
		
		val notCancelled = useId(id)
		
		if (notCancelled)
			Popup.LoadingScreen("Awaiting peer connection...") {
				awaitAnswerJob.join()
			}.display()
		else
			awaitAnswerJob.cancel()
		
		return notCancelled
	}
	
	suspend fun join(chooseOffer: suspend (List<WebRTCOpenSession>) -> WebRTCOpenSession?): Boolean {
		initConnection()
		
		val listDataPacket = jsonString {
			it.type = "list-data"
			it.game = GAME_NAME
		}
		
		val listDeferred = GameScope.async {
			listDataChannel.receive().list
		}
		
		ws.send(listDataPacket)
		
		val list = listDeferred.await()
		val sess = chooseOffer(list) ?: return false
		val offer = sess.offer
		
		val hasDataChannel = Job()
		
		val answer = WebRTC.join(offer) { _ -> hasDataChannel.complete() }
		
		val answerPacket = jsonString {
			it.type = "join-answer"
			it.id = sess.id
			it.answer = answer
		}
		
		ws.send(answerPacket)
		
		WebRTC.dumpGatheredIceCandidates()
		
		hasDataChannel.join()
		
		return true
	}
	
	suspend fun exchangeIce() {
		WebRTC.awaitIceCandidates()
	}
}

@Serializable
data class WebRTCOpenSession(val id: String, val name: String, val offer: String)

@Serializable
data class WebRTCHostId(val id: String)

@Serializable
data class WebRTCHostList(val list: List<WebRTCOpenSession>)

@Serializable
data class WebRTCHostAnswer(val answer: String)
