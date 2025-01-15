package com.dicoding.picodiploma.mycamera.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {

    // Menyimpan hasil deteksi ke database
    @Insert
    suspend fun insertDetection(detection: DetectionEntity)

    // Mengambil semua hasil deteksi (dibatasi 50 entri terbaru)
    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllDetections(): Flow<List<DetectionEntity>>

    // Fungsi pencarian berdasarkan label
    @Query("SELECT * FROM detection_history WHERE label LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchByLabel(query: String): Flow<List<DetectionEntity>>

    // Menghapus entri berdasarkan ID
    @Delete
    suspend fun deleteDetection(detection: DetectionEntity)

    @Query("DELETE FROM detection_history")
    suspend fun deleteAllDetections()

    // Menghapus entri paling lama jika jumlah entri melebihi 50
    @Query("DELETE FROM detection_history WHERE id NOT IN (SELECT id FROM detection_history ORDER BY timestamp DESC LIMIT 50)")
    suspend fun deleteOldDetections()
}
