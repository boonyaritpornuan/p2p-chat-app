package org.example.p2p_chat_app.shared.crypto

import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.protocol.CiphertextMessage as SignalCiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle as SignalPreKeyBundle
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.state.impl.InMemorySignalProtocolStore
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.util.KeyHelper
import java.nio.charset.StandardCharsets

// --- Actual Implementations for Android using libsignal-client (Java) ---

actual typealias SignalAddress = SignalProtocolAddress

actual typealias PreKeyBundle = SignalPreKeyBundle

actual class CiphertextMessage(internal val delegate: SignalCiphertextMessage) {
    actual val type: Int = delegate.type
    actual fun serialize(): ByteArray = delegate.serialize()

    // Constructor to create from serialized data (needed for decryption)
    constructor(serialized: ByteArray) : this(try {
        // Determine type and instantiate accordingly
        // NOTE: This is a simplification. Real implementation needs robust type checking.
        if (serialized.isNotEmpty() && serialized[0].toInt() >= 5) { // Heuristic for PreKeySignalMessage
             SignalCiphertextMessage(PreKeySignalMessage(serialized))
        } else {
             SignalCiphertextMessage(serialized) // Assuming WhisperMessage
        }
    } catch (e: Exception) {
        // Handle invalid format exceptions
        throw IllegalArgumentException("Failed to deserialize CiphertextMessage", e)
    })
}

// Basic In-Memory Store for demonstration. Replace with persistent storage.
// NOTE: This store is NOT thread-safe and loses all keys on restart.
private val inMemoryStore = InMemorySignalProtocolStore(
    KeyHelper.generateIdentityKeyPair(),
    KeyHelper.generateRegistrationId(false)
)

actual class SignalProtocolManager {

    private val store: SignalProtocolStore = inMemoryStore

    actual fun generateLocalKeys() {
        // Keys are generated when InMemorySignalProtocolStore is initialized
        // For a persistent store, this method would load or generate keys.
        if (!store.isLocalIdentity(store.localRegistrationId)) {
             // This part is more relevant for persistent stores to ensure keys exist.
             // For InMemory, it's initialized already.
             val identityKeyPair = KeyHelper.generateIdentityKeyPair()
             val registrationId = KeyHelper.generateRegistrationId(false)
             // store.storeIdentityKeyPair(identityKeyPair) // InMemory store handles this
             // store.storeLocalRegistrationId(registrationId) // InMemory store handles this
        }
        // Generate PreKeys and SignedPreKey
        val preKeys = KeyHelper.generatePreKeys(0, 100)
        val signedPreKey = KeyHelper.generateSignedPreKey(store.identityKeyPair, 0)

        preKeys.forEach { store.storePreKey(it.id, it) }
        store.storeSignedPreKey(signedPreKey.id, signedPreKey)
        println("Generated and stored local keys (in-memory)")
    }

    actual fun getLocalRegistrationId(): Int {
        return store.localRegistrationId
    }

    actual fun getLocalIdentityPublicKey(): ByteArray {
        return store.identityKeyPair.publicKey.serialize()
    }

    actual fun generatePreKeyBundle(): PreKeyBundle {
        // Retrieve the necessary keys from the store
        val registrationId = store.localRegistrationId
        val identityKey = store.identityKeyPair.publicKey
        // Get one preKey (e.g., the last one generated)
        // A real implementation needs a strategy to manage preKeys
        val preKey = store.loadPreKeys().maxByOrNull { it.id } ?: throw IllegalStateException("No PreKeys available")
        // Get the signed preKey
        // Assuming only one signed prekey for simplicity
        val signedPreKey = store.loadSignedPreKeys().firstOrNull() ?: throw IllegalStateException("No SignedPreKey available")

        return PreKeyBundle(
            registrationId,
            preKey.id,
            preKey.keyPair.publicKey,
            signedPreKey.id,
            signedPreKey.keyPair.publicKey,
            signedPreKey.signature,
            identityKey
        )
    }

    actual fun processPreKeyBundle(address: SignalAddress, bundle: PreKeyBundle) {
        val sessionBuilder = SessionBuilder(store, address)
        sessionBuilder.process(bundle)
        println("Processed PreKeyBundle for address: $address")
    }

    actual fun encrypt(address: SignalAddress, plaintext: ByteArray): CiphertextMessage {
        val sessionCipher = SessionCipher(store, address)
        val ciphertext = sessionCipher.encrypt(plaintext)
        return CiphertextMessage(ciphertext)
    }

    actual fun decrypt(address: SignalAddress, ciphertextMessage: CiphertextMessage): ByteArray {
        val sessionCipher = SessionCipher(store, address)
        return when (ciphertextMessage.type) {
            SignalCiphertextMessage.PREKEY_TYPE -> {
                sessionCipher.decrypt(PreKeySignalMessage(ciphertextMessage.serialize()))
            }
            SignalCiphertextMessage.WHISPER_TYPE -> {
                sessionCipher.decrypt(org.whispersystems.libsignal.protocol.WhisperMessage(ciphertextMessage.serialize()))
            }
            else -> throw IllegalArgumentException("Unknown ciphertext type: ${ciphertextMessage.type}")
        }
    }

    actual fun decryptPreKeyMessage(address: SignalAddress, ciphertext: ByteArray): ByteArray {
        val sessionCipher = SessionCipher(store, address)
        val preKeySignalMessage = PreKeySignalMessage(ciphertext)
        return sessionCipher.decrypt(preKeySignalMessage)
    }
}

// Actual implementation for the helper function
actual fun createSignalAddress(name: String, deviceId: Int): SignalAddress {
    return SignalProtocolAddress(name, deviceId)
}

