import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromDynamic
import org.w3c.dom.WebSocket

object WebRTCSignalling {
	private lateinit var ws: WebSocket
	
	private var offerIdHandler: ((WebRTCHostId) -> Unit)? = null
	private var answerIdHandler: ((WebRTCJoinId) -> Unit)? = null
	private var listDataHandler: ((WebRTCHostList) -> Unit)? = null
	private var answerDataHandler: ((WebRTCHostAnswer) -> Unit)? = null
	
	@OptIn(ExperimentalSerializationApi::class)
	suspend fun initConnection() {
		if (this::ws.isInitialized)
			ws.close()
		
		ws = WebSocket("wss://franciscusrex.dev/rtc-signal/")
		
		ws.onmessage = { e ->
			val packet = JSON.parse<dynamic>(e.data as String)
			
			when (packet.type) {
				"offer-id" -> {
					val offerId = jsonSerializer.decodeFromDynamic(WebRTCHostId.serializer(), packet)
					offerIdHandler?.invoke(offerId)
				}
				"list-data" -> {
					val listData = jsonSerializer.decodeFromDynamic(WebRTCHostList.serializer(), packet)
					listDataHandler?.invoke(listData)
				}
				"join-id" -> {
					val answerId = jsonSerializer.decodeFromDynamic(WebRTCJoinId.serializer(), packet)
					answerIdHandler?.invoke(answerId)
				}
				"answer-data" -> {
					val answerData = jsonSerializer.decodeFromDynamic(WebRTCHostAnswer.serializer(), packet)
					answerDataHandler?.invoke(answerData)
				}
				"ice-candidate" -> {
					val iceCandidate = packet.candidate.unsafeCast<String>()
					
					GlobalScope.launch {
						WebRTC.receiveIceCandidate(JSON.parse(iceCandidate))
					}
				}
				"connection-error" -> {
					val errorMessage = packet.message.unsafeCast<String>()
					
					GlobalScope.launch {
						Popup.Message("CONNECTION ERROR: $errorMessage", true, "RETURN TO MAIN MENU").display()
						
						mainJob.cancel()
						mainJob = MainScope().launch {
							gameMain()
						}
					}
				}
			}
			
			Unit
		}
		
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
			it.offer = offer
		}
		
		val idDeferred = GlobalScope.async {
			this@WebRTCSignalling::offerIdHandler.await().id
		}
		
		ws.send(offerPacket)
		
		val id = idDeferred.await()
		
		WebRTC.dumpGatheredIceCandidates()
		
		val awaitAnswerJob = GlobalScope.launch {
			val answer = this@WebRTCSignalling::answerDataHandler.await().answer
			WebRTC.host2(answer)
		}
		
		val notCancelled = useId(id)
		
		if (!notCancelled)
			awaitAnswerJob.cancel()
		
		return notCancelled
	}
	
	suspend fun join(getId: suspend (List<String>) -> String?): Boolean {
		initConnection()
		
		val listDataPacket = jsonString {
			it.type = "list-data"
		}
		
		val listDeferred = GlobalScope.async {
			this@WebRTCSignalling::listDataHandler.await().list
		}
		
		ws.send(listDataPacket)
		
		val list = listDeferred.await()
		val id = getId(list.map { it.id })
		val offer = list.firstOrNull { it.id == id }?.offer ?: return false
		
		val answer = WebRTC.join(offer)
		
		val answerPacket = jsonString {
			it.type = "join-answer"
			it.id = id
			it.answer = answer
		}
		
		ws.send(answerPacket)
		
		WebRTC.dumpGatheredIceCandidates()
		
		return true
	}
	
	suspend fun exchangeIce() {
		WebRTC.awaitIceCandidates()
	}
}

@Serializable
data class WebRTCOpenSession(val id: String, val offer: String)

@Serializable
data class WebRTCHostId(val id: String)

@Serializable
data class WebRTCJoinId(val valid: Boolean)

@Serializable
data class WebRTCHostList(val list: List<WebRTCOpenSession>)

@Serializable
data class WebRTCHostAnswer(val answer: String)
