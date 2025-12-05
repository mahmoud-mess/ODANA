package com.yuzi.odana.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yuzi.odana.ml.AppProfile
import com.yuzi.odana.ml.FeedbackDao
import com.yuzi.odana.ml.ProfileDao
import com.yuzi.odana.ml.UserFeedback

@Database(
    entities = [FlowEntity::class, FlowFeatures::class, AppProfile::class, UserFeedback::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flowDao(): FlowDao
    abstract fun featureDao(): FeatureDao
    abstract fun profileDao(): ProfileDao
    abstract fun feedbackDao(): FeedbackDao
}
