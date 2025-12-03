package com.yuzi.odana.data

import android.content.Context
import android.util.Log
import androidx.work.*
import com.yuzi.odana.FlowManager
import java.util.concurrent.TimeUnit

/**
 * Background worker that runs daily to clean up old flow data.
 * Deletes flows older than 14 days to maintain storage efficiency.
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CleanupWorker"
        private const val WORK_NAME = "odana_cleanup_work"
        
        /**
         * Schedule the cleanup worker to run daily.
         * Uses constraints to only run when device is idle and charging.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS) // Don't run immediately on app start
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, TimeUnit.MINUTES
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Don't restart if already scheduled
                cleanupRequest
            )
            
            Log.i(TAG, "Cleanup worker scheduled")
        }
        
        /**
         * Run cleanup immediately (for manual trigger from settings).
         */
        fun runNow(context: Context) {
            val cleanupRequest = OneTimeWorkRequestBuilder<CleanupWorker>()
                .build()
            
            WorkManager.getInstance(context).enqueue(cleanupRequest)
            Log.i(TAG, "Immediate cleanup requested")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting cleanup work...")
        
        return try {
            // Ensure FlowManager is initialized
            FlowManager.initialize(applicationContext)
            
            // Run the cleanup
            FlowManager.cleanupOldFlows()
            
            Log.i(TAG, "Cleanup work completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup work failed", e)
            Result.retry()
        }
    }
}

