package com.yuzi.odana.ml

import kotlin.math.sqrt

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * ONLINE STATISTICAL UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * These classes compute statistics incrementally without storing raw data.
 * Critical for privacy (no data retention) and memory efficiency (constant space).
 */

/**
 * Exponential Moving Average (EMA)
 * 
 * WHY: Tracks "typical" values while giving more weight to recent observations.
 * Unlike simple averages, EMA naturally adapts to changing behavior over time.
 * 
 * MATH: EMA_new = α * value + (1-α) * EMA_old
 * 
 * @param alpha Smoothing factor (0-1). Higher = faster adaptation, more noise.
 *              0.1 = slow adaptation (good for long-term baselines)
 *              0.3 = medium adaptation (good for weekly patterns)
 *              0.5 = fast adaptation (good for detecting sudden changes)
 */
class ExponentialMovingAverage(
    private val alpha: Float = 0.1f
) {
    var value: Float = 0f
        private set
    var count: Long = 0
        private set
    
    /**
     * Update with a new observation.
     * First observation initializes the EMA; subsequent ones blend in.
     */
    fun update(newValue: Float) {
        value = if (count == 0L) {
            newValue
        } else {
            alpha * newValue + (1 - alpha) * value
        }
        count++
    }
    
    /**
     * Check if we have enough data to be meaningful.
     * Rule of thumb: need ~1/alpha observations to stabilize.
     */
    fun isStable(): Boolean = count >= (1 / alpha).toLong()
    
    /**
     * Serialize for Room storage.
     */
    fun serialize(): String = "$value,$count,$alpha"
    
    companion object {
        fun deserialize(s: String): ExponentialMovingAverage {
            val parts = s.split(",")
            return ExponentialMovingAverage(parts[2].toFloat()).apply {
                value = parts[0].toFloat()
                count = parts[1].toLong()
            }
        }
    }
}

/**
 * Welford's Online Algorithm for Mean and Variance
 * 
 * WHY: Computes mean and variance in a single pass, numerically stable.
 * Essential for detecting "unusual" values (how many std devs from mean?).
 * 
 * MATH: Uses Welford's method to avoid catastrophic cancellation.
 * Standard deviation tells us the "spread" of typical behavior.
 */
class RunningStats {
    var count: Long = 0
        private set
    var mean: Double = 0.0
        private set
    private var m2: Double = 0.0  // Sum of squares of differences from mean
    
    /**
     * Update with a new observation using Welford's algorithm.
     */
    fun update(value: Double) {
        count++
        val delta = value - mean
        mean += delta / count
        val delta2 = value - mean
        m2 += delta * delta2
    }
    
    /**
     * Population variance (for the data seen so far).
     */
    val variance: Double
        get() = if (count < 2) 0.0 else m2 / count
    
    /**
     * Standard deviation - typical "spread" from the mean.
     */
    val stdDev: Double
        get() = sqrt(variance)
    
    /**
     * How many standard deviations is this value from the mean?
     * Used for anomaly scoring: |z-score| > 3 is often "unusual".
     */
    fun zScore(value: Double): Double {
        val sd = stdDev
        return if (sd < 0.0001) 0.0 else (value - mean) / sd
    }
    
    /**
     * Is this value anomalous? (Beyond threshold std devs from mean)
     */
    fun isAnomaly(value: Double, threshold: Double = 3.0): Boolean {
        return kotlin.math.abs(zScore(value)) > threshold
    }
    
    fun isStable(): Boolean = count >= 30  // Central limit theorem threshold
    
    fun serialize(): String = "$count,$mean,$m2"
    
    companion object {
        fun deserialize(s: String): RunningStats {
            val parts = s.split(",")
            return RunningStats().apply {
                count = parts[0].toLong()
                mean = parts[1].toDouble()
                m2 = parts[2].toDouble()
            }
        }
    }
}

