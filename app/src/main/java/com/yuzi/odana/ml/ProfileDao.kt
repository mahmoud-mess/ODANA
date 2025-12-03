package com.yuzi.odana.ml

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROFILE DAO - Database Access for App Behavioral Profiles
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Handles persistence of AppProfile entities.
 * Profiles are keyed by appUid (unique per app on device).
 */
@Dao
interface ProfileDao {
    
    /**
     * Insert or replace a profile.
     * Uses REPLACE strategy since we update profiles frequently.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AppProfile)
    
    /**
     * Get profile for a specific app.
     * Returns null if we haven't seen this app before.
     */
    @Query("SELECT * FROM app_profiles WHERE appUid = :appUid")
    suspend fun getProfile(appUid: Int): AppProfile?
    
    /**
     * Get all profiles (for export/analysis).
     */
    @Query("SELECT * FROM app_profiles ORDER BY flowCount DESC")
    suspend fun getAllProfiles(): List<AppProfile>
    
    /**
     * Get profiles with at least N flows (mature enough for detection).
     */
    @Query("SELECT * FROM app_profiles WHERE flowCount >= :minFlows ORDER BY flowCount DESC")
    suspend fun getMatureProfiles(minFlows: Long = 30): List<AppProfile>
    
    /**
     * Live observation of all profiles.
     */
    @Query("SELECT * FROM app_profiles ORDER BY lastUpdatedTimestamp DESC")
    fun observeAllProfiles(): Flow<List<AppProfile>>
    
    /**
     * Get profile count.
     */
    @Query("SELECT COUNT(*) FROM app_profiles")
    suspend fun getProfileCount(): Int
    
    /**
     * Get total flows across all profiles.
     */
    @Query("SELECT SUM(flowCount) FROM app_profiles")
    suspend fun getTotalFlowCount(): Long?
    
    /**
     * Delete a specific profile (e.g., when app is uninstalled).
     */
    @Query("DELETE FROM app_profiles WHERE appUid = :appUid")
    suspend fun deleteProfile(appUid: Int)
    
    /**
     * Delete all profiles (reset learning).
     */
    @Query("DELETE FROM app_profiles")
    suspend fun deleteAllProfiles()
    
    /**
     * Get profiles that haven't been updated in a while.
     * Useful for cleanup of stale profiles.
     */
    @Query("SELECT * FROM app_profiles WHERE lastUpdatedTimestamp < :cutoff")
    suspend fun getStaleProfiles(cutoff: Long): List<AppProfile>
}

