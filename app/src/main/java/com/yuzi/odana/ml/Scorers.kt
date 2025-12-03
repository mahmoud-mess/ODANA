package com.yuzi.odana.ml

import com.yuzi.odana.Flow
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ln

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * ANOMALY SCORING MODELS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Three specialized scorers that each look at a different aspect of network behavior.
 * Scores range from 0.0 (completely normal) to 1.0 (highly anomalous).
 * 
 * The ensemble combines these scores with learned weights.
 */

/**
 * Result from any scorer, with explanation for transparency.
 */
data class ScorerResult(
    val score: Float,           // 0.0 = normal, 1.0 = anomalous
    val confidence: Float,      // 0.0 = no confidence, 1.0 = full confidence
    val reasons: List<String>   // Human-readable explanations
) {
    companion object {
        val NORMAL = ScorerResult(0f, 1f, emptyList())
        val UNKNOWN = ScorerResult(0f, 0f, listOf("Insufficient data"))
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TEMPORAL SCORER
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Detects when an app communicates at unusual times.
 * 
 * USE CASES:
 * - Banking app active at 3 AM → suspicious
 * - Work app active on weekend → unusual
 * - Perfectly periodic intervals → C2 beacon signature
 * 
 * FEATURES ANALYZED:
 * - Hour of day (compared to historical pattern)
 * - Day of week (is this a typical day?)
 * - Inter-flow interval (too regular = beaconing)
 */
object TemporalScorer {
    
    /**
     * Score how unusual this flow's timing is.
     */
    fun score(flow: Flow, profile: AppProfile): ScorerResult {
        // Not enough data to judge
        if (profile.maturityLevel == AppProfile.MATURITY_INFANT) {
            return ScorerResult.UNKNOWN
        }
        
        val calendar = Calendar.getInstance().apply { 
            timeInMillis = flow.startTime 
        }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1  // 0 = Sunday
        
        val reasons = mutableListOf<String>()
        var totalScore = 0f
        var factors = 0
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 1: Hour of day unusualness
        // ─────────────────────────────────────────────────────────────────────
        val hourScore = profile.hourlyHistogram.unusualScore(hour)
        if (hourScore > 0.5f) {
            reasons.add("Unusual hour: ${formatHour(hour)} (typically inactive)")
        }
        totalScore += hourScore
        factors++
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 2: Day of week unusualness
        // ─────────────────────────────────────────────────────────────────────
        val isDayActive = (profile.activeDaysOfWeek and (1 shl dayOfWeek)) != 0
        val dayScore = if (!isDayActive && profile.flowCount > 50) {
            reasons.add("First activity on ${getDayName(dayOfWeek)}")
            0.6f
        } else {
            0f
        }
        totalScore += dayScore
        factors++
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 3: Beaconing detection (suspiciously regular intervals)
        // ─────────────────────────────────────────────────────────────────────
        // C2 beacons often have very low variance in their check-in intervals
        // Coefficient of Variation (CV) = stdDev / mean
        // CV < 0.1 with high flow count = very suspicious regularity
        if (profile.interFlowInterval.count > 20) {
            val mean = profile.interFlowInterval.mean
            val stdDev = profile.interFlowInterval.stdDev
            
            if (mean > 1000) {  // At least 1 second intervals
                val cv = if (mean > 0) stdDev / mean else 1.0
                val beaconScore = when {
                    cv < 0.05 -> {
                        reasons.add("Highly regular intervals (possible beacon)")
                        0.9f
                    }
                    cv < 0.1 -> {
                        reasons.add("Suspiciously regular timing")
                        0.6f
                    }
                    else -> 0f
                }
                totalScore += beaconScore
                factors++
            }
        }
        
        // Combine scores (average of factors)
        val finalScore = if (factors > 0) totalScore / factors else 0f
        val confidence = when (profile.maturityLevel) {
            AppProfile.MATURITY_LEARNING -> 0.5f
            AppProfile.MATURITY_MATURE -> 1f
            else -> 0f
        }
        
        return ScorerResult(
            score = finalScore.coerceIn(0f, 1f),
            confidence = confidence,
            reasons = reasons
        )
    }
    
    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12 AM"
            hour < 12 -> "$hour AM"
            hour == 12 -> "12 PM"
            else -> "${hour - 12} PM"
        }
    }
    
    private fun getDayName(day: Int): String {
        return listOf("Sunday", "Monday", "Tuesday", "Wednesday", 
                      "Thursday", "Friday", "Saturday").getOrElse(day) { "Unknown" }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * VOLUME SCORER
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Detects unusual data transfer volumes.
 * 
 * USE CASES:
 * - Calculator app sending 50MB → exfiltration
 * - App suddenly uploading 10x normal → data theft
 * - Tiny packets at high rate → reconnaissance
 * 
 * FEATURES ANALYZED:
 * - Total bytes vs historical average
 * - Upload/download ratio changes
 * - Flow duration outliers
 */
object VolumeScorer {
    
    // Thresholds for anomaly detection
    private const val VOLUME_MULTIPLIER_THRESHOLD = 5f  // 5x normal = suspicious
    private const val RATIO_SHIFT_THRESHOLD = 0.4f      // 40% shift in up/down ratio
    
    fun score(flow: Flow, profile: AppProfile): ScorerResult {
        if (profile.maturityLevel == AppProfile.MATURITY_INFANT) {
            return ScorerResult.UNKNOWN
        }
        
        val reasons = mutableListOf<String>()
        var totalScore = 0f
        var factors = 0
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 1: Total bytes vs typical
        // ─────────────────────────────────────────────────────────────────────
        val totalBytes = flow.bytesIn + flow.bytesOut
        val typicalBytes = profile.typicalBytesIn() + profile.typicalBytesOut()
        
        if (typicalBytes > 100) {  // Need baseline
            val ratio = totalBytes / typicalBytes
            val volumeScore = when {
                ratio > 20 -> {
                    reasons.add("Volume ${ratio.toInt()}x higher than typical")
                    1f
                }
                ratio > 10 -> {
                    reasons.add("Volume ${ratio.toInt()}x higher than typical")
                    0.8f
                }
                ratio > VOLUME_MULTIPLIER_THRESHOLD -> {
                    reasons.add("Volume ${String.format("%.1f", ratio)}x higher than typical")
                    0.5f
                }
                else -> 0f
            }
            totalScore += volumeScore
            factors++
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 2: Upload/Download ratio shift
        // ─────────────────────────────────────────────────────────────────────
        // Detects apps that suddenly start uploading much more
        val flowUploadRatio = if (totalBytes > 0) {
            flow.bytesOut.toFloat() / totalBytes
        } else 0f
        
        val typicalTotal = profile.typicalBytesIn() + profile.typicalBytesOut()
        val typicalUploadRatio = if (typicalTotal > 0) {
            profile.typicalBytesOut() / typicalTotal
        } else 0.5f
        
        val ratioShift = flowUploadRatio - typicalUploadRatio
        val ratioScore = when {
            ratioShift > RATIO_SHIFT_THRESHOLD && flow.bytesOut > 10000 -> {
                reasons.add("Unusual upload ratio (${(flowUploadRatio * 100).toInt()}% vs typical ${(typicalUploadRatio * 100).toInt()}%)")
                0.7f
            }
            ratioShift > RATIO_SHIFT_THRESHOLD / 2 && flow.bytesOut > 10000 -> {
                0.3f
            }
            else -> 0f
        }
        totalScore += ratioScore
        factors++
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 3: Duration outlier
        // ─────────────────────────────────────────────────────────────────────
        val duration = flow.lastUpdated - flow.startTime
        if (profile.durationStats.isStable()) {
            val zScore = abs(profile.durationZScore(duration))
            val durationScore = when {
                zScore > 4 -> {
                    reasons.add("Unusual flow duration (${duration}ms)")
                    0.6f
                }
                zScore > 3 -> 0.3f
                else -> 0f
            }
            totalScore += durationScore
            factors++
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 4: Tiny packets at high rate (potential scanning)
        // ─────────────────────────────────────────────────────────────────────
        if (flow.packets > 10) {
            val avgPacketSize = totalBytes.toFloat() / flow.packets
            if (avgPacketSize < 100 && flow.packets > 50) {
                reasons.add("Many small packets (avg ${avgPacketSize.toInt()} bytes)")
                totalScore += 0.5f
                factors++
            }
        }
        
        val finalScore = if (factors > 0) totalScore / factors else 0f
        val confidence = when (profile.maturityLevel) {
            AppProfile.MATURITY_LEARNING -> 0.5f
            AppProfile.MATURITY_MATURE -> 1f
            else -> 0f
        }
        
        return ScorerResult(
            score = finalScore.coerceIn(0f, 1f),
            confidence = confidence,
            reasons = reasons
        )
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DESTINATION SCORER
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Detects connections to unusual destinations.
 * 
 * USE CASES:
 * - First-ever IP contact → potential new C2 server
 * - Unusual port for this app → suspicious
 * - High-entropy domain name → DGA (Domain Generation Algorithm)
 * 
 * FEATURES ANALYZED:
 * - Is this destination new?
 * - Is this port typical for this app?
 * - Domain name entropy (randomness)
 */
object DestinationScorer {
    
    // Common ports that are generally expected
    private val COMMON_PORTS = setOf(80, 443, 8080, 8443, 53, 853, 123)
    
    fun score(flow: Flow, profile: AppProfile): ScorerResult {
        if (profile.maturityLevel == AppProfile.MATURITY_INFANT) {
            return ScorerResult.UNKNOWN
        }
        
        val reasons = mutableListOf<String>()
        var totalScore = 0f
        var factors = 0
        
        val destIp = flow.key.destIp
        val destPort = flow.key.destPort
        val sni = flow.detectedSni
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 1: New destination
        // ─────────────────────────────────────────────────────────────────────
        val isNewDest = profile.isNewDestination(destIp, destPort)
        if (isNewDest) {
            // New destinations are more suspicious for mature profiles
            val newDestScore = when (profile.maturityLevel) {
                AppProfile.MATURITY_MATURE -> {
                    reasons.add("First connection to $destIp:$destPort")
                    0.6f
                }
                else -> 0.3f
            }
            totalScore += newDestScore
            factors++
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 2: Unusual port for this app
        // ─────────────────────────────────────────────────────────────────────
        if (profile.isUnusualPort(destPort) && destPort !in COMMON_PORTS) {
            reasons.add("Unusual port $destPort for this app")
            totalScore += 0.5f
            factors++
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 3: Unusual protocol for this app
        // ─────────────────────────────────────────────────────────────────────
        if (profile.isUnusualProtocol(flow.key.protocol)) {
            val protoName = if (flow.key.protocol == 6) "TCP" else "UDP"
            reasons.add("First use of $protoName by this app")
            totalScore += 0.4f
            factors++
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 4: Domain entropy (DGA detection)
        // ─────────────────────────────────────────────────────────────────────
        sni?.let { domain ->
            val entropy = calculateDomainEntropy(domain)
            
            // High entropy + long random-looking subdomain = potential DGA
            if (entropy > 3.5f && domain.length > 20) {
                reasons.add("High-entropy domain name (possible DGA)")
                totalScore += 0.7f
                factors++
            }
            
            // New domain for this app
            if (profile.isNewDomain(domain) && profile.maturityLevel == AppProfile.MATURITY_MATURE) {
                reasons.add("First connection to domain: $domain")
                totalScore += 0.3f
                factors++
            }
        }
        
        // ─────────────────────────────────────────────────────────────────────
        // Factor 5: Non-standard TLS port
        // ─────────────────────────────────────────────────────────────────────
        if (sni != null && destPort !in setOf(443, 8443, 853)) {
            reasons.add("TLS on non-standard port $destPort")
            totalScore += 0.4f
            factors++
        }
        
        val finalScore = if (factors > 0) totalScore / factors else 0f
        val confidence = when (profile.maturityLevel) {
            AppProfile.MATURITY_LEARNING -> 0.5f
            AppProfile.MATURITY_MATURE -> 1f
            else -> 0f
        }
        
        return ScorerResult(
            score = finalScore.coerceIn(0f, 1f),
            confidence = confidence,
            reasons = reasons
        )
    }
    
    /**
     * Calculate Shannon entropy of a domain name.
     * High entropy = more random-looking = potential DGA.
     * 
     * Normal domains: ~2.5-3.0 bits
     * DGA domains: ~3.5-4.0+ bits
     */
    private fun calculateDomainEntropy(domain: String): Float {
        // Only analyze the subdomain part (before first .)
        val subdomain = domain.substringBefore(".")
        if (subdomain.length < 5) return 0f
        
        val charCounts = subdomain.lowercase().groupingBy { it }.eachCount()
        val len = subdomain.length.toFloat()
        
        var entropy = 0f
        for ((_, count) in charCounts) {
            val p = count / len
            entropy -= p * (ln(p.toDouble()) / ln(2.0)).toFloat()
        }
        
        return entropy
    }
}