/**
 * P² Algorithm for Online Quantile Estimation
 * 
 * WHY: Estimates percentiles (P50, P90, P99) without storing all data.
 * Critical for detecting "this flow is bigger than 99% of previous flows".
 * 
 * MATH: Maintains 5 markers that track the distribution shape.
 * Updates markers using piecewise-parabolic prediction.
 * 
 * Memory: O(1) - just 5 floats regardless of data size!
 * 
 * Reference: Jain & Chlamtac, "The P² Algorithm for Dynamic Calculation 
 *            of Quantiles and Histograms Without Storing Observations"
 */
class QuantileEstimator(
    private val p: Float = 0.5f  // Target quantile (0.5 = median, 0.9 = P90, etc.)
) {
    // The 5 markers: min, p/2, p, (1+p)/2, max
    private val q = FloatArray(5)  // Marker heights (quantile estimates)
    private val n = IntArray(5)    // Marker positions
    private val nPrime = FloatArray(5)  // Desired marker positions
    private val dn = FloatArray(5)      // Increments for desired positions
    
    var count: Int = 0
        private set
    
    init {
        // Initialize desired position increments
        dn[0] = 0f
        dn[1] = p / 2f
        dn[2] = p
        dn[3] = (1f + p) / 2f
        dn[4] = 1f
    }
    
    /**
     * Update with a new observation.
     */
    fun update(value: Float) {
        if (count < 5) {
            // Initialization: collect first 5 values
            q[count] = value
            count++
            if (count == 5) {
                // Sort and initialize positions
                q.sort()
                for (i in 0..4) {
                    n[i] = i
                    nPrime[i] = dn[i] * 4f  // Scale to 0..4
                }
            }
            return
        }
        
        count++
        
        // Find cell k where value falls
        val k = when {
            value < q[0] -> { q[0] = value; 0 }
            value < q[1] -> 0
            value < q[2] -> 1
            value < q[3] -> 2
            value < q[4] -> 3
            else -> { q[4] = value; 3 }
        }
        
        // Increment positions of markers k+1 to 4
        for (i in (k + 1)..4) {
            n[i]++
        }
        
        // Update desired positions
        for (i in 0..4) {
            nPrime[i] += dn[i]
        }
        
        // Adjust marker heights using P² formula
        for (i in 1..3) {
            val d = nPrime[i] - n[i]
            if ((d >= 1 && n[i + 1] - n[i] > 1) || (d <= -1 && n[i - 1] - n[i] < -1)) {
                val sign = if (d >= 0) 1 else -1
                val qNew = parabolic(i, sign)
                if (q[i - 1] < qNew && qNew < q[i + 1]) {
                    q[i] = qNew
                } else {
                    q[i] = linear(i, sign)
                }
                n[i] += sign
            }
        }
    }
    
    private fun parabolic(i: Int, d: Int): Float {
        val qi = q[i]
        val qim1 = q[i - 1]
        val qip1 = q[i + 1]
        val ni = n[i].toFloat()
        val nim1 = n[i - 1].toFloat()
        val nip1 = n[i + 1].toFloat()
        
        return qi + (d.toFloat() / (nip1 - nim1)) * (
            (ni - nim1 + d) * (qip1 - qi) / (nip1 - ni) +
            (nip1 - ni - d) * (qi - qim1) / (ni - nim1)
        )
    }
    
    private fun linear(i: Int, d: Int): Float {
        val idx = i + d
        return q[i] + d * (q[idx] - q[i]) / (n[idx] - n[i])
    }
    
    /**
     * Get the estimated quantile value.
     */
    fun quantile(): Float = if (count >= 5) q[2] else {
        // Not enough data - return median of what we have
        if (count == 0) 0f else q.take(count).sorted()[count / 2]
    }
    
    /**
     * Check if a value exceeds this quantile by a factor.
     * e.g., isExceeded(value, 2.0) = "is this 2x bigger than P90?"
     */
    fun isExceeded(value: Float, factor: Float = 1f): Boolean {
        return value > quantile() * factor
    }
    
    fun isStable(): Boolean = count >= 30
    
    fun serialize(): String = buildString {
        append("$count,$p,")
        append(q.joinToString(","))
        append(",")
        append(n.joinToString(","))
        append(",")
        append(nPrime.joinToString(","))
    }
    
    companion object {
        fun deserialize(s: String): QuantileEstimator {
            val parts = s.split(",")
            val count = parts[0].toInt()
            val p = parts[1].toFloat()
            val est = QuantileEstimator(p)
            est.count = count
            if (count >= 5) {
                for (i in 0..4) {
                    est.q[i] = parts[2 + i].toFloat()
                    est.n[i] = parts[7 + i].toInt()
                    est.nPrime[i] = parts[12 + i].toFloat()
                }
            }
            return est
        }
        
        // Convenience: Create a set of common quantile estimators
        fun createSet(): QuantileSet = QuantileSet()
    }
}

