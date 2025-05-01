import SwiftUI
import shared // Import the shared Kotlin module
import Combine // For observing flows

// ObservableObject to bridge Kotlin Flows to SwiftUI
class ChatViewModel: ObservableObject {
    private var cancellables = Set<AnyCancellable>()
    private let webRtcManager: WebRtcManager

    @Published var messages: [String] = []
    @Published var inputText: String = ""
    @Published var dataChannelState: DataChannelState? = nil
    @Published var iceConnectionState: PeerConnectionIceConnectionState? = nil
    @Published var isSendEnabled: Bool = false

    init(webRtcManager: WebRtcManager) {
        self.webRtcManager = webRtcManager

        // Observe received messages
        FlowCollector<NSString>(flow: webRtcManager.receivedMessages) { [weak self] message in
            DispatchQueue.main.async {
                self?.messages.append("Peer: \(message as String)")
            }
        }.store(in: &cancellables)

        // Observe data channel state
        FlowCollector<DataChannelState>(flow: webRtcManager.dataChannelState) { [weak self] state in
            DispatchQueue.main.async {
                self?.dataChannelState = state
                self?.isSendEnabled = (state == .open)
            }
        }.store(in: &cancellables)

        // Observe ICE connection state
        FlowCollector<PeerConnectionIceConnectionState>(flow: webRtcManager.iceConnectionState) { [weak self] state in
            DispatchQueue.main.async {
                self?.iceConnectionState = state
            }
        }.store(in: &cancellables)
    }

    func sendMessage() {
        let messageToSend = inputText
        if !messageToSend.isEmpty {
            // Use Kotlin Coroutines suspend function from Swift
            webRtcManager.sendMessage(message: messageToSend) { success, error in
                DispatchQueue.main.async {
                    if success?.boolValue == true {
                        self.messages.append("Me: \(messageToSend)")
                        self.inputText = ""
                    } else {
                        self.messages.append("Error: Failed to send message - \(error?.localizedDescription ?? "Unknown error")")
                    }
                }
            }
        }
    }
}

struct ContentView: View {
    // Use StateObject to keep the ViewModel alive
    @StateObject private var viewModel = ChatViewModel(webRtcManager: SharedInstances.webRtcManager)

	var body: some View {
        VStack {
            // Display connection status (optional)
            Text("ICE State: \(viewModel.iceConnectionState?.name ?? "Unknown")")
                .font(.caption)
            Text("Data Channel State: \(viewModel.dataChannelState?.name ?? "Unknown")")
                .font(.caption)

            // Message List
            List(viewModel.messages, id: \.self) {
                Text($0)
            }
            .listStyle(.plain)

            // Input Row
            HStack {
                TextField("Enter message", text: $viewModel.inputText)
                    .textFieldStyle(.roundedBorder)
                Button("Send") {
                    viewModel.sendMessage()
                }
                .disabled(!viewModel.isSendEnabled)
            }
            .padding()
        }
	}
}

// Helper to collect Kotlin Flows in Swift (needs to be defined or imported)
// This is a basic implementation placeholder
class FlowCollector<T>: NSObject, Kotlinx_coroutines_coreFlowCollector {
    let callback: (T) -> Void
    private var job: Kotlinx_coroutines_coreJob? = nil

    init(flow: Kotlinx_coroutines_coreFlow, callback: @escaping (T) -> Void) {
        self.callback = callback
        super.init()
        // Launch a coroutine to collect the flow
        // This requires a CoroutineScope, often provided from the shared module or created here.
        // For simplicity, using a global scope (not recommended for production)
        job = SharedScopeProvider.shared.scope.launch {
            flow.collect(collector: self) { error in
                // Handle completion or errors
                print("Flow collection finished or failed: \(error?.localizedDescription ?? "Finished")")
            }
        }
    }

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        if let typedValue = value as? T {
            callback(typedValue)
            completionHandler(nil)
        } else {
            // Handle incorrect type or null if T is not optional
            completionHandler(NSError(domain: "FlowCollector", code: 1, userInfo: [NSLocalizedDescriptionKey: "Incorrect type received"]))
        }
    }

    func cancel() {
        job?.cancel(cause: nil)
    }

    deinit {
        cancel()
    }

    func store(in set: inout Set<AnyCancellable>) {
        set.insert(AnyCancellable(self.cancel))
    }
}

// Placeholder for providing a CoroutineScope (needs actual implementation)
object SharedScopeProvider {
    let shared = SharedScopeProvider()
    let scope = // ... Initialize a CoroutineScope, e.g., using MainScope()
}

// Helper for AnyCancellable
struct AnyCancellable: Cancellable, Hashable {
    private let cancellable: () -> Void

    init(_ cancel: @escaping () -> Void) {
        self.cancellable = cancel
    }

    func cancel() {
        cancellable()
    }

    func hash(into hasher: inout Hasher) {
        // Implement hash based on identity or a unique identifier if needed
    }

    static func == (lhs: AnyCancellable, rhs: AnyCancellable) -> Bool {
        // Implement equality based on identity or a unique identifier if needed
        return false // Placeholder
    }
}

