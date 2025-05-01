package org.example.p2p_chat_app.shared.signaling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.example.p2p_chat_app.shared.webrtc.IceCandidateData
import org.example.p2p_chat_app.shared.webrtc.SessionDescriptionData
import org.example.p2p_chat_app.shared.webrtc.SignalingClient

/**
 * A basic in-memory signaling client implementation for testing purposes.
 * In a real application, this would be replaced by a proper signaling mechanism
 * (e.g., WebSocket server, Firebase, QR code exchange, etc.).
 */
class InMemorySignalingClient(private val role: String) : SignalingClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Flow for incoming messages (simulated)
    private val _incomingMessages = MutableSharedFlow<Any>(replay = 1)
    override val incomingMessages: SharedFlow<Any> = _incomingMessages

    // Static object to hold references between two clients for simulation
    companion object {
        private var clientA: InMemorySignalingClient? = null
        private var clientB: InMemorySignalingClient? = null

        fun connectClients(client1: InMemorySignalingClient, client2: InMemorySignalingClient) {
            clientA = client1
            clientB = client2
            println("InMemorySignalingClient: Clients connected for simulation.")
        }

        fun clearClients() {
            clientA = null
            clientB = null
        }

        // Simulate sending a message from one client to the other
        fun forwardMessage(senderRole: String, message: Any) {
            val targetClient = if (senderRole == "A") clientB else clientA
            targetClient?.let {
                println("InMemorySignalingClient: Forwarding ${message::class.simpleName} from $senderRole to ${it.role}")
                it.scope.launch {
                    it._incomingMessages.emit(message)
                }
            }
        }
    }

    init {
        println("InMemorySignalingClient ($role) initialized.")
    }

    override suspend fun sendOffer(sdp: SessionDescriptionData) {
        println("InMemorySignalingClient ($role): Sending Offer")
        forwardMessage(role, sdp)
    }

    override suspend fun sendAnswer(sdp: SessionDescriptionData) {
        println("InMemorySignalingClient ($role): Sending Answer")
        forwardMessage(role, sdp)
    }

    override suspend fun sendIceCandidate(candidate: IceCandidateData) {
        println("InMemorySignalingClient ($role): Sending ICE Candidate")
        forwardMessage(role, candidate)
    }

    // Function to manually simulate receiving a message (for testing)
    suspend fun simulateReceiveMessage(message: Any) {
        println("InMemorySignalingClient ($role): Simulating receive message: ${message::class.simpleName}")
        _incomingMessages.emit(message)
    }
}

