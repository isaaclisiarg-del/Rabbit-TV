package com.luncher.domain.rabbitconnect

import com.rabbit.connect.protocol.RabbitConnectDeduplicator
import com.rabbit.connect.protocol.RabbitConnectKeyStore
import com.rabbit.connect.protocol.RabbitConnectProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RabbitConnectProtocolTest {
    @Test fun firstPairAndReconnectCarryAuthenticatedEncryptedMessages() {
        val tvKeys = MemoryKeyStore()
        val phoneKeys = MemoryKeyStore()
        ServerSocket(0).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                fun roundTrip(code: String?) {
                    val result = executor.submit {
                        listener.accept().use { socket ->
                            RabbitConnectProtocol.accept(socket, tvKeys, { "012345678901" }) { bytes -> "ack:".toByteArray() + bytes }
                        }
                    }
                    Socket("127.0.0.1", listener.localPort).use { socket ->
                        val channel = RabbitConnectProtocol.connect(socket, phoneKeys, code)
                        val payload = "synthetic-only".toByteArray()
                        channel.write(payload)
                        assertArrayEquals("ack:".toByteArray() + payload, channel.read())
                    }
                    result.get(10, TimeUnit.SECONDS)
                }
                roundTrip("012345678901")
                assertTrue(tvKeys.load()!!.contentEquals(phoneKeys.load()!!))
                roundTrip(null)
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun rejectsMalformedTemporaryCodeBeforeOpeningSocket() {
        val keys = MemoryKeyStore()
        try {
            RabbitConnectProtocol.connect(Socket(), keys, "1234")
            throw AssertionError("invalid pairing code accepted")
        } catch (_: IllegalArgumentException) {
            assertTrue(keys.load() == null)
        }
    }

    @Test fun deduplicatorAllowsUpdatesButSuppressesRepeatedFrames() {
        val dedupe = RabbitConnectDeduplicator(2)
        assertTrue(dedupe.shouldSend("notice", 1))
        assertFalse(dedupe.shouldSend("notice", 1))
        assertTrue(dedupe.shouldSend("notice", 2))
        assertEquals(false, dedupe.isDuplicate("new", 2))
        dedupe.record("new", 2)
        assertTrue(dedupe.isDuplicate("new", 2))
    }

    private class MemoryKeyStore : RabbitConnectKeyStore {
        private var secret: ByteArray? = null
        override fun load(): ByteArray? = secret?.copyOf()
        override fun save(key: ByteArray) { secret = key.copyOf() }
        override fun clear() { secret = null }
    }
}
