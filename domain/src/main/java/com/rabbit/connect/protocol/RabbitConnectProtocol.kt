package com.rabbit.connect.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.LinkedHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

interface RabbitConnectKeyStore {
    fun load(): ByteArray?
    fun save(key: ByteArray)
    fun clear()
}

/** ECDH authenticated by a one-time, high-entropy TV code; messages use AES-GCM. */
object RabbitConnectProtocol {
    const val PORT = 38841
    const val MAX_PLAINTEXT = 4096
    private const val MAX_FRAME = MAX_PLAINTEXT + 64
    private const val PAIR = 0x50
    private const val RESUME = 0x52
    private const val UNPAIRED = 0x40
    private const val PAIRED = 0x41
    private const val ITERATIONS = 700_000
    private val random = SecureRandom()

    fun isValidPairingCode(value: String?): Boolean = value?.matches(Regex("[0-9]{12}")) == true

    fun accept(
        socket: Socket,
        keys: RabbitConnectKeyStore,
        pairingCode: () -> String,
        onAuthenticated: () -> Unit = {},
        onMessage: (ByteArray) -> ByteArray?,
    ) {
        socket.soTimeout = 8_000
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val request = input.readUnsignedByte()
        require(request == PAIR || request == RESUME) { "Unknown connection mode" }
        val saved = keys.load()
        val session = if (saved == null) {
            require(request == PAIR) { "TV is not paired" }
            output.writeByte(UNPAIRED)
            val serverNonce = randomBytes(32)
            val serverPair = newKeyPair()
            output.write(serverNonce)
            writeBytes(output, serverPair.public.encoded)
            output.flush()
            val clientNonce = ByteArray(32).also(input::readFully)
            val clientPublicBytes = readBytes(input, 1024)
            val clientPublic = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(clientPublicBytes))
            val proof = ByteArray(32).also(input::readFully)
            val code = pairingCode()
            require(code.matches(Regex("[0-9]{12}"))) { "Pairing code expired" }
            val codeKey = passwordKey(code, serverNonce + clientNonce)
            val transcript = serverNonce + clientNonce + serverPair.public.encoded + clientPublicBytes
            require(MessageDigest.isEqual(proof, hmac(codeKey, "client".toByteArray() + transcript))) { "Pairing proof rejected" }
            output.write(hmac(codeKey, "server".toByteArray() + transcript))
            output.flush()
            val root = hkdf(sharedSecret(serverPair, clientPublic), codeKey, "rabbit-connect-root".toByteArray() + transcript)
            keys.save(root)
            hkdf(root, clientNonce + serverNonce, "rabbit-connect-session".toByteArray())
        } else {
            require(request == RESUME) { "TV already paired; revoke pairing before linking another phone" }
            output.writeByte(PAIRED)
            val serverNonce = randomBytes(32)
            output.write(serverNonce)
            output.flush()
            val clientNonce = ByteArray(32).also(input::readFully)
            val proof = ByteArray(32).also(input::readFully)
            val transcript = serverNonce + clientNonce
            require(MessageDigest.isEqual(proof, hmac(saved, "resume-client".toByteArray() + transcript))) { "Phone authentication failed" }
            output.write(hmac(saved, "resume-server".toByteArray() + transcript))
            output.flush()
            hkdf(saved, clientNonce + serverNonce, "rabbit-connect-session".toByteArray())
        }
        onAuthenticated()
        socket.soTimeout = 0
        val channel = SecureChannel(input, output, session, false)
        while (!socket.isClosed) {
            val plaintext = channel.read() ?: break
            onMessage(plaintext)?.let(channel::write)
        }
    }

    fun connect(socket: Socket, keys: RabbitConnectKeyStore, pairingCode: String?): SecureChannel {
        val saved = keys.load()
        if (saved == null) require(isValidPairingCode(pairingCode)) { "Enter the temporary TV pairing code" }
        socket.soTimeout = 8_000
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        if (saved == null) {
            val code = requireNotNull(pairingCode)
            output.writeByte(PAIR)
            output.flush()
            require(input.readUnsignedByte() == UNPAIRED) { "TV is already paired" }
            val serverNonce = ByteArray(32).also(input::readFully)
            val serverPublicBytes = readBytes(input, 1024)
            val serverPublic = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(serverPublicBytes))
            val clientPair = newKeyPair()
            val clientNonce = randomBytes(32)
            val codeKey = passwordKey(code, serverNonce + clientNonce)
            val transcript = serverNonce + clientNonce + serverPublicBytes + clientPair.public.encoded
            output.write(clientNonce)
            writeBytes(output, clientPair.public.encoded)
            output.write(hmac(codeKey, "client".toByteArray() + transcript))
            output.flush()
            val serverProof = ByteArray(32).also(input::readFully)
            require(MessageDigest.isEqual(serverProof, hmac(codeKey, "server".toByteArray() + transcript))) { "TV pairing proof rejected" }
            val root = hkdf(sharedSecret(clientPair, serverPublic), codeKey, "rabbit-connect-root".toByteArray() + transcript)
            keys.save(root)
            socket.soTimeout = 0
            return SecureChannel(input, output, hkdf(root, clientNonce + serverNonce, "rabbit-connect-session".toByteArray()), true)
        }
        output.writeByte(RESUME)
        output.flush()
        require(input.readUnsignedByte() == PAIRED) { "TV pairing was revoked" }
        val serverNonce = ByteArray(32).also(input::readFully)
        val clientNonce = randomBytes(32)
        val transcript = serverNonce + clientNonce
        output.write(clientNonce)
        output.write(hmac(saved, "resume-client".toByteArray() + transcript))
        output.flush()
        val serverProof = ByteArray(32).also(input::readFully)
        require(MessageDigest.isEqual(serverProof, hmac(saved, "resume-server".toByteArray() + transcript))) { "TV authentication failed" }
        socket.soTimeout = 0
        return SecureChannel(input, output, hkdf(saved, clientNonce + serverNonce, "rabbit-connect-session".toByteArray()), true)
    }

    private fun newKeyPair() = KeyPairGenerator.getInstance("EC").run {
        initialize(java.security.spec.ECGenParameterSpec("secp256r1"), random)
        generateKeyPair()
    }
    private fun passwordKey(code: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun sharedSecret(pair: java.security.KeyPair, peer: java.security.PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run { init(pair.private); doPhase(peer, true); generateSecret() }
    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(data)
    }
    private fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val prk = hmac(salt, input)
        return hmac(prk, info + byteArrayOf(1))
    }
    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)
    private fun writeBytes(output: DataOutputStream, value: ByteArray) { require(value.size <= 1024); output.writeShort(value.size); output.write(value) }
    private fun readBytes(input: DataInputStream, max: Int): ByteArray {
        val size = input.readUnsignedShort()
        require(size in 1..max) { "Invalid field size" }
        return ByteArray(size).also(input::readFully)
    }

    class SecureChannel internal constructor(
        private val input: DataInputStream,
        private val output: DataOutputStream,
        session: ByteArray,
        phoneSide: Boolean,
    ) {
        private val txKey = hmac(session, if (phoneSide) "sentinel-to-tv".toByteArray() else "tv-to-sentinel".toByteArray())
        private val rxKey = hmac(session, if (phoneSide) "tv-to-sentinel".toByteArray() else "sentinel-to-tv".toByteArray())
        private var txCounter = 0L
        private var rxCounter = 0L
        @Synchronized fun write(plaintext: ByteArray) {
            require(plaintext.size <= MAX_PLAINTEXT) { "Message too large" }
            val counter = txCounter++
            val ciphertext = crypt(Cipher.ENCRYPT_MODE, txKey, counter, plaintext)
            val size = 8 + ciphertext.size
            require(size <= MAX_FRAME)
            output.writeInt(size); output.writeLong(counter); output.write(ciphertext); output.flush()
        }
        @Synchronized fun read(): ByteArray? {
            val size = try { input.readInt() } catch (_: java.io.EOFException) { return null }
            require(size in 24..MAX_FRAME) { "Invalid frame size" }
            val counter = input.readLong()
            require(counter == rxCounter) { "Unexpected message sequence" }
            val ciphertext = ByteArray(size - 8).also(input::readFully)
            val plaintext = crypt(Cipher.DECRYPT_MODE, rxKey, counter, ciphertext)
            require(plaintext.size <= MAX_PLAINTEXT)
            rxCounter++
            return plaintext
        }
        private fun crypt(mode: Int, key: ByteArray, counter: Long, input: ByteArray): ByteArray {
            val nonce = ByteBuffer.allocate(12).putInt(0x52424331).putLong(counter).array()
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); doFinal(input)
            }
        }
    }
}

/** Bounded key/fingerprint deduplication shared by the sender and the TV overlay. */
class RabbitConnectDeduplicator(private val capacity: Int = 256) {
    private val seen = object : LinkedHashMap<String, Int>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > capacity
    }
    @Synchronized fun isDuplicate(id: String, fingerprint: Int): Boolean = seen[id] == fingerprint
    @Synchronized fun record(id: String, fingerprint: Int) { seen[id] = fingerprint }
    @Synchronized fun shouldSend(id: String, fingerprint: Int): Boolean {
        if (isDuplicate(id, fingerprint)) return false
        record(id, fingerprint)
        return true
    }
}
