package org.example.p2p_chat_app.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.p2p_chat_app.shared.webrtc.WebRtcManager

@Composable
fun ChatScreen(webRtcManager: WebRtcManager) {
    val messages = remember { mutableStateListOf<String>() }
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Collect received messages
    LaunchedEffect(webRtcManager) {
        webRtcManager.receivedMessages.collect {
            messages.add("Peer: $it") // Prefix to indicate received message
        }
    }

    // Collect Data Channel state for UI feedback (optional)
    val dataChannelState by webRtcManager.dataChannelState.collectAsState()
    val iceConnectionState by webRtcManager.iceConnectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Display connection status (optional)
        Text("ICE State: ${iceConnectionState?.name ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
        Text("Data Channel State: ${dataChannelState?.name ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))

        // Message List
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(messages) {
                Text(it, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter message") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val messageToSend = inputText
                        coroutineScope.launch {
                            val success = webRtcManager.sendMessage(messageToSend)
                            if (success) {
                                messages.add("Me: $messageToSend") // Add sent message to list
                                inputText = "" // Clear input field
                            } else {
                                // Optionally show an error to the user
                                messages.add("Error: Failed to send message")
                            }
                        }
                    }
                },
                enabled = dataChannelState == com.shepeliev.webrtc_kmp.DataChannel.State.OPEN // Enable only when channel is open
            ) {
                Text("Send")
            }
        }
    }
}

