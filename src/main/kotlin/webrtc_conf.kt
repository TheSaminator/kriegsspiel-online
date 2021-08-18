import externals.webrtc.RTCConfiguration
import externals.webrtc.RTCIceServer
import externals.webrtc.RTCPeerConnection
import kotlinx.browser.window
import kotlinx.coroutines.await

suspend fun createRtcPeerConn() = if (isDevEnv)
	RTCPeerConnection(getDevEnvRtcConfig())
else
	RTCPeerConnection(getRtcConfig())

fun getDevEnvRtcConfig() = configure<RTCConfiguration> {
	val stunServer = configure<RTCIceServer> { urls = "stun:franciscusrex.dev" }
	
	iceServers = arrayOf(stunServer)
}

suspend fun getRtcConfig() = configure<RTCConfiguration> {
	val stunServer = configure<RTCIceServer> { urls = "stun:franciscusrex.dev" }
	val turnServer = window
		.fetch("https://franciscusrex.dev/turn/confmaker.php?sitename=$GAME_NAME")
		.await()
		.json()
		.await()
		.unsafeCast<RTCIceServer>()
	
	iceServers = arrayOf(stunServer, turnServer)
}
