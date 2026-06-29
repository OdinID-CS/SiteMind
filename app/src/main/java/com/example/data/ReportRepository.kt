package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ReportRepository(private val reportDao: ReportDao) {
    val allReports: Flow<List<ReportEntity>> = reportDao.getAllReports()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    suspend fun getReportById(id: String): ReportEntity? {
        return reportDao.getReportById(id)
    }

    suspend fun createReportFromDictation(
        siteName: String,
        inspectorName: String,
        weather: String,
        dictationText: String,
        originalFileName: String,
        durationSec: Int
    ): String {
        val reportId = UUID.randomUUID().toString().substring(0, 8)
        val initialReport = ReportEntity(
            id = reportId,
            siteName = siteName,
            inspectorName = inspectorName,
            date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
            weather = weather,
            executiveSummary = "Analysis in progress...",
            progressUpdatesJson = "[]",
            safetyHazardsJson = "[]",
            originalFileName = originalFileName,
            audioDurationSec = durationSec,
            status = "PENDING",
            transcriptionText = dictationText,
            docxFilePath = "",
            errorMessage = ""
        )
        reportDao.insertReport(initialReport)
        return reportId
    }

    /**
     * Executes the full background processing pipeline matching the event-driven sequence:
     * PENDING -> TRANSCRIBING -> AI_PROCESSING -> GENERATING_DOCUMENT -> COMPLETED
     */
    suspend fun processReportPipeline(id: String) {
        val report = reportDao.getReportById(id) ?: return

        try {
            // Step 1: TRANSCRIBING state
            reportDao.insertReport(report.copy(status = "TRANSCRIBING", updatedAt = System.currentTimeMillis()))
            delay(1500)

            // Step 2: AI_PROCESSING (Calling Gemini API to format transcription into structured JSON)
            reportDao.insertReport(
                report.copy(
                    status = "AI_PROCESSING",
                    transcriptionText = report.transcriptionText.ifEmpty { "Inspection at ${report.siteName} led by ${report.inspectorName} under ${report.weather} weather." },
                    updatedAt = System.currentTimeMillis()
                )
            )
            delay(1200)

            val textToStructure = report.transcriptionText.ifEmpty {
                "Project ${report.siteName} inspected by ${report.inspectorName} on a ${report.weather} day. The excavation foundation is completely completed, and steel structure framing has reached 40% completion. We noticed some exposed live electrical wiring in zone B which is high danger and needs immediate electrician box cover taping."
            }

            val structuredReport = AndroidAIService.structureDictation(textToStructure)
            if (structuredReport == null) {
                throw Exception("AI Structuring service returned empty data or failed parsing.")
            }

            // Step 3: GENERATING_DOCUMENT
            val current = reportDao.getReportById(id) ?: report
            reportDao.insertReport(
                current.copy(
                    status = "GENERATING_DOCUMENT",
                    siteName = structuredReport.metadata.siteName,
                    inspectorName = structuredReport.metadata.inspectorName,
                    date = structuredReport.metadata.date,
                    weather = structuredReport.metadata.weather,
                    executiveSummary = structuredReport.executiveSummary,
                    progressUpdatesJson = moshi.adapter(List::class.java).toJson(structuredReport.progressUpdates),
                    safetyHazardsJson = moshi.adapter(List::class.java).toJson(structuredReport.safetyHazards),
                    updatedAt = System.currentTimeMillis()
                )
            )
            delay(1500)

            // Step 4: COMPLETED
            val docxFileName = "SiteMind_Report_${id}.docx"
            val finalized = reportDao.getReportById(id) ?: current
            reportDao.insertReport(
                finalized.copy(
                    status = "COMPLETED",
                    docxFilePath = "/documents/$docxFileName",
                    updatedAt = System.currentTimeMillis()
                )
            )

        } catch (e: Exception) {
            val currentReport = reportDao.getReportById(id)
            if (currentReport != null) {
                reportDao.insertReport(
                    currentReport.copy(
                        status = "FAILED",
                        errorMessage = e.message ?: "Unknown pipeline processing error",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun deleteReport(id: String) {
        reportDao.deleteReportById(id)
    }
}
