package com.yuzi.odana.ml

import android.util.Log
import com.yuzi.odana.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * ANOMALY DETECTOR - The Ensemble Orchestrator
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This is the main entry point for the ML system. It:
 * 1. Maintains in-memory cache of app profiles
 * 2. Orchestrates all three scorers
 * 3. Combines scores with configurable weights
 * 4. Produces final anomaly verdicts with explanations
 * 
 * DESIGN DECISIONS:
 * - Profiles cached in memory for fast scoring
 * - Async persistence to DB (batched for efficiency)
 * - Lock-free reads, locked writes per app
 * - Configurable thresholds for different sensitivity levels
 */
object AnomalyDetector {
    
    private const val TAG = "AnomalyDetector"
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Weights for combining scorer outputs.
     * These can be tuned based on what types of anomalies matter most.
     */
    data class ScorerWeights(
        val temporal: Float = 0.25f,
        val volume: Float = 0.35f,
        val destination: Float = 0.40f
    ) {
        init {
            require(temporal + volume + destination <= 1.01f) { 
                "Weights must sum to ~1.0" 
            }
        }
    }
    
    /**
     * Thresholds for anomaly classification.
     */
    data class Thresholds(
        val lowAnomaly: Float = 0.3f,     // > this = worth noting
        val mediumAnomaly: Float = 0.5f,  // > this = suspicious
        val highAnomaly: Float = 0.7f     // > this = likely malicious
    )
    
    // Default configurations
    var weights = ScorerWeights()
        private set
    var thresholds = Thresholds()
        private set
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    // In-memory profile cache (appUid -> profile)
    private val profileCache = ConcurrentHashMap<Int, AppProfile>()
    
    // Per-app locks for safe updates
    private val profileLocks = ConcurrentHashMap<Int, Mutex>()
    
    // Reference to DAO (set during initialization)
    private var profileDao: ProfileDao? = null
    
    // Profiles that need to be persisted
    private val dirtyProfiles = ConcurrentHashMap.newKeySet<Int>()
    
    // Is the detector initialized?
    var isInitialized = false
        private set
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialize the detector with database access.
     * Call this once during app startup.
     */
    suspend fun initialize(dao: ProfileDao) {
        if (isInitialized) return
        
        profileDao = dao
        
        // Load existing profiles into cache
        withContext(Dispatchers.IO) {
            try {
                val profiles = dao.getAllProfiles()
                profiles.forEach { profile ->
                    profileCache[profile.appUid] = profile
                    profileLocks[profile.appUid] = Mutex()
                }
                Log.i(TAG, "Loaded ${profiles.size} app profiles")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profiles", e)
            }
        }
        
        isInitialized = true
    }
    
