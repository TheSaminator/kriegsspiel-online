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
	
	private suspend fun beginConn() {
		rtcPeerConnection = createRtcPeerConn()
		
		iceCandidateSendQueue = Channel(Channel.UNLIMITED)
		iceCandidateReceiveQueue = Channel(Channel.UNLIMITED)
	}
	
	suspend fun host1(): String {
		closeConn()
		beginConn()
		
		dataChannel = rtcPeerConnection.createDataChannel(DATA_CHANNEL_LABEL, configure {
			ordered = true
		})
		
		rtcPeerConnection.addEventListener("icecandidate", { e ->
			val candidate = e.unsafeCast<RTCPeerConnectionIceEvent>().candidate
			if (isReadyToSend)
				iceCandidateHandler(candidate)
			else if (candidate == null)
				iceCandidateSendQueue.close()
			else
				GameScope.launch {
					iceCandidateSendQueue.send(candidate)
				}
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
		beginConn()
		
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
				GameScope.launch {
					iceCandidateSendQueue.send(candidate)
				}
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
	
	private lateinit var iceCandidateSendQueue: Channel<RTCIceCandidate?>
	private lateinit var iceCandidateReceiveQueue: Channel<RTCIceCandidate?>
	
	lateinit var iceCandidateHandler: (RTCIceCandidate?) -> Unit
	
	suspend fun dumpGatheredIceCandidates() {
		isReadyToSend = true
		iceCandidateSendQueue.close()
		
		for (candidate in iceCandidateSendQueue) {
			iceCandidateHandler(candidate)
		}
		
		iceCandidateHandler(null)
	}
	
	private suspend fun dumpReceivedIceCandidates() {
		isReadyToReceive = true
		iceCandidateReceiveQueue.close()
		
		for (candidate in iceCandidateReceiveQueue) {
			receiveIceCandidate(candidate)
		}
		
		receiveIceCandidate(null)
	}
	
	suspend fun receiveIceCandidate(iceCandidate: RTCIceCandidate?) {
		if (isReadyToReceive) {
			if (iceCandidate == null) {
				if (!isChrome)
					rtcPeerConnection.addIceCandidate().await()
			} else
				rtcPeerConnection.addIceCandidate(iceCandidate).await()
		} else
			iceCandidateReceiveQueue.send(iceCandidate)
	}
	
	suspend fun awaitIceCandidates() {
		while (rtcPeerConnection.iceGatheringState != "complete")
			delay(100L)
	}
	
	private const val DATA_CHANNEL_LABEL = "kriegsspiel_data"
	private lateinit var dataChannel: RTCDataChannel
	
	private var connectionOpen: Boolean = false
	
	suspend fun makeDataChannel(onClose: suspend () -> Unit) {
		messageChannel = Channel()
		
		with(dataChannel) {
			addEventListener("message", messageEventHandler)
			addEventListener("close", {
				removeEventListener("message", messageEventHandler)
				messageChannel.close()
				connectionOpen = false
				
				GameScope.launch {
					onClose()
				}
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
	
	lateinit var messageChannel: Channel<String>
	
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
