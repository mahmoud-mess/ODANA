package com.yuzi.odana

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class TcpSession(
    val key: String,
    val sourceIp: String,
    val sourcePort: Int,
    val destIp: String,
    val destPort: Int,
    var channel: SocketChannel?
) {
    enum class State {
        CLOSED, SYN_RECEIVED, ESTABLISHED, FIN_WAIT, CLOSE_WAIT
    }

    var state: State = State.CLOSED
    
    // Sequence numbers (unsigned int 32-bit logic required, handled as Long)
    var mySeq: Long = 0   // The sequence number I send to the App
    var myAck: Long = 0   // The acknowledgment number I expect from the App (== App's Seq)
    
    // Helper to track if we are connected to the backend
    var isConnectedToBackend = false
    
    // Buffer for data received from App while waiting for backend connection
    val pendingWriteBuffer = java.util.concurrent.ConcurrentLinkedQueue<ByteBuffer>()
    
    var lastActiveTime: Long = System.currentTimeMillis()

    fun updateActivity() {
        lastActiveTime = System.currentTimeMillis()
    }
}
