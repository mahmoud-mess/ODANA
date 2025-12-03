package com.yuzi.odana.ml

import android.util.Base64
import java.util.BitSet
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * BLOOM FILTER - Space-Efficient Set Membership
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * WHY USE THIS:
 * We need to track "has this app ever connected to this IP/destination before?"
 * Storing every IP would use unbounded memory. A Bloom filter uses fixed memory
 * regardless of how many items we add.
 * 
 * PROPERTIES:
 * - NO false negatives: If we say "never seen", it's definitely never seen
 * - POSSIBLE false positives: We might say "seen before" when we haven't
 * - False positive rate is tunable via size/hash count
 * 
 * FOR ANOMALY DETECTION:
 * False positives mean we might miss some "new destination" alerts.
 * That's okay - we'd rather miss some anomalies than have unbounded memory.
 * With our parameters: ~1% false positive rate at 1000 items using 1.2KB.
 * 
 * MATH:
 * - m = number of bits
 * - k = number of hash functions
 * - n = expected number of items
 * - p = false positive probability
 * 
 * Optimal: m = -n*ln(p) / (ln2)^2, k = m/n * ln2
 */
class BloomFilter(
    private val expectedItems: Int = 1000,
    private val falsePositiveRate: Double = 0.01
) {
    // Calculate optimal size and hash count
    private val bitSize: Int = optimalBitSize(expectedItems, falsePositiveRate)
    private val hashCount: Int = optimalHashCount(bitSize, expectedItems)
    
    private val bits = BitSet(bitSize)
    var itemCount: Int = 0
        private set
    
    /**
     * Add an item to the filter.
     * Once added, contains() will always return true for this item.
     */
    fun add(item: String) {
        val hashes = getHashes(item)
        hashes.forEach { bits.set(it) }
        itemCount++
    }
    
    /**
     * Check if an item might be in the filter.
     * 
     * @return false = DEFINITELY not in set (never seen)
     *         true = PROBABLY in set (might be false positive)
     */
    fun mightContain(item: String): Boolean {
        val hashes = getHashes(item)
        return hashes.all { bits.get(it) }
    }
    
    /**
     * Convenience: Check if this is a NEW item (not seen before).
     * This is the main use case for anomaly detection.
     */
    fun isNew(item: String): Boolean = !mightContain(item)
    
    /**
     * Add and return whether it was new.
     * Combines the check-and-add pattern.
     */
    fun addAndCheckNew(item: String): Boolean {
        val wasNew = isNew(item)
        add(item)
        return wasNew
    }
    
    /**
     * Estimated current false positive rate based on fill level.
     * Increases as filter fills up.
     */
    fun estimatedFalsePositiveRate(): Double {
        val fillRatio = bits.cardinality().toDouble() / bitSize
        return fillRatio.pow(hashCount.toDouble())
    }
    
    /**
     * Is the filter getting too full? (FP rate degrading)
     * If true, consider creating a new filter.
     */
    fun isOverloaded(): Boolean {
        return estimatedFalsePositiveRate() > falsePositiveRate * 5
    }
    
    /**
     * Generate k hash values for an item.
     * Uses double hashing: h(i) = h1 + i*h2 mod m
     * This gives us k independent-ish hashes from just 2 hash computations.
     */
    private fun getHashes(item: String): IntArray {
        val h1 = item.hashCode()
        val h2 = murmurHash(item)
        
        return IntArray(hashCount) { i ->
            val combined = h1 + i * h2
            abs(combined % bitSize)
        }
    }
    
    /**
     * Simple Murmur-inspired hash for second hash function.
     * Different from hashCode() to reduce correlation.
     */
    private fun murmurHash(s: String): Int {
        var h = 0x811c9dc5.toInt()
        for (c in s) {
            h = h xor c.code
            h *= 0x01000193
        }
        return h
    }
    
    /**
     * Serialize for Room storage.
     * Encodes the BitSet as Base64 for compact storage.
     */
    fun serialize(): String {
        val bytes = bits.toByteArray()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "$expectedItems,$falsePositiveRate,$itemCount,$encoded"
    }
    
    /**
     * Memory usage in bytes (approximate).
     */
    fun memorySizeBytes(): Int = bitSize / 8 + 32  // bits + overhead
    
    companion object {
        /**
         * Optimal number of bits for given items and false positive rate.
         */
        private fun optimalBitSize(n: Int, p: Double): Int {
            val m = -n * ln(p) / (ln(2.0).pow(2))
            return ceil(m).toInt().coerceAtLeast(64)
        }
        
        /**
         * Optimal number of hash functions.
         */
        private fun optimalHashCount(m: Int, n: Int): Int {
            val k = (m.toDouble() / n) * ln(2.0)
            return ceil(k).toInt().coerceIn(1, 16)
        }
        
        /**
         * Deserialize from stored string.
         */
        fun deserialize(s: String): BloomFilter {
            val parts = s.split(",", limit = 4)
            val expectedItems = parts[0].toInt()
            val fpRate = parts[1].toDouble()
            val itemCount = parts[2].toInt()
            val encoded = parts[3]
            
            val filter = BloomFilter(expectedItems, fpRate)
            filter.itemCount = itemCount
            
            if (encoded.isNotEmpty()) {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                val restored = BitSet.valueOf(bytes)
                filter.bits.or(restored)
            }
            
            return filter
        }
        
        /**
         * Create a filter sized for tracking IP:port destinations.
         * Most apps connect to < 100 unique destinations; we size for 500 with headroom.
         */
        fun forDestinations(): BloomFilter = BloomFilter(
            expectedItems = 500,
            falsePositiveRate = 0.01  // 1% FP rate
        )
        
        /**
         * Create a filter for tracking SNI/domain names.
         */
        fun forDomains(): BloomFilter = BloomFilter(
            expectedItems = 200,
            falsePositiveRate = 0.01
        )
    }
}

