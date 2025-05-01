package org.example.p2p_chat_app.shared.webrtc

import com.shepeliev.webrtc_kmp.DataChannel
import com.shepeliev.webrtc_kmp.IceServer
import com.shepeliev.webrtc_kmp.MediaConstraints
import com.shepeliev.webrtc_kmp.PeerConnection
import com.shepeliev.webrtc_kmp.PeerConnectionFactory
import com.shepeliev.webrtc_kmp.RtcConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.example.p2p_chat_app.shared.crypto.SignalAddress // Import Signal types
import org.example.p2p_chat_app.shared.crypto.SignalProtocolManager
import org.example.p2p_chat_app.shared.crypto.CiphertextMessage
import org.example.p2p_chat_app.shared.crypto.createSignalAddress
import kotlin.native.concurrent.ThreadLocal // For holding SignalProtocolManager instance

// Define data classes for signaling messages
data class SessionDescriptionData(val type: String, val sdp: String)
data class IceCandidateData(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int)

// Interface for signaling communication (to be implemented by platform-specific code or a common signaling mechanism)
interface SignalingClient {
    // Methods for sending signaling data
    suspend fun sendOffer(sdp: SessionDescriptionData)
    suspend fun sendAnswer(sdp: SessionDescriptionData)
    suspend fun sendIceCandidate(candidate: IceCandidateData)
    // Flow for receiving signaling data (Offers, Answers, ICE Candidates)
    // Also needs to handle PreKeyBundle exchange for Signal Protocol setup
    val incomingMessages: SharedFlow<Any>
    // Method to send PreKeyBundle (needs to be added to signaling)
    suspend fun sendPreKeyBundle(bundle: Any) // Type should be PreKeyBundle, using Any for now
}

// Companion object to hold the SignalProtocolManager instance (simplistic approach)
// A better approach would use dependency injection.
@ThreadLocal
object CryptoManagerHolder {
    lateinit var signalProtocolManager: SignalProtocolManager
}

class WebRtcManager(private val signalProtocolManager: SignalProtocolManager) { // Inject SignalProtocolManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private val peerConnectionFactory by lazy { PeerConnectionFactory() }
    private var remoteSignalAddress: SignalAddress? = null // Store the remote peer's Signal address

    // Flow to emit local ICE candidates to be sent via signaling
    private val _iceCandidates = MutableSharedFlow<IceCandidateData>()
    val iceCandidates: SharedFlow<IceCandidateData> = _iceCandidates

    // Flow to emit local Session Description (Offer/Answer) to be sent via signaling
    private val _sessionDescription = MutableSharedFlow<SessionDescriptionData>()
    val sessionDescription: SharedFlow<SessionDescriptionData> = _sessionDescription

    // Flow to emit received DECRYPTED messages from the data channel
    private val _receivedMessages = MutableSharedFlow<String>()
    val receivedMessages: SharedFlow<String> = _receivedMessages

    // State flow for data channel status
    private val _dataChannelState = MutableStateFlow<DataChannel.State?>(null)
    val dataChannelState: StateFlow<DataChannel.State?> = _dataChannelState.asStateFlow()

    // State flow for ICE connection status
    private val _iceConnectionState = MutableStateFlow<PeerConnection.IceConnectionState?>(null)
    val iceConnectionState: StateFlow<PeerConnection.IceConnectionState?> = _iceConnectionState.asStateFlow()

    companion object {
        private const val DATA_CHANNEL_LABEL = "chatDataChannel"
    }

    init {
        // Initialize PeerConnectionFactory (platform-specific initialization might be needed)
        // Consider calling initializePeerConnectionFactory(context) in platform modules
        CryptoManagerHolder.signalProtocolManager = signalProtocolManager // Store instance (simplistic)
        signalProtocolManager.generateLocalKeys() // Ensure local keys are ready
        println("WebRtcManager initialized, Signal keys generated/loaded.")
    }

