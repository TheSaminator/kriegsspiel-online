import externals.webrtc.RTCConfiguration
import externals.webrtc.RTCIceServer
import externals.webrtc.iceServers
import externals.webrtc.urls
import kotlinx.browser.window
import kotlinx.coroutines.await

suspend fun getRtcConfig(): RTCConfiguration {
	return configure {
		iceServers = arrayOf(
			configure { urls = "stun:franciscusrex.dev" },
			*window
				.fetch("https://franciscusrex.dev/turn/confmaker.php?sitename=kriegsspiel")
				.await()
				.json()
				.await()
				.unsafeCast<Array<RTCIceServer>>()
		)
	}
}
