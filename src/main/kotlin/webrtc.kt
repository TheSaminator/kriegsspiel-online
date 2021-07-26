import externals.webrtc.*
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener

@Suppress("DuplicatedCode")
object WebRTC {
	private lateinit var rtcPeerConnection: RTCPeerConnection
	
	fun closeConn() {
		if (this::rtcPeerConnection.isInitialized && rtcPeerConnection.connectionState != "closed")
			rtcPeerConnection.close()
	}
	
	suspend fun host1(): String {
		closeConn()
		rtcPeerConnection = RTCPeerConnection(getRtcConfig())
		
		dataChannel = rtcPeerConnection.createDataChannel(DATA_CHANNEL_LABEL, configure {
			ordered = true
		})
		
		rtcPeerConnection.addEventListener("icecandidate", { e ->
			val candidate = e.unsafeCast<RTCPeerConnectionIceEvent>().candidate
			if (isReadyToSend)
				iceCandidateHandler(candidate)
			else
				iceCandidateSendQueue.add(candidate)
		})
		
		val offer = rtcPeerConnection.createOffer().await()
		rtcPeerConnection.setLocalDescription(offer).await()
		return JSON.stringify(offer)
	}
	
	suspend fun host2(answerStr: String) {
		val answer = JSON.parse<RTCSessionDescriptionInit>(answerStr)
		rtcPeerConnection.setRemoteDescription(answer).await()
		
		dumpReceivedIceCandidates()
	}
	
	suspend fun join(offerStr: String): String {
		closeConn()
		rtcPeerConnection = RTCPeerConnection(getRtcConfig())
		
		rtcPeerConnection.addEventListener("datachannel", {
			val e = it.unsafeCast<RTCDataChannelEvent>()
			dataChannel = e.channel
			
			if (isDevEnv)
				console.log("Data channel connected on guest")
		})
		
		rtcPeerConnection.addEventListener("icecandidate", { e ->
			val candidate = e.unsafeCast<RTCPeerConnectionIceEvent>().candidate
			if (isReadyToSend)
				iceCandidateHandler(candidate)
			else
				iceCandidateSendQueue.add(candidate)
		})
		
		val offer = JSON.parse<RTCSessionDescriptionInit>(offerStr)
		rtcPeerConnection.setRemoteDescription(offer).await()
		val answer = rtcPeerConnection.createAnswer().await()
		rtcPeerConnection.setLocalDescription(answer).await()
		
		dumpReceivedIceCandidates()
		
		return JSON.stringify(answer)
	}
	
	private var isReadyToSend = false
	private var isReadyToReceive = false
	private val iceCandidateSendQueue = mutableListOf<RTCIceCandidate?>()
	private val iceCandidateReceiveQueue = mutableListOf<RTCIceCandidate?>()
	lateinit var iceCandidateHandler: (RTCIceCandidate?) -> Unit
	
	fun dumpGatheredIceCandidates() {
		isReadyToSend = true
		
		iceCandidateSendQueue.forEach {
			iceCandidateHandler(it)
		}
		
		iceCandidateSendQueue.clear()
	}
	
	private suspend fun dumpReceivedIceCandidates() {
		isReadyToReceive = true
		
		iceCandidateReceiveQueue.forEach {
			receiveIceCandidate(it)
		}
		
		iceCandidateReceiveQueue.clear()
	}
	
	suspend fun receiveIceCandidate(iceCandidate: RTCIceCandidate?) {
		if (isReadyToReceive) {
			if (iceCandidate == null) {
				if (!isChrome)
					rtcPeerConnection.addIceCandidate().await()
			} else
				rtcPeerConnection.addIceCandidate(iceCandidate).await()
		} else {
			iceCandidateReceiveQueue.add(iceCandidate)
		}
	}
	
	suspend fun awaitIceCandidates() {
		while (rtcPeerConnection.iceGatheringState != "complete")
			delay(100L)
	}
	
	const val DATA_CHANNEL_LABEL = "kriegsspiel_data"
	private lateinit var dataChannel: RTCDataChannel
	
	var connectionOpen: Boolean = false
		private set
	
	suspend fun makeDataChannel(onClose: suspend () -> Unit) {
		with(dataChannel) {
			addEventListener("message", messageEventHandler)
			addEventListener("close", {
				removeEventListener("message", messageEventHandler)
				GameScope.launch {
					onClose()
				}
				connectionOpen = false
			})
			
			if (dataChannel.readyState != "open")
				awaitEvent("open")
			
			connectionOpen = true
		}
		
		window.addEventListener("unload", {
			if (connectionOpen)
				dataChannel.close()
		})
	}
	
	val messageChannel = Channel<String>()
	
	private val messageEventHandler = object : EventListener {
		override fun handleEvent(event: Event) {
			val data = (event.unsafeCast<MessageEvent>()).data as String
			
			GameScope.launch {
				messageChannel.send(data)
			}
		}
	}
	
	fun sendData(message: String) {
		if (!connectionOpen) {
			console.error("Cannot send message on closed RTCDataChannel!")
			return
		}
		
		dataChannel.send(message)
	}
}
