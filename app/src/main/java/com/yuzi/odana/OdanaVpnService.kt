package com.yuzi.odana

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.yuzi.odana.data.BlockList
import java.io.IOException
import java.nio.ByteBuffer

class OdanaVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    companion object {
        const val ACTION_START = "com.yuzi.odana.ACTION_START"
        const val ACTION_STOP = "com.yuzi.odana.ACTION_STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "odana_vpn_channel"
        private const val TAG = "OdanaVpnService"
    }

    override fun onCreate() {
        super.onCreate()
        FlowManager.initialize(this)
        BlockList.initialize(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private var nioProxy: NioProxy? = null

    private fun startVpn() {
        if (isRunning) return
        Log.i(TAG, "Starting VPN Service...")

        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        isRunning = true
        
        // Initialize NioProxy with a writer callback
        nioProxy = NioProxy { buffer ->
            writeToTun(buffer)
        }
        nioProxy?.start()

        serviceScope.launch {
            try {
                establishVpn()
                startCleanupLoop()
                startTunReader()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting VPN", e)
                stopVpn()
            }
        }
    }

    private fun startCleanupLoop() {
        serviceScope.launch {
            while (isRunning) {
                kotlinx.coroutines.delay(10000) // Check every 10s
                FlowManager.cleanupStaleFlows()
            }
        }
    }

    private fun startTunReader() {
        val inputStream = java.io.FileInputStream(vpnInterface!!.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767) // Max IP packet size

        Log.i(TAG, "Starting TUN reader loop...")
        
        while (isRunning) {
            try {
                val length = inputStream.read(buffer.array())
                if (length > 0) {
                    buffer.limit(length)
                    buffer.position(0)
                    
                    try {
                        // We must copy the buffer because Packet/NioProxy might queue it 
                        // and this buffer is reused in the next iteration.
                        val packetData = ByteBuffer.allocate(length)
                        packetData.put(buffer)
                        packetData.flip()
                        
                        val packet = Packet(packetData)
                        
                        // 1. Log/Analyze (existing logic)
                        FlowManager.processPacket(packet)
                        
                        // 2. Forward to Proxy (New Logic)
                        nioProxy?.onPacketFromTun(packet)
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "Bad packet", e)
                    }
                    
                    buffer.clear()
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Read error", e)
                break
            }
        }
    }
    
    private fun writeToTun(buffer: ByteBuffer) {
        try {
            val os = java.io.FileOutputStream(vpnInterface?.fileDescriptor ?: return)
            // FileOutputStream.write(byte[]) writes from 0 to length.
            // buffer might be sliced or have a position.
            // We need to extract the bytes.
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            os.write(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to TUN", e)
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN Service...")
        isRunning = false
        
        nioProxy?.stop()
        nioProxy = null
        
        // We use runBlocking to ensure the flush completes before we kill the service/scope.
        // This blocks the main thread, but since we are stopping, it is acceptable for a short DB write.
        try {
            kotlinx.coroutines.runBlocking {
                FlowManager.flushAllFlows()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing flows on stop", e)
        }

        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing interface", e)
        }
        
        serviceScope.cancel()
        stopForeground(true)
        stopSelf()
    }

    private fun establishVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        builder.setMtu(1500)
        builder.setSession("OdanaVPN")
        
        // Important: Block ourselves to avoid loops
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exclude self from VPN", e)
        }

        vpnInterface = builder.establish()
        Log.i(TAG, "VPN Interface established: ${vpnInterface?.fileDescriptor}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ODANA VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ODANA Active")
            .setContentText("Monitoring network traffic...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
