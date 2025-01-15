package com.dicoding.picodiploma.mycamera.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detection_history")
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val imageUri: String,
    val label: String,
    val score: Float,
    val boundingBox: String,
    val timestamp: Long = System.currentTimeMillis()
)
