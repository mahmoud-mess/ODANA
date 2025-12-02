package com.yuzi.odana

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.yuzi.odana.data.AppDatabase
import com.yuzi.odana.data.FlowEntity
import kotlinx.coroutines.Dispatchers
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
    private var db: AppDatabase? = null
    
    fun initialize(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        packageManager = context.packageManager
        
        db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "odana-db"
        ).build()
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
            Log.i(TAG, "New Flow: $key [App: ${flow.appName}]")
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
        val iterator = activeFlows.entries.iterator()
        
        // We need to collect flows to remove first to avoid concurrent modification issues 
        // if we were to do complex DB ops inside the iterator loop, though iterator.remove() is safe for CHM.
        // But DB ops are slow, so we shouldn't block the CHM iterator.
        
        val staleEntries = mutableListOf<Flow>()
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastUpdated > UDP_TIMEOUT_MS) {
                staleEntries.add(entry.value)
                iterator.remove()
            }
        }

        if (staleEntries.isNotEmpty()) {
            Log.i(TAG, "Persisting ${staleEntries.size} stale flows")
            saveFlows(staleEntries)
        }
    }
    
    private suspend fun saveFlows(flows: List<Flow>) {
        withContext(Dispatchers.IO) {
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
                    sni = flow.detectedSni
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
