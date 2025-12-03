package com.yuzi.odana.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ML feature vectors.
 * Optimized for training data retrieval and model building.
 */
@Dao
interface FeatureDao {
    
    @Insert
    suspend fun insert(features: FlowFeatures)
    
    @Insert
    suspend fun insertAll(features: List<FlowFeatures>)
    
    /**
     * Get all features for a specific app (for per-app model training).
     */
    @Query("SELECT * FROM flow_features WHERE appUid = :appUid ORDER BY flowTimestamp DESC")
    suspend fun getFeaturesForApp(appUid: Int): List<FlowFeatures>
    
    /**
     * Get features for an app within a time window (for incremental learning).
     */
    @Query("""
        SELECT * FROM flow_features 
        WHERE appUid = :appUid AND flowTimestamp >= :sinceTimestamp 
        ORDER BY flowTimestamp DESC
    """)
    suspend fun getRecentFeaturesForApp(appUid: Int, sinceTimestamp: Long): List<FlowFeatures>
    
    /**
     * Get feature count per app (for deciding when to train).
     */
    @Query("SELECT appUid, COUNT(*) as count FROM flow_features GROUP BY appUid")
    suspend fun getFeatureCountsByApp(): List<AppFeatureCount>
    
    /**
     * Get all features (for global model or export).
     */
    @Query("SELECT * FROM flow_features ORDER BY flowTimestamp DESC LIMIT :limit")
    suspend fun getAllFeatures(limit: Int = 10000): List<FlowFeatures>
    
    /**
     * Delete old features (retention policy).
     */
    @Query("DELETE FROM flow_features WHERE flowTimestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int
    
    /**
     * Get total feature count.
     */
    @Query("SELECT COUNT(*) FROM flow_features")
    suspend fun getTotalCount(): Int
    
    /**
     * Get unique app count with features.
     */
    @Query("SELECT COUNT(DISTINCT appUid) FROM flow_features")
    suspend fun getUniqueAppCount(): Int
    
    /**
     * Live count for UI.
     */
    @Query("SELECT COUNT(*) FROM flow_features")
    fun observeTotalCount(): Flow<Int>
}

/**
 * Simple data class for feature counts query.
 */
data class AppFeatureCount(
    val appUid: Int?,
    val count: Int
)

