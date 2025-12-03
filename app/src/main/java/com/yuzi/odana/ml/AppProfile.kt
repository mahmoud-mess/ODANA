package com.yuzi.odana.ml

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.yuzi.odana.Flow
import java.util.Calendar

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * APP BEHAVIORAL PROFILE
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This entity captures everything we've learned about an app's network behavior.
 * Each app gets its own profile that evolves over time.
 * 
 * DESIGN PHILOSOPHY:
 * - Store statistical summaries, not raw data (privacy + efficiency)
 * - Support incremental updates (online learning)
 * - Serialize complex structures to strings for Room storage
 * - Track maturity to avoid false positives during cold start
 * 
 * WHAT WE TRACK:
 * 1. TEMPORAL: When does this app usually talk? (time-of-day, day-of-week)
 * 2. VOLUME: How much data does it typically transfer?
 * 3. DESTINATIONS: What IPs/ports/domains does it connect to?
 * 4. PROTOCOL: Does it use TCP, UDP, or both?
 */
@Entity(tableName = "app_profiles")
data class AppProfile(
    @PrimaryKey
    val appUid: Int,
    
    val appName: String?,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // META: Profile lifecycle tracking
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Total flows seen from this app */
    val flowCount: Long = 0,
    
    /** When we first saw this app */
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    
    /** When we last updated this profile */
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    
    /**
     * Profile maturity level:
     * 0 = INFANT: < 30 flows, just collecting data
     * 1 = LEARNING: 30-200 flows, soft detection
     * 2 = MATURE: > 200 flows, full detection confidence
     */
    val maturityLevel: Int = 0,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPORAL: When does this app communicate?
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Serialized HourlyHistogram - activity by hour of day */
    val hourlyHistogramData: String = "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
    
    /** Serialized RunningStats for inter-flow intervals (ms between flows) */
    val interFlowIntervalData: String = "0,0.0,0.0",
    
    /** Days of week this app is active (bitmask: bit 0 = Sunday, bit 6 = Saturday) */
    val activeDaysOfWeek: Int = 0,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VOLUME: How much data does this app move?
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Serialized EMA for bytes received per flow */
    val bytesInEmaData: String = "0.0,0,0.1",
    
    /** Serialized EMA for bytes sent per flow */
    val bytesOutEmaData: String = "0.0,0,0.1",
    
    /** Serialized QuantileSet for total bytes per flow */
    val bytesQuantilesData: String = "",
    
    /** Serialized RunningStats for flow duration */
    val durationStatsData: String = "0,0.0,0.0",
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DESTINATIONS: Where does this app connect?
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Serialized BloomFilter for seen IP:port combinations */
    val destinationFilterData: String = "",
    
    /** Serialized BloomFilter for seen SNI/domain names */
    val domainFilterData: String = "",
    
    /** Map of port -> count (top ports only, as JSON-ish string) */
    val portFrequencyData: String = "{}",
    
    /** Count of unique destinations seen (estimated from bloom filter) */
    val uniqueDestinationCount: Int = 0,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PROTOCOL: What protocols does this app use?
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Count of TCP flows */
    val tcpFlowCount: Long = 0,
    
    /** Count of UDP flows */
    val udpFlowCount: Long = 0,
    
    /** Does this app ever use TCP? */
    val usesTcp: Boolean = false,
    
    /** Does this app ever use UDP? */
    val usesUdp: Boolean = false
    
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSIENT FIELDS (not stored, reconstructed from serialized data)
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Ignore
    private var _hourlyHistogram: HourlyHistogram? = null
    
    @Ignore
    private var _interFlowInterval: RunningStats? = null
    
    @Ignore
    private var _bytesInEma: ExponentialMovingAverage? = null
    
    @Ignore
    private var _bytesOutEma: ExponentialMovingAverage? = null
    
    @Ignore
    private var _durationStats: RunningStats? = null
    
    @Ignore
    private var _destinationFilter: BloomFilter? = null
    
    @Ignore
    private var _domainFilter: BloomFilter? = null
    
    @Ignore
    private var _portFrequency: MutableMap<Int, Int>? = null
    
    @Ignore
    private var _lastFlowTimestamp: Long = 0
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LAZY ACCESSORS (deserialize on first access)
    // ═══════════════════════════════════════════════════════════════════════════
    
    val hourlyHistogram: HourlyHistogram
        get() {
            if (_hourlyHistogram == null) {
                _hourlyHistogram = HourlyHistogram.deserialize(hourlyHistogramData)
            }
            return _hourlyHistogram!!
        }
    
    val interFlowInterval: RunningStats
        get() {
            if (_interFlowInterval == null) {
                _interFlowInterval = RunningStats.deserialize(interFlowIntervalData)
            }
            return _interFlowInterval!!
        }
    
    val bytesInEma: ExponentialMovingAverage
        get() {
            if (_bytesInEma == null) {
                _bytesInEma = ExponentialMovingAverage.deserialize(bytesInEmaData)
            }
            return _bytesInEma!!
        }
    
    val bytesOutEma: ExponentialMovingAverage
        get() {
            if (_bytesOutEma == null) {
                _bytesOutEma = ExponentialMovingAverage.deserialize(bytesOutEmaData)
            }
            return _bytesOutEma!!
        }
    
    val durationStats: RunningStats
        get() {
            if (_durationStats == null) {
                _durationStats = RunningStats.deserialize(durationStatsData)
            }
            return _durationStats!!
        }
    
    val destinationFilter: BloomFilter
        get() {
            if (_destinationFilter == null) {
                _destinationFilter = if (destinationFilterData.isNotEmpty()) {
                    BloomFilter.deserialize(destinationFilterData)
                } else {
                    BloomFilter.forDestinations()
                }
            }
            return _destinationFilter!!
        }
    
    val domainFilter: BloomFilter
        get() {
            if (_domainFilter == null) {
                _domainFilter = if (domainFilterData.isNotEmpty()) {
                    BloomFilter.deserialize(domainFilterData)
                } else {
                    BloomFilter.forDomains()
                }
            }
            return _domainFilter!!
        }
    
    val portFrequency: MutableMap<Int, Int>
        get() {
            if (_portFrequency == null) {
                _portFrequency = parsePortFrequency(portFrequencyData)
            }
            return _portFrequency!!
        }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Update profile with a new flow observation.
     * Returns a new AppProfile with updated statistics.
     */
    fun updateWithFlow(flow: Flow): AppProfile {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = flow.startTime }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1  // 0-6
        
        // Update temporal
        hourlyHistogram.increment(hour)
        
        // Inter-flow interval
        if (_lastFlowTimestamp > 0) {
            val interval = (flow.startTime - _lastFlowTimestamp).toDouble()
            interFlowInterval.update(interval)
        }
        _lastFlowTimestamp = flow.startTime
        
        // Update volume
        bytesInEma.update(flow.bytesIn.toFloat())
        bytesOutEma.update(flow.bytesOut.toFloat())
        durationStats.update((flow.lastUpdated - flow.startTime).toDouble())
        
        // Update destinations
        val destKey = "${flow.key.destIp}:${flow.key.destPort}"
        val isNewDest = destinationFilter.addAndCheckNew(destKey)
        
        // Update domains
        flow.detectedSni?.let { sni ->
            domainFilter.add(sni)
        }
        
        // Update port frequency (track top 20 ports)
        val port = flow.key.destPort
        portFrequency[port] = (portFrequency[port] ?: 0) + 1
        if (portFrequency.size > 20) {
            // Prune least used port
            val minPort = portFrequency.minByOrNull { it.value }?.key
            minPort?.let { portFrequency.remove(it) }
        }
        
        // Calculate new maturity
        val newFlowCount = flowCount + 1
        val newMaturity = when {
            newFlowCount < 30 -> 0
            newFlowCount < 200 -> 1
            else -> 2
        }
        
        // Return updated profile
        return copy(
            flowCount = newFlowCount,
            lastUpdatedTimestamp = now,
            maturityLevel = newMaturity,
            
            // Serialize updated structures
            hourlyHistogramData = hourlyHistogram.serialize(),
            interFlowIntervalData = interFlowInterval.serialize(),
            activeDaysOfWeek = activeDaysOfWeek or (1 shl dayOfWeek),
            
            bytesInEmaData = bytesInEma.serialize(),
            bytesOutEmaData = bytesOutEma.serialize(),
            durationStatsData = durationStats.serialize(),
            
            destinationFilterData = destinationFilter.serialize(),
            domainFilterData = domainFilter.serialize(),
            portFrequencyData = serializePortFrequency(portFrequency),
            uniqueDestinationCount = if (isNewDest) uniqueDestinationCount + 1 else uniqueDestinationCount,
            
            tcpFlowCount = if (flow.key.protocol == 6) tcpFlowCount + 1 else tcpFlowCount,
            udpFlowCount = if (flow.key.protocol == 17) udpFlowCount + 1 else udpFlowCount,
            usesTcp = usesTcp || flow.key.protocol == 6,
            usesUdp = usesUdp || flow.key.protocol == 17
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY METHODS (for scoring)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Is this hour unusual for this app? */
    fun isUnusualHour(hour: Int): Boolean {
        if (maturityLevel == 0) return false
        return hourlyHistogram.unusualScore(hour) > 0.7f
    }
    
    /** Is this a new destination we've never seen? */
    fun isNewDestination(ip: String, port: Int): Boolean {
        return destinationFilter.isNew("$ip:$port")
    }
    
    /** Is this domain new? */
    fun isNewDomain(sni: String): Boolean {
        return domainFilter.isNew(sni)
    }
    
    /** Is this port unusual for this app? */
    fun isUnusualPort(port: Int): Boolean {
        if (portFrequency.isEmpty()) return false
        return port !in portFrequency
    }
    
    /** Is this protocol unusual for this app? */
    fun isUnusualProtocol(protocol: Int): Boolean {
        return when (protocol) {
            6 -> !usesTcp && usesUdp   // TCP is unusual if we only ever saw UDP
            17 -> !usesUdp && usesTcp  // UDP is unusual if we only ever saw TCP
            else -> true
        }
    }
    
    /** Get typical bytes in (EMA) */
    fun typicalBytesIn(): Float = bytesInEma.value
    
    /** Get typical bytes out (EMA) */
    fun typicalBytesOut(): Float = bytesOutEma.value
    
    /** Get Z-score for this duration */
    fun durationZScore(durationMs: Long): Double = durationStats.zScore(durationMs.toDouble())
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun parsePortFrequency(data: String): MutableMap<Int, Int> {
        if (data == "{}" || data.isEmpty()) return mutableMapOf()
        
        return try {
            val content = data.trim('{', '}')
            if (content.isEmpty()) return mutableMapOf()
            
            content.split(",")
                .filter { it.contains(":") }
                .associate { entry ->
                    val (port, count) = entry.split(":")
                    port.trim().toInt() to count.trim().toInt()
                }
                .toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }
    
    private fun serializePortFrequency(map: Map<Int, Int>): String {
        if (map.isEmpty()) return "{}"
        return "{" + map.entries.joinToString(",") { "${it.key}:${it.value}" } + "}"
    }
    
    companion object {
        /** Create a new profile for an app */
        fun create(appUid: Int, appName: String?): AppProfile {
            return AppProfile(
                appUid = appUid,
                appName = appName
            )
        }
        
        // Maturity level constants
        const val MATURITY_INFANT = 0
        const val MATURITY_LEARNING = 1
        const val MATURITY_MATURE = 2
    }
}

