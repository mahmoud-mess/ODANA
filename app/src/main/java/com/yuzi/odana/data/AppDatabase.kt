package com.yuzi.odana.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FlowEntity::class, FlowFeatures::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flowDao(): FlowDao
    abstract fun featureDao(): FeatureDao
}
