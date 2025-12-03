package com.yuzi.odana.data

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

object BlockList {
    private const val PREFS_NAME = "odana_blocklist"
    private const val KEY_BLOCKED_UIDS = "blocked_uids"
    private lateinit var prefs: SharedPreferences
    
    // In-memory set for fast lookups
    private val blockedUids = ConcurrentHashMap.newKeySet<Int>()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        val saved = prefs.getStringSet(KEY_BLOCKED_UIDS, emptySet()) ?: emptySet()
        blockedUids.clear()
        saved.forEach { 
            try { blockedUids.add(it.toInt()) } catch (e: Exception) {}
        }
    }

    fun isUidBlocked(uid: Int): Boolean {
        return blockedUids.contains(uid)
    }

    fun toggleBlockUid(uid: Int) {
        if (blockedUids.contains(uid)) {
            blockedUids.remove(uid)
        } else {
            blockedUids.add(uid)
        }
        save()
    }

    private fun save() {
        val set = blockedUids.map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_BLOCKED_UIDS, set).apply()
    }
}