/**
 * Counting Bloom Filter - Allows removals (at cost of more memory)
 * 
 * WHY: Sometimes we want to "forget" old destinations after a while.
 * Standard Bloom filters don't support removal. This variant uses
 * counters instead of bits, allowing decrement.
 * 
 * TRADEOFF: 4x memory (4-bit counters vs 1-bit)
 * Use only when removal is needed.
 */
class CountingBloomFilter(
    private val expectedItems: Int = 1000,
    private val falsePositiveRate: Double = 0.01
) {
    private val bitSize: Int = optimalBitSize(expectedItems, falsePositiveRate)
    private val hashCount: Int = optimalHashCount(bitSize, expectedItems)
    
    // 4-bit counters (0-15) packed into bytes
    private val counters = ByteArray((bitSize + 1) / 2)
    var itemCount: Int = 0
        private set
    
    fun add(item: String) {
        val hashes = getHashes(item)
        hashes.forEach { idx ->
            val counter = getCounter(idx)
            if (counter < 15) {  // Don't overflow
                setCounter(idx, counter + 1)
            }
        }
        itemCount++
    }
    
    fun remove(item: String): Boolean {
        if (!mightContain(item)) return false
        
        val hashes = getHashes(item)
        hashes.forEach { idx ->
            val counter = getCounter(idx)
            if (counter > 0) {
                setCounter(idx, counter - 1)
            }
        }
        itemCount--
        return true
    }
    
    fun mightContain(item: String): Boolean {
        val hashes = getHashes(item)
        return hashes.all { getCounter(it) > 0 }
    }
    
    fun isNew(item: String): Boolean = !mightContain(item)
    
    private fun getCounter(idx: Int): Int {
        val byteIdx = idx / 2
        val isHigh = idx % 2 == 1
        val byte = counters[byteIdx].toInt() and 0xFF
        return if (isHigh) (byte shr 4) else (byte and 0x0F)
    }
    
    private fun setCounter(idx: Int, value: Int) {
        val byteIdx = idx / 2
        val isHigh = idx % 2 == 1
        val byte = counters[byteIdx].toInt() and 0xFF
        val newByte = if (isHigh) {
            (byte and 0x0F) or ((value and 0x0F) shl 4)
        } else {
            (byte and 0xF0) or (value and 0x0F)
        }
        counters[byteIdx] = newByte.toByte()
    }
    
    private fun getHashes(item: String): IntArray {
        val h1 = item.hashCode()
        val h2 = murmurHash(item)
        return IntArray(hashCount) { i ->
            abs((h1 + i * h2) % bitSize)
        }
    }
    
    private fun murmurHash(s: String): Int {
        var h = 0x811c9dc5.toInt()
        for (c in s) {
            h = h xor c.code
            h *= 0x01000193
        }
        return h
    }
    
    companion object {
        private fun optimalBitSize(n: Int, p: Double): Int {
            val m = -n * ln(p) / (ln(2.0).pow(2))
            return ceil(m).toInt().coerceAtLeast(64)
        }
        
        private fun optimalHashCount(m: Int, n: Int): Int {
            val k = (m.toDouble() / n) * ln(2.0)
            return ceil(k).toInt().coerceIn(1, 16)
        }
    }
}

