package com.yuzi.odana

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzi.odana.ui.formatBytes
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import com.yuzi.odana.SummaryCard


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ODANA Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryCard(
                    title = "Active Connections",
                    value = uiState.activeFlowsCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Data Transferred",
                    value = formatBytes(uiState.totalDataTransferred),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            RealtimeNetworkFlow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                activeFlowsCount = uiState.activeFlowsCount
            )
            Spacer(modifier = Modifier.height(16.dp))
            TopBandwidthUsageChart(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TopBandwidthUsageChart(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val appUsageStats by viewModel.appUsageStats.collectAsState()
    val topApps = appUsageStats.take(5)
    val totalUsage = appUsageStats.sumOf { it.totalBytes }.toFloat()

    // 1. Capture the color here (in the Composable scope)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = Color.Gray

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top Bandwidth Usage",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (topApps.isEmpty()) {
                Text("No usage data yet.", color = Color.Gray)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    topApps.forEach { appUsage ->
                        val percentage = if (totalUsage > 0) (appUsage.totalBytes / totalUsage) else 0f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = appUsage.appName ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.4f)
                            )
                            Row(
                                modifier = Modifier.weight(0.6f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                                    drawRect(
                                        color = trackColor,
                                        size = size
                                    )
                                    // 2. Use the captured variable here
                                    drawRect(
                                        color = barColor, 
                                        size = size.copy(width = size.width * percentage)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.1f%%", percentage * 100),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float,
    var size: Float
)

@Composable
fun RealtimeNetworkFlow(
    modifier: Modifier = Modifier,
    activeFlowsCount: Int,
    maxParticles: Int = 200,
    particleColor: Color = MaterialTheme.colorScheme.primary
) {
    val particles = remember { mutableStateListOf<Particle>() }
    var lastFrameTime by remember { mutableStateOf(0L) }

    // Initialize particles
    LaunchedEffect(Unit) {
        for (i in 0 until maxParticles) {
            particles.add(Particle(0f, 0f, 0f, 0f, 0f, 0f))
        }
    }

    LaunchedEffect(activeFlowsCount) {
        while (true) {
            withFrameNanos { frameTime ->
                val deltaTime = if (lastFrameTime > 0) {
                    (frameTime - lastFrameTime) / 1_000_000f
                } else {
                    16f // Assume 60fps for the first frame
                }
                lastFrameTime = frameTime

                val activeParticleCount = (activeFlowsCount * 10).coerceAtMost(maxParticles)

                for (i in 0 until maxParticles) {
                    val p = particles[i]

                    if (i < activeParticleCount) {
                        if (p.alpha <= 0f) {
                            // Revive particle
                            p.x = 0f
                            p.y = 0f
                            p.vx = (Math.random().toFloat() - 0.5f) * 4
                            p.vy = (Math.random().toFloat() - 0.5f) * 4
                            p.alpha = 1f
                            p.size = (Math.random().toFloat() * 2 + 1)
                        } else {
                            // Update particle
                            p.x += p.vx * deltaTime * 0.1f
                            p.y += p.vy * deltaTime * 0.1f
                            p.alpha -= 0.005f * deltaTime
                        }
                    } else {
                        // Deactivate particle
                        p.alpha = 0f
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        particles.forEach { p ->
            if (p.alpha > 0) {
                drawCircle(
                    color = particleColor.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                    radius = p.size,
                    center = Offset(centerX + p.x, centerY + p.y)
                )
            }
        }
    }
}
