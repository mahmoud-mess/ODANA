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
    
    private var connectivityManager: ConnectivityManager? = null
    private var packageManager: PackageManager? = null
    var db: AppDatabase? = null
    
    // Track if we're currently flushing to show UI indicator
    private val _isFlushing = MutableStateFlow(false)
    val isFlushing: StateFlow<Boolean> = _isFlushing

    // Live Flow State
    private val managerScope = CoroutineScope(Dispatchers.Default)
    private val _activeFlowsState = MutableStateFlow<List<Flow>>(emptyList())
    val activeFlowsState: StateFlow<List<Flow>> = _activeFlowsState
    
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
    }

    fun getFlow(packet: Packet): Flow {
        val key = FlowKey(
            packet.sourceIp,
            packet.sourcePort,
            packet.destIp,
            packet.destPort,
            packet.protocol
        )
        
        return activeFlows.computeIfAbsent(key) { 
            val flow = Flow(key)
            resolveAppUid(flow, packet)
            // Filter out our own traffic from logs to prevent flooding
            if (flow.appName != "com.yuzi.odana") {
                Log.i(TAG, "New Flow: $key [App: ${flow.appName}]")
            }
            flow
        }
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

        val flow = getFlow(packet)
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
                
                // Smaller batch saves = more frequent but lighter writes
                if (staleEntries.size >= BATCH_SIZE) {
                    saveFlows(staleEntries.toList())
                    staleEntries.clear()
                }
            }
        }

        if (staleEntries.isNotEmpty()) {
            Log.i(TAG, "Persisting ${staleEntries.size} stale flows")
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
            
        } finally {
            _isFlushing.value = false
            Log.i(TAG, "Flush complete")
        }
    }
    
    private suspend fun saveFlows(flows: List<Flow>) {
        withContext(NonCancellable + Dispatchers.IO) {
            val entities = mutableListOf<FlowEntity>()
            val features = mutableListOf<FlowFeatures>()
            
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
        }
    }
}