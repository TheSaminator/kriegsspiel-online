import externals.webrtc.RTCConfiguration
import externals.webrtc.RTCIceServer
import kotlinx.browser.window
import kotlinx.coroutines.await

suspend fun getRtcConfig() = configure<RTCConfiguration> {
	val stunServer = configure<RTCIceServer> { urls = "stun:franciscusrex.dev" }
	val turnServer = window
		.fetch("https://franciscusrex.dev/turn/confmaker.php?sitename=kriegsspiel")
		.await()
		.json()
		.await()
		.unsafeCast<RTCIceServer>()
	
	iceServers = arrayOf(stunServer, turnServer)
}
