@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS", "EXTERNAL_DELEGATION", "UnsafeCastFromDynamic", "unused", "NOTHING_TO_INLINE", "UNUSED_PARAMETER")

package externals.webrtc

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.ArrayBufferView
import org.w3c.dom.EventSource
import org.w3c.dom.MessageEvent
import org.w3c.dom.Window
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamTrack
import org.w3c.files.Blob
import kotlin.js.Promise

interface RTCAnswerOptions
interface RTCBundlePolicy
interface RTCCertificate
interface RTCConfiguration
interface RTCDataChannel : EventTarget
interface RTCDataChannelEvent : Event
interface RTCDataChannelInit
interface RTCDataChannelState
interface RTCDegradationPreference
interface RTCDtlsTransport
interface RTCDtxStatus
interface RTCErrorEvent
interface RTCIceCandidate
interface RTCIceCandidateInit
interface RTCIceCandidatePair
interface RTCIceCredentialType
interface RTCIceGatheringState
interface RTCIceParameters
interface RTCIceServer
interface RTCIceTransport
interface RTCIceTransportPolicy
interface RTCOfferAnswerOptions
interface RTCOfferOptions
interface RTCPeerConnectionIceErrorEvent : Event
interface RTCPeerConnectionIceEvent : Event
interface RTCPeerConnectionState
interface RTCRtcpMuxPolicy
interface RTCRtpCodecCapability
interface RTCRtpCodecParameters
interface RTCPeerConnection : EventTarget
interface RTCRtpContributingSource
interface RTCRtpEncodingParameters
interface RTCRtpHeaderExtensionCapability
interface RTCRtpHeaderExtensionParameters
interface RTCRtpParameters
interface RTCRtpReceiver
interface RTCRtpSender
interface RTCRtpTransceiver
interface RTCRtpTransceiverDirection
interface RTCRtpTransceiverInit
interface RTCSctpTransport
interface RTCSessionDescription
interface RTCSessionDescriptionInit
interface RTCSignalingState
interface RTCStatsReport
interface RTCTrackEvent : Event

inline var RTCOfferAnswerOptions.voiceActivityDetection: Boolean?
	get() = this.asDynamic().voiceActivityDetection
	set(value) {
		this.asDynamic().voiceActivityDetection = value
	}

inline var RTCOfferOptions.iceRestart: Boolean?
	get() = this.asDynamic().iceRestart
	set(value) {
		this.asDynamic().iceRestart = value
	}

inline var RTCIceServer.credential: String?
	get() = this.asDynamic().credential
	set(value) {
		this.asDynamic().credential = value
	}

inline var RTCIceServer.credentialType: RTCIceCredentialType?
	get() = this.asDynamic().credentialType
	set(value) {
		this.asDynamic().credentialType = value
	}

inline var RTCIceServer.urls: dynamic
	get() = this.asDynamic().urls
	set(value) {
		this.asDynamic().urls = value
	}

inline var RTCIceServer.username: String?
	get() = this.asDynamic().username
	set(value) {
		this.asDynamic().username = value
	}

typealias IceTransportEventHandler = ((self: RTCIceTransport, ev: Event) -> Any)?

