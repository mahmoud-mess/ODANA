package com.yuzi.odana

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.yuzi.odana.data.AppDatabase
import com.yuzi.odana.data.FlowEntity
import com.yuzi.odana.data.FlowFeatures
import com.yuzi.odana.ml.AnomalyDetector
import com.yuzi.odana.ml.AnomalyResult
import com.yuzi.odana.ml.AnomalySeverity
import com.yuzi.odana.ml.FeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

object FlowManager {
    private const val TAG = "FlowManager"
    private val activeFlows = ConcurrentHashMap<FlowKey, Flow>()
    private val uidCache = ConcurrentHashMap<Int, String>()
    private const val UDP_TIMEOUT_MS = 60000L // 60 seconds
    
    // Tuned for reliability
    private const val BATCH_SIZE = 20              // Smaller batches = more frequent, lighter writes
    private const val STALE_TIMEOUT_MS = 15000L    // 15 seconds idle = stale
    private const val RETENTION_DAYS = 14          // Keep detailed flows for 14 days
    private const val MAX_RECENT_ANOMALIES = 50    // Keep last N anomalies for UI
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FLOOD PROTECTION - Prevent DoS from port scans / traffic floods
    // ═══════════════════════════════════════════════════════════════════════════
    private const val MAX_ACTIVE_FLOWS = 500           // Global limit on active flows
    private const val MAX_FLOWS_PER_APP = 100          // Per-app limit (port scan detection)
    private const val FLOOD_THRESHOLD_PER_SECOND = 400  // Packets/sec that triggers flood mode
    private const val FLOOD_SAMPLE_RATE = 10           // In flood mode, process every Nth packet
    
    private var isFloodMode = false
    private var packetCounter = 0L
    private var lastPacketCountTime = System.currentTimeMillis()
    private var packetsDropped = 0L
    private val appFlowCounts = ConcurrentHashMap<Int, Int>()  // UID -> active flow count
    private val portScanAlertedApps = mutableSetOf<Int>()      // Apps already alerted for scanning
    
    // ═══════════════════════════════════════════════════════════════════════════
    
    private var connectivityManager: ConnectivityManager? = null
    private var packageManager: PackageManager? = null
    var db: AppDatabase? = null
    
    // Track if we're currently flushing to show UI indicator
    private val _isFlushing = MutableStateFlow(false)
    val isFlushing: StateFlow<Boolean> = _isFlushing
    
    // Flood detection state for UI
    private val _isUnderFlood = MutableStateFlow(false)
    val isUnderFlood: StateFlow<Boolean> = _isUnderFlood

    // Live Flow State
    private val managerScope = CoroutineScope(Dispatchers.Default)
    private val _activeFlowsState = MutableStateFlow<List<Flow>>(emptyList())
    val activeFlowsState: StateFlow<List<Flow>> = _activeFlowsState
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ML: Anomaly Detection State
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Recent anomalies detected (for UI alerts) */
    private val _recentAnomalies = MutableStateFlow<List<AnomalyResult>>(emptyList())
    val recentAnomalies: StateFlow<List<AnomalyResult>> = _recentAnomalies
    
    /** Count of anomalies detected this session */
    private val _anomalyCount = MutableStateFlow(0)
    val anomalyCount: StateFlow<Int> = _anomalyCount
    
    /** Is ML analysis enabled? */
    var mlEnabled = true
    
    fun initialize(context: Context) {
        if (db != null) return // Already initialized
        
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        packageManager = context.packageManager
        
        db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "odana-db"
        )
        .fallbackToDestructiveMigration()
        .build()
        
        // Initialize ML Anomaly Detector and Feedback Manager
        managerScope.launch(Dispatchers.IO) {
            try {
                AnomalyDetector.initialize(db!!.profileDao())
                FeedbackManager.initialize(db!!.feedbackDao())
                FeedbackManager.loadMultipliers()
                Log.i(TAG, "Anomaly Detector and Feedback Manager initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ML systems", e)
            }
        }
        
        // Initialize Anomaly Notifier
        AnomalyNotifier.initialize(context)
        AnomalyNotifier.resetSession()
        
        // Start periodic UI update loop (Throttle: 500ms)
        managerScope.launch {
            while (true) {
                if (activeFlows.isNotEmpty()) {
                    _activeFlowsState.value = activeFlows.values.toList()
                        .sortedByDescending { it.lastUpdated }
                } else {
                    if (_activeFlowsState.value.isNotEmpty()) {
                        _activeFlowsState.value = emptyList()
                    }
                }
                delay(500)
            }
        }
        
