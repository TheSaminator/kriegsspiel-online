import externals.webrtc.RTCConfiguration
import externals.webrtc.RTCIceServer
import externals.webrtc.iceServers
import externals.webrtc.urls
import kotlinx.browser.window
import kotlinx.coroutines.asDeferred
import kotlinx.coroutines.awaitAll

suspend fun getRtcConfig(): RTCConfiguration {
	val rawUrls = window.asDynamic().iceConfig.iceUrls.unsafeCast<Array<String>>()
	val iceRaw = rawUrls.map { configure<RTCIceServer> { urls = it } }
	
	val fetchUrls = window.asDynamic().iceConfig.fetchUrls.unsafeCast<Array<String>>()
	val iceFetched = fetchUrls
		.map { window.fetch(it).asDeferred() }
		.awaitAll()
		.map { it.json().asDeferred() }
		.awaitAll()
		.map { it.unsafeCast<RTCIceServer>() }
	
	return configure {
		iceServers = (iceRaw + iceFetched).toTypedArray()
	}
}
