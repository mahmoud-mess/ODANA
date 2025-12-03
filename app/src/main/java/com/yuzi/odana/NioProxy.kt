package com.yuzi.odana

import android.util.Log
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.channels.spi.AbstractSelectableChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import java.net.InetAddress

import com.yuzi.odana.data.BlockList

class NioProxy(private val vpnWriter: (ByteBuffer) -> Unit) : Runnable {

    private val TAG = "NioProxy"
    private val selector: Selector = Selector.open()
    private val packetQueue = ConcurrentLinkedQueue<Packet>()
    
    // UDP Map
    // Key: "SourceIp:SourcePort|DestIp:DestPort|Protocol"
    private val flowMap = ConcurrentHashMap<String, AbstractSelectableChannel>()
    
    // TCP Map
    private val tcpSessionMap = ConcurrentHashMap<String, TcpSession>()
    
    // Reverse mapping: Channel -> FlowKey
    private val channelKeyMap = ConcurrentHashMap<AbstractSelectableChannel, FlowKey>()

    data class FlowKey(
        val sourceIp: String,
        val sourcePort: Int,
        val destIp: String,
        val destPort: Int,
        val protocol: Int
    ) {
        override fun toString(): String = "$sourceIp:$sourcePort|$destIp:$destPort|$protocol"
    }

    @Volatile
    private var isRunning = true

    fun start() {
        isRunning = true
        Thread(this, "NioProxyThread").start()
    }

    fun stop() {
        isRunning = false
        selector.wakeup()
    }

    fun onPacketFromTun(packet: Packet) {
        packetQueue.offer(packet)
        selector.wakeup()
    }

    override fun run() {
        Log.i(TAG, "NioProxy loop started")
        var lastCleanupTime = System.currentTimeMillis()
        
        while (isRunning) {
            try {
                processQueuedPackets()

                // Use timeout to allow periodic cleanup even if no network traffic
                val readyChannels = selector.select(1000)
                
                if (readyChannels > 0) {
                    val keys = selector.selectedKeys().iterator()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        keys.remove()

                        if (!key.isValid) continue

                        if (key.isConnectable) {
                            handleTcpConnect(key)
                        }
                        if (key.isReadable) {
                            handleRead(key)
                        }
                    }
                }
                
                // Cleanup Stale Sessions (every 60 seconds)
                val now = System.currentTimeMillis()
                if (now - lastCleanupTime > 60000) {
                    cleanupStaleSessions(now)
                    lastCleanupTime = now
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in NioProxy loop", e)
            }
        }
        cleanup()
    }
    
