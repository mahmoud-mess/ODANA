package com.yuzi.odana

import java.nio.ByteBuffer

object TlsParser {
    
    fun extractSni(payload: ByteBuffer): String? {
        try {
            if (payload.remaining() < 5) return null
            
            val recordType = payload.get(payload.position()).toInt() and 0xFF
            if (recordType != 0x16) return null // Not a Handshake
            
            // Skip Record Header (5 bytes: Type(1), Ver(2), Len(2))
            var cursor = payload.position() + 5
            if (cursor >= payload.limit()) return null
            
            val handshakeType = payload.get(cursor).toInt() and 0xFF
            if (handshakeType != 0x01) return null // Not Client Hello
            
            // Skip Handshake Header (4 bytes: Type(1), Len(3))
            cursor += 4
            
            // Skip Client Version (2 bytes) + Random (32 bytes)
            cursor += 34
            
            // Session ID Length (1 byte)
            if (cursor >= payload.limit()) return null
            val sessionIdLen = payload.get(cursor).toInt() and 0xFF
            cursor += 1 + sessionIdLen
            
            // Cipher Suites Length (2 bytes)
            if (cursor + 2 >= payload.limit()) return null
            val cipherSuitesLen = payload.getShort(cursor).toInt() and 0xFFFF
            cursor += 2 + cipherSuitesLen
            
            // Compression Methods Length (1 byte)
            if (cursor >= payload.limit()) return null
            val compressionLen = payload.get(cursor).toInt() and 0xFF
            cursor += 1 + compressionLen
            
            // Extensions Length (2 bytes)
            if (cursor + 2 >= payload.limit()) return null
            val extensionsLen = payload.getShort(cursor).toInt() and 0xFFFF
            cursor += 2
            
            val extensionsEnd = cursor + extensionsLen
            while (cursor + 4 <= extensionsEnd && cursor < payload.limit()) {
                val extensionType = payload.getShort(cursor).toInt() and 0xFFFF
                val extensionLen = payload.getShort(cursor + 2).toInt() and 0xFFFF
                cursor += 4
                
                if (extensionType == 0x0000) { // SNI Extension
                    // Server Name List Length (2 bytes)
                    val listLen = payload.getShort(cursor).toInt() and 0xFFFF
                    if (listLen > 0) {
                        // Name Type (1 byte) - 0x00 is Hostname
                        val nameType = payload.get(cursor + 2).toInt()
                        if (nameType == 0) {
                            val nameLen = payload.getShort(cursor + 3).toInt() and 0xFFFF
                            val nameBytes = ByteArray(nameLen)
                            
                            // Save current position to restore later if needed
                            val originalPos = payload.position()
                            payload.position(cursor + 5)
                            payload.get(nameBytes)
                            payload.position(originalPos)
                            
                            return String(nameBytes)
                        }
                    }
                }
                cursor += extensionLen
            }
            
        } catch (e: Exception) {
            // Malformed packet or index out of bounds
        }
        return null
    }
}
