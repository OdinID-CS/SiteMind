package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProgressUpdate
import com.example.data.ReportEntity
import com.example.data.SafetyHazard
import com.example.ui.ReportViewModel
import com.example.ui.theme.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: ReportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SiteMindTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: ReportViewModel,
    modifier: Modifier = Modifier
) {
    val reports by viewModel.allReports.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main scrollable / viewable content based on Selected Tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Shared header for all workspace tabs
                HeaderSection()

                Spacer(modifier = Modifier.height(8.dp))

                when (currentTab) {
                    0 -> { // Home Tab: Dashboard
                        val activeReport = reports.firstOrNull {
                            it.status in listOf("PENDING", "TRANSCRIBING", "AI_PROCESSING", "GENERATING_DOCUMENT")
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Active Process Card from Design HTML (if any active pipeline)
                            if (activeReport != null) {
                                item {
                                    ActiveProcessCard(
                                        report = activeReport,
                                        onSelect = { viewModel.selectedReport = activeReport }
                                    )
                                }
                            }

                            // Quick Actions Block (Section 2 from Design HTML)
                            item {
                                QuickActionsSection(
                                    onNewReportClick = { showCreateDialog = true }
                                )
                            }

                            // Recent Documents Title & Action Section
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Documents",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SleekOnSurface,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    TextButton(
                                        onClick = { currentTab = 1 },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "View all",
                                            color = SleekPrimary,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            // Empty State / Report Items List
                            if (reports.isEmpty()) {
                                item {
                                    EmptyStateSection(
                                        onCreateClick = { showCreateDialog = true }
                                    )
                                }
                            } else {
                                // Take top 5 recent reports for the dashboard
                                items(reports.take(5), key = { it.id }) { report ->
                                    ReportCardItem(
                                        report = report,
                                        onDelete = { viewModel.deleteReport(report.id) },
                                        onReprocess = { viewModel.reprocessReport(report.id) },
                                        onSelect = { viewModel.selectedReport = report }
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    1 -> { // Reports Tab: Full Scroll List
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "All Field Reports (${reports.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = SleekOnSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            if (reports.isEmpty()) {
                                EmptyStateSection(onCreateClick = { showCreateDialog = true })
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(reports, key = { it.id }) { report ->
                                        ReportCardItem(
                                            report = report,
                                            onDelete = { viewModel.deleteReport(report.id) },
                                            onReprocess = { viewModel.reprocessReport(report.id) },
                                            onSelect = { viewModel.selectedReport = report }
                                        )
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    2 -> { // Queue Tab: Processing Monitor
                        val queueReports = reports.filter {
                            it.status in listOf("PENDING", "TRANSCRIBING", "AI_PROCESSING", "GENERATING_DOCUMENT")
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Active Transcription Queue (${queueReports.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = SleekOnSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            if (queueReports.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = SleekStatusGreen,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "All pipelines finished!",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "No reports currently queueing or formatting.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SleekOnSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(queueReports, key = { it.id }) { report ->
                                        ActiveProcessCard(
                                            report = report,
                                            onSelect = { viewModel.selectedReport = report }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    3 -> { // Settings Tab
                        SettingsScreen()
                    }
                }
            }

            // Sleek Bottom Navigation bar matching design
            SleekBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = SleekPrimary,
            contentColor = SleekSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 104.dp) // Elevated to sit perfectly above bottom nav
                .testTag("add_report_fab"),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Field Report",
                modifier = Modifier.size(28.dp)
            )
        }

        // Document Details Overlay View (Bottom Sheet style)
        viewModel.selectedReport?.let { report ->
            ReportDetailsOverlay(
                report = report,
                onDismiss = { viewModel.selectedReport = null },
                onDelete = {
                    viewModel.deleteReport(report.id)
                    viewModel.selectedReport = null
                }
            )
        }

        // New Report Generator Sheet
        if (showCreateDialog) {
            CreateReportBottomSheet(
                viewModel = viewModel,
                onDismiss = { showCreateDialog = false }
            )
        }
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SleekPrimary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Rotating visual diamond structure matching Sleek HTML theme logo
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Transparent)
                        .border(2.dp, SleekSurfaceVariant, RoundedCornerShape(2.dp))
                )
            }
            Column {
                Text(
                    text = "SiteMind",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SleekOnBackground
                )
                Text(
                    text = "AI Field Report Writer",
                    style = MaterialTheme.typography.bodySmall,
                    color = SleekOnSurface
                )
            }
        }

        // JD Avatar placeholder matching theme
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SleekTertiary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "JD",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SleekOnTertiary
            )
        }
    }
}

@Composable
fun ActiveProcessCard(
    report: ReportEntity,
    onSelect: () -> Unit
) {
    val progress = when (report.status) {
        "PENDING" -> 0.15f
        "TRANSCRIBING" -> 0.40f
        "AI_PROCESSING" -> 0.68f
        "GENERATING_DOCUMENT" -> 0.90f
        else -> 0.0f
    }
    val progressText = when (report.status) {
        "PENDING" -> "Queueing Report..."
        "TRANSCRIBING" -> "Transcribing Audio..."
        "AI_PROCESSING" -> "Structuring Data..."
        "GENERATING_DOCUMENT" -> "Compiling Word..."
        else -> "Processing..."
    }
    val percentageString = "${(progress * 100).toInt()}%"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("active_process_card"),
        colors = CardDefaults.cardColors(containerColor = SleekSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SleekBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "ACTIVE PROCESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = SleekPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Site Audit: ${report.siteName.ifEmpty { "North Wing" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnBackground
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(SleekTertiary)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = report.status,
                        color = SleekOnTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status: $progressText",
                        style = MaterialTheme.typography.bodySmall,
                        color = SleekOnSurface
                    )
                    Text(
                        text = percentageString,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnBackground
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = SleekPrimary,
                    trackColor = SleekBorder,
                )

                Text(
                    text = "Gemini is identifying safety hazards and progress updates from your dictation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SleekOnSurface,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun QuickActionsSection(
    onNewReportClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onNewReportClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = SleekSecondary,
                contentColor = SleekOnTertiary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .testTag("new_report_button"),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "New Report",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        IconButton(
            onClick = { /* Simulated folder view */ },
            modifier = Modifier
                .size(56.dp)
                .background(SleekBorder, RoundedCornerShape(16.dp))
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = "View Folders",
                tint = SleekOnBackground,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun EmptyStateSection(
    onCreateClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Analytics,
                contentDescription = null,
                tint = SleekPrimary.copy(alpha = 0.3f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Field Reports Written",
                style = MaterialTheme.typography.titleMedium,
                color = SleekOnBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dictate or write work observations. SiteMind's integrated Gemini pipeline transcribes, organizes progress indicators, and compiles Word documents instantly.",
                style = MaterialTheme.typography.bodySmall,
                color = SleekOnSurface,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary, contentColor = SleekSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_state_create_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Field Dictation")
            }
        }
    }
}

@Composable
fun ReportCardItem(
    report: ReportEntity,
    onDelete: () -> Unit,
    onReprocess: () -> Unit,
    onSelect: () -> Unit
) {
    val isCompleted = report.status == "COMPLETED"
    val isFailed = report.status == "FAILED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("report_item_${report.id}"),
        colors = CardDefaults.cardColors(containerColor = SleekSurfaceVariant),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SleekBorderVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Status/Type badge box
            val badgeBg = when {
                isCompleted -> Color(0xFFF2B8B5)
                isFailed -> Color(0xFFFFDAD6)
                else -> Color(0xFFEADDFF)
            }
            val badgeTextColor = when {
                isCompleted -> Color(0xFF601410)
                isFailed -> Color(0xFFB3261E)
                else -> Color(0xFF21005D)
            }
            val badgeText = when {
                isCompleted -> "DOCX"
                isFailed -> "FAIL"
                else -> "AI"
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = badgeTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Center Content
            Column(modifier = Modifier.weight(1f)) {
                val cleanSiteName = report.siteName.trim().replace(" ", "_")
                val fileName = if (cleanSiteName.isNotEmpty()) {
                    if (cleanSiteName.endsWith(".docx")) cleanSiteName else "${cleanSiteName}.docx"
                } else {
                    "SiteMind_Report_${report.id}.docx"
                }

                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SleekOnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val statusLabel = when (report.status) {
                    "PENDING" -> "Queueing"
                    "TRANSCRIBING" -> "Transcribing"
                    "AI_PROCESSING" -> "AI Formatting"
                    "GENERATING_DOCUMENT" -> "Compiling"
                    "COMPLETED" -> "Completed"
                    else -> "Failed"
                }
                Text(
                    text = "$statusLabel • ${report.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SleekOnSurface
                )
            }

            // Actions on the Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = "Ready for download",
                        tint = SleekOnSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isFailed) {
                    IconButton(
                        onClick = onReprocess,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reprocess",
                            tint = SleekPrimary
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_report_${report.id}")
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = SleekStatusRed
                    )
                }
            }
        }
    }
}

