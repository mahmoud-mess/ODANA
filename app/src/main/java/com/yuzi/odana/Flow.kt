package com.yuzi.odana

class Flow(val key: FlowKey) {
    var startTime: Long = System.currentTimeMillis()
    var lastUpdated: Long = System.currentTimeMillis()
    
    var packets: Long = 0
    var bytes: Long = 0
    
    // State
    var isClosed: Boolean = false
    
    // Metadata
    var appUid: Int? = null
    var appName: String? = null
    var detectedSni: String? = null
    
    fun addPacket(packet: Packet) {
        lastUpdated = System.currentTimeMillis()
        packets++
        bytes += packet.totalLength
        
        // Check for TCP Flags (Simple heuristic)
        if (packet.isTcp) {
             // Attempt SNI extraction on Client Hello
             if (detectedSni == null && packet.payload != null) {
                 detectedSni = TlsParser.extractSni(packet.payload!!)
                 if (detectedSni != null) {
                     android.util.Log.i("Flow", "SNI Detected: $detectedSni for app $appName")
                 }
             }
        }
    }
}