inline var RTCIceTransport.gatheringState: RTCIceGatheringState
	get() = this.asDynamic().gatheringState
	set(value) {
		this.asDynamic().gatheringState = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCIceTransport.getLocalCandidates(): Array<RTCIceCandidate> = this.asDynamic().getLocalCandidates()

/* extending interface from lib.dom.d.ts */
inline fun RTCIceTransport.getRemoteCandidates(): Array<RTCIceCandidate> = this.asDynamic().getRemoteCandidates()

/* extending interface from lib.dom.d.ts */
inline fun RTCIceTransport.getSelectedCandidatePair(): RTCIceCandidatePair? = this.asDynamic().getSelectedCandidatePair()

/* extending interface from lib.dom.d.ts */
inline fun RTCIceTransport.getLocalParameters(): RTCIceParameters? = this.asDynamic().getLocalParameters()

/* extending interface from lib.dom.d.ts */
inline fun RTCIceTransport.getRemoteParameters(): RTCIceParameters? = this.asDynamic().getRemoteParameters()

inline var RTCIceTransport.onstatechange: IceTransportEventHandler
	get() = this.asDynamic().onstatechange
	set(value) {
		this.asDynamic().onstatechange = value
	}

inline var RTCIceTransport.ongatheringstatechange: IceTransportEventHandler
	get() = this.asDynamic().ongatheringstatechange
	set(value) {
		this.asDynamic().ongatheringstatechange = value
	}

inline var RTCIceTransport.onselectedcandidatepairchange: IceTransportEventHandler
	get() = this.asDynamic().onselectedcandidatepairchange
	set(value) {
		this.asDynamic().onselectedcandidatepairchange = value
	}

typealias DtlsTransportEventHandler = ((self: RTCDtlsTransport, ev: Event) -> Any)?

inline var RTCDtlsTransport.transport: RTCIceTransport
	get() = this.asDynamic().transport
	set(value) {
		this.asDynamic().transport = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCDtlsTransport.getRemoteCertificates(): Array<ArrayBuffer> = this.asDynamic().getRemoteCertificates()

inline var RTCDtlsTransport.onstatechange: DtlsTransportEventHandler
	get() = this.asDynamic().onstatechange
	set(value) {
		this.asDynamic().onstatechange = value
	}

inline var RTCRtpCodecCapability.mimeType: String
	get() = this.asDynamic().mimeType
	set(value) {
		this.asDynamic().mimeType = value
	}

inline var RTCRtpHeaderExtensionCapability.uri: String?
	get() = this.asDynamic().uri
	set(value) {
		this.asDynamic().uri = value
	}

inline var RTCRtpEncodingParameters.dtx: RTCDtxStatus?
	get() = this.asDynamic().dtx
	set(value) {
		this.asDynamic().dtx = value
	}

inline var RTCRtpEncodingParameters.rid: String
	get() = this.asDynamic().rid
	set(value) {
		this.asDynamic().rid = value
	}

inline var RTCRtpEncodingParameters.scaleResolutionDownBy: Number?
	get() = this.asDynamic().scaleResolutionDownBy
	set(value) {
		this.asDynamic().scaleResolutionDownBy = value
	}

inline var RTCRtpHeaderExtensionParameters.encrypted: Boolean?
	get() = this.asDynamic().encrypted
	set(value) {
		this.asDynamic().encrypted = value
	}

inline var RTCRtpCodecParameters.mimeType: String
	get() = this.asDynamic().mimeType
	set(value) {
		this.asDynamic().mimeType = value
	}

inline var RTCRtpCodecParameters.channels: Number?
	get() = this.asDynamic().channels
	set(value) {
		this.asDynamic().channels = value
	}

inline var RTCRtpCodecParameters.sdpFmtpLine: String?
	get() = this.asDynamic().sdpFmtpLine
	set(value) {
		this.asDynamic().sdpFmtpLine = value
	}

inline var RTCRtpParameters.transactionId: String
	get() = this.asDynamic().transactionId
	set(value) {
		this.asDynamic().transactionId = value
	}

inline var RTCRtpParameters.degradationPreference: RTCDegradationPreference?
	get() = this.asDynamic().degradationPreference
	set(value) {
		this.asDynamic().degradationPreference = value
	}

inline var RTCRtpContributingSource.source: Number
	get() = this.asDynamic().source
	set(value) {
		this.asDynamic().source = value
	}

inline var RTCRtpContributingSource.voiceActivityFlag: Boolean?
	get() = this.asDynamic().voiceActivityFlag
	set(value) {
		this.asDynamic().voiceActivityFlag = value
	}

external interface RTCRtcCapabilities {
	var codecs: Array<RTCRtpCodecCapability>
	var headerExtensions: Array<RTCRtpHeaderExtensionCapability>
}

/* extending interface from lib.dom.d.ts */
inline fun RTCRtpSender.setParameters(): Promise<Unit> = this.asDynamic().setParameters()

inline fun RTCRtpSender.setParameters(parameters: RTCRtpParameters): Promise<Unit> = this.asDynamic().setParameters(parameters)

/* extending interface from lib.dom.d.ts */
inline fun RTCRtpSender.getParameters(): RTCRtpParameters = this.asDynamic().getParameters()

/* extending interface from lib.dom.d.ts */
inline fun RTCRtpSender.replaceTrack(withTrack: MediaStreamTrack): Promise<Unit> = this.asDynamic().replaceTrack(withTrack)

/* extending interface from lib.dom.d.ts */
inline fun RTCRtpReceiver.getParameters(): RTCRtpParameters = this.asDynamic().getParameters()

/* extending interface from lib.dom.d.ts */
inline fun RTCRtpReceiver.getContributingSources(): Array<RTCRtpContributingSource> = this.asDynamic().getContributingSources()

inline var RTCRtpTransceiver.mid: String?
	get() = this.asDynamic().mid
	set(value) {
		this.asDynamic().mid = value
	}

inline var RTCRtpTransceiver.sender: RTCRtpSender
	get() = this.asDynamic().sender
	set(value) {
		this.asDynamic().sender = value
	}

inline var RTCRtpTransceiver.receiver: RTCRtpReceiver
	get() = this.asDynamic().receiver
	set(value) {
		this.asDynamic().receiver = value
	}

inline var RTCRtpTransceiver.stopped: Boolean
	get() = this.asDynamic().stopped
	set(value) {
		this.asDynamic().stopped = value
	}

inline var RTCRtpTransceiver.direction: RTCRtpTransceiverDirection
	get() = this.asDynamic().direction
	set(value) {
		this.asDynamic().direction = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCRtpTransceiver.setDirection(direction: RTCRtpTransceiverDirection) {
	this.asDynamic().setDirection(direction)
}

/* extending interface from lib.dom.d.ts */
inline fun RTCRtpTransceiver.stop() {
	this.asDynamic().stop()
}

/* extending interface from lib.dom.d.ts */
inline fun RTCRtpTransceiver.setCodecPreferences(codecs: Array<RTCRtpCodecCapability>) {
	this.asDynamic().setCodecPreferences(codecs)
}

inline var RTCRtpTransceiverInit.direction: RTCRtpTransceiverDirection?
	get() = this.asDynamic().direction
	set(value) {
		this.asDynamic().direction = value
	}

inline var RTCRtpTransceiverInit.streams: Array<MediaStream>?
	get() = this.asDynamic().streams
	set(value) {
		this.asDynamic().streams = value
	}

inline var RTCRtpTransceiverInit.sendEncodings: Array<RTCRtpEncodingParameters>?
	get() = this.asDynamic().sendEncodings
	set(value) {
		this.asDynamic().sendEncodings = value
	}

inline var RTCCertificate.expires: Number
	get() = this.asDynamic().expires
	set(value) {
		this.asDynamic().expires = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCCertificate.getAlgorithm(): String = this.asDynamic().getAlgorithm()

inline var RTCConfiguration.iceServers: Array<RTCIceServer>?
	get() = this.asDynamic().iceServers
	set(value) {
		this.asDynamic().iceServers = value
	}

inline var RTCConfiguration.iceTransportPolicy: RTCIceTransportPolicy?
	get() = this.asDynamic().iceTransportPolicy
	set(value) {
		this.asDynamic().iceTransportPolicy = value
	}

inline var RTCConfiguration.bundlePolicy: RTCBundlePolicy?
	get() = this.asDynamic().bundlePolicy
	set(value) {
		this.asDynamic().bundlePolicy = value
	}

inline var RTCConfiguration.rtcpMuxPolicy: RTCRtcpMuxPolicy?
	get() = this.asDynamic().rtcpMuxPolicy
	set(value) {
		this.asDynamic().rtcpMuxPolicy = value
	}

inline var RTCConfiguration.peerIdentity: String?
	get() = this.asDynamic().peerIdentity
	set(value) {
		this.asDynamic().peerIdentity = value
	}

inline var RTCConfiguration.certificates: Array<RTCCertificate>?
	get() = this.asDynamic().certificates
	set(value) {
		this.asDynamic().certificates = value
	}

inline var RTCConfiguration.iceCandidatePoolSize: Number?
	get() = this.asDynamic().iceCandidatePoolSize
	set(value) {
		this.asDynamic().iceCandidatePoolSize = value
	}

typealias RTCPeerConnectionConfig = RTCConfiguration

inline var RTCSctpTransport.transport: RTCDtlsTransport
	get() = this.asDynamic().transport
	set(value) {
		this.asDynamic().transport = value
	}

inline var RTCSctpTransport.maxMessageSize: Number
	get() = this.asDynamic().maxMessageSize
	set(value) {
		this.asDynamic().maxMessageSize = value
	}

inline var RTCDataChannelInit.ordered: Boolean?
	get() = this.asDynamic().ordered
	set(value) {
		this.asDynamic().ordered = value
	}

inline var RTCDataChannelInit.maxPacketLifeTime: Number?
	get() = this.asDynamic().maxPacketLifeTime
	set(value) {
		this.asDynamic().maxPacketLifeTime = value
	}

inline var RTCDataChannelInit.maxRetransmits: Number?
	get() = this.asDynamic().maxRetransmits
	set(value) {
		this.asDynamic().maxRetransmits = value
	}

inline var RTCDataChannelInit.protocol: String?
	get() = this.asDynamic().protocol
	set(value) {
		this.asDynamic().protocol = value
	}

inline var RTCDataChannelInit.negotiated: Boolean?
	get() = this.asDynamic().negotiated
	set(value) {
		this.asDynamic().negotiated = value
	}

inline var RTCDataChannelInit.id: Number?
	get() = this.asDynamic().id
	set(value) {
		this.asDynamic().id = value
	}

typealias DataChannelEventHandler<E> = ((self: RTCDataChannel, ev: E) -> Any)?

inline var RTCDataChannel.label: String
	get() = this.asDynamic().label
	set(value) {
		this.asDynamic().label = value
	}

inline var RTCDataChannel.ordered: Boolean
	get() = this.asDynamic().ordered
	set(value) {
		this.asDynamic().ordered = value
	}

inline var RTCDataChannel.maxPacketLifeTime: Number?
	get() = this.asDynamic().maxPacketLifeTime
	set(value) {
		this.asDynamic().maxPacketLifeTime = value
	}

inline var RTCDataChannel.maxRetransmits: Number?
	get() = this.asDynamic().maxRetransmits
	set(value) {
		this.asDynamic().maxRetransmits = value
	}

inline var RTCDataChannel.protocol: String
	get() = this.asDynamic().protocol
	set(value) {
		this.asDynamic().protocol = value
	}

inline var RTCDataChannel.negotiated: Boolean
	get() = this.asDynamic().negotiated
	set(value) {
		this.asDynamic().negotiated = value
	}

inline var RTCDataChannel.id: Number?
	get() = this.asDynamic().id
	set(value) {
		this.asDynamic().id = value
	}

inline var RTCDataChannel.readyState: String
	get() = this.asDynamic().readyState
	set(value) {
		this.asDynamic().readyState = value
	}

inline var RTCDataChannel.bufferedAmount: Number
	get() = this.asDynamic().bufferedAmount
	set(value) {
		this.asDynamic().bufferedAmount = value
	}

inline var RTCDataChannel.bufferedAmountLowThreshold: Number
	get() = this.asDynamic().bufferedAmountLowThreshold
	set(value) {
		this.asDynamic().bufferedAmountLowThreshold = value
	}

inline var RTCDataChannel.binaryType: String
	get() = this.asDynamic().binaryType
	set(value) {
		this.asDynamic().binaryType = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCDataChannel.close() {
	this.asDynamic().close()
}

/* extending interface from lib.dom.d.ts */
inline fun RTCDataChannel.send(data: String) {
	this.asDynamic().send(data)
}

/* extending interface from lib.dom.d.ts */
inline fun RTCDataChannel.send(data: Blob) {
	this.asDynamic().send(data)
}

/* extending interface from lib.dom.d.ts */
inline fun RTCDataChannel.send(data: ArrayBuffer) {
	this.asDynamic().send(data)
}

/* extending interface from lib.dom.d.ts */
inline fun RTCDataChannel.send(data: ArrayBufferView) {
	this.asDynamic().send(data)
}

inline var RTCDataChannel.onopen: DataChannelEventHandler<Event>
	get() = this.asDynamic().onopen
	set(value) {
		this.asDynamic().onopen = value
	}

inline var RTCDataChannel.onmessage: DataChannelEventHandler<MessageEvent>
	get() = this.asDynamic().onmessage
	set(value) {
		this.asDynamic().onmessage = value
	}

inline var RTCDataChannel.onbufferedamountlow: DataChannelEventHandler<Event>
	get() = this.asDynamic().onbufferedamountlow
	set(value) {
		this.asDynamic().onbufferedamountlow = value
	}

inline var RTCDataChannel.onerror: DataChannelEventHandler<RTCErrorEvent>
	get() = this.asDynamic().onerror
	set(value) {
		this.asDynamic().onerror = value
	}

inline var RTCDataChannel.onclose: DataChannelEventHandler<Event>
	get() = this.asDynamic().onclose
	set(value) {
		this.asDynamic().onclose = value
	}

inline var RTCTrackEvent.receiver: RTCRtpReceiver
	get() = this.asDynamic().receiver
	set(value) {
		this.asDynamic().receiver = value
	}

inline var RTCTrackEvent.track: MediaStreamTrack
	get() = this.asDynamic().track
	set(value) {
		this.asDynamic().track = value
	}

inline var RTCTrackEvent.streams: Array<MediaStream>
	get() = this.asDynamic().streams
	set(value) {
		this.asDynamic().streams = value
	}

inline var RTCTrackEvent.transceiver: RTCRtpTransceiver
	get() = this.asDynamic().transceiver
	set(value) {
		this.asDynamic().transceiver = value
	}

inline var RTCPeerConnectionIceEvent.candidate: RTCIceCandidate?
	get() = this.asDynamic().candidate
	set(value) {
		this.asDynamic().candidate = value
	}

inline var RTCPeerConnectionIceEvent.url: String?
	get() = this.asDynamic().url
	set(value) {
		this.asDynamic().url = value
	}

inline var RTCPeerConnectionIceErrorEvent.hostCandidate: String
	get() = this.asDynamic().hostCandidate
	set(value) {
		this.asDynamic().hostCandidate = value
	}

inline var RTCPeerConnectionIceErrorEvent.url: String
	get() = this.asDynamic().url
	set(value) {
		this.asDynamic().url = value
	}

inline var RTCPeerConnectionIceErrorEvent.errorCode: Number
	get() = this.asDynamic().errorCode
	set(value) {
		this.asDynamic().errorCode = value
	}

inline var RTCPeerConnectionIceErrorEvent.errorText: String
	get() = this.asDynamic().errorText
	set(value) {
		this.asDynamic().errorText = value
	}

inline var RTCDataChannelEvent.channel: RTCDataChannel
	get() = this.asDynamic().channel
	set(value) {
		this.asDynamic().channel = value
	}

typealias PeerConnectionEventHandler<E> = ((self: RTCPeerConnection, ev: E) -> Any)?

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.createOffer(): Promise<RTCSessionDescriptionInit> = this.asDynamic().createOffer()

inline fun RTCPeerConnection.createOffer(options: RTCOfferOptions): Promise<RTCSessionDescriptionInit> = this.asDynamic().createOffer(options)

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.createAnswer(): Promise<RTCSessionDescriptionInit> = this.asDynamic().createAnswer()

inline fun RTCPeerConnection.createAnswer(options: RTCAnswerOptions): Promise<RTCSessionDescriptionInit> = this.asDynamic().createAnswer(options)

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.setLocalDescription(description: RTCSessionDescriptionInit): Promise<Unit> = this.asDynamic().setLocalDescription(description)

inline var RTCPeerConnection.localDescription: RTCSessionDescription?
	get() = this.asDynamic().localDescription
	set(value) {
		this.asDynamic().localDescription = value
	}

inline var RTCPeerConnection.currentLocalDescription: RTCSessionDescription?
	get() = this.asDynamic().currentLocalDescription
	set(value) {
		this.asDynamic().currentLocalDescription = value
	}

inline var RTCPeerConnection.pendingLocalDescription: RTCSessionDescription?
	get() = this.asDynamic().pendingLocalDescription
	set(value) {
		this.asDynamic().pendingLocalDescription = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.setRemoteDescription(description: RTCSessionDescriptionInit): Promise<Unit> = this.asDynamic().setRemoteDescription(description)

inline var RTCPeerConnection.remoteDescription: RTCSessionDescription?
	get() = this.asDynamic().remoteDescription
	set(value) {
		this.asDynamic().remoteDescription = value
	}

inline var RTCPeerConnection.currentRemoteDescription: RTCSessionDescription?
	get() = this.asDynamic().currentRemoteDescription
	set(value) {
		this.asDynamic().currentRemoteDescription = value
	}

inline var RTCPeerConnection.pendingRemoteDescription: RTCSessionDescription?
	get() = this.asDynamic().pendingRemoteDescription
	set(value) {
		this.asDynamic().pendingRemoteDescription = value
	}

/* extending interface from lib.dom.d.ts */

inline fun RTCPeerConnection.addIceCandidate(): Promise<Unit> = this.asDynamic().addIceCandidate()

inline fun RTCPeerConnection.addIceCandidate(candidate: RTCIceCandidateInit): Promise<Unit> = this.asDynamic().addIceCandidate(candidate)

inline fun RTCPeerConnection.addIceCandidate(candidate: RTCIceCandidate): Promise<Unit> = this.asDynamic().addIceCandidate(candidate)

inline var RTCPeerConnection.signalingState: RTCSignalingState
	get() = this.asDynamic().signalingState
	set(value) {
		this.asDynamic().signalingState = value
	}

inline var RTCPeerConnection.connectionState: String
	get() = this.asDynamic().connectionState
	set(value) {
		this.asDynamic().connectionState = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.getConfiguration(): RTCConfiguration = this.asDynamic().getConfiguration()

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.setConfiguration(configuration: RTCConfiguration) {
	this.asDynamic().setConfiguration(configuration)
}

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.close() {
	this.asDynamic().close()
}

inline var RTCPeerConnection.onicecandidateerror: PeerConnectionEventHandler<RTCPeerConnectionIceErrorEvent>
	get() = this.asDynamic().onicecandidateerror
	set(value) {
		this.asDynamic().onicecandidateerror = value
	}

inline var RTCPeerConnection.onconnectionstatechange: PeerConnectionEventHandler<Event>
	get() = this.asDynamic().onconnectionstatechange
	set(value) {
		this.asDynamic().onconnectionstatechange = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.getSenders(): Array<RTCRtpSender> = this.asDynamic().getSenders()

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.getReceivers(): Array<RTCRtpReceiver> = this.asDynamic().getReceivers()

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.getTransceivers(): Array<RTCRtpTransceiver> = this.asDynamic().getTransceivers()

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.addTrack(track: MediaStreamTrack, vararg streams: MediaStream): RTCRtpSender = this.asDynamic().addTrack(track, streams)

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.removeTrack(sender: RTCRtpSender) {
	this.asDynamic().removeTrack(sender)
}

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.addTransceiver(trackOrKind: MediaStreamTrack): RTCRtpTransceiver = this.asDynamic().addTransceiver(trackOrKind)

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.addTransceiver(trackOrKind: String): RTCRtpTransceiver = this.asDynamic().addTransceiver(trackOrKind)

inline fun RTCPeerConnection.addTransceiver(trackOrKind: MediaStreamTrack, init: RTCRtpTransceiverInit): RTCRtpTransceiver = this.asDynamic().addTransceiver(trackOrKind, init)

inline fun RTCPeerConnection.addTransceiver(trackOrKind: String, init: RTCRtpTransceiverInit): RTCRtpTransceiver = this.asDynamic().addTransceiver(trackOrKind, init)

inline var RTCPeerConnection.ontrack: PeerConnectionEventHandler<RTCTrackEvent>
	get() = this.asDynamic().ontrack
	set(value) {
		this.asDynamic().ontrack = value
	}

inline var RTCPeerConnection.sctp: RTCSctpTransport?
	get() = this.asDynamic().sctp
	set(value) {
		this.asDynamic().sctp = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.createDataChannel(label: String?): RTCDataChannel = this.asDynamic().createDataChannel(label)

inline fun RTCPeerConnection.createDataChannel(label: String?, dataChannelDict: RTCDataChannelInit): RTCDataChannel = this.asDynamic().createDataChannel(label, dataChannelDict)

inline var RTCPeerConnection.ondatachannel: PeerConnectionEventHandler<RTCDataChannelEvent>
	get() = this.asDynamic().ondatachannel
	set(value) {
		this.asDynamic().ondatachannel = value
	}

/* extending interface from lib.dom.d.ts */
inline fun RTCPeerConnection.getStats(): Promise<RTCStatsReport> = this.asDynamic().getStats()

inline fun RTCPeerConnection.getStats(selector: MediaStreamTrack?): Promise<RTCStatsReport> = this.asDynamic().getStats(selector)

external interface RTCPeerConnectionStatic {
	var defaultIceServers: Array<RTCIceServer>
	fun generateCertificate(keygenAlgorithm: String): Promise<RTCCertificate>
}

inline fun RTCPeerConnection(config: RTCConfiguration): RTCPeerConnection =
	js("new RTCPeerConnection(config)")

inline var Window.RTCPeerConnection: RTCPeerConnectionStatic
	get() = this.asDynamic().RTCPeerConnection
	set(value) {
		this.asDynamic().RTCPeerConnection = value
	}

inline var RTCPeerConnection.iceGatheringState: String
	get() = this.asDynamic().iceGatheringState
	set(value) {
		this.asDynamic().iceGatheringState = value
	}
