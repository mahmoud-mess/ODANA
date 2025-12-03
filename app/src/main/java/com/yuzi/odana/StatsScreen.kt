package com.yuzi.odana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzi.odana.data.AppUsage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val stats: List<AppUsage> by viewModel.appUsageStats.collectAsState()
    
    // Calculate max for progress bar scaling
    val maxBytes = remember(stats) {
        stats.maxOfOrNull { it.totalBytes } ?: 1L
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Usage (Top 20)") },
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
                items(stats) { item ->
                    AppUsageItem(item, maxBytes)
                }
            }
        }
    }
}

@Composable
fun AppUsageItem(item: AppUsage, maxBytes: Long) {
    val percentage = if (maxBytes > 0) item.totalBytes.toFloat() / maxBytes.toFloat() else 0f
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            color = MaterialTheme.colorScheme.primary
        )
    }
}