    private fun cleanupStaleSessions(now: Long) {
        // TCP Timeout: 2 minutes
        val tcpTimeout = 120000L 
        
        val iterator = tcpSessionMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val session = entry.value
            if (now - session.lastActiveTime > tcpTimeout) {
                Log.d(TAG, "Closing stale TCP session: ${session.key}")
                closeSession(session.key)
            }
        }
    }

    private fun processQueuedPackets() {
        while (true) {
            val packet = packetQueue.poll() ?: break
            try {
                // Check Blocklist
                val flow = FlowManager.getFlow(packet)
                if (flow.appUid != null && BlockList.isUidBlocked(flow.appUid!!)) {
                    Log.d(TAG, "Blocked packet from UID: ${flow.appUid}")
                    // Optional: Send RST if TCP? For now just drop (silent drop)
                    continue 
                }

                if (packet.isUdp) {
                    handleUdpPacket(packet)
                } else if (packet.isTcp) {
                    handleTcpPacket(packet)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing queued packet", e)
            }
        }
    }

    // --- TCP Handler ---

    private fun handleTcpPacket(packet: Packet) {
        val key = FlowKey(packet.sourceIp, packet.sourcePort, packet.destIp, packet.destPort, packet.protocol)
        val keyStr = key.toString()
        
        var session = tcpSessionMap[keyStr]

        // Parse TCP Header Basics
        val tcpHeaderStart = packet.headerLength
        val seqNum = packet.buffer.getInt(tcpHeaderStart + 4).toLong() and 0xFFFFFFFFL
        val ackNum = packet.buffer.getInt(tcpHeaderStart + 8).toLong() and 0xFFFFFFFFL
        val flags = packet.buffer.get(tcpHeaderStart + 13).toInt()
        val isSyn = (flags and 0x02) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0
        val isAck = (flags and 0x10) != 0
        val isPsh = (flags and 0x08) != 0 // PSH usually comes with Data

        // 1. Handle RST (Reset) - Close immediately
        if (isRst) {
            closeSession(keyStr)
            return
        }

        // 2. New Connection (SYN)
        if (session == null) {
            if (isSyn) {
                try {
                    val channel = SocketChannel.open()
                    channel.configureBlocking(false)
                    channel.connect(InetSocketAddress(packet.destIp, packet.destPort))
                    channel.register(selector, SelectionKey.OP_CONNECT)

                    session = TcpSession(keyStr, packet.sourceIp, packet.sourcePort, packet.destIp, packet.destPort, channel)
                    session.state = TcpSession.State.SYN_RECEIVED
                    
                    // Initialize Seq Numbers
                    // We ack their SYN (Seq + 1)
                    session.myAck = seqNum + 1
                    // We start our Seq at a random number (e.g., 1000 for debug)
                    session.mySeq = 1000 

                    tcpSessionMap[keyStr] = session
                    channelKeyMap[channel] = key
                    
                    // Send SYN-ACK
                    sendTcpPacket(session, (0x02 or 0x10), null) // SYN | ACK
                    
                    // Increment our Seq because we sent SYN
                    session.mySeq++ 
                    
                    Log.d(TAG, "New TCP Session: $keyStr")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create TCP channel", e)
                    // Send RST
                    // (Simple RST construction logic needed here, or just ignore)
                }
            } else {
                // Received Data/Ack for unknown session -> RST
                // We can use the old handleTcpReset logic here if we wanted, but for now ignore.
            }
            return
        }
        
        session.updateActivity()

        // 3. Handle Existing Session
        
        // If FIN, close
        if (isFin) {
            session.state = TcpSession.State.CLOSE_WAIT
            // Send ACK for FIN
            session.myAck = seqNum + 1
            sendTcpPacket(session, 0x10, null) // ACK
            // Close backend
            closeSession(keyStr)
            return
        }

        // Data Transfer
        // Payload?
        // TCP Header Length
        val dataOffset = (packet.buffer.get(tcpHeaderStart + 12).toInt() shr 4) and 0x0F
        val transportHeaderLen = dataOffset * 4
        val payloadLen = packet.totalLength - (packet.headerLength + transportHeaderLen)
        
        if (payloadLen > 0) {
            // App sent data.
            // Update Ack to include this data length
            session.myAck = seqNum + payloadLen
            
            val payload = packet.buffer
            payload.position(packet.headerLength + transportHeaderLen)
            val data = ByteBuffer.allocate(payloadLen)
            data.put(payload)
            data.flip()

            if (session.isConnectedToBackend) {
                try {
                    session.channel?.write(data)
                } catch (e: Exception) {
                    closeSession(keyStr)
                }
            } else {
                session.pendingWriteBuffer.offer(data)
            }
            
            // Send ACK to App
            sendTcpPacket(session, 0x10, null) // ACK
        } else {
            // Just an ACK packet from App?
            // If it's the ACK for our SYN-ACK (Handshake complete)
            if (session.state == TcpSession.State.SYN_RECEIVED && isAck) {
                session.state = TcpSession.State.ESTABLISHED
            }
        }
    }

    private fun handleTcpConnect(key: SelectionKey) {
        val channel = key.channel() as SocketChannel
        val flowKey = channelKeyMap[channel] ?: return
        val session = tcpSessionMap[flowKey.toString()] ?: return

        try {
            if (channel.finishConnect()) {
                session.isConnectedToBackend = true
                channel.register(selector, SelectionKey.OP_READ)
                
                // Flush pending writes
                while (true) {
                    val data = session.pendingWriteBuffer.poll() ?: break
                    channel.write(data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TCP Connect failed", e)
            closeSession(flowKey.toString())
        }
    }
    
    private fun handleTcpRead(key: SelectionKey) {
        val channel = key.channel() as SocketChannel
        val flowKey = channelKeyMap[channel] ?: return
        val session = tcpSessionMap[flowKey.toString()] ?: return

        val buffer = ByteBuffer.allocate(4096)
        try {
            val readBytes = channel.read(buffer)
            if (readBytes == -1) {
                // EOF -> Server closed
                closeSession(flowKey.toString())
                // Send FIN to App
                sendTcpPacket(session, (0x01 or 0x10), null) // FIN | ACK
            } else if (readBytes > 0) {
                buffer.flip()
                
                // Send PSH | ACK to App
                sendTcpPacket(session, (0x08 or 0x10), buffer)
                
                // Increase our Seq because we sent data
                session.mySeq += readBytes
            }
        } catch (e: Exception) {
            closeSession(flowKey.toString())
        }
    }

    private fun sendTcpPacket(session: TcpSession, flags: Int, payload: ByteBuffer?) {
        val packet = IpUtil.buildTcpPacket(
            InetAddress.getByName(session.destIp).address, // Src is remote server
            InetAddress.getByName(session.sourceIp).address, // Dst is App
            session.destPort,
            session.sourcePort,
            session.mySeq,
            session.myAck,
            flags,
            65535, // Window
            payload
        )
        vpnWriter(packet)
    }

    // --- UDP Handler --- (Preserved)

    private fun handleUdpPacket(packet: Packet) {
        val key = FlowKey(packet.sourceIp, packet.sourcePort, packet.destIp, packet.destPort, packet.protocol)
        val keyStr = key.toString()

        var channel = flowMap[keyStr] as? DatagramChannel

        if (channel == null) {
            try {
                channel = DatagramChannel.open()
                channel.configureBlocking(false)
                channel.connect(InetSocketAddress(packet.destIp, packet.destPort))
                channel.register(selector, SelectionKey.OP_READ)

                flowMap[keyStr] = channel
                channelKeyMap[channel] = key
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create UDP channel", e)
                return
            }
        }

        packet.payload?.let { payload ->
            try {
                channel.write(payload)
            } catch (e: Exception) {
                closeSession(keyStr)
            }
        }
    }

    private fun handleRead(key: SelectionKey) {
        val channel = key.channel()
        
        if (channel is SocketChannel) {
            handleTcpRead(key)
            return
        }
        
        if (channel is DatagramChannel) {
            val buffer = ByteBuffer.allocate(4096)
            try {
                if (!channel.isConnected) return 
                val readBytes = channel.read(buffer)
                if (readBytes > 0) {
                    buffer.flip()
                    val flowKey = channelKeyMap[channel] ?: return
                    
                    val responsePacket = IpUtil.buildUdpPacket(
                        InetAddress.getByName(flowKey.destIp).address,
                        InetAddress.getByName(flowKey.sourceIp).address,
                        flowKey.destPort,
                        flowKey.sourcePort,
                        buffer
                    )
                    vpnWriter(responsePacket)
                }
            } catch (e: Exception) {
                val flowKey = channelKeyMap[channel]
                if (flowKey != null) closeSession(flowKey.toString())
            }
        }
    }
    
    private fun closeSession(keyStr: String) {
        // Try UDP
        val udpChannel = flowMap.remove(keyStr)
        if (udpChannel != null) {
            channelKeyMap.remove(udpChannel)
            try { udpChannel.close() } catch (e: Exception) {}
        }
        
        // Try TCP
        val tcpSession = tcpSessionMap.remove(keyStr)
        if (tcpSession != null) {
            tcpSession.state = TcpSession.State.CLOSED
            tcpSession.channel?.let { ch ->
                channelKeyMap.remove(ch)
                try { ch.close() } catch (e: Exception) {}
            }
            tcpSession.channel = null
        }
    }

    private fun cleanup() {
        try {
            selector.close()
            flowMap.values.forEach { try { it.close() } catch (e: Exception) {} }
            flowMap.clear()
            
            tcpSessionMap.values.forEach { 
                try { it.channel?.close() } catch (e: Exception) {} 
            }
            tcpSessionMap.clear()
            
            channelKeyMap.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing selector", e)
        }
    }
}
