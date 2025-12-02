package com.yuzi.odana.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {
    @Insert
    suspend fun insert(flow: FlowEntity)

    @Query("SELECT * FROM flow_history ORDER BY timestamp DESC LIMIT 100")
    fun getRecentFlows(): Flow<List<FlowEntity>>

    @Query("DELETE FROM flow_history WHERE timestamp < :cutoff")
    suspend fun deleteOldFlows(cutoff: Long)
}
