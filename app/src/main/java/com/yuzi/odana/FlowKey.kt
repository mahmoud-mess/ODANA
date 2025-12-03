package com.yuzi.odana

data class FlowKey(
    val sourceIp: String,
    val sourcePort: Int,
    val destIp: String,
    val destPort: Int,
    val protocol: Int
) {
    override fun toString(): String = "$sourceIp:$sourcePort|$destIp:$destPort|$protocol"
}