    /**
     * Configure scorer weights.
     */
    fun configure(newWeights: ScorerWeights? = null, newThresholds: Thresholds? = null) {
        newWeights?.let { weights = it }
        newThresholds?.let { thresholds = it }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Analyze a flow and return an anomaly result.
     * Also updates the app's profile with this observation.
     * 
     * This is the main entry point - call this for every completed flow.
     */
    suspend fun analyzeFlow(flow: Flow): AnomalyResult {
        val appUid = flow.appUid ?: return AnomalyResult.unknown("No app UID")
        
        // Get or create profile
        val profile = getOrCreateProfile(appUid, flow.appName)
        
        // Run all scorers
        val temporalResult = TemporalScorer.score(flow, profile)
        val volumeResult = VolumeScorer.score(flow, profile)
        val destResult = DestinationScorer.score(flow, profile)
        
        // Combine scores with confidence weighting
        val combinedScore = combineScores(
            temporalResult, volumeResult, destResult, profile
        )
        
        // Collect all reasons
        val allReasons = temporalResult.reasons + volumeResult.reasons + destResult.reasons
        
        // Determine severity
        val severity = when {
            combinedScore >= thresholds.highAnomaly -> AnomalySeverity.HIGH
            combinedScore >= thresholds.mediumAnomaly -> AnomalySeverity.MEDIUM
            combinedScore >= thresholds.lowAnomaly -> AnomalySeverity.LOW
            else -> AnomalySeverity.NONE
        }
        
        // Update profile with this flow observation
        updateProfile(appUid, flow)
        
        return AnomalyResult(
            score = combinedScore,
            severity = severity,
            reasons = allReasons,
            breakdown = ScoreBreakdown(
                temporal = temporalResult.score,
                volume = volumeResult.score,
                destination = destResult.score
            ),
            appUid = appUid,
            appName = flow.appName,
            flowKey = "${flow.key.destIp}:${flow.key.destPort}",
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Just update profile without scoring (for training phase).
     */
    suspend fun observeFlow(flow: Flow) {
        val appUid = flow.appUid ?: return
        updateProfile(appUid, flow)
    }
    
    /**
     * Get the current profile for an app.
     */
    fun getProfile(appUid: Int): AppProfile? = profileCache[appUid]
    
    /**
     * Get all profiles (for debugging/export).
     */
    fun getAllProfiles(): List<AppProfile> = profileCache.values.toList()
    
    /**
     * Force persist all dirty profiles to database.
     */
    suspend fun flushProfiles() {
        val dao = profileDao ?: return
        
        withContext(Dispatchers.IO) {
            val toSave = dirtyProfiles.toList()
            dirtyProfiles.clear()
            
            toSave.forEach { appUid ->
                profileCache[appUid]?.let { profile ->
                    try {
                        dao.upsert(profile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist profile for $appUid", e)
                    }
                }
            }
            
            if (toSave.isNotEmpty()) {
                Log.d(TAG, "Persisted ${toSave.size} profiles")
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun getOrCreateProfile(appUid: Int, appName: String?): AppProfile {
        return profileCache.getOrPut(appUid) {
            profileLocks.getOrPut(appUid) { Mutex() }
            AppProfile.create(appUid, appName)
        }
    }
    
    private suspend fun updateProfile(appUid: Int, flow: Flow) {
        val lock = profileLocks.getOrPut(appUid) { Mutex() }
        
        lock.withLock {
            val current = profileCache[appUid] ?: AppProfile.create(appUid, flow.appName)
            val updated = current.updateWithFlow(flow)
            profileCache[appUid] = updated
            dirtyProfiles.add(appUid)
        }
    }
    
    /**
     * Combine scores from all three scorers.
     * Uses confidence to weight the contribution of each scorer.
     */
    private fun combineScores(
        temporal: ScorerResult,
        volume: ScorerResult,
        destination: ScorerResult,
        profile: AppProfile
    ): Float {
        // Weight by both configured weights AND scorer confidence
        val effectiveTemporalWeight = weights.temporal * temporal.confidence
        val effectiveVolumeWeight = weights.volume * volume.confidence
        val effectiveDestWeight = weights.destination * destination.confidence
        
        val totalWeight = effectiveTemporalWeight + effectiveVolumeWeight + effectiveDestWeight
        
        if (totalWeight < 0.1f) {
            // No scorer has confidence - profile too immature
            return 0f
        }
        
        val weightedSum = (temporal.score * effectiveTemporalWeight) +
                          (volume.score * effectiveVolumeWeight) +
                          (destination.score * effectiveDestWeight)
        
        return (weightedSum / totalWeight).coerceIn(0f, 1f)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// RESULT DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Severity levels for anomalies.
 */
enum class AnomalySeverity {
    NONE,    // Normal behavior
    LOW,     // Worth noting, probably fine
    MEDIUM,  // Suspicious, investigate
    HIGH     // Likely malicious, alert user
}

/**
 * Breakdown of scores from each scorer.
 */
data class ScoreBreakdown(
    val temporal: Float,
    val volume: Float,
    val destination: Float
)

/**
 * Complete anomaly analysis result.
 */
data class AnomalyResult(
    /** Combined anomaly score (0-1) */
    val score: Float,
    
    /** Classified severity */
    val severity: AnomalySeverity,
    
    /** Human-readable explanations */
    val reasons: List<String>,
    
    /** Individual scorer contributions */
    val breakdown: ScoreBreakdown,
    
    /** Which app this is for */
    val appUid: Int,
    val appName: String?,
    
    /** Flow identifier */
    val flowKey: String,
    
    /** When this analysis was done */
    val timestamp: Long
) {
    /** Is this actually anomalous? */
    val isAnomalous: Boolean 
        get() = severity != AnomalySeverity.NONE
    
    /** Get a summary string */
    fun summary(): String {
        return if (isAnomalous) {
            val reasonText = reasons.take(2).joinToString("; ")
            "[$severity] ${appName ?: "UID:$appUid"}: $reasonText"
        } else {
            "Normal: ${appName ?: "UID:$appUid"}"
        }
    }
    
    companion object {
        fun unknown(reason: String) = AnomalyResult(
            score = 0f,
            severity = AnomalySeverity.NONE,
            reasons = listOf(reason),
            breakdown = ScoreBreakdown(0f, 0f, 0f),
            appUid = -1,
            appName = null,
            flowKey = "",
            timestamp = System.currentTimeMillis()
        )
    }
}

