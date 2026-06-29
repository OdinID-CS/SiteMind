package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ReportEntity
import com.example.data.ReportRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ReportRepository

    val allReports: StateFlow<List<ReportEntity>>

    // Fields for New Report Creation
    var newSiteName by mutableStateOf("")
    var newInspectorName by mutableStateOf("")
    var newWeather by mutableStateOf("")
    var newDictationText by mutableStateOf("")

    // State to toggle views
    var isRecordingDictation by mutableStateOf(false)
    var selectedReport by mutableStateOf<ReportEntity?>(null)

    init {
        val reportDao = AppDatabase.getDatabase(application).reportDao()
        repository = ReportRepository(reportDao)
        allReports = repository.allItemsFlow()
    }

    // Helper extension to keep code compilation robust and clean
    private fun ReportRepository.allItemsFlow(): StateFlow<List<ReportEntity>> {
        return this.allReports.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun addReport() {
        if (newSiteName.isBlank() || newInspectorName.isBlank()) return

        val site = newSiteName.trim()
        val inspector = newInspectorName.trim()
        val weather = newWeather.trim().ifEmpty { "Clear & Sunny" }
        val dictation = newDictationText.trim().ifEmpty {
            "Inspection report for $site. Grading is fully completed. Steel framing is currently active and is about 40 percent finished. General safety review noticed an unprotected elevator floor opening near section C which is high risk. Remedied immediately with marked barricades."
        }

        viewModelScope.launch {
            val reportId = repository.createReportFromDictation(
                siteName = site,
                inspectorName = inspector,
                weather = weather,
                dictationText = dictation,
                originalFileName = "dictation_${System.currentTimeMillis()}.wav",
                durationSec = (45..120).random()
            )

            // Reset inputs
            newSiteName = ""
            newInspectorName = ""
            newWeather = ""
            newDictationText = ""

            // Launch async pipeline simulation
            launch {
                repository.processReportPipeline(reportId)
            }
        }
    }

    fun deleteReport(id: String) {
        viewModelScope.launch {
            repository.deleteReport(id)
            if (selectedReport?.id == id) {
                selectedReport = null
            }
        }
    }

    fun reprocessReport(id: String) {
        viewModelScope.launch {
            repository.processReportPipeline(id)
        }
    }
}
