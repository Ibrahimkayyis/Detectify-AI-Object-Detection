package com.dicoding.picodiploma.mycamera.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DetectionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun detectionDao(): DetectionDao

    companion object {
        const val DATABASE_NAME = "detection_db"
    }
}