    // Need the remote peer's Signal name/ID to create SignalAddress
    fun createPeerConnection(signalingClient: SignalingClient, remotePeerSignalName: String, remotePeerDeviceId: Int) {
        // Create the SignalAddress for the remote peer
        // This assumes device ID 1 for simplicity, real app needs proper device ID management
        remoteSignalAddress = createSignalAddress(remotePeerSignalName, remotePeerDeviceId)
        println("Targeting remote peer: $remoteSignalAddress")

        val rtcConfiguration = RtcConfiguration(
            iceServers = listOf(
                IceServer("stun:stun.l.google.com:19302"),
                IceServer("stun:stun1.l.google.com:19302"),
                // TODO: Add TURN server configuration if needed
            )
        )
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfiguration) {
            onIceCandidate {
                scope.launch {
                    _iceCandidates.emit(IceCandidateData(it.sdp, it.sdpMid, it.sdpMLineIndex))
                }
            }
            onTrack { trackEvent ->
                println("Received remote track: ${trackEvent.track?.kind}")
            }
            onIceConnectionStateChange { state ->
                println("ICE Connection State: $state")
                _iceConnectionState.value = state
            }
            onDataChannel { remoteDataChannel ->
                println("Data channel received: ${remoteDataChannel.label}")
                if (remoteDataChannel.label == DATA_CHANNEL_LABEL) {
                    dataChannel = remoteDataChannel
                    setupDataChannelCallbacks(remoteDataChannel)
                }
            }
        }

