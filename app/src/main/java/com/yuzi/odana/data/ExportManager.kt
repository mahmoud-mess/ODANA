package com.yuzi.odana.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yuzi.odana.FlowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles exporting flow data to user-selected locations via Storage Access Framework.
 */
object ExportManager {
    private const val TAG = "ExportManager"
    
    // Export state for UI
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState
    
    sealed class ExportState {
        object Idle : ExportState()
        data class Exporting(val progress: Int, val total: Int) : ExportState()
        data class Success(val flowCount: Int, val filePath: String) : ExportState()
        data class Error(val message: String) : ExportState()
    }
    
    /**
     * Export all flows to JSON format at the given URI.
     * Uses SAF so works with any document provider (local, cloud, etc).
     */
    suspend fun exportToJson(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dao = FlowManager.db?.flowDao() ?: run {
                    _exportState.value = ExportState.Error("Database not initialized")
                    return@withContext false
                }
                
                val totalCount = dao.getTotalFlowCount()
                if (totalCount == 0) {
                    _exportState.value = ExportState.Error("No flows to export")
                    return@withContext false
                }
                
                _exportState.value = ExportState.Exporting(0, totalCount)
                
                val flows = dao.getAllFlowsForExport()
                val oldestTimestamp = dao.getOldestFlowTimestamp()
                val newestTimestamp = dao.getNewestFlowTimestamp()
                
                // Build JSON
                val root = JSONObject().apply {
                    put("export_version", 1)
                    put("exported_at", System.currentTimeMillis())
                    put("exported_at_readable", formatTimestamp(System.currentTimeMillis()))
                    put("flow_count", flows.size)
                    put("date_range", JSONObject().apply {
                        put("from", oldestTimestamp ?: 0)
                        put("from_readable", oldestTimestamp?.let { formatTimestamp(it) } ?: "N/A")
                        put("to", newestTimestamp ?: 0)
                        put("to_readable", newestTimestamp?.let { formatTimestamp(it) } ?: "N/A")
                    })
                    
                    val flowsArray = JSONArray()
                    flows.forEachIndexed { index, flow ->
                        if (index % 100 == 0) {
                            _exportState.value = ExportState.Exporting(index, totalCount)
                        }
                        
                        flowsArray.put(JSONObject().apply {
                            put("id", flow.id)
                            put("timestamp", flow.timestamp)
                            put("timestamp_readable", formatTimestamp(flow.timestamp))
                            put("app_uid", flow.appUid ?: JSONObject.NULL)
                            put("app_name", flow.appName ?: JSONObject.NULL)
                            put("remote_ip", flow.remoteIp)
                            put("remote_port", flow.remotePort)
                            put("protocol", if (flow.protocol == 6) "TCP" else "UDP")
                            put("bytes", flow.bytes)
                            put("packets", flow.packets)
                            put("duration_ms", flow.durationMs)
                            put("sni", flow.sni ?: JSONObject.NULL)
                        })
                    }
                    put("flows", flowsArray)
                }
                
                // Write to URI
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(root.toString(2)) // Pretty print with 2-space indent
                    }
                } ?: run {
                    _exportState.value = ExportState.Error("Could not open output file")
                    return@withContext false
                }
                
                _exportState.value = ExportState.Success(flows.size, uri.lastPathSegment ?: "export.json")
                Log.i(TAG, "Exported ${flows.size} flows to $uri")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _exportState.value = ExportState.Error(e.message ?: "Unknown error")
                false
            }
        }
    }
    
    /**
     * Export to CSV format for spreadsheet compatibility.
     */
    suspend fun exportToCsv(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dao = FlowManager.db?.flowDao() ?: run {
                    _exportState.value = ExportState.Error("Database not initialized")
                    return@withContext false
                }
                
                val totalCount = dao.getTotalFlowCount()
                if (totalCount == 0) {
                    _exportState.value = ExportState.Error("No flows to export")
                    return@withContext false
                }
                
                _exportState.value = ExportState.Exporting(0, totalCount)
                
                val flows = dao.getAllFlowsForExport()
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        // CSV Header
                        writer.write("id,timestamp,timestamp_readable,app_uid,app_name,remote_ip,remote_port,protocol,bytes,packets,duration_ms,sni\n")
                        
                        // Data rows
                        flows.forEachIndexed { index, flow ->
                            if (index % 100 == 0) {
                                _exportState.value = ExportState.Exporting(index, totalCount)
                            }
                            
                            val line = listOf(
                                flow.id.toString(),
                                flow.timestamp.toString(),
                                formatTimestamp(flow.timestamp),
                                flow.appUid?.toString() ?: "",
                                escapeCsv(flow.appName ?: ""),
                                flow.remoteIp,
                                flow.remotePort.toString(),
                                if (flow.protocol == 6) "TCP" else "UDP",
                                flow.bytes.toString(),
                                flow.packets.toString(),
                                flow.durationMs.toString(),
                                escapeCsv(flow.sni ?: "")
                            ).joinToString(",")
                            
                            writer.write("$line\n")
                        }
                    }
                } ?: run {
                    _exportState.value = ExportState.Error("Could not open output file")
                    return@withContext false
                }
                
                _exportState.value = ExportState.Success(flows.size, uri.lastPathSegment ?: "export.csv")
                Log.i(TAG, "Exported ${flows.size} flows to CSV at $uri")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "CSV export failed", e)
                _exportState.value = ExportState.Error(e.message ?: "Unknown error")
                false
            }
        }
    }
    
    fun resetState() {
        _exportState.value = ExportState.Idle
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
    
    /**
     * Generate a suggested filename for export.
     */
    fun generateFilename(extension: String): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        return "odana_export_$timestamp.$extension"
    }
}