@Composable
fun SleekBottomNavBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        color = Color(0xFFF3EDF7),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, SleekBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(80.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple("Home", Icons.Default.Home, Icons.Outlined.Home),
                Triple("Reports", Icons.Default.Description, Icons.Outlined.Description),
                Triple("Queue", Icons.Default.HourglassEmpty, Icons.Outlined.HourglassEmpty),
                Triple("Settings", Icons.Default.Settings, Icons.Outlined.Settings)
            )

            tabs.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
                val selected = currentTab == index
                val activeColor = SleekPrimary
                val inactiveColor = SleekOnSurface

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 4.dp, horizontal = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) Color(0xFFE8DEF8) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) filledIcon else outlinedIcon,
                            contentDescription = label,
                            tint = if (selected) activeColor else inactiveColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = if (selected) activeColor else inactiveColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Inspector Workspace",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = SleekOnBackground
        )

        // Inspector Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SleekBorder)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(SleekTertiary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnTertiary
                    )
                }

                Column {
                    Text(
                        text = "John Doe",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnBackground
                    )
                    Text(
                        text = "Lead Site Inspector",
                        style = MaterialTheme.typography.bodySmall,
                        color = SleekOnSurface
                    )
                    Text(
                        text = "ID: INSP-2026-098",
                        style = MaterialTheme.typography.labelSmall,
                        color = SleekPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // App Configuration settings group
        Text(
            text = "APP CONFIGURATION",
            style = MaterialTheme.typography.labelSmall,
            color = SleekOnSurface,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsItemRow(
                icon = Icons.Outlined.SettingsSuggest,
                title = "Gemini API Pipeline",
                subtitle = "Active • Server-Side REST Key",
                trailing = "Configured"
            )
            SettingsItemRow(
                icon = Icons.Outlined.Translate,
                title = "Primary Dictation Language",
                subtitle = "English (United States)",
                trailing = "Change"
            )
            SettingsItemRow(
                icon = Icons.Outlined.VerifiedUser,
                title = "Security & Compliance",
                subtitle = "Standard OSHA Template v4.2",
                trailing = "OSHA"
            )
        }
    }
}

