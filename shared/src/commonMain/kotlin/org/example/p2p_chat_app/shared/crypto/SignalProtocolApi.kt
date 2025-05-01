package org.example.p2p_chat_app.shared.crypto

/**
 * Represents a unique address for a Signal Protocol participant.
 * Typically composed of a name (like a phone number or user ID) and a device ID.
 */
expect class SignalAddress {
    val name: String
    val deviceId: Int
}

/**
 * Represents the data needed to establish a session with a recipient.
 * Contains identity key, registration ID, pre-key, and signed pre-key.
 */
expect class PreKeyBundle

/**
 * Represents an encrypted message using the Signal Protocol.
 * Contains the ciphertext and type (PreKey message or regular message).
 */
expect class CiphertextMessage {
    val type: Int
    fun serialize(): ByteArray
}

/**
 * Interface for managing Signal Protocol sessions and cryptographic operations.
 * This defines the common API for E2EE functionality.
 */
expect class SignalProtocolManager {

    /**
     * Generates local identity keys, registration ID, and pre-keys.
     * Should be called once during initial setup.
     */
    fun generateLocalKeys()

    /**
     * Retrieves the local registration ID.
     */
    fun getLocalRegistrationId(): Int

    /**
     * Retrieves the local identity public key.
     */
    fun getLocalIdentityPublicKey(): ByteArray

    /**
     * Generates a PreKeyBundle for the local user to be shared with others.
     */
    fun generatePreKeyBundle(): PreKeyBundle

    /**
     * Processes a received PreKeyBundle from a remote peer to establish a session.
     * @param address The address of the remote peer.
     * @param bundle The PreKeyBundle received from the remote peer.
     */
    fun processPreKeyBundle(address: SignalAddress, bundle: PreKeyBundle)

    /**
     * Encrypts a plaintext message for a specific recipient.
     * @param address The address of the recipient.
     * @param plaintext The message to encrypt (UTF-8 encoded bytes).
     * @return The encrypted CiphertextMessage.
     */
    fun encrypt(address: SignalAddress, plaintext: ByteArray): CiphertextMessage

    /**
     * Decrypts a received CiphertextMessage.
     * @param address The address of the sender.
     * @param ciphertextMessage The received encrypted message.
     * @return The decrypted plaintext (UTF-8 encoded bytes).
     */
    fun decrypt(address: SignalAddress, ciphertextMessage: CiphertextMessage): ByteArray

    /**
     * Decrypts a received PreKeyWhisperMessage (initial session setup message).
     * @param address The address of the sender.
     * @param ciphertext The serialized PreKeyWhisperMessage.
     * @return The decrypted plaintext (UTF-8 encoded bytes).
     */
    fun decryptPreKeyMessage(address: SignalAddress, ciphertext: ByteArray): ByteArray

    // TODO: Add methods for session management, key storage/retrieval if needed
}

// Helper function to create SignalAddress instances (can be in commonMain)
fun createSignalAddress(name: String, deviceId: Int): SignalAddress {
    // Implementation will depend on the actual class structure
    // This might need to be an expect/actual function if the constructor is platform-specific
    // For now, assume a common constructor or factory is possible via expect/actual
    throw NotImplementedError("createSignalAddress needs platform implementation or expect/actual constructor")
}

