package com.yuzi.odana.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FlowDao {
    @Insert
    suspend fun insert(flow: FlowEntity)

    @Query("""
        SELECT id, timestamp, appUid, appName, remoteIp, remotePort, protocol, bytes, packets, durationMs, sni 
        FROM flow_history 
        WHERE (:query = '' OR appName LIKE '%' || :query || '%' OR remoteIp LIKE '%' || :query || '%' OR sni LIKE '%' || :query || '%' OR CAST(remotePort AS TEXT) LIKE '%' || :query || '%')
        ORDER BY timestamp DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchFlows(query: String, limit: Int, offset: Int): List<FlowSummary>

    @Query("""
        SELECT COUNT(*) FROM flow_history 
        WHERE (:query = '' OR appName LIKE '%' || :query || '%' OR remoteIp LIKE '%' || :query || '%' OR sni LIKE '%' || :query || '%' OR CAST(remotePort AS TEXT) LIKE '%' || :query || '%')
    """)
    suspend fun countFlows(query: String): Int

    @Query("DELETE FROM flow_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM flow_history WHERE id = :id")
    suspend fun getFlowById(id: Long): FlowEntity?

    @Query("SELECT appName, SUM(bytes) as totalBytes FROM flow_history GROUP BY appName ORDER BY totalBytes DESC LIMIT 20")
    fun getAppUsageStats(): kotlinx.coroutines.flow.Flow<List<AppUsage>>
}
