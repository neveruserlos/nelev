package com.nelev.dev.data

object SyncSender {
    private const val SYNC_PORT = 55002
    private val sock by lazy { try { java.net.DatagramSocket() } catch (_: Exception) { null } }
    fun send(targetIp: String, payload: Map<String, Any>) {
        if (targetIp.isBlank()) return
        try {
            val json = payload.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${if (v is String) "\"$v\"" else v}" }
            val data = ("NSYN$json").toByteArray(Charsets.UTF_8)
            sock?.send(java.net.DatagramPacket(data, data.size, java.net.InetAddress.getByName(targetIp), SYNC_PORT))
        } catch (_: Exception) {}
    }
}
