package com.yuzi.odana.data

import androidx.room.ColumnInfo

data class FlowSummary(
    val id: Long,
    val timestamp: Long,
    val appUid: Int?,
    val appName: String?,
    val remoteIp: String,
    val remotePort: Int,
    val protocol: Int,
    val bytes: Long,
    val packets: Long,
    val durationMs: Long,
    val sni: String?
)