        // Periodic profile persistence (every 30 seconds)
        managerScope.launch {
            while (true) {
                delay(30000)
                if (AnomalyDetector.isInitialized) {
                    AnomalyDetector.flushProfiles()
                }
            }
        }
    }

    fun getFlow(packet: Packet): Flow? {
        val key = FlowKey(
            packet.sourceIp,
            packet.sourcePort,
            packet.destIp,
            packet.destPort,
            packet.protocol
        )
        
        // Check if flow already exists (fast path)
        activeFlows[key]?.let { return it }
        
        // ═══════════════════════════════════════════════════════════════════════
        // FLOOD PROTECTION CHECKS (for new flows only)
        // ═══════════════════════════════════════════════════════════════════════
        
        // Global limit - prevent memory exhaustion
        if (activeFlows.size >= MAX_ACTIVE_FLOWS) {
            packetsDropped++
            if (packetsDropped % 100 == 0L) {
                Log.w(TAG, "Flood protection: Dropped $packetsDropped packets (max flows reached)")
            }
            return null
        }
        
        // Create and setup new flow
        val flow = Flow(key)
        resolveAppUid(flow, packet)
        
        // Per-app limit - detect port scanning
        flow.appUid?.let { uid ->
            val currentCount = appFlowCounts.getOrDefault(uid, 0)
            if (currentCount >= MAX_FLOWS_PER_APP) {
                // This app is creating too many flows - likely scanning
                packetsDropped++
                
                // Only alert once per app per flood event
                if (!portScanAlertedApps.contains(uid)) {
                    portScanAlertedApps.add(uid)
                    Log.w(TAG, "App ${flow.appName} (UID:$uid) exceeds flow limit - possible port scan!")
                    
                    // Create port scan anomaly
                    val scanAnomaly = AnomalyResult(
                        score = 0.95f,
                        severity = AnomalySeverity.HIGH,
                        reasons = listOf(
                            "Port scanning detected: ${MAX_FLOWS_PER_APP}+ simultaneous connections",
                            "Rapid connection attempts to multiple ports",
                            "This behavior may indicate network reconnaissance"
                        ),
                        breakdown = com.yuzi.odana.ml.ScoreBreakdown(
                            temporal = 0.3f,
                            volume = 0.5f,
                            destination = 1.0f
                        ),
                        appUid = uid,
                        appName = flow.appName,
                        flowKey = "Multiple ports (scan)",
                        timestamp = System.currentTimeMillis()
                    )
                    addAnomaly(scanAnomaly)
                }
                return null
            }
            appFlowCounts[uid] = currentCount + 1
        }
        
        // Store the new flow
        val existing = activeFlows.putIfAbsent(key, flow)
        if (existing != null) {
            // Another thread beat us - use existing, undo app count
            flow.appUid?.let { uid ->
                appFlowCounts.computeIfPresent(uid) { _, count -> 
                    if (count > 0) count - 1 else 0 
                }
            }
            return existing
        }
        
        // Filter out our own traffic from logs
        if (flow.appName != "com.yuzi.odana") {
            Log.d(TAG, "New Flow: $key [App: ${flow.appName}] (active: ${activeFlows.size})")
        }
        
        return flow
    }
    
    private fun resolveAppUid(flow: Flow, packet: Packet) {
        if (packet.protocol != 6 && packet.protocol != 17) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && connectivityManager != null) {
            try {
                val sourceAddress = InetSocketAddress(packet.sourceIp, packet.sourcePort)
                val destAddress = InetSocketAddress(packet.destIp, packet.destPort)
                val uid = connectivityManager!!.getConnectionOwnerUid(
                    packet.protocol,
                    sourceAddress,
                    destAddress
                )
                
                if (uid != -1) {
                    flow.appUid = uid
                    
                    // Check Cache
                    if (uidCache.containsKey(uid)) {
                        flow.appName = uidCache[uid]
                        return
                    }

                    var name: String? = null
                    try {
                        val packages = packageManager?.getPackagesForUid(uid)
                        name = if (!packages.isNullOrEmpty()) {
                            packages[0] // Return the package name (e.g. com.google.android.youtube)
                        } else {
                            "UID:$uid"
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Cross-profile access denied for UID $uid")
                        name = "DualApp/WorkProfile:$uid"
                    } catch (e: Exception) {
                        Log.w(TAG, "Error resolving package for UID $uid", e)
                        name = "UID:$uid"
                    }
                    
                    if (name != null) {
                        flow.appName = name
                        uidCache[uid] = name
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve UID", e)
            }
        }
    }

    fun processPacket(packet: Packet) {
        if (!packet.isIpv4) return
        
        // ═══════════════════════════════════════════════════════════════════════
        // FLOOD DETECTION & SAMPLING
        // ═══════════════════════════════════════════════════════════════════════
        packetCounter++
        
        val now = System.currentTimeMillis()
        val elapsed = now - lastPacketCountTime
        
        // Check packet rate every second
        if (elapsed >= 1000) {
            val packetsPerSecond = (packetCounter * 1000) / elapsed
            
            val wasFloodMode = isFloodMode
            isFloodMode = packetsPerSecond > FLOOD_THRESHOLD_PER_SECOND
            _isUnderFlood.value = isFloodMode
            
            if (isFloodMode && !wasFloodMode) {
                Log.w(TAG, "⚠️ FLOOD MODE ACTIVATED: $packetsPerSecond pkt/s - sampling 1:$FLOOD_SAMPLE_RATE")
                
                // Create flood alert
                val floodAnomaly = AnomalyResult(
                    score = 0.85f,
                    severity = AnomalySeverity.MEDIUM,
                    reasons = listOf(
                        "Traffic flood detected: $packetsPerSecond packets/sec",
                        "Sampling mode activated to prevent app crash",
                        "May indicate DoS attempt or network scanning"
                    ),
                    breakdown = com.yuzi.odana.ml.ScoreBreakdown(
                        temporal = 0.2f,
                        volume = 1.0f,
                        destination = 0.5f
                    ),
                    appUid = -1,
                    appName = "System",
                    flowKey = "Traffic Flood",
                    timestamp = System.currentTimeMillis()
                )
                addAnomaly(floodAnomaly)
            } else if (!isFloodMode && wasFloodMode) {
                Log.i(TAG, "✓ Flood mode deactivated, normal processing resumed")
                // Reset scan alerts so they can trigger again next flood
                portScanAlertedApps.clear()
            }
            
            packetCounter = 0
            lastPacketCountTime = now
        }
        
        // In flood mode, only process every Nth packet
        if (isFloodMode && (packetCounter % FLOOD_SAMPLE_RATE != 0L)) {
            packetsDropped++
            return
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        
        val flow = getFlow(packet) ?: return  // null = dropped due to limits
        flow.addPacket(packet)
        
        if (flow.appUid == null && flow.packets < 5) {
             resolveAppUid(flow, packet)
        }
    }
    
    suspend fun cleanupStaleFlows() {
        val now = System.currentTimeMillis()
        val staleEntries = mutableListOf<Flow>()
        
        val iterator = activeFlows.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val flow = entry.value
            
            // Eviction Criteria:
            // 1. TCP Connection Closed (FIN/RST) - persist immediately
            // 2. Idle for > STALE_TIMEOUT_MS (UDP or stuck TCP)
            val isStale = flow.isClosed || (now - flow.lastUpdated > STALE_TIMEOUT_MS)
            
            if (isStale) {
                staleEntries.add(flow)
                iterator.remove()
                
                // Decrement per-app flow counter
                flow.appUid?.let { uid ->
                    appFlowCounts.computeIfPresent(uid) { _, count ->
                        if (count > 1) count - 1 else null  // Remove entry if 0
                    }
                }
                
                // Smaller batch saves = more frequent but lighter writes
                if (staleEntries.size >= BATCH_SIZE) {
                    saveFlows(staleEntries.toList())
                    staleEntries.clear()
                }
            }
        }

        if (staleEntries.isNotEmpty()) {
            Log.i(TAG, "Persisting ${staleEntries.size} stale flows (active: ${activeFlows.size})")
            saveFlows(staleEntries)
        }
    }
    
    /**
     * Clean up flows older than RETENTION_DAYS.
     * Should be called periodically (e.g., once per day via WorkManager).
     */
    suspend fun cleanupOldFlows() {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - (RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            try {
                val deleted = db?.flowDao()?.deleteFlowsOlderThan(cutoffTime) ?: 0
                if (deleted > 0) {
                    Log.i(TAG, "Cleaned up $deleted flows older than $RETENTION_DAYS days")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old flows", e)
            }
        }
    }

    suspend fun flushAllFlows() {
        _isFlushing.value = true
        Log.i(TAG, "Flushing all active flows to database...")
        
        try {
            val allFlows = ArrayList(activeFlows.values)
            activeFlows.clear()
            appFlowCounts.clear()  // Reset per-app counters
            portScanAlertedApps.clear()  // Reset scan alerts
            
            // Reset flood state
            isFloodMode = false
            _isUnderFlood.value = false
            packetCounter = 0
            
            // Update UI immediately to show flows are gone
            _activeFlowsState.value = emptyList()
            
            // Try to resolve names one last time for any that are still unknown
            allFlows.forEach { flow ->
                if (flow.appName == null || flow.appName!!.startsWith("UID:")) {
                    if (flow.appUid != null) {
                        try {
                            val uid = flow.appUid!!
                            val name = if (uidCache.containsKey(uid)) {
                                uidCache[uid]
                            } else {
                                val packages = packageManager?.getPackagesForUid(uid)
                                if (!packages.isNullOrEmpty()) packages[0] else "UID:$uid"
                            }
                            flow.appName = name
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            }

            // Save in smaller chunks to avoid memory pressure
            if (allFlows.isNotEmpty()) {
                allFlows.chunked(BATCH_SIZE).forEach { chunk ->
                    saveFlows(chunk)
                    // Small delay between batches to let GC breathe
                    delay(50)
                }
            }
            
            // Also run retention cleanup while we're at it
            cleanupOldFlows()
            
            // Persist ML profiles
            if (AnomalyDetector.isInitialized) {
                AnomalyDetector.flushProfiles()
            }
            
        } finally {
            _isFlushing.value = false
            Log.i(TAG, "Flush complete")
        }
    }
    
    private suspend fun saveFlows(flows: List<Flow>) {
        withContext(NonCancellable + Dispatchers.IO) {
            val entities = mutableListOf<FlowEntity>()
            val features = mutableListOf<FlowFeatures>()
            val anomalies = mutableListOf<AnomalyResult>()
            
            flows.forEach { flow ->
                // Create flow entity for history
                val entity = FlowEntity(
                    timestamp = flow.startTime,
                    appUid = flow.appUid,
                    appName = flow.appName,
                    remoteIp = flow.key.destIp,
                    remotePort = flow.key.destPort,
                    protocol = flow.key.protocol,
                    bytes = flow.bytes,
                    packets = flow.packets,
                    durationMs = flow.lastUpdated - flow.startTime,
                    sni = flow.detectedSni,
                    payloadHex = flow.getPayloadHex(),
                    payloadText = flow.getPayloadText()
                )
                entities.add(entity)
                
                // Extract ML features (compact, fixed-size)
                try {
                    val feature = FlowFeatures.fromFlow(flow, flow.detectedSni)
                    features.add(feature)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract features for flow", e)
                }
                
                // Run anomaly analysis (also updates profiles)
                if (mlEnabled && AnomalyDetector.isInitialized) {
                    try {
                        val result = AnomalyDetector.analyzeFlow(flow)
                        if (result.isAnomalous) {
                            anomalies.add(result)
                            Log.w(TAG, "ANOMALY: ${result.summary()}")
                            
                            // Show notification for high-severity anomalies
                            AnomalyNotifier.notifyIfNeeded(result)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to analyze flow", e)
                    }
                }
            }
            
            // Batch insert entities
            try {
                entities.forEach { entity ->
                    db?.flowDao()?.insert(entity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving flow entities", e)
            }
            
            // Batch insert features
            try {
                if (features.isNotEmpty()) {
                    db?.featureDao()?.insertAll(features)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving flow features", e)
            }
            
            // Update anomaly state for UI
            if (anomalies.isNotEmpty()) {
                val currentAnomalies = _recentAnomalies.value.toMutableList()
                currentAnomalies.addAll(0, anomalies)
                
                // Keep only recent anomalies
                if (currentAnomalies.size > MAX_RECENT_ANOMALIES) {
                    _recentAnomalies.value = currentAnomalies.take(MAX_RECENT_ANOMALIES)
                } else {
                    _recentAnomalies.value = currentAnomalies
                }
                
                _anomalyCount.value += anomalies.size
            }
        }
    }
    
    /**
     * Clear all detected anomalies (user dismissed them).
     */
    fun clearAnomalies() {
        _recentAnomalies.value = emptyList()
        _anomalyCount.value = 0
    }
    
    /**
     * Add an anomaly to the list (used by flood/scan detection).
     */
    private fun addAnomaly(anomaly: AnomalyResult) {
        val current = _recentAnomalies.value.toMutableList()
        current.add(0, anomaly)
        if (current.size > MAX_RECENT_ANOMALIES) {
            _recentAnomalies.value = current.take(MAX_RECENT_ANOMALIES)
        } else {
            _recentAnomalies.value = current
        }
        _anomalyCount.value += 1
        
        // Also trigger notification
        AnomalyNotifier.notifyIfNeeded(anomaly)
    }
    
    /**
     * Get current ML profile stats for debugging.
     */
    fun getProfileStats(): String {
        if (!AnomalyDetector.isInitialized) return "ML not initialized"
        
        val profiles = AnomalyDetector.getAllProfiles()
        val mature = profiles.count { it.maturityLevel >= 2 }
        val learning = profiles.count { it.maturityLevel == 1 }
        val infant = profiles.count { it.maturityLevel == 0 }
        val totalFlows = profiles.sumOf { it.flowCount }
        
        return "Profiles: ${profiles.size} (Mature: $mature, Learning: $learning, New: $infant) | Flows learned: $totalFlows"
    }
}