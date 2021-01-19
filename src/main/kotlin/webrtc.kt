import externals.webrtc.*
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
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
			id = 1
			negotiated = true
		})
		
		iceCandidateHandler?.let { handler ->
			rtcPeerConnection.addEventListener("icecandidate", { e ->
				val candidate = e.unsafeCast<RTCPeerConnectionIceEvent>().candidate
				if (isReadyToSend)
					handler(candidate)
				else
					iceCandidateSendQueue.add(candidate)
			})
		}
		
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
		
		dataChannel = rtcPeerConnection.createDataChannel(DATA_CHANNEL_LABEL, configure {
			id = 1
			negotiated = true
		})
		
		iceCandidateHandler?.let { handler ->
			rtcPeerConnection.addEventListener("icecandidate", { e ->
				val candidate = e.unsafeCast<RTCPeerConnectionIceEvent>().candidate
				if (isReadyToSend)
					handler(candidate)
				else
					iceCandidateSendQueue.add(candidate)
			})
		}
		
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
	var iceCandidateHandler: ((RTCIceCandidate?) -> Unit)? = null
	
	fun dumpGatheredIceCandidates() {
		isReadyToSend = true
		
		iceCandidateSendQueue.forEach {
			iceCandidateHandler?.invoke(it)
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
	
	suspend fun makeDataChannel() {
		with(dataChannel) {
			addEventListener("message", messageEventHandler)
			addEventListener("close", {
				removeEventListener("message", messageEventHandler)
				channelCloseHandler?.invoke()
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
	
	var messageHandler: ((String) -> Unit)? = null
	var channelCloseHandler: (() -> Unit)? = null
	
	private val messageEventHandler = object : EventListener {
		override fun handleEvent(event: Event) {
			val data = (event.unsafeCast<MessageEvent>()).data as String
			messageHandler?.invoke(data)
		}
	}
	
	fun sendData(message: String) {
		dataChannel.send(message)
	}
}
