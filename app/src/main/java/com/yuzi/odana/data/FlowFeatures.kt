package com.yuzi.odana.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yuzi.odana.Flow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ML Feature Vector for a completed flow.
 * Compact, fixed-size representation optimized for on-device learning.
 * 
 * Features are designed for anomaly detection via clustering:
 * - Volume metrics (bytes in/out, packet counts)
 * - Timing metrics (duration, IAT variance)
 * - Behavioral metrics (time of day, protocol)
 * - Context (app UID, foreground status)
 */
@Entity(tableName = "flow_features")
data class FlowFeatures(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Reference to the flow
    val flowTimestamp: Long,
    val appUid: Int?,
    val appName: String?,
    
    // Protocol & Destination
    val protocol: Int,                    // 6=TCP, 17=UDP
    val remotePort: Int,
    val isCommonPort: Boolean,            // 80, 443, 53, etc.
    
    // Volume Features (log-normalized)
    val logBytesIn: Float,                // log(1 + bytesIn)
    val logBytesOut: Float,               // log(1 + bytesOut)
    val logPacketCount: Float,            // log(1 + packets)
    val bytesRatio: Float,                // bytesOut / (bytesIn + bytesOut + 1)
    
    // Timing Features
    val durationMs: Long,
    val logDuration: Float,               // log(1 + durationMs)
    val iatMean: Float,                   // Mean inter-arrival time
    val iatVariance: Float,               // Variance of IAT (jitter indicator)
    
    // First N Packet Sizes (behavioral fingerprint)
    val pktSize1: Int,
    val pktSize2: Int,
    val pktSize3: Int,
    val pktSize4: Int,
    val pktSize5: Int,
    
    // Time-of-Day (cyclical encoding for periodicity detection)
    val timeSin: Float,                   // sin(2π * hour/24)
    val timeCos: Float,                   // cos(2π * hour/24)
    
    // Day-of-Week (cyclical)
    val daySin: Float,                    // sin(2π * dayOfWeek/7)
    val dayCos: Float,                    // cos(2π * dayOfWeek/7)
    
    // SNI/Destination info
    val hasSni: Boolean,
    val sniLength: Int,                   // Length can indicate tracking domains
    
    // Computed at extraction time
    val extractedAt: Long = System.currentTimeMillis()
) {
    companion object {
        // Common ports that are "expected" traffic
        private val COMMON_PORTS = setOf(80, 443, 8080, 8443, 53, 853, 123, 5228, 5229, 5230)
        
        /**
         * Extract features from a completed Flow object.
         */
        fun fromFlow(flow: Flow, sni: String? = null): FlowFeatures {
            val timestamp = flow.startTime
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = timestamp
            }
            val hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            
            // Calculate IAT statistics
            val packetCount = flow.packets.toInt()
            val iatMean = if (packetCount > 1) {
                (flow.iatSum / (packetCount - 1)).toFloat()
            } else 0f
            
            val iatVariance = if (packetCount > 2) {
                val mean = flow.iatSum / (packetCount - 1)
                val variance = (flow.iatSumSq / (packetCount - 1)) - (mean * mean)
                sqrt(variance.coerceAtLeast(0.0)).toFloat() // Standard deviation
            } else 0f
            
            val totalBytes = flow.bytesIn + flow.bytesOut + 1
            
            return FlowFeatures(
                flowTimestamp = timestamp,
                appUid = flow.appUid,
                appName = flow.appName,
                
                protocol = flow.key.protocol,
                remotePort = flow.key.destPort,
                isCommonPort = flow.key.destPort in COMMON_PORTS,
                
                logBytesIn = ln(1.0 + flow.bytesIn).toFloat(),
                logBytesOut = ln(1.0 + flow.bytesOut).toFloat(),
                logPacketCount = ln(1.0 + flow.packets).toFloat(),
                bytesRatio = (flow.bytesOut.toFloat() / totalBytes),
                
                durationMs = flow.lastUpdated - flow.startTime,
                logDuration = ln(1.0 + (flow.lastUpdated - flow.startTime)).toFloat(),
                iatMean = iatMean,
                iatVariance = iatVariance,
                
                pktSize1 = flow.packetSizes.getOrNull(0)?.toInt() ?: 0,
                pktSize2 = flow.packetSizes.getOrNull(1)?.toInt() ?: 0,
                pktSize3 = flow.packetSizes.getOrNull(2)?.toInt() ?: 0,
                pktSize4 = flow.packetSizes.getOrNull(3)?.toInt() ?: 0,
                pktSize5 = flow.packetSizes.getOrNull(4)?.toInt() ?: 0,
                
                // Cyclical time encoding
                timeSin = sin(2 * PI * hourOfDay / 24.0).toFloat(),
                timeCos = cos(2 * PI * hourOfDay / 24.0).toFloat(),
                daySin = sin(2 * PI * dayOfWeek / 7.0).toFloat(),
                dayCos = cos(2 * PI * dayOfWeek / 7.0).toFloat(),
                
                hasSni = !sni.isNullOrEmpty(),
                sniLength = sni?.length ?: 0
            )
        }
        
        /**
         * Get feature vector as FloatArray for ML input.
         * Order matters - must be consistent!
         */
        fun FlowFeatures.toVector(): FloatArray {
            return floatArrayOf(
                protocol.toFloat(),
                if (isCommonPort) 1f else 0f,
                logBytesIn,
                logBytesOut,
                logPacketCount,
                bytesRatio,
                logDuration,
                iatMean / 1000f,  // Normalize to seconds
                iatVariance / 1000f,
                pktSize1.toFloat() / 1500f,  // Normalize to MTU
                pktSize2.toFloat() / 1500f,
                pktSize3.toFloat() / 1500f,
                pktSize4.toFloat() / 1500f,
                pktSize5.toFloat() / 1500f,
                timeSin,
                timeCos,
                daySin,
                dayCos,
                if (hasSni) 1f else 0f,
                sniLength.toFloat() / 100f  // Normalize
            )
        }
        
        /** Feature vector dimension */
        const val VECTOR_SIZE = 20
    }
}

