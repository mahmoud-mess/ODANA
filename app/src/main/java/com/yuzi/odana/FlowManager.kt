package com.yuzi.odana

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.yuzi.odana.data.AppDatabase
import com.yuzi.odana.data.FlowEntity
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
    
    private var connectivityManager: ConnectivityManager? = null
    private var packageManager: PackageManager? = null
    var db: AppDatabase? = null

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
            packet.protocol,
            packet.sourceIp,
            packet.sourcePort,
            packet.destIp,
            packet.destPort
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
        val batchSize = 50
        
        val iterator = activeFlows.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val flow = entry.value
            
            // Eviction Criteria:
            // 1. TCP Connection Closed (FIN/RST)
            // 2. Idle for > 30 seconds (UDP or stuck TCP)
            val isStale = flow.isClosed || (now - flow.lastUpdated > 30000)
            
            if (isStale) {
                staleEntries.add(flow)
                iterator.remove()
                
                // Batch Save to prevent memory spike if many flows close at once
                if (staleEntries.size >= batchSize) {
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

    suspend fun flushAllFlows() {
        Log.i(TAG, "Flushing all active flows to database...")
        val allFlows = ArrayList(activeFlows.values)
        activeFlows.clear()
        
        // Try to resolve names one last time for any that are still unknown
        allFlows.forEach { flow ->
            if (flow.appName == null || flow.appName!!.startsWith("UID:")) {
                 // We can't easily pass the packet here, but if we have the UID we can try.
                 // However, resolveAppUid requires a packet for connection owner lookup.
                 // If we already have the UID (from previous lookup), we can try to fetch the name again.
                 if (flow.appUid != null) {
                     try {
                         // Re-run just the name lookup part
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

        if (allFlows.isNotEmpty()) {
            saveFlows(allFlows)
        }
    }
    
    private suspend fun saveFlows(flows: List<Flow>) {
        withContext(NonCancellable + Dispatchers.IO) {
            flows.forEach { flow ->
                val entity = FlowEntity(
                    timestamp = flow.startTime,
                    appUid = flow.appUid,
                    appName = flow.appName,
                    remoteIp = flow.key.dstIp,
                    remotePort = flow.key.dstPort,
                    protocol = flow.key.protocol,
                    bytes = flow.bytes,
                    packets = flow.packets,
                    durationMs = flow.lastUpdated - flow.startTime,
                    sni = flow.detectedSni,
                    payloadHex = flow.getPayloadHex(),
                    payloadText = flow.getPayloadText()
                )
                try {
                    db?.flowDao()?.insert(entity)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving flow", e)
                }
            }
        }
    }
}