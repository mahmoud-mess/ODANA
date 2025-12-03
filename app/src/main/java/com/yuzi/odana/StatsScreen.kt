package com.yuzi.odana

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzi.odana.data.AppUsage
import com.yuzi.odana.ui.components.ChartData
import com.yuzi.odana.ui.components.DonutChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val stats: List<AppUsage> by viewModel.appUsageStats.collectAsState()
    
    // Calculate max for progress bar scaling
    val maxBytes = remember(stats) {
        stats.maxOfOrNull { it.totalBytes } ?: 1L
    }
    
    // Prepare Chart Data
    val chartColors = remember {
        listOf(
            Color(0xFF00BCD4), // Cyan
            Color(0xFFE91E63), // Pink
            Color(0xFF4CAF50), // Green
            Color(0xFFFF9800), // Orange
            Color(0xFF9C27B0),  // Purple
            Color(0xFF607D8B), // Blue Grey
            Color(0xFF795548), // Brown
            Color(0xFFFDD835), // Yellow
            Color(0xFFD32F2F), // Red
            Color(0xFF00ACC1) // Light Cyan
        )
    }
    
    val (chartSectors, colorMap) = remember(stats) {
        val total = stats.sumOf { it.totalBytes }
        if (total == 0L) return@remember Pair(emptyList<ChartData>(), emptyMap<String, Color>())
        
        val colorMapInternal = mutableMapOf<String, Color>()
        stats.forEachIndexed { index, app ->
            val appName = app.appName ?: "Unknown"
            colorMapInternal[appName] = chartColors.getOrElse(index) { Color.Gray }
        }

        // Aggregate "Others" for chart if more than 5 apps
        val top5 = stats.take(5)
        val othersBytes = stats.drop(5).sumOf { it.totalBytes }
        
        val chartSectorsInternal = top5.map { app ->
            ChartData(
                value = app.totalBytes.toFloat(),
                color = colorMapInternal[app.appName] ?: Color.Gray,
                label = app.appName ?: "Unknown"
            )
        }.toMutableList()
        
        if (othersBytes > 0) {
            chartSectorsInternal.add(
                ChartData(
                    value = othersBytes.toFloat(),
                    color = Color.LightGray, // Consistent color for "Others"
                    label = "Others"
                )
            )
        }
        Pair(chartSectorsInternal, colorMapInternal)
    }
    
    val totalString = remember(stats) {
        val total = stats.sumOf { it.totalBytes }
        formatBytes(total)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Usage") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (stats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No data usage recorded yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DonutChart(
                        data = chartSectors, // Use chartSectors
                        centerText = totalString,
                        modifier = Modifier.height(300.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Top Applications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                items(stats) { item ->
                    AppUsageItem(item, maxBytes, colorMap[item.appName] ?: Color.Gray) // Use colorMap
                }
            }
        }
    }
}

@Composable
fun AppUsageItem(item: AppUsage, maxBytes: Long, chartColor: Color) {
    val percentage = if (maxBytes > 0) item.totalBytes.toFloat() / maxBytes.toFloat() else 0f
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Color indicator for chart legend
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(chartColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppIcon(item.appName, Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.appName ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = formatBytes(item.totalBytes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = chartColor // Use chart color for the progress indicator
        )
    }
}