@Composable
fun SettingsItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SleekSurfaceVariant),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SleekBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SleekBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SleekPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnBackground
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SleekOnSurface
                    )
                }
            }

            Text(
                text = trailing,
                color = SleekPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CreateReportBottomSheet(
    viewModel: ReportViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var isSimulatingRecording by remember { mutableStateOf(false) }
    var countdownTimer by remember { mutableStateOf(5) }

    // Timer simulation
    LaunchedEffect(isSimulatingRecording) {
        if (isSimulatingRecording) {
            countdownTimer = 5
            while (countdownTimer > 0) {
                delay(1000)
                countdownTimer--
            }
            isSimulatingRecording = false
            // Auto inject dictation observations if empty
            if (viewModel.newDictationText.isBlank()) {
                viewModel.newDictationText = "Observation notes at site ${viewModel.newSiteName}. Groundwork concrete foundation works are 100% completed and approved. Structural framing steel beams started and is on track around 40% completion. Noticed a key safety concern regarding a floor hole opening that was left unmarked near zone B, handled and resolved immediately."
            }
            step = 3
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (step == 3) {
                Button(
                    onClick = {
                        viewModel.addReport()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary, contentColor = SleekSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("submit_report_button")
                ) {
                    Text("Analyze & Compile")
                }
            } else if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary, contentColor = SleekSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    enabled = viewModel.newSiteName.isNotBlank() && viewModel.newInspectorName.isNotBlank()
                ) {
                    Text("Continue to Dictation")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SleekOnSurface)
            }
        },
        containerColor = SleekSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = if (step == 1) "Report Configuration" else "Field Dictation Voice Pad",
                color = SleekOnBackground,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (step == 1) {
                    OutlinedTextField(
                        value = viewModel.newSiteName,
                        onValueChange = { viewModel.newSiteName = it },
                        label = { Text("Site / Project Name") },
                        placeholder = { Text("e.g. SiteMind HQ") },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SleekBorder,
                            focusedBorderColor = SleekPrimary,
                            unfocusedTextColor = SleekOnBackground,
                            focusedTextColor = SleekOnBackground,
                            unfocusedLabelColor = SleekOnSurface,
                            focusedLabelColor = SleekPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_site_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = viewModel.newInspectorName,
                        onValueChange = { viewModel.newInspectorName = it },
                        label = { Text("Inspector Name") },
                        placeholder = { Text("e.g. Quincy Solomon") },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SleekBorder,
                            focusedBorderColor = SleekPrimary,
                            unfocusedTextColor = SleekOnBackground,
                            focusedTextColor = SleekOnBackground,
                            unfocusedLabelColor = SleekOnSurface,
                            focusedLabelColor = SleekPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_inspector_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = viewModel.newWeather,
                        onValueChange = { viewModel.newWeather = it },
                        label = { Text("Weather Conditions") },
                        placeholder = { Text("e.g. Sunny & Windy, 75°F") },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SleekBorder,
                            focusedBorderColor = SleekPrimary,
                            unfocusedTextColor = SleekOnBackground,
                            focusedTextColor = SleekOnBackground,
                            unfocusedLabelColor = SleekOnSurface,
                            focusedLabelColor = SleekPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else if (step == 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SleekSurfaceVariant)
                            .border(1.dp, SleekBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSimulatingRecording) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Recording Dictation Audio...",
                                    color = SleekStatusRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "0:0$countdownTimer",
                                    color = SleekOnBackground,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                CircularProgressIndicator(
                                    color = SleekStatusRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { isSimulatingRecording = true },
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(SleekPrimary, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Start Dictation",
                                        tint = SleekSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap to Record Field Dictation",
                                    color = SleekOnBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { step = 3 }) {
                                    Text("Skip to Manual Voice Transcript", color = SleekPrimary)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Voice Dictation Transcript Preview",
                        color = SleekOnSurface,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = viewModel.newDictationText,
                        onValueChange = { viewModel.newDictationText = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SleekBorder,
                            focusedBorderColor = SleekPrimary,
                            unfocusedTextColor = SleekOnBackground,
                            focusedTextColor = SleekOnBackground
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag("input_dictation"),
                        maxLines = 6
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You can edit this transcribed text preview before compiling to Word.",
                        color = SleekOnSurface,
                        fontSize = 11.sp
                    )
                }
            }
        }
    )
}

@Composable
fun ReportDetailsOverlay(
    report: ReportEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) }
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // Parse progress list
    val progressUpdates = remember(report.progressUpdatesJson) {
        try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, ProgressUpdate::class.java)
            val adapter = moshi.adapter<List<ProgressUpdate>>(listType)
            adapter.fromJson(report.progressUpdatesJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Parse safety list
    val safetyHazards = remember(report.safetyHazardsJson) {
        try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, SafetyHazard::class.java)
            val adapter = moshi.adapter<List<SafetyHazard>>(listType)
            adapter.fromJson(report.safetyHazardsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (report.status == "COMPLETED") {
                Button(
                    onClick = { /* Simulated download action */ },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekStatusGreen, contentColor = SleekSurfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download .docx")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SleekOnSurface)
            }
        },
        containerColor = SleekSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("report_details_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.siteName,
                        color = SleekOnBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "ID: ${report.id}  •  ${report.date}",
                        color = SleekOnSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Custom Sleek Badge
                val badgeColor = when (report.status) {
                    "COMPLETED" -> SleekStatusGreen
                    "FAILED" -> SleekStatusRed
                    else -> SleekPrimary
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = report.status,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Divider line
                HorizontalDivider(color = SleekBorder, modifier = Modifier.padding(vertical = 8.dp))

                // Tabs Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SleekBorder)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabs = listOf("Overview", "Progress", "Hazards")
                    tabs.forEachIndexed { idx, label ->
                        val selected = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) SleekPrimary else Color.Transparent)
                                .clickable { activeTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) SleekSurfaceVariant else SleekOnSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab details
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    when (activeTab) {
                        0 -> { // Overview Tab
                            Column(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    item {
                                        Text(
                                            text = "Site Meta Details",
                                            fontWeight = FontWeight.Bold,
                                            color = SleekPrimary,
                                            fontSize = 13.sp
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Inspector:", color = SleekOnSurface, fontSize = 13.sp)
                                            Text(report.inspectorName, color = SleekOnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Weather Info:", color = SleekOnSurface, fontSize = 13.sp)
                                            Text(report.weather, color = SleekOnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    item {
                                        HorizontalDivider(color = SleekBorder, modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "Executive Analysis",
                                            fontWeight = FontWeight.Bold,
                                            color = SleekPrimary,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = report.executiveSummary,
                                            color = SleekOnBackground,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }

                                    if (report.status == "FAILED") {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(SleekStatusRedBg)
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = "Error: ${report.errorMessage}",
                                                    color = SleekStatusRed,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> { // Progress Updates Tab
                            if (progressUpdates.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No progress items recorded or pending AI parsing.", color = SleekOnSurface, fontSize = 13.sp)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(progressUpdates) { update ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SleekSurfaceVariant)
                                                .border(1.dp, SleekBorder, RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = update.area,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SleekOnBackground,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "${update.completionPercentage}% - ${update.status}",
                                                    color = if (update.completionPercentage == 100) SleekStatusGreen else SleekPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { update.completionPercentage / 100f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(CircleShape),
                                                color = if (update.completionPercentage == 100) SleekStatusGreen else SleekPrimary,
                                                trackColor = SleekBorder,
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = update.details,
                                                color = SleekOnSurface,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        2 -> { // Safety Hazards Tab
                            if (safetyHazards.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No safety issues detected. Good job!", color = SleekStatusGreen, fontSize = 13.sp)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(safetyHazards) { hazard ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SleekSurfaceVariant)
                                                .border(1.dp, SleekBorder, RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = hazard.hazardType,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SleekOnBackground,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            when (hazard.severity) {
                                                                "HIGH" -> SleekStatusRedBg
                                                                "MEDIUM" -> Color(0xFFFEF3C7) // Light warning yellow bg
                                                                else -> Color(0xFFD1FAE5) // Light green bg
                                                            }
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = hazard.severity,
                                                        color = when (hazard.severity) {
                                                            "HIGH" -> SleekStatusRed
                                                            "MEDIUM" -> SleekStatusYellow
                                                            else -> SleekStatusGreen
                                                        },
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Mitigation: ${hazard.mitigationAction}",
                                                color = SleekOnSurface,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun HorizontalDivider(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}
