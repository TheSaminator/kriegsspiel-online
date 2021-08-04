@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS", "EXTERNAL_DELEGATION", "UnsafeCastFromDynamic", "unused", "NOTHING_TO_INLINE", "UNUSED_PARAMETER")

package externals.webrtc

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.ArrayBufferView
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.EventInit
import org.w3c.dom.EventListenerOptions
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.EventTarget
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamTrack
import org.w3c.files.Blob
import kotlin.js.Promise

external interface DoubleRange {
	var max: Double?
		get() = definedExternally
		set(value) = definedExternally
	var min: Double?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCAnswerOptions : RTCOfferAnswerOptions

external interface RTCConfiguration {
	var iceServers: Array<RTCIceServer>?
		get() = definedExternally
		set(value) = definedExternally
	var iceTransportPolicy: String? /* "all" | "relay" */
		get() = definedExternally
		set(value) = definedExternally
	var bundlePolicy: String? /* "balanced" | "max-bundle" | "max-compat" */
		get() = definedExternally
		set(value) = definedExternally
	var rtcpMuxPolicy: String? /* "negotiate" | "require" */
		get() = definedExternally
		set(value) = definedExternally
	var peerIdentity: String?
		get() = definedExternally
		set(value) = definedExternally
	var certificates: Array<RTCCertificate>?
		get() = definedExternally
		set(value) = definedExternally
	var iceCandidatePoolSize: Number?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCDTMFToneChangeEventInit : EventInit {
	var tone: String
}

external interface RTCDataChannelEventInit : EventInit {
	var channel: RTCDataChannel
}

external interface RTCDataChannelInit {
	var ordered: Boolean?
		get() = definedExternally
		set(value) = definedExternally
	var maxPacketLifeTime: Number?
		get() = definedExternally
		set(value) = definedExternally
	var maxRetransmits: Number?
		get() = definedExternally
		set(value) = definedExternally
	var protocol: String?
		get() = definedExternally
		set(value) = definedExternally
	var negotiated: Boolean?
		get() = definedExternally
		set(value) = definedExternally
	var id: Number?
		get() = definedExternally
		set(value) = definedExternally
	var priority: String? /* "high" | "low" | "medium" | "very-low" */
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCDtlsFingerprint {
	var algorithm: String?
		get() = definedExternally
		set(value) = definedExternally
	var value: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCErrorEventInit : EventInit {
	var error: RTCError
}

external interface RTCErrorInit {
	var errorDetail: String /* "data-channel-failure" | "dtls-failure" | "fingerprint-failure" | "hardware-encoder-error" | "hardware-encoder-not-available" | "idp-bad-script-failure" | "idp-execution-failure" | "idp-load-failure" | "idp-need-login" | "idp-timeout" | "idp-tls-failure" | "idp-token-expired" | "idp-token-invalid" | "sctp-failure" | "sdp-syntax-error" */
	var httpRequestStatusCode: Number?
		get() = definedExternally
		set(value) = definedExternally
	var receivedAlert: Number?
		get() = definedExternally
		set(value) = definedExternally
	var sctpCauseCode: Number?
		get() = definedExternally
		set(value) = definedExternally
	var sdpLineNumber: Number?
		get() = definedExternally
		set(value) = definedExternally
	var sentAlert: Number?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCIceCandidateComplete

external interface RTCIceCandidateDictionary {
	var foundation: String?
		get() = definedExternally
		set(value) = definedExternally
	var ip: String?
		get() = definedExternally
		set(value) = definedExternally
	var msMTurnSessionId: String?
		get() = definedExternally
		set(value) = definedExternally
	var port: Number?
		get() = definedExternally
		set(value) = definedExternally
	var priority: Number?
		get() = definedExternally
		set(value) = definedExternally
	var protocol: String? /* "tcp" | "udp" */
		get() = definedExternally
		set(value) = definedExternally
	var relatedAddress: String?
		get() = definedExternally
		set(value) = definedExternally
	var relatedPort: Number?
		get() = definedExternally
		set(value) = definedExternally
	var tcpType: String? /* "active" | "passive" | "so" */
		get() = definedExternally
		set(value) = definedExternally
	var type: String? /* "host" | "prflx" | "relay" | "srflx" */
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCIceCandidateInit {
	var candidate: String?
		get() = definedExternally
		set(value) = definedExternally
	var sdpMLineIndex: Number?
		get() = definedExternally
		set(value) = definedExternally
	var sdpMid: String?
		get() = definedExternally
		set(value) = definedExternally
	var usernameFragment: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCIceCandidatePair {
	var local: RTCIceCandidate?
		get() = definedExternally
		set(value) = definedExternally
	var remote: RTCIceCandidate?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCIceParameters {
	var password: String?
		get() = definedExternally
		set(value) = definedExternally
	var usernameFragment: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCIceServer {
	var credentialType: String? /* "oauth" | "password" */
		get() = definedExternally
		set(value) = definedExternally
	var credential: dynamic /* String? | RTCOAuthCredential? */
		get() = definedExternally
		set(value) = definedExternally
	var urls: dynamic /* String | Array<String> */
		get() = definedExternally
		set(value) = definedExternally
	var username: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCIdentityProviderOptions {
	var peerIdentity: String?
		get() = definedExternally
		set(value) = definedExternally
	var protocol: String?
		get() = definedExternally
		set(value) = definedExternally
	var usernameHint: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCOAuthCredential {
	var accessToken: String
	var macKey: String
}

external interface RTCOfferAnswerOptions {
	var voiceActivityDetection: Boolean?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCOfferOptions : RTCOfferAnswerOptions {
	var iceRestart: Boolean?
		get() = definedExternally
		set(value) = definedExternally
	var offerToReceiveAudio: Boolean?
		get() = definedExternally
		set(value) = definedExternally
	var offerToReceiveVideo: Boolean?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCPeerConnectionIceErrorEventInit : EventInit {
	var errorCode: Number
	var hostCandidate: String?
		get() = definedExternally
		set(value) = definedExternally
	var statusText: String?
		get() = definedExternally
		set(value) = definedExternally
	var url: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCPeerConnectionIceEventInit : EventInit {
	var candidate: RTCIceCandidate?
		get() = definedExternally
		set(value) = definedExternally
	var url: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtcpParameters {
	var cname: String?
		get() = definedExternally
		set(value) = definedExternally
	var reducedSize: Boolean?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtpCapabilities {
	var codecs: Array<RTCRtpCodecCapability>
	var headerExtensions: Array<RTCRtpHeaderExtensionCapability>
}

external interface RTCRtpCodecCapability {
	var mimeType: String
	var channels: Number?
		get() = definedExternally
		set(value) = definedExternally
	var clockRate: Number
	var sdpFmtpLine: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtpCodecParameters {
	var mimeType: String
	var channels: Number?
		get() = definedExternally
		set(value) = definedExternally
	var sdpFmtpLine: String?
		get() = definedExternally
		set(value) = definedExternally
	var clockRate: Number
	var payloadType: Number
}

external interface RTCRtpCodingParameters {
	var rid: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtpContributingSource {
	var source: Number
	var voiceActivityFlag: Boolean?
		get() = definedExternally
		set(value) = definedExternally
	var audioLevel: Number?
		get() = definedExternally
		set(value) = definedExternally
	var rtpTimestamp: Number
	var timestamp: Number
}

external interface RTCRtpDecodingParameters : RTCRtpCodingParameters

external interface RTCRtpEncodingParameters : RTCRtpCodingParameters {
	var scaleResolutionDownBy: Number?
		get() = definedExternally
		set(value) = definedExternally
	var active: Boolean?
		get() = definedExternally
		set(value) = definedExternally
	var codecPayloadType: Number?
		get() = definedExternally
		set(value) = definedExternally
	var dtx: String? /* "disabled" | "enabled" */
		get() = definedExternally
		set(value) = definedExternally
	var maxBitrate: Number?
		get() = definedExternally
		set(value) = definedExternally
	var maxFramerate: Number?
		get() = definedExternally
		set(value) = definedExternally
	var ptime: Number?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtpFecParameters {
	var mechanism: String?
		get() = definedExternally
		set(value) = definedExternally
	var ssrc: Number?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtpHeaderExtensionCapability {
	var uri: String?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtpHeaderExtensionParameters {
	var encrypted: Boolean?
		get() = definedExternally
		set(value) = definedExternally
	var id: Number
	var uri: String
}

external interface RTCRtpParameters {
	var transactionId: String
	var codecs: Array<RTCRtpCodecParameters>
	var headerExtensions: Array<RTCRtpHeaderExtensionParameters>
	var rtcp: RTCRtcpParameters
}

external interface RTCRtpReceiveParameters : RTCRtpParameters {
	var encodings: Array<RTCRtpDecodingParameters>
}

external interface RTCRtpRtxParameters {
	var ssrc: Number?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtpSendParameters : RTCRtpParameters {
	var degradationPreference: String? /* "balanced" | "maintain-framerate" | "maintain-resolution" */
		get() = definedExternally
		set(value) = definedExternally
	var encodings: Array<RTCRtpEncodingParameters>
	var priority: String? /* "high" | "low" | "medium" | "very-low" */
		get() = definedExternally
		set(value) = definedExternally
	override var transactionId: String
}

external interface RTCRtpSynchronizationSource : RTCRtpContributingSource {
	override var voiceActivityFlag: Boolean?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCRtpTransceiverInit {
	var direction: String? /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
		get() = definedExternally
		set(value) = definedExternally
	var streams: Array<MediaStream>?
		get() = definedExternally
		set(value) = definedExternally
	var sendEncodings: Array<RTCRtpEncodingParameters>?
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCSessionDescriptionInit {
	var sdp: String?
		get() = definedExternally
		set(value) = definedExternally
	var type: String? /* "answer" | "offer" | "pranswer" | "rollback" */
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCTrackEventInit : EventInit {
	var receiver: RTCRtpReceiver
	var streams: Array<MediaStream>?
		get() = definedExternally
		set(value) = definedExternally
	var track: MediaStreamTrack
	var transceiver: RTCRtpTransceiver
}

external interface RTCCertificate {
	var expires: Number
	fun getAlgorithm(): String
	fun getFingerprints(): Array<RTCDtlsFingerprint>
}

external interface RTCDTMFSenderEventMap {
	var tonechange: RTCDTMFToneChangeEvent
}

external interface RTCDTMFSender : EventTarget {
	var canInsertDTMF: Boolean
	var ontonechange: ((self: RTCDTMFSender, ev: RTCDTMFToneChangeEvent) -> Any)?
	var toneBuffer: String
	fun insertDTMF(tones: String, duration: Number = definedExternally, interToneGap: Number = definedExternally)
	fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions = definedExternally)
	fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions = definedExternally)
}

external interface RTCDTMFToneChangeEvent : Event {
	var tone: String
}

external interface RTCDataChannelEventMap {
	var bufferedamountlow: Event
	var close: Event
	var error: RTCErrorEvent
	var message: MessageEvent
	var open: Event
}

external interface RTCDataChannel : EventTarget {
	var label: String
	var ordered: Boolean
	var maxPacketLifeTime: Number?
	var maxRetransmits: Number?
	var protocol: String
	var negotiated: Boolean
	var id: Number?
	var readyState: String /* "closed" | "closing" | "connecting" | "open" */
	var bufferedAmount: Number
	var bufferedAmountLowThreshold: Number
	fun close()
	fun send(data: String)
	fun send(data: Blob)
	fun send(data: ArrayBuffer)
	fun send(data: ArrayBufferView)
	var onopen: ((self: RTCDataChannel, ev: Event) -> Any)?
	var onmessage: ((self: RTCDataChannel, ev: MessageEvent) -> Any)?
	var onbufferedamountlow: ((self: RTCDataChannel, ev: Event) -> Any)?
	var onclose: ((self: RTCDataChannel, ev: Event) -> Any)?
	var binaryType: String
	var onerror: ((self: RTCDataChannel, ev: RTCErrorEvent) -> Any)?
	var priority: String /* "high" | "low" | "medium" | "very-low" */
	fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions = definedExternally)
	fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions = definedExternally)
}

external interface RTCDataChannelEvent : Event {
	var channel: RTCDataChannel
}

external interface RTCDtlsTransportEventMap {
	var error: RTCErrorEvent
	var statechange: Event
}

external interface RTCDtlsTransport : EventTarget {
	var transport: RTCIceTransport
	fun getRemoteCertificates(): Array<ArrayBuffer>
	var onstatechange: ((self: RTCDtlsTransport, ev: Event) -> Any)?
	var iceTransport: RTCIceTransport
	var onerror: ((self: RTCDtlsTransport, ev: RTCErrorEvent) -> Any)?
	var state: String /* "closed" | "connected" | "connecting" | "failed" | "new" */
	fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions = definedExternally)
	fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions = definedExternally)
}

external interface RTCDtlsTransportStateChangedEvent : Event {
	var state: String /* "closed" | "connected" | "connecting" | "failed" | "new" */
}

external interface RTCError : Throwable {
	var errorDetail: String /* "data-channel-failure" | "dtls-failure" | "fingerprint-failure" | "hardware-encoder-error" | "hardware-encoder-not-available" | "idp-bad-script-failure" | "idp-execution-failure" | "idp-load-failure" | "idp-need-login" | "idp-timeout" | "idp-tls-failure" | "idp-token-expired" | "idp-token-invalid" | "sctp-failure" | "sdp-syntax-error" */
	var httpRequestStatusCode: Number?
	var receivedAlert: Number?
	var sctpCauseCode: Number?
	var sdpLineNumber: Number?
	var sentAlert: Number?
}

external interface RTCErrorEvent : Event {
	var error: RTCError
}

external interface RTCIceCandidate {
	var candidate: String
	var component: String /* "rtcp" | "rtp" */
	var foundation: String?
	var port: Number?
	var priority: Number?
	var protocol: String /* "tcp" | "udp" */
	var relatedAddress: String?
	var relatedPort: Number?
	var sdpMLineIndex: Number?
	var sdpMid: String?
	var tcpType: String /* "active" | "passive" | "so" */
	var type: String /* "host" | "prflx" | "relay" | "srflx" */
	var usernameFragment: String?
	fun toJSON(): RTCIceCandidateInit
}

external interface RTCIceCandidatePairChangedEvent : Event {
	var pair: RTCIceCandidatePair
}

external interface RTCIceGathererEvent : Event {
	var candidate: dynamic /* RTCIceCandidateDictionary | RTCIceCandidateComplete */
		get() = definedExternally
		set(value) = definedExternally
}

external interface RTCIceTransportEventMap {
	var gatheringstatechange: Event
	var selectedcandidatepairchange: Event
	var statechange: Event
}

external interface RTCIceTransport : EventTarget {
	var gatheringState: String /* "complete" | "gathering" | "new" */
	fun getLocalCandidates(): Array<RTCIceCandidate>
	fun getRemoteCandidates(): Array<RTCIceCandidate>
	fun getLocalParameters(): RTCIceParameters?
	fun getRemoteParameters(): RTCIceParameters?
	var onstatechange: ((self: RTCIceTransport, ev: Event) -> Any)?
	var ongatheringstatechange: ((self: RTCIceTransport, ev: Event) -> Any)?
	var onselectedcandidatepairchange: ((self: RTCIceTransport, ev: Event) -> Any)?
	var component: String /* "rtcp" | "rtp" */
	var role: String /* "controlled" | "controlling" | "unknown" */
	var state: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
	fun getSelectedCandidatePair(): RTCIceCandidatePair?
	fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions = definedExternally)
	fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions = definedExternally)
}

external interface RTCIceTransportStateChangedEvent : Event {
	var state: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
}

external interface RTCIdentityAssertion {
	var idp: String
	var name: String
}

inline fun RTCPeerConnection(config: RTCConfiguration) = js("new RTCPeerConnection(config)").unsafeCast<RTCPeerConnection>()

external interface RTCPeerConnection : EventTarget {
	fun createOffer(options: RTCOfferOptions = definedExternally): Promise<RTCSessionDescriptionInit>
	fun createOffer(): Promise<RTCSessionDescriptionInit>
	fun createAnswer(options: RTCAnswerOptions = definedExternally): Promise<RTCSessionDescriptionInit>
	fun createAnswer(): Promise<RTCSessionDescriptionInit>
	fun setLocalDescription(description: RTCSessionDescriptionInit): Promise<Unit>
	var localDescription: RTCSessionDescription?
	var currentLocalDescription: RTCSessionDescription?
	var pendingLocalDescription: RTCSessionDescription?
	fun setRemoteDescription(description: RTCSessionDescriptionInit): Promise<Unit>
	var remoteDescription: RTCSessionDescription?
	var currentRemoteDescription: RTCSessionDescription?
	var pendingRemoteDescription: RTCSessionDescription?
	fun addIceCandidate(candidate: RTCIceCandidateInit = definedExternally): Promise<Unit>
	fun addIceCandidate(): Promise<Unit>
	fun addIceCandidate(candidate: RTCIceCandidate = definedExternally): Promise<Unit>
	var signalingState: String /* "closed" | "have-local-offer" | "have-local-pranswer" | "have-remote-offer" | "have-remote-pranswer" | "stable" */
	var connectionState: String /* "closed" | "connected" | "connecting" | "disconnected" | "failed" | "new" */
	fun getConfiguration(): RTCConfiguration
	fun setConfiguration(configuration: RTCConfiguration)
	fun close()
	var onicecandidateerror: ((self: RTCPeerConnection, ev: RTCPeerConnectionIceErrorEvent) -> Any)?
	var onconnectionstatechange: ((self: RTCPeerConnection, ev: Event) -> Any)?
	fun getSenders(): Array<RTCRtpSender>
	fun getReceivers(): Array<RTCRtpReceiver>
	fun getTransceivers(): Array<RTCRtpTransceiver>
	fun addTrack(track: MediaStreamTrack, vararg streams: MediaStream): RTCRtpSender
	fun removeTrack(sender: RTCRtpSender)
	fun addTransceiver(trackOrKind: MediaStreamTrack, init: RTCRtpTransceiverInit = definedExternally): RTCRtpTransceiver
	fun addTransceiver(trackOrKind: MediaStreamTrack): RTCRtpTransceiver
	fun addTransceiver(trackOrKind: String, init: RTCRtpTransceiverInit = definedExternally): RTCRtpTransceiver
	fun addTransceiver(trackOrKind: String): RTCRtpTransceiver
	var ontrack: ((self: RTCPeerConnection, ev: RTCTrackEvent) -> Any)?
	var sctp: RTCSctpTransport?
	fun createDataChannel(label: String?, dataChannelDict: RTCDataChannelInit = definedExternally): RTCDataChannel
	fun createDataChannel(label: String?): RTCDataChannel
	var ondatachannel: ((self: RTCPeerConnection, ev: RTCDataChannelEvent) -> Any)?
	var canTrickleIceCandidates: Boolean?
	var iceConnectionState: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
	var iceGatheringState: String /* "complete" | "gathering" | "new" */
	var idpErrorInfo: String?
	var idpLoginUrl: String?
	var onicecandidate: ((self: RTCPeerConnection, ev: RTCPeerConnectionIceEvent) -> Any)?
	var oniceconnectionstatechange: ((self: RTCPeerConnection, ev: Event) -> Any)?
	var onicegatheringstatechange: ((self: RTCPeerConnection, ev: Event) -> Any)?
	var onnegotiationneeded: ((self: RTCPeerConnection, ev: Event) -> Any)?
	var onsignalingstatechange: ((self: RTCPeerConnection, ev: Event) -> Any)?
	var peerIdentity: Promise<RTCIdentityAssertion>
	fun createAnswer(options: RTCOfferOptions = definedExternally): Promise<RTCSessionDescriptionInit>
	fun createDataChannel(label: String, dataChannelDict: RTCDataChannelInit = definedExternally): RTCDataChannel
	fun createDataChannel(label: String): RTCDataChannel
	fun getIdentityAssertion(): Promise<String>
	fun setIdentityProvider(provider: String, options: RTCIdentityProviderOptions = definedExternally)
	fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions = definedExternally)
	fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions = definedExternally)
}

external interface RTCPeerConnectionIceErrorEvent : Event {
	var hostCandidate: String
	var url: String
	var errorCode: Number
	var errorText: String
}

external interface RTCPeerConnectionIceEvent : Event {
	var url: String?
	var candidate: RTCIceCandidate?
}

external interface RTCRtpReceiver {
	fun getParameters(): dynamic /* RTCRtpParameters | RTCRtpReceiveParameters */
	fun getContributingSources(): Array<RTCRtpContributingSource>
	var rtcpTransport: RTCDtlsTransport?
	var track: MediaStreamTrack
	var transport: RTCDtlsTransport?
	fun getSynchronizationSources(): Array<RTCRtpSynchronizationSource>
}

external interface RTCRtpSender {
	fun setParameters(parameters: RTCRtpParameters = definedExternally): Promise<Unit>
	fun setParameters(): Promise<Unit>
	fun getParameters(): dynamic /* RTCRtpParameters | RTCRtpSendParameters */
	fun replaceTrack(withTrack: MediaStreamTrack): Promise<Unit>
	var dtmf: RTCDTMFSender?
	var rtcpTransport: RTCDtlsTransport?
	var track: MediaStreamTrack?
	var transport: RTCDtlsTransport?
	fun replaceTrack(withTrack: MediaStreamTrack?): Promise<Unit>
	fun setParameters(parameters: RTCRtpSendParameters): Promise<Unit>
	fun setStreams(vararg streams: MediaStream)
}

external interface RTCRtpTransceiver {
	var mid: String?
	var sender: RTCRtpSender
	var receiver: RTCRtpReceiver
	var stopped: Boolean
	var direction: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
	fun setDirection(direction: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */)
	fun stop()
	fun setCodecPreferences(codecs: Array<RTCRtpCodecCapability>)
	fun setCodecPreferences(codecs: Iterable<RTCRtpCodecCapability>)
	var currentDirection: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
}

external interface RTCSctpTransportEventMap {
	var statechange: Event
}

external interface RTCSctpTransport : EventTarget {
	var transport: RTCDtlsTransport
	var maxMessageSize: Number
	var maxChannels: Number?
	var onstatechange: ((self: RTCSctpTransport, ev: Event) -> Any)?
	var state: String /* "closed" | "connected" | "connecting" */
	fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions = definedExternally)
	fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions = definedExternally)
}

external interface RTCSessionDescription {
	var sdp: String
	var type: String /* "answer" | "offer" | "pranswer" | "rollback" */
	fun toJSON(): Any
}

external interface RTCSsrcConflictEvent : Event {
	var ssrc: Number
}

external interface RTCTrackEvent : Event {
	var receiver: RTCRtpReceiver
	var track: MediaStreamTrack
	var streams: Array<MediaStream>
	var transceiver: RTCRtpTransceiver
}
