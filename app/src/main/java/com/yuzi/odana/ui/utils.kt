package com.yuzi.odana.ui

import java.text.SimpleDateFormat
import java.util.*

fun formatBytes(bytes: Long): String {
    val kb = 1024
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        bytes >= gb -> String.format("%.1f GB", bytes / gb.toFloat())
        bytes >= mb -> String.format("%.1f MB", bytes / mb.toFloat())
        bytes >= kb -> String.format("%.1f KB", bytes / kb.toFloat())
        else -> "$bytes B"
    }
}

fun formatBytesPerSecond(bytesPerSecond: Long): String {
    return "${formatBytes(bytesPerSecond)}/s"
}

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        minutes > 0 -> String.format("%d:%02d", minutes, seconds % 60)
        else -> String.format("%d.%ds", seconds, (millis % 1000) / 100)
    }
}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
