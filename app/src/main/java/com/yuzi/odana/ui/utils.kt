package com.yuzi.odana.ui

fun formatBytes(bytes: Long): String {
    val kb = 1024
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb.toFloat())
        bytes >= mb -> String.format("%.2f MB", bytes / mb.toFloat())
        bytes >= kb -> String.format("%.2f KB", bytes / kb.toFloat())
        else -> "$bytes B"
    }
}
