import SwiftUI
import shared // Import the shared Kotlin module

@main
struct iOSApp: App {

    init() {
        // Initialize shared components (consider dependency injection)
        // Initialize PeerConnectionFactory for iOS
        PlatformInitKt.initializePeerConnectionFactory()

        // Initialize SignalProtocolManager (using the placeholder actual for iOS for now)
        let signalManager = SignalProtocolManager()
        // Initialize WebRtcManager
        let webRtcManager = WebRtcManager(signalProtocolManager: signalManager)

        // Store instances where they can be accessed by the UI (e.g., EnvironmentObject, StateObject)
        // For simplicity, we might pass them directly or use a simple holder initially.
        // This needs a proper architecture (like MVVM) in a real app.
        SharedInstances.webRtcManager = webRtcManager
        SharedInstances.signalManager = signalManager

        print("iOSApp initialized with shared modules.")

        // TODO: Implement actual signaling client and peer connection setup logic
        // let signalingClient = ...
        // webRtcManager.createPeerConnection(signalingClient: signalingClient, remotePeerSignalName: "remotePeerName", remotePeerDeviceId: 1)
    }

	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}

// Simple holder for shared instances (replace with proper DI)
object SharedInstances {
    lateinit var webRtcManager: WebRtcManager
    lateinit var signalManager: SignalProtocolManager
}

