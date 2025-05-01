package org.example.p2p_chat_app.shared.webrtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.example.p2p_chat_app.shared.signaling.InMemorySignalingClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Basic integration test for WebRtcManager and InMemorySignalingClient.
 * This test simulates two peers connecting and sending a message.
 * Note: This requires platform-specific initialization to be called beforehand if run on actual devices/emulators.
 * For common tests, PeerConnectionFactory might not fully initialize without platform context,
 * but we can test the signaling flow and manager logic.
 */
class WebRtcManagerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var clientA: InMemorySignalingClient
    private lateinit var clientB: InMemorySignalingClient
    private lateinit var managerA: WebRtcManager
    private lateinit var managerB: WebRtcManager

    // Mock platform initialization for common test environment
    @BeforeTest
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default + Job())
        // Simulate platform initialization (might be limited in commonTest)
        try {
            initializePeerConnectionFactory(null) // Pass null or a mock context if needed
        } catch (e: Exception) {
            println("Warning: PeerConnectionFactory initialization might be limited in commonTest: ${e.message}")
        }

        clientA = InMemorySignalingClient("A")
        clientB = InMemorySignalingClient("B")
        InMemorySignalingClient.connectClients(clientA, clientB)

        managerA = WebRtcManager()
        managerB = WebRtcManager()

        // Observe signaling messages and forward them
        scope.launch {
            managerA.sessionDescription.collect { clientA.sendOffer(it) }
        }
        scope.launch {
            managerA.iceCandidates.collect { clientA.sendIceCandidate(it) }
        }
        scope.launch {
            managerB.sessionDescription.collect { clientB.sendAnswer(it) }
        }
        scope.launch {
            managerB.iceCandidates.collect { clientB.sendIceCandidate(it) }
        }

        println("Test Setup Complete")
    }

    @AfterTest
    fun tearDown() {
        managerA.close()
        managerB.close()
        InMemorySignalingClient.clearClients()
        scope.cancel()
        println("Test Teardown Complete")
    }

    @Test
    fun testPeerConnectionAndDataChannel() = runBlocking {
        println("Starting testPeerConnectionAndDataChannel...")

        // Initiate connection from A
        managerA.createPeerConnection(clientA)
        // Peer B receives the connection attempt (implicitly via signaling setup)
        managerB.createPeerConnection(clientB)

        println("Peer connections created. A creating offer...")
        managerA.createOffer()

        // Wait for connection to establish and data channel to open
        println("Waiting for Data Channel A to open...")
        val stateA = withTimeoutOrNull(15000) { // 15-second timeout
            managerA.dataChannelState.first { it == DataChannel.State.OPEN }
        }
        println("Data Channel A State: $stateA")
        assertEquals(DataChannel.State.OPEN, stateA, "DataChannel A did not open")

        println("Waiting for Data Channel B to open...")
        val stateB = withTimeoutOrNull(15000) { // 15-second timeout
            managerB.dataChannelState.first { it == DataChannel.State.OPEN }
        }
        println("Data Channel B State: $stateB")
        assertEquals(DataChannel.State.OPEN, stateB, "DataChannel B did not open")

        // Send a message from A to B
        val messageToSend = "Hello from Peer A!"
        println("Sending message from A: ")
        val sent = managerA.sendMessage(messageToSend)
        assertTrue(sent, "Message sending failed from A")

        // Wait for B to receive the message
        println("Waiting for message at B...")
        val receivedMessage = withTimeoutOrNull(5000) { // 5-second timeout
            managerB.receivedMessages.first()
        }

        println("Message received at B: $receivedMessage")
        assertNotNull(receivedMessage, "Message not received at B within timeout")
        assertEquals(messageToSend, receivedMessage, "Received message does not match sent message")

        println("testPeerConnectionAndDataChannel finished successfully.")
        // Add a small delay to ensure all logs are flushed before teardown
        delay(100)
    }
}

