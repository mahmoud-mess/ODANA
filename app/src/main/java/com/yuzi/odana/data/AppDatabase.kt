package com.yuzi.odana.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FlowEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flowDao(): FlowDao
}
