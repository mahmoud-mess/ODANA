package com.yuzi.odana

import java.nio.ByteBuffer

object IpUtil {

    fun updateChecksum(buffer: ByteBuffer, offset: Int, length: Int, checksumOffset: Int) {
        val checksum = calculateChecksum(buffer, offset, length)
        buffer.putShort(checksumOffset, checksum.toShort())
    }

    fun calculateChecksum(buffer: ByteBuffer, offset: Int, length: Int): Int {
        var sum = 0
        val end = offset + length
        var current = offset

        while (current < end - 1) {
            sum += (buffer.getShort(current).toInt() and 0xFFFF)
            current += 2
        }

        if (current < end) {
            sum += (buffer.get(current).toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return sum.inv() and 0xFFFF
    }

    /**
     * Calculates TCP/UDP checksum using the Pseudo Header.
     */
    fun calculatePseudoChecksum(
        sourceIp: ByteArray,
        destIp: ByteArray,
        protocol: Int,
        transportLen: Int,
        buffer: ByteBuffer,
        offset: Int
    ): Int {
        var sum = 0

        // Pseudo Header
        // Source IP (4 bytes)
        for (i in 0 until 4 step 2) {
            sum += ((sourceIp[i].toInt() and 0xFF) shl 8) or (sourceIp[i + 1].toInt() and 0xFF)
        }
        // Dest IP (4 bytes)
        for (i in 0 until 4 step 2) {
            sum += ((destIp[i].toInt() and 0xFF) shl 8) or (destIp[i + 1].toInt() and 0xFF)
        }
        // Reserved (1 byte) + Protocol (1 byte)
        sum += protocol and 0xFF
        // TCP/UDP Length (2 bytes)
        sum += transportLen and 0xFFFF

        // Transport Header + Data
        val end = offset + transportLen
        var current = offset

        while (current < end - 1) {
            sum += (buffer.getShort(current).toInt() and 0xFFFF)
            current += 2
        }

        if (current < end) {
            sum += (buffer.get(current).toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return sum.inv() and 0xFFFF
    }

    fun buildUdpPacket(
        sourceIp: ByteArray,
        destIp: ByteArray,
        sourcePort: Int,
        destPort: Int,
        payload: ByteBuffer
    ): ByteBuffer {
        val payloadLen = payload.remaining()
        val totalLen = 20 + 8 + payloadLen // IP(20) + UDP(8) + Data
        val buffer = ByteBuffer.allocate(totalLen)

        // --- IPv4 Header ---
        buffer.put(0x45.toByte()) // Version 4, IHL 5
        buffer.put(0x00.toByte()) // TOS
        buffer.putShort(totalLen.toShort()) // Total Length
        buffer.putShort(0.toShort()) // ID
        buffer.putShort(0x4000.toShort()) // Flags (DF) + Fragment Offset
        buffer.put(64.toByte()) // TTL
        buffer.put(17.toByte()) // Protocol (UDP)
        buffer.putShort(0.toShort()) // IP Checksum (Zero for calculation)
        buffer.put(sourceIp)
        buffer.put(destIp)

        // Calculate IP Checksum
        val ipChecksum = calculateChecksum(buffer, 0, 20)
        buffer.putShort(10, ipChecksum.toShort())

        // --- UDP Header ---
        buffer.putShort(sourcePort.toShort())
        buffer.putShort(destPort.toShort())
        val udpLen = 8 + payloadLen
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort()) // UDP Checksum (Zero initially)

        // Payload
        buffer.put(payload)

        // Calculate UDP Checksum (Pseudo-header + Header + Data)
        // Note: UDP Checksum is optional for IPv4 (can be 0), but good to have.
        // If 0, it means "no checksum". If calculated is 0, store as 0xFFFF.
        // However, Android/Network stacks usually prefer it.
        
        // Reset position to start of UDP header (after IP header)
        // We passed a fresh buffer so IP is 0..19, UDP is 20..27, Data 28..
        // calculatePseudoChecksum expects buffer to contain UDP header + data at 'offset'
        
        val udpChecksum = calculatePseudoChecksum(
            sourceIp, destIp, 17, udpLen,
            buffer, 20
        )
        val finalUdpChecksum = if (udpChecksum == 0) 0xFFFF else udpChecksum
        buffer.putShort(26, finalUdpChecksum.toShort())

        buffer.position(0)
        return buffer
    }

    fun buildTcpPacket(
        sourceIp: ByteArray,
        destIp: ByteArray,
        sourcePort: Int,
        destPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int,
        payload: ByteBuffer?
    ): ByteBuffer {
        val payloadLen = payload?.remaining() ?: 0
        // Header Length: 5 (20 bytes). No options for now.
        val tcpHeaderLen = 20
        val totalLen = 20 + tcpHeaderLen + payloadLen
        val buffer = ByteBuffer.allocate(totalLen)

        // --- IPv4 Header ---
        buffer.put(0x45.toByte()) // Version 4, IHL 5
        buffer.put(0x00.toByte()) // TOS
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0.toShort()) // ID
        buffer.putShort(0x4000.toShort()) // Flags(DF)
        buffer.put(64.toByte()) // TTL
        buffer.put(6.toByte()) // Protocol (TCP)
        buffer.putShort(0.toShort()) // IP Checksum
        buffer.put(sourceIp)
        buffer.put(destIp)

        val ipChecksum = calculateChecksum(buffer, 0, 20)
        buffer.putShort(10, ipChecksum.toShort())

        // --- TCP Header ---
        // Offset 20
        buffer.putShort(sourcePort.toShort())
        buffer.putShort(destPort.toShort())
        buffer.putInt(seq.toInt())
        buffer.putInt(ack.toInt())
        
        // Data Offset (4 bits) + Reserved (6 bits) + Flags (6 bits)
        // Data Offset = 5 (5 * 4 = 20 bytes)
        val dataOffsetAndFlags = (5 shl 12) or (flags and 0x3F)
        buffer.putShort(dataOffsetAndFlags.toShort())
        
        buffer.putShort(window.toShort())
        buffer.putShort(0.toShort()) // Checksum (Zero initially)
        buffer.putShort(0.toShort()) // Urgent Pointer

        if (payload != null) {
            buffer.put(payload)
        }

        // Calculate TCP Checksum
        // Pseudo Header + TCP Header + Data
        val tcpLen = tcpHeaderLen + payloadLen
        val tcpChecksum = calculatePseudoChecksum(
            sourceIp, destIp, 6, tcpLen,
            buffer, 20
        )
        buffer.putShort(36, tcpChecksum.toShort())

        buffer.position(0)
        return buffer
    }
}
