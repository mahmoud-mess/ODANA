package com.yuzi.odana

class Flow(val key: FlowKey) {
    var startTime: Long = System.currentTimeMillis()
    var lastUpdated: Long = System.currentTimeMillis()
    
    var packets: Long = 0
    var bytes: Long = 0
    
    // ML Features
    var bytesOut: Long = 0
    var bytesIn: Long = 0
    var packetSizes = LongArray(5) { 0 }
    var iatSum: Double = 0.0
    var iatSumSq: Double = 0.0
    private var lastPacketTime: Long = startTime
    
    // State
    var isClosed: Boolean = false
    
    // Metadata
    var appUid: Int? = null
    var appName: String? = null
    var detectedSni: String? = null
    
    // Payload Capture (Limit 64KB - reduced from 256KB for memory efficiency)
    private val payloadStream = java.io.ByteArrayOutputStream()
    
    companion object {
        const val MAX_PAYLOAD_SIZE = 65536 // 64KB (reduced from 256KB)
    }
    
    fun addPacket(packet: Packet) {
        val now = System.currentTimeMillis()
        lastUpdated = now
        packets++
        bytes += packet.totalLength
        
        // ML: Traffic Direction (Assuming key.sourceIp is the App/Local IP)
        // Comparing String IPs is safe here
        if (packet.getSrcIp() == key.sourceIp) {
            bytesOut += packet.totalLength
        } else {
            bytesIn += packet.totalLength
        }
        
        // ML: First N Packet Sizes
        if (packets <= 5) {
            packetSizes[packets.toInt() - 1] = packet.totalLength.toLong()
        }
        
        // ML: IAT (Inter-Arrival Time)
        if (packets > 1) {
            val iat = (now - lastPacketTime).toDouble()
            iatSum += iat
            iatSumSq += (iat * iat)
        }
        lastPacketTime = now
        
        // Capture Payload (256KB limit)
        packet.payload?.let { buffer ->
            if (payloadStream.size() < MAX_PAYLOAD_SIZE) {
                val remaining = buffer.remaining()
                if (remaining > 0) {
                    val bytes = ByteArray(remaining)
                    val pos = buffer.position()
                    buffer.get(bytes)
                    buffer.position(pos) // Restore position for other readers
                    
                    val space = MAX_PAYLOAD_SIZE - payloadStream.size()
                    val toWrite = kotlin.math.min(remaining, space)
                    payloadStream.write(bytes, 0, toWrite)
                }
            }
        }
        
        // Check for TCP Flags
        if (packet.isTcp) {
             val buffer = packet.buffer
             val flagsOffset = packet.headerLength + 13
             if (buffer.limit() > flagsOffset) {
                 val flags = buffer.get(flagsOffset).toInt()
                 val isFin = (flags and 0x01) != 0
                 val isRst = (flags and 0x04) != 0
                 if (isFin || isRst) {
                     isClosed = true
                 }
             }

             // Attempt SNI extraction on Client Hello
             if (detectedSni == null && packet.payload != null) {
                 detectedSni = TlsParser.extractSni(packet.payload!!)
                 if (detectedSni != null) {
                     android.util.Log.i("Flow", "SNI Detected: $detectedSni for app $appName")
                 }
             }
        }
    }
    
    fun isStale(timeoutMs: Long): Boolean {
        return (System.currentTimeMillis() - lastUpdated) > timeoutMs
    }
    
    fun getPayloadHex(): String {
        val bytes = payloadStream.toByteArray()
        if (bytes.isEmpty()) return ""
        
        val sb = StringBuilder()
        // Simple Hex + ASCII Dump
        for (i in bytes.indices step 16) {
            // Hex
            for (j in 0 until 16) {
                if (i + j < bytes.size) {
                    sb.append(String.format("%02X ", bytes[i + j]))
                } else {
                    sb.append("   ")
                }
            }
            sb.append("  ")
            // ASCII
            for (j in 0 until 16) {
                if (i + j < bytes.size) {
                    val b = bytes[i + j].toInt().toChar()
                    if (b in ' '..'~') sb.append(b) else sb.append('.')
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }
    
    fun getPayloadText(): String {
        val bytes = payloadStream.toByteArray()
        if (bytes.isEmpty()) return ""
        
        // Attempt to create a string, replacing control chars
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            // Allow newlines and tabs, otherwise printable ASCII
            if (c == '\n' || c == '\r' || c == '\t' || (c in ' '..'~')) {
                sb.append(c)
            } else {
                sb.append('.')
            }
        }
        return sb.toString()
    }
}
