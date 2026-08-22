package de.lifeos.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

class AndroidMacP2PClient(
    private val macTailscaleIp: String = "100.64.0.2",
    private val port: Int = 8443,
    private val pairingToken: String
) {
    suspend fun syncNodeToMac(nodeId: String, payloadJson: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(macTailscaleIp, port), 1200)
                val dos = DataOutputStream(socket.getOutputStream())
                val dis = DataInputStream(socket.getInputStream())

                // Step 1: Send auth token (SHA256 hexdigest of pairing token, 64 bytes)
                val authToken = MessageDigest.getInstance("SHA-256")
                    .digest(pairingToken.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                dos.write(authToken.toByteArray(Charsets.UTF_8))
                dos.flush()

                // Step 2: Read server auth response
                val authResponse = ByteArray(8)
                dis.readFully(authResponse)
                if (String(authResponse) != "AUTH_OK\n") {
                    return@runCatching false
                }

                // Step 3: Send SYNC command
                dos.write("SYNC".toByteArray(Charsets.UTF_8))
                dos.flush()

                // Step 4: Send payload
                val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
                dos.writeInt(payloadBytes.size)
                dos.write(payloadBytes)
                dos.flush()

                // Step 5: Read response
                val ackLen = dis.readInt()
                val ackBytes = ByteArray(ackLen)
                dis.readFully(ackBytes)
                String(ackBytes) == "ACK_SAVED"
            }
        }.getOrDefault(false)
    }

    suspend fun pingMac(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(macTailscaleIp, port), 1200)
                val dos = DataOutputStream(socket.getOutputStream())
                val dis = DataInputStream(socket.getInputStream())

                // Send auth token
                val authToken = MessageDigest.getInstance("SHA-256")
                    .digest(pairingToken.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                dos.write(authToken.toByteArray(Charsets.UTF_8))
                dos.flush()

                // Read auth response
                val authResponse = ByteArray(8)
                dis.readFully(authResponse)
                if (String(authResponse) != "AUTH_OK\n") {
                    return@runCatching false
                }

                // Send PING command
                dos.write("PING".toByteArray(Charsets.UTF_8))
                dos.flush()

                // Read response
                val resp = ByteArray(16)
                dis.readFully(resp)
                String(resp).trim() == "PONG_MAC_READY"
            }
        }.getOrDefault(false)
    }
}
