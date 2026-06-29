package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProgressUpdate(
    val area: String,
    val status: String,
    val completionPercentage: Int,
    val details: String
)

@JsonClass(generateAdapter = true)
data class SafetyHazard(
    val hazardType: String,
    val severity: String, // LOW, MEDIUM, HIGH
    val mitigationAction: String
)

@JsonClass(generateAdapter = true)
data class StructuredReport(
    val metadata: ReportMetadata,
    val executiveSummary: String,
    val progressUpdates: List<ProgressUpdate>,
    val safetyHazards: List<SafetyHazard>
)

@JsonClass(generateAdapter = true)
data class ReportMetadata(
    val siteName: String,
    val inspectorName: String,
    val date: String,
    val weather: String
)
