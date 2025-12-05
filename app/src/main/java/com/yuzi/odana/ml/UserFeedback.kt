package com.yuzi.odana.ml

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * USER FEEDBACK SYSTEM
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Stores user feedback on anomaly alerts to improve detection accuracy.
 * 
 * DESIGN:
 * - "Normal" feedback = light effect (user might be wrong)
 * - "Suspicious" feedback = heavy effect (user took time to confirm)
 * - Feedback affects per-app suspicion multiplier
 */

@Entity(tableName = "user_feedback")
data class UserFeedback(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** App UID this feedback is about */
    val appUid: Int,
    
    /** App name for display */
    val appName: String?,
    
    /** What the user marked it as: "normal" or "suspicious" */
    val verdict: String,
    
    /** The anomaly score when user gave feedback */
    val originalScore: Float,
    
    /** Destination that triggered the alert */
    val destination: String,
    
    /** Reasons shown to user */
    val reasons: String,
    
    /** When feedback was given */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val VERDICT_NORMAL = "normal"
        const val VERDICT_SUSPICIOUS = "suspicious"
    }
}

@Dao
interface FeedbackDao {
    @Insert
    suspend fun insert(feedback: UserFeedback)
    
    @Query("SELECT * FROM user_feedback WHERE appUid = :appUid ORDER BY timestamp DESC")
    suspend fun getFeedbackForApp(appUid: Int): List<UserFeedback>
    
    @Query("SELECT * FROM user_feedback ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFeedback(limit: Int = 50): List<UserFeedback>
    
    @Query("SELECT COUNT(*) FROM user_feedback WHERE appUid = :appUid AND verdict = :verdict")
    suspend fun countFeedbackByVerdict(appUid: Int, verdict: String): Int
    
    @Query("DELETE FROM user_feedback WHERE appUid = :appUid")
    suspend fun deleteFeedbackForApp(appUid: Int)
    
    @Query("DELETE FROM user_feedback")
    suspend fun deleteAll()
}

/**
 * Manages user feedback and applies it to scoring.
 */
object FeedbackManager {
    private var feedbackDao: FeedbackDao? = null
    
    // Cache of app suspicion adjustments: appUid -> multiplier
    // > 1.0 = more suspicious (user marked things as bad)
    // < 1.0 = less suspicious (user marked things as normal)
    private val suspicionMultipliers = mutableMapOf<Int, Float>()
    
    // How much each feedback type affects the multiplier
    private const val NORMAL_FEEDBACK_WEIGHT = 0.05f    // Light effect
    private const val SUSPICIOUS_FEEDBACK_WEIGHT = 0.20f // Heavy effect
    
    // Bounds for multiplier
    private const val MIN_MULTIPLIER = 0.3f  // Can reduce suspicion to 30%
    private const val MAX_MULTIPLIER = 3.0f  // Can increase suspicion to 300%
    
    fun initialize(dao: FeedbackDao) {
        feedbackDao = dao
    }
    
    /**
     * Record user feedback on an anomaly.
     */
    suspend fun recordFeedback(
        appUid: Int,
        appName: String?,
        verdict: String,
        originalScore: Float,
        destination: String,
        reasons: List<String>
    ) {
        val feedback = UserFeedback(
            appUid = appUid,
            appName = appName,
            verdict = verdict,
            originalScore = originalScore,
            destination = destination,
            reasons = reasons.joinToString("; ")
        )
        
        feedbackDao?.insert(feedback)
        
        // Update cached multiplier
        updateMultiplier(appUid, verdict)
    }
    
    /**
     * Get the suspicion multiplier for an app.
     * Returns 1.0 if no feedback exists.
     */
    fun getSuspicionMultiplier(appUid: Int): Float {
        return suspicionMultipliers[appUid] ?: 1.0f
    }
    
    /**
     * Load multipliers from database (call on init).
     */
    suspend fun loadMultipliers() {
        val dao = feedbackDao ?: return
        
        // Get all unique app UIDs with feedback
        val recentFeedback = dao.getRecentFeedback(500)
        val appUids = recentFeedback.map { it.appUid }.distinct()
        
        for (uid in appUids) {
            recalculateMultiplier(uid)
        }
    }
    
    /**
     * Recalculate multiplier for an app based on all feedback.
     */
    private suspend fun recalculateMultiplier(appUid: Int) {
        val dao = feedbackDao ?: return
        
        val normalCount = dao.countFeedbackByVerdict(appUid, UserFeedback.VERDICT_NORMAL)
        val suspiciousCount = dao.countFeedbackByVerdict(appUid, UserFeedback.VERDICT_SUSPICIOUS)
        
        // Calculate net adjustment
        // Suspicious has more weight than normal
        val normalEffect = normalCount * NORMAL_FEEDBACK_WEIGHT
        val suspiciousEffect = suspiciousCount * SUSPICIOUS_FEEDBACK_WEIGHT
        
        val netEffect = suspiciousEffect - normalEffect
        val multiplier = (1.0f + netEffect).coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
        
        suspicionMultipliers[appUid] = multiplier
    }
    
    private fun updateMultiplier(appUid: Int, verdict: String) {
        val current = suspicionMultipliers[appUid] ?: 1.0f
        
        val adjustment = when (verdict) {
            UserFeedback.VERDICT_NORMAL -> -NORMAL_FEEDBACK_WEIGHT
            UserFeedback.VERDICT_SUSPICIOUS -> SUSPICIOUS_FEEDBACK_WEIGHT
            else -> 0f
        }
        
        suspicionMultipliers[appUid] = (current + adjustment).coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }
    
    /**
     * Clear all feedback for an app (when resetting profile).
     */
    suspend fun clearFeedbackForApp(appUid: Int) {
        feedbackDao?.deleteFeedbackForApp(appUid)
        suspicionMultipliers.remove(appUid)
    }
    
    /**
     * Get feedback summary for display.
     */
    suspend fun getFeedbackSummary(appUid: Int): String {
        val dao = feedbackDao ?: return "No feedback"
        
        val normalCount = dao.countFeedbackByVerdict(appUid, UserFeedback.VERDICT_NORMAL)
        val suspiciousCount = dao.countFeedbackByVerdict(appUid, UserFeedback.VERDICT_SUSPICIOUS)
        
        if (normalCount == 0 && suspiciousCount == 0) {
            return "No feedback yet"
        }
        
        val multiplier = getSuspicionMultiplier(appUid)
        val multiplierText = when {
            multiplier > 1.5f -> "High alert"
            multiplier > 1.1f -> "Elevated"
            multiplier < 0.5f -> "Trusted"
            multiplier < 0.9f -> "Low alert"
            else -> "Normal"
        }
        
        return "$multiplierText • ${normalCount}× ok, ${suspiciousCount}× flagged"
    }
}

