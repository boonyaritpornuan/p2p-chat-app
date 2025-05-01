package org.example.p2p_chat_app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.example.p2p_chat_app.android.ui.ChatScreen
import org.example.p2p_chat_app.android.ui.theme.P2PChatAppTheme
import org.example.p2p_chat_app.shared.crypto.SignalProtocolManager // Import shared module classes
import org.example.p2p_chat_app.shared.webrtc.WebRtcManager
import org.example.p2p_chat_app.shared.webrtc.initializePeerConnectionFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize PeerConnectionFactory for Android
        initializePeerConnectionFactory(applicationContext)

        // Initialize shared components (consider dependency injection for a real app)
        // Note: SignalProtocolManager actual implementation for Android is used here.
        val signalManager = SignalProtocolManager()
        val webRtcManager = WebRtcManager(signalManager)

        // TODO: Implement actual signaling client and peer connection setup logic
        // val signalingClient = ...
        // webRtcManager.createPeerConnection(signalingClient, "remotePeerName", 1)

        setContent {
            P2PChatAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass the WebRtcManager to the ChatScreen (or via ViewModel)
                    ChatScreen(webRtcManager = webRtcManager)
                }
            }
        }
    }
}