        // Create the data channel proactively
        val dataChannelInit = DataChannel.Init(ordered = true)
        dataChannel = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, dataChannelInit)?.also {
            println("Data channel created: ${it.label}")
            setupDataChannelCallbacks(it)
        }

        // Listen for incoming signaling messages (including PreKeyBundle)
        scope.launch {
            signalingClient.incomingMessages.collect { message ->
                when (message) {
                    is SessionDescriptionData -> handleRemoteSessionDescription(message)
                    is IceCandidateData -> handleRemoteIceCandidate(message)
                    // TODO: Need to cast 'Any' safely based on actual signaling implementation
                    // is PreKeyBundle -> handleRemotePreKeyBundle(message)
                    else -> println("Received unknown signaling message type: ${message::class.simpleName}")
                }
            }
        }

        // TODO: Trigger sending local PreKeyBundle via signalingClient.sendPreKeyBundle()
        // This should happen after initialization, maybe triggered externally or here.
        // scope.launch {
        //     val localBundle = signalProtocolManager.generatePreKeyBundle()
        //     signalingClient.sendPreKeyBundle(localBundle)
        // }
    }

    // TODO: Add handler for receiving remote PreKeyBundle
    // private fun handleRemotePreKeyBundle(bundle: PreKeyBundle) {
    //     val remoteAddr = remoteSignalAddress ?: error("Remote SignalAddress not set")
    //     signalProtocolManager.processPreKeyBundle(remoteAddr, bundle)
    //     println("Processed remote PreKeyBundle from $remoteAddr")
    // }

    private fun setupDataChannelCallbacks(channel: DataChannel) {
        channel.onOpen {
            println("Data channel opened: ${channel.label}")
            _dataChannelState.value = DataChannel.State.OPEN
        }
        channel.onClose {
            println("Data channel closed: ${channel.label}")
            _dataChannelState.value = DataChannel.State.CLOSED
        }
        channel.onMessage {
            val receivedData = it.readByteArray()
            println("Data channel raw data received: ${receivedData.size} bytes")
            val remoteAddr = remoteSignalAddress ?: run {
                println("Error: Remote SignalAddress not set, cannot decrypt message.")
                return@onMessage
            }
            try {
                // Attempt to decrypt the received data
                // Need to reconstruct CiphertextMessage from serialized bytes
                // The actual CiphertextMessage constructor might need platform specifics
                // This requires the CiphertextMessage expect class to have a constructor
                // or factory method that takes serialized ByteArray.

                // TODO: This part needs the actual CiphertextMessage(ByteArray) constructor/factory
                // val ciphertext = CiphertextMessage(receivedData)
                // val plaintextBytes = if (ciphertext.type == 3) { // PREKEY_TYPE = 3 in libsignal-protocol-java
                //     signalProtocolManager.decryptPreKeyMessage(remoteAddr, receivedData)
                // } else {
                //     signalProtocolManager.decrypt(remoteAddr, ciphertext)
                // }

                // *** Placeholder Decryption Logic ***
                // Assuming the constructor CiphertextMessage(ByteArray) exists via expect/actual
                val ciphertext = CiphertextMessage(receivedData) // This needs actual implementation
                val plaintextBytes = signalProtocolManager.decrypt(remoteAddr, ciphertext)
                // *** End Placeholder ***

                val decryptedText = plaintextBytes.decodeToString()
                println("Decrypted message: $decryptedText")
                scope.launch {
                    _receivedMessages.emit(decryptedText)
                }
            } catch (e: Exception) {
                // Handle decryption errors (e.g., InvalidMessageException, DuplicateMessageException, etc.)
                println("Failed to decrypt message from $remoteAddr: ${e.message}")
                // Optionally notify UI or log error
            }
        }
        channel.onError {
            println("Data channel error: ${it.message}")
        }
        _dataChannelState.value = channel.state
    }

    suspend fun createOffer() {
        val pc = peerConnection ?: error("PeerConnection not initialized")
        val constraints = MediaConstraints(
            mandatory = mapOf("OfferToReceiveAudio" to "false", "OfferToReceiveVideo" to "false"),
            optional = mapOf("DtlsSrtpKeyAgreement" to "true")
        )
        val offer = pc.createOffer(constraints)
        pc.setLocalDescription(offer)
        _sessionDescription.emit(SessionDescriptionData(offer.type.name.lowercase(), offer.sdp))
    }

    private suspend fun handleRemoteSessionDescription(sdpData: SessionDescriptionData) {
        val pc = peerConnection ?: error("PeerConnection not initialized")
        val sdp = com.shepeliev.webrtc_kmp.SessionDescription(
            type = com.shepeliev.webrtc_kmp.SessionDescription.Type.valueOf(sdpData.type.uppercase()),
            sdp = sdpData.sdp
        )
        pc.setRemoteDescription(sdp)

        if (sdp.type == com.shepeliev.webrtc_kmp.SessionDescription.Type.OFFER) {
            val constraints = MediaConstraints(
                mandatory = mapOf("OfferToReceiveAudio" to "false", "OfferToReceiveVideo" to "false"),
                optional = mapOf("DtlsSrtpKeyAgreement" to "true")
            )
            val answer = pc.createAnswer(constraints)
            pc.setLocalDescription(answer)
            _sessionDescription.emit(SessionDescriptionData(answer.type.name.lowercase(), answer.sdp))
        }
    }

    private suspend fun handleRemoteIceCandidate(candidateData: IceCandidateData) {
        val pc = peerConnection ?: error("PeerConnection not initialized")
        val candidate = com.shepeliev.webrtc_kmp.IceCandidate(
            sdp = candidateData.candidate,
            sdpMid = candidateData.sdpMid,
            sdpMLineIndex = candidateData.sdpMLineIndex
        )
        try {
            pc.addIceCandidate(candidate)
        } catch (e: Exception) {
            println("Error adding received ICE candidate: ${e.message}")
        }
    }

    /**
     * Encrypts the message using Signal Protocol and sends it over the DataChannel.
     */
    fun sendMessage(message: String): Boolean {
        val remoteAddr = remoteSignalAddress ?: run {
            println("Error: Remote SignalAddress not set, cannot encrypt message.")
            return false
        }
        return if (dataChannel?.state == DataChannel.State.OPEN) {
            try {
                val plaintextBytes = message.encodeToByteArray()
                val ciphertextMessage = signalProtocolManager.encrypt(remoteAddr, plaintextBytes)
                val serializedCiphertext = ciphertextMessage.serialize()

                val buffer = Buffer()
                buffer.write(serializedCiphertext)
                val dataBuffer = DataChannel.Buffer(buffer, true) // true for binary message
                val success = dataChannel?.send(dataBuffer) ?: false
                if (success) {
                    println("Encrypted message sent (${serializedCiphertext.size} bytes)")
                } else {
                    println("Failed to send encrypted message over data channel")
                }
                success
            } catch (e: Exception) {
                // Handle encryption errors (e.g., NoSessionException)
                println("Failed to encrypt message for $remoteAddr: ${e.message}")
                // TODO: Maybe need to re-establish session (re-exchange PreKeyBundles)
                false
            }
        } else {
            println("Cannot send message, DataChannel is not open. State: ${dataChannel?.state}")
            false
        }
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
        dataChannel = null
        peerConnection = null
        _dataChannelState.value = null
        _iceConnectionState.value = null
        remoteSignalAddress = null
        println("WebRtcManager closed.")
        // Consider cancelling the scope if the manager is permanently destroyed
        // scope.cancel()
    }
}

