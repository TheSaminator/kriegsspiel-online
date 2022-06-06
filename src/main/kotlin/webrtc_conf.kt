import externals.webrtc.RTCConfiguration
import externals.webrtc.RTCIceServer
import externals.webrtc.RTCPeerConnection

fun createRtcPeerConn() = RTCPeerConnection(getRtcConfig())

fun getRtcConfig() = configure<RTCConfiguration> {
	iceServers = arrayOf(configure<RTCIceServer> { urls = "stun.l.google.com:19302" })
}
