package com.yuzi.odana

import java.nio.ByteBuffer
import java.net.InetAddress

class Packet(val buffer: ByteBuffer) {
    
    // IP Header Fields
    var version: Int = 0
    var ihl: Int = 0
    var protocol: Int = 0
    var sourceIp: String = ""
    var destIp: String = ""
    
    fun getSrcIp() = sourceIp
    var headerLength: Int = 0
    var totalLength: Int = 0

    // Transport Header Fields
    var sourcePort: Int = 0
    var destPort: Int = 0
    var isTcp: Boolean = false
    var isUdp: Boolean = false
    
    // Payload
    var payload: ByteBuffer? = null
    var isIpv4: Boolean = false

    init {
        parse()
    }

    private fun parse() {
        if (buffer.remaining() < 20) return
        
        // Basic IPv4 Parsing
        val ipByte = buffer.get(0).toInt()
        version = (ipByte shr 4) and 0x0F
        
        if (version != 4) {
            return
        }
        isIpv4 = true

        ihl = (ipByte and 0x0F)
        headerLength = ihl * 4
        
        protocol = buffer.get(9).toInt() and 0xFF
        
        val srcBytes = ByteArray(4)
        val dstBytes = ByteArray(4)
        buffer.position(12)
        buffer.get(srcBytes)
        buffer.get(dstBytes)
        sourceIp = InetAddress.getByAddress(srcBytes).hostAddress!!
        destIp = InetAddress.getByAddress(dstBytes).hostAddress!!
        
        totalLength = buffer.getShort(2).toInt() and 0xFFFF

        // Transport Layer
        if (protocol == 6) { // TCP
            isTcp = true
            buffer.position(headerLength)
            sourcePort = buffer.getShort().toInt() and 0xFFFF
            destPort = buffer.getShort().toInt() and 0xFFFF
            
            // TCP Header Length is in the 12th byte of TCP header (Data Offset)
            val dataOffset = (buffer.get(headerLength + 12).toInt() shr 4) and 0x0F
            val transportHeaderLen = dataOffset * 4
            val payloadOffset = headerLength + transportHeaderLen
            
            if (totalLength > payloadOffset) {
                buffer.position(payloadOffset)
                payload = buffer.slice()
            }

        } else if (protocol == 17) { // UDP
            isUdp = true
            buffer.position(headerLength)
            sourcePort = buffer.getShort().toInt() and 0xFFFF
            destPort = buffer.getShort().toInt() and 0xFFFF
            
            val transportHeaderLen = 8
            val payloadOffset = headerLength + transportHeaderLen
             
            if (totalLength > payloadOffset) {
                buffer.position(payloadOffset)
                payload = buffer.slice()
            }
        }
    }

    override fun toString(): String {
        val proto = if (isTcp) "TCP" else if (isUdp) "UDP" else "Proto($protocol)"
        return "[$proto] $sourceIp:$sourcePort -> $destIp:$destPort"
    }
}
