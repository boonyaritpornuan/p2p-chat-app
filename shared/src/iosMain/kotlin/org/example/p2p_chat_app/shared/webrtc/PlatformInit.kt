package org.example.p2p_chat_app.shared.webrtc

import com.shepeliev.webrtc_kmp.PeerConnectionFactory

/**
 * Initializes the PeerConnectionFactory for iOS.
 * Currently, webrtc-kmp handles initialization internally for iOS when the factory is created.
 * This function can be used for any additional iOS-specific setup if needed in the future.
 */
actual fun initializePeerConnectionFactory(context: Any?) {
    // PeerConnectionFactory initialization is handled internally by webrtc-kmp on iOS.
    // No explicit context is needed here.
    // PeerConnectionFactory.initialize()
    println("PeerConnectionFactory initialization handled internally on iOS.")
}

