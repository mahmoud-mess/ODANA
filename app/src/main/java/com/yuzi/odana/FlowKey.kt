package com.yuzi.odana

data class FlowKey(
    val protocol: Int,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int
)