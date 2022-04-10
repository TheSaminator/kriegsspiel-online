import externals.webrtc.RTCDataChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

object WebRTCSignalling {
	private lateinit var ws: WebSocket
	
	private val offerIdChannel = Channel<String>()
	private val listDataChannel = Channel<List<WebRTCOpenSession>>()
	private val answerDataChannel = Channel<String>()
	
	@OptIn(ExperimentalSerializationApi::class)
	suspend fun initConnection() {
		if (this::ws.isInitialized)
			ws.close()
		
		ws = WebSocket("wss://franciscusrex.dev/rtc-signal/")
		
		ws.addEventListener("message", { e ->
			val packet = jsonSerializer.decodeFromString(SignalServerS2CPacket.serializer(), e.unsafeCast<MessageEvent>().data as String)
			
			GameScope.launch {
				when (packet) {
					is SignalServerS2CPacket.OfferId -> {
						offerIdChannel.send(packet.id)
					}
					is SignalServerS2CPacket.ListData -> {
						listDataChannel.send(packet.list)
					}
					is SignalServerS2CPacket.AnswerData -> {
						answerDataChannel.send(packet.answer)
					}
					is SignalServerS2CPacket.IceCandidate -> {
						launch {
							WebRTC.receiveIceCandidate(JSON.parse(packet.candidate))
						}
					}
					is SignalServerS2CPacket.ConnectionError -> {
						launch {
							Popup.Message("Connection error: ${packet.message}", true, "Return to Main Menu").display()
							
							gameMain()
						}
					}
				}
			}
		})
		
		WebRTC.iceCandidateHandler = { candidate ->
			val candidatePacket = SignalServerC2SPacket.IceCandidate(JSON.stringify(candidate))
			val packet = jsonSerializer.encodeToString(SignalServerC2SPacket.serializer(), candidatePacket)
			
			ws.send(packet)
		}
		
		ws.awaitEvent("open")
	}
	
	suspend fun host(data: String, useId: suspend (String) -> Nothing) {
		initConnection()
		
		val offer = WebRTC.host1()
		
		val offerPacket = SignalServerC2SPacket.HostOffer(playerName!!, GAME_NAME, data, offer)
		val offerPacketEncoded = jsonSerializer.encodeToString(SignalServerC2SPacket.serializer(), offerPacket)
		
		val idDeferred = GameScope.async {
			offerIdChannel.receive()
		}
		
		ws.send(offerPacketEncoded)
		
		val id = idDeferred.await()
		
		WebRTC.dumpGatheredIceCandidates()
		
		val awaitAnswerJob = GameScope.launch {
			val answer = answerDataChannel.receive()
			WebRTC.host2(answer)
		}
		
		coroutineScope {
			val showIdJob = launch { useId(id) }
			awaitAnswerJob.join()
			showIdJob.cancel()
		}
	}
	
	suspend fun join(chooseOffer: suspend (List<WebRTCOpenSession>) -> WebRTCOpenSession?): Boolean {
		initConnection()
		
		val listDataPacket = SignalServerC2SPacket.ListData(GAME_NAME)
		val listDataPacketEncoded = jsonSerializer.encodeToString(SignalServerC2SPacket.serializer(), listDataPacket)
		
		val listDeferred = GameScope.async {
			listDataChannel.receive()
		}
		
		ws.send(listDataPacketEncoded)
		
		val list = listDeferred.await()
		val sess = chooseOffer(list) ?: return false
		val offer = sess.offer
		
		val hasDataChannel = CompletableDeferred<RTCDataChannel>()
		
		val answer = WebRTC.join(offer) { hasDataChannel.complete(it) }
		val answerPacket = SignalServerC2SPacket.JoinAnswer(sess.id, answer)
		val answerPacketEncoded = jsonSerializer.encodeToString(SignalServerC2SPacket.serializer(), answerPacket)
		
		ws.send(answerPacketEncoded)
		
		WebRTC.dumpGatheredIceCandidates()
		hasDataChannel.join()
		
		return true
	}
	
	suspend fun exchangeIce() {
		WebRTC.awaitIceCandidates()
	}
}

@Serializable
sealed class SignalServerC2SPacket {
	@Serializable
	@SerialName("host-offer")
	data class HostOffer(val name: String, val game: String, val data: String, val offer: String) : SignalServerC2SPacket()
	
	@Serializable
	@SerialName("list-data")
	data class ListData(val game: String) : SignalServerC2SPacket()
	
	@Serializable
	@SerialName("join-answer")
	data class JoinAnswer(val id: String, val answer: String) : SignalServerC2SPacket()
	
	@Serializable
	@SerialName("ice-candidate")
	data class IceCandidate(val candidate: String) : SignalServerC2SPacket()
}

@Serializable
data class WebRTCOpenSession(val id: String, val name: String, val data: String, val offer: String)

@Serializable
sealed class SignalServerS2CPacket {
	@Serializable
	@SerialName("offer-id")
	data class OfferId(val id: String) : SignalServerS2CPacket()
	
	@Serializable
	@SerialName("list-data")
	data class ListData(val list: List<WebRTCOpenSession>) : SignalServerS2CPacket()
	
	@Serializable
	@SerialName("answer-data")
	data class AnswerData(val answer: String) : SignalServerS2CPacket()
	
	@Serializable
	@SerialName("ice-candidate")
	data class IceCandidate(val candidate: String) : SignalServerS2CPacket()
	
	@Serializable
	@SerialName("connection-error")
	data class ConnectionError(val message: String) : SignalServerS2CPacket()
}
