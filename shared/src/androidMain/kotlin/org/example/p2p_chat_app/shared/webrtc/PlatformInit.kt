package org.example.p2p_chat_app.shared.webrtc

import android.content.Context
import com.shepeliev.webrtc_kmp.PeerConnectionFactory

actual fun initializePeerConnectionFactory(context: Any?) {
    if (context !is Context) {
        throw IllegalArgumentException("Android context is required for PeerConnectionFactory initialization")
    }
    PeerConnectionFactory.initialize(context)
    println("PeerConnectionFactory initialized for Android.")
}

