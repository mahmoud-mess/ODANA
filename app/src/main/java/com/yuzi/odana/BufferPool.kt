package com.yuzi.odana

import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe ByteBuffer pool to reduce allocation churn.
 * 
 * Pre-allocates a pool of buffers that can be acquired and released.
 * Falls back to direct allocation if pool is exhausted.
 * 
 * Usage:
 *   val buffer = BufferPool.acquire(4096)
 *   try {
 *       // use buffer
 *   } finally {
 *       BufferPool.release(buffer)
 *   }
 */
object BufferPool {
    private const val TAG = "BufferPool"
    
    // Pool configuration
    private const val POOL_SIZE = 64
    private const val DEFAULT_BUFFER_SIZE = 32768  // 32KB - covers most packets
    private const val MAX_BUFFER_SIZE = 65536      // 64KB - max we'll pool
    
    // The actual pool
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()
    
    // Metrics for debugging
    private val hits = AtomicInteger(0)
    private val misses = AtomicInteger(0)
    private val allocations = AtomicInteger(0)
    
    init {
        // Pre-allocate pool
        repeat(POOL_SIZE) {
            pool.offer(ByteBuffer.allocate(DEFAULT_BUFFER_SIZE))
        }
        Log.i(TAG, "Initialized with $POOL_SIZE buffers of ${DEFAULT_BUFFER_SIZE / 1024}KB each")
    }
    
    /**
     * Acquire a buffer from the pool.
     * 
     * @param minSize Minimum required capacity. If larger than MAX_BUFFER_SIZE,
     *                a new buffer is allocated directly (not pooled).
     * @return A ByteBuffer ready for use (position=0, limit=capacity)
     */
    fun acquire(minSize: Int = DEFAULT_BUFFER_SIZE): ByteBuffer {
        // For large buffers, allocate directly (don't pollute pool)
        if (minSize > MAX_BUFFER_SIZE) {
            allocations.incrementAndGet()
            return ByteBuffer.allocate(minSize)
        }
        
        // Try to get from pool
        val buffer = pool.poll()
        
        return if (buffer != null && buffer.capacity() >= minSize) {
            hits.incrementAndGet()
            buffer.clear()
            buffer
        } else {
            misses.incrementAndGet()
            // Pool exhausted or buffer too small - allocate new
            val size = maxOf(minSize, DEFAULT_BUFFER_SIZE)
            ByteBuffer.allocate(size)
        }
    }
    
    /**
     * Return a buffer to the pool for reuse.
     * 
     * @param buffer The buffer to release. Will be cleared and added back to pool
     *               if pool has room and buffer is standard size.
     */
    fun release(buffer: ByteBuffer) {
        // Only pool buffers of standard size
        if (buffer.capacity() <= MAX_BUFFER_SIZE && pool.size < POOL_SIZE * 2) {
            buffer.clear()
            pool.offer(buffer)
        }
        // Else: let GC handle it
    }
    
    /**
     * Get pool statistics for debugging.
     */
    fun getStats(): PoolStats {
        val totalRequests = hits.get() + misses.get()
        val hitRate = if (totalRequests > 0) {
            hits.get().toFloat() / totalRequests
        } else 0f
        
        return PoolStats(
            poolSize = pool.size,
            hits = hits.get(),
            misses = misses.get(),
            directAllocations = allocations.get(),
            hitRate = hitRate
        )
    }
    
    /**
     * Reset metrics (for testing).
     */
    fun resetStats() {
        hits.set(0)
        misses.set(0)
        allocations.set(0)
    }
    
    data class PoolStats(
        val poolSize: Int,
        val hits: Int,
        val misses: Int,
        val directAllocations: Int,
        val hitRate: Float
    ) {
        override fun toString(): String {
            return "BufferPool[size=$poolSize, hits=$hits, misses=$misses, " +
                   "directAllocs=$directAllocations, hitRate=${(hitRate * 100).toInt()}%]"
        }
    }
}
