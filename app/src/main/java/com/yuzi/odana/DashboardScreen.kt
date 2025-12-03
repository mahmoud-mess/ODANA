package com.yuzi.odana

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzi.odana.ui.components.*
import com.yuzi.odana.ui.formatBytes
import com.yuzi.odana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val appUsageStats by viewModel.appUsageStats.collectAsState()
    
    // Calculate protocol distribution from active flows
    val activeFlows = FlowManager.activeFlowsState.collectAsState().value
    val tcpCount = activeFlows.count { it.key.protocol == 6 }
    val udpCount = activeFlows.count { it.key.protocol == 17 }
    val total = (tcpCount + udpCount).coerceAtLeast(1)
    val tcpPercent = tcpCount.toFloat() / total
    val udpPercent = udpCount.toFloat() / total
    
    // Top 3 apps for quick view
    val topApps = appUsageStats.take(3)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ═══════════════════════════════════════════════════════════════
            // HEADER
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ODANA",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Network Analyzer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                
                // Status indicator
                StatusIndicator(
                    isActive = uiState.activeFlowsCount > 0
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // NETWORK PULSE - Hero visualization
            // ═══════════════════════════════════════════════════════════════
            GradientGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                animated = uiState.activeFlowsCount > 0
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    NetworkPulse(
                        activeConnections = uiState.activeFlowsCount,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // QUICK STATS ROW
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatCard(
                    title = "Data Total",
                    value = formatBytes(uiState.totalDataTransferred),
                    icon = Icons.Outlined.CloudDownload,
                    color = CyberMint,
                    modifier = Modifier.weight(1f)
                )
                
                QuickStatCard(
                    title = "Connections",
                    value = uiState.activeFlowsCount.toString(),
                    icon = Icons.Outlined.Hub,
                    color = Wisteria400,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // ═══════════════════════════════════════════════════════════════
            // PROTOCOL DISTRIBUTION
            // ═══════════════════════════════════════════════════════════════
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Protocol Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Orbital visualization
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OrbitalRing(
                                tcpPercent = tcpPercent,
                                udpPercent = udpPercent,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        // Legend
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProtocolLegendItem(
                                label = "TCP",
                                count = tcpCount,
                                percent = (tcpPercent * 100).toInt(),
                                color = TcpColor
                            )
                            ProtocolLegendItem(
                                label = "UDP",
                                count = udpCount,
                                percent = (udpPercent * 100).toInt(),
                                color = UdpColor
                            )
                        }
                    }
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // BANDWIDTH FLOW VISUALIZATION
            // ═══════════════════════════════════════════════════════════════
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Network Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        GlowDot(
                            color = if (uiState.activeFlowsCount > 0) SuccessGreen else Wisteria700,
                            isAnimated = uiState.activeFlowsCount > 0
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    BandwidthWave(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        color = if (uiState.activeFlowsCount > 0) CyberMint else Wisteria700.copy(alpha = 0.5f)
                    )
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // TOP APPS SECTION
            // ═══════════════════════════════════════════════════════════════
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Top Data Users",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Icon(
                            imageVector = Icons.Outlined.Leaderboard,
                            contentDescription = null,
                            tint = Wisteria400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (topApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Analytics,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No data yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        val maxBytes = topApps.maxOfOrNull { it.totalBytes } ?: 1L
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            topApps.forEachIndexed { index, app ->
                                TopAppItem(
                                    rank = index + 1,
                                    appName = app.appName ?: "Unknown",
                                    bytes = app.totalBytes,
                                    maxBytes = maxBytes,
                                    color = ChartPalette.getOrElse(index) { Wisteria400 }
                                )
                            }
                        }
                    }
                }
            }
            
            // Bottom spacer for nav bar
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SUPPORTING COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatusIndicator(isActive: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) SuccessGreen.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlowDot(
            color = if (isActive) SuccessGreen else Wisteria700,
            size = 10.dp,
            isAnimated = isActive
        )
        Text(
            text = if (isActive) "Monitoring" else "Idle",
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun QuickStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = value,
                style = DataDisplaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ProtocolLegendItem(
    label: String,
    count: Int,
    percent: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$count flows · $percent%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TopAppItem(
    rank: Int,
    appName: String,
    bytes: Long,
    maxBytes: Long,
    color: Color
) {
    val progress = bytes.toFloat() / maxBytes.toFloat()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Rank badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when (rank) {
                        1 -> Brush.linearGradient(listOf(CyberGold, CyberGold.copy(alpha = 0.7f)))
                        2 -> Brush.linearGradient(listOf(Color(0xFFC0C0C0), Color(0xFF9E9E9E)))
                        3 -> Brush.linearGradient(listOf(Color(0xFFCD7F32), Color(0xFFA0522D)))
                        else -> Brush.linearGradient(listOf(Wisteria700, Wisteria800))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        // App info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            GradientProgressBar(
                progress = progress,
                colors = listOf(color, color.copy(alpha = 0.6f)),
                height = 6
            )
        }
        
        // Data amount
        Text(
            text = formatBytes(bytes),
            style = MonoSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