/**
 * A set of common quantile estimators bundled together.
 * Tracks P50 (median), P90, P95, and P99.
 */
class QuantileSet {
    val p50 = QuantileEstimator(0.5f)
    val p90 = QuantileEstimator(0.9f)
    val p95 = QuantileEstimator(0.95f)
    val p99 = QuantileEstimator(0.99f)
    
    fun update(value: Float) {
        p50.update(value)
        p90.update(value)
        p95.update(value)
        p99.update(value)
    }
    
    fun isStable(): Boolean = p50.isStable()
    
    /**
     * Score how extreme this value is (0 = typical, 1 = very extreme)
     */
    fun extremityScore(value: Float): Float {
        return when {
            value <= p50.quantile() -> 0f
            value <= p90.quantile() -> 0.3f
            value <= p95.quantile() -> 0.5f
            value <= p99.quantile() -> 0.7f
            value <= p99.quantile() * 2 -> 0.85f
            else -> 1f
        }
    }
    
    fun serialize(): String = listOf(
        p50.serialize(),
        p90.serialize(),
        p95.serialize(),
        p99.serialize()
    ).joinToString("|")
    
    companion object {
        fun deserialize(s: String): QuantileSet {
            val parts = s.split("|")
            return QuantileSet().apply {
                // Would need to copy internals - simplified for now
            }
        }
    }
}

/**
 * Histogram with fixed buckets for time-of-day patterns.
 * 
 * WHY: Captures "this app is usually active at these hours".
 * 24 buckets = 24 hours, simple and interpretable.
 */
class HourlyHistogram {
    private val counts = IntArray(24)
    var total: Int = 0
        private set
    
    /** Public access to hourly counts for visualization */
    val hours: List<Int>
        get() = counts.toList()
    
    fun increment(hour: Int) {
        require(hour in 0..23) { "Hour must be 0-23" }
        counts[hour]++
        total++
    }
    
    /**
     * Get probability of activity at this hour.
     */
    fun probability(hour: Int): Float {
        return if (total == 0) 1f / 24f else counts[hour].toFloat() / total
    }
    
    /**
     * Score how unusual this hour is (0 = typical, 1 = very unusual)
     * Based on how rarely we see activity at this hour.
     */
    fun unusualScore(hour: Int): Float {
        if (total < 10) return 0f  // Not enough data
        
        val p = probability(hour)
        val avgP = 1f / 24f
        
        return when {
            p >= avgP -> 0f                    // At or above average
            p >= avgP / 2 -> 0.3f              // Half of average
            p >= avgP / 4 -> 0.6f              // Quarter of average
            p > 0 -> 0.8f                      // Rare but seen before
            else -> 1f                         // Never seen at this hour
        }
    }
    
    /**
     * Get top N most active hours.
     */
    fun topHours(n: Int = 5): List<Int> {
        return counts.indices.sortedByDescending { counts[it] }.take(n)
    }
    
    fun serialize(): String = counts.joinToString(",")
    
    companion object {
        fun deserialize(s: String): HourlyHistogram {
            val parts = s.split(",")
            return HourlyHistogram().apply {
                parts.forEachIndexed { i, v ->
                    counts[i] = v.toIntOrNull() ?: 0
                    total += counts[i]
                }
            }
        }
    }
}

