package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "field_reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val siteName: String,
    val inspectorName: String,
    val date: String,
    val weather: String,
    val executiveSummary: String,
    val progressUpdatesJson: String, // JSON list of progress
    val safetyHazardsJson: String,   // JSON list of hazards
    val originalFileName: String,
    val audioDurationSec: Int,
    val status: String,              // PENDING, TRANSCRIBING, AI_PROCESSING, GENERATING_DOCUMENT, COMPLETED, FAILED
    val transcriptionText: String,
    val docxFilePath: String,
    val errorMessage: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
