package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Query("SELECT * FROM field_reports ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM field_reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: String): ReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Update
    suspend fun updateReport(report: ReportEntity)

    @Query("DELETE FROM field_reports WHERE id = :id")
    suspend fun deleteReportById(id: String)
}
