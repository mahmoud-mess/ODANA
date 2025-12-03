package com.yuzi.odana.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flow_history")
data class FlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val appUid: Int?,
    val appName: String?,
    val remoteIp: String,
    val remotePort: Int,
    val protocol: Int,
    val bytes: Long,
    val packets: Long,
    val durationMs: Long,
    val sni: String?,
    val payloadHex: String? = null,
    val payloadText: String? = null
)
