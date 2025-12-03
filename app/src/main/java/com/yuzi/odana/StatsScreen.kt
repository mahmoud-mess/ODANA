package com.yuzi.odana

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzi.odana.data.AppUsage
import com.yuzi.odana.data.ExportManager
import com.yuzi.odana.ui.components.*
import com.yuzi.odana.ui.formatBytes
import com.yuzi.odana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: MainViewModel,
    onExportJson: () -> Unit = {},
    onExportCsv: () -> Unit = {}
) {
    val stats: List<AppUsage> by viewModel.appUsageStats.collectAsState()
    
    val maxBytes = remember(stats) {
        stats.maxOfOrNull { it.totalBytes } ?: 1L
    }
    
    // Prepare Chart Data with wisteria palette
    val (chartSectors, colorMap) = remember(stats) {
        if (stats.sumOf { it.totalBytes } == 0L) {
            Pair(emptyList<ChartData>(), emptyMap<String, Color>())
        } else {
            val colorMapInternal = mutableMapOf<String, Color>()
            stats.forEachIndexed { index, app ->
                val appName = app.appName ?: "Unknown"
                colorMapInternal[appName] = ChartPalette.getOrElse(index) { Wisteria400 }
            }

            val top5 = stats.take(5)
            val othersBytes = stats.drop(5).sumOf { it.totalBytes }

            val chartSectorsInternal = top5.map { app ->
                ChartData(
                    value = app.totalBytes.toFloat(),
                    color = colorMapInternal[app.appName] ?: Wisteria400,
                    label = app.appName ?: "Unknown"
                )
            }.toMutableList()

            if (othersBytes > 0) {
                chartSectorsInternal.add(
                    ChartData(
                        value = othersBytes.toFloat(),
                        color = Wisteria700.copy(alpha = 0.5f),
                        label = "Others"
                    )
                )
            }

            Pair(chartSectorsInternal.toList(), colorMapInternal.toMap())
        }
    }
    
    val totalString = remember(stats) {
        val total = stats.sumOf { it.totalBytes }
        formatBytes(total)
    }

    val exportState by ExportManager.exportState.collectAsState()
    var showExportMenu by remember { mutableStateOf(false) }
    
    // Show snackbar for export results
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportManager.ExportState.Success -> {
                snackbarHostState.showSnackbar("Exported ${state.flowCount} flows")
                ExportManager.resetState()
            }
            is ExportManager.ExportState.Error -> {
                snackbarHostState.showSnackbar("Export failed: ${state.message}")
                ExportManager.resetState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (stats.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PieChart,
                        contentDescription = null,
                        tint = Wisteria400.copy(alpha = 0.4f),
                        modifier = Modifier.size(80.dp)
                    )
                    Text(
                        text = "No Usage Data Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Start monitoring to collect statistics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with export button
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "Data Usage",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Breakdown by application",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        
                        // Export button with dropdown
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (exportState is ExportManager.ExportState.Exporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Wisteria400
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = "Export",
                                        tint = Wisteria400
                                    )
                                }
                            }
                            
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export as JSON") },
                                    leadingIcon = { 
                                        Icon(Icons.Outlined.Code, contentDescription = null) 
                                    },
                                    onClick = {
                                        showExportMenu = false
                                        onExportJson()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as CSV") },
                                    leadingIcon = { 
                                        Icon(Icons.Outlined.TableChart, contentDescription = null) 
                                    },
                                    onClick = {
                                        showExportMenu = false
                                        onExportCsv()
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Donut Chart Card
                item {
                    GradientGlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DonutChart(
                                data = chartSectors,
                                centerText = totalString,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                strokeWidth = 40f,
                                showGlow = true
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Legend row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                chartSectors.take(3).forEach { data ->
                                    ChartLegendItem(
                                        color = data.color,
                                        label = data.label.take(10)
                                    )
                                }
                            }
                            
                            if (chartSectors.size > 3) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    chartSectors.drop(3).forEach { data ->
                                        ChartLegendItem(
                                            color = data.color,
                                            label = data.label.take(10)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Section header
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Applications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${stats.size} apps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
                
                // App list
                itemsIndexed(stats) { index, item ->
                    AppUsageCard(
                        item = item,
                        maxBytes = maxBytes,
                        color = colorMap[item.appName] ?: ChartPalette.getOrElse(index) { Wisteria400 },
                        rank = index + 1
                    )
                }
                
                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        
        // Snackbar host for export messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ChartLegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun AppUsageCard(
    item: AppUsage,
    maxBytes: Long,
    color: Color,
    rank: Int
) {
    val progress = item.totalBytes.toFloat() / maxBytes.toFloat()
    
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App icon placeholder with rank
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                // Try to show app icon, fallback to rank
                AppIcon(
                    packageName = item.appName,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Info section
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.appName ?: "Unknown",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = formatBytes(item.totalBytes),
                        style = MonoMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress bar with percentage
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GradientProgressBar(
                        progress = progress,
                        colors = listOf(color, color.copy(alpha = 0.5f)),
                        height = 6,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
