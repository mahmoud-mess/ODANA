package com.yuzi.odana

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.yuzi.odana.ml.AnomalyDetector
import com.yuzi.odana.ml.AppProfile
import com.yuzi.odana.ui.components.*
import com.yuzi.odana.ui.formatBytes
import com.yuzi.odana.ui.theme.*

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * APP PROFILES SCREEN
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Shows the learned ML profile for each app - their "normal" behavior.
 * Users can see what patterns the ML has learned.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onBack: () -> Unit
) {
    val profiles = remember { AnomalyDetector.getAllProfiles().sortedByDescending { it.flowCount } }
    var selectedProfile by remember { mutableStateOf<AppProfile?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("ML Profiles", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${profiles.size} apps learned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = Wisteria400.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "No profiles yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Start monitoring to build app profiles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.appUid }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isExpanded = selectedProfile == profile,
                        onClick = { 
                            selectedProfile = if (selectedProfile == profile) null else profile
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: AppProfile,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val maturityColor = when (profile.maturityLevel) {
        AppProfile.MATURITY_MATURE -> SuccessGreen
        AppProfile.MATURITY_LEARNING -> WarningAmber
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    
    val maturityLabel = when (profile.maturityLevel) {
        AppProfile.MATURITY_MATURE -> "Mature"
        AppProfile.MATURITY_LEARNING -> "Learning"
        else -> "New"
    }
    
    // Load app icon
    val appIcon = remember(profile.appName) {
        try {
            profile.appName?.let { packageName ->
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Wisteria500.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = profile.appName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = Wisteria400,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // App name and flow count
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.appName ?: "UID:${profile.appUid}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.flowCount} flows learned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Maturity badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(maturityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = maturityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = maturityColor
                    )
                }
            }
            
            // Expanded details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    // Hourly Activity Histogram
                    Text(
                        text = "Activity by Hour",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    HourlyHistogramChart(histogram = profile.hourlyHistogram)
                    
                    // Volume Stats
                    Text(
                        text = "Typical Traffic",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Download",
                            value = formatBytes(profile.typicalBytesIn().toLong()),
                            icon = Icons.Filled.ArrowDownward,
                            color = CyberMint
                        )
                        StatItem(
                            label = "Upload",
                            value = formatBytes(profile.typicalBytesOut().toLong()),
                            icon = Icons.Filled.ArrowUpward,
                            color = CyberPink
                        )
                    }
                    
                    // Protocol breakdown
                    Text(
                        text = "Protocol Usage",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tcpPercent = if (profile.flowCount > 0) {
                            (profile.tcpFlowCount.toFloat() / profile.flowCount * 100).toInt()
                        } else 0
                        val udpPercent = 100 - tcpPercent
                        
                        ProtocolBar(label = "TCP", percent = tcpPercent, color = CyberBlue)
                        ProtocolBar(label = "UDP", percent = udpPercent, color = CyberGold)
                    }
                    
                    // Destination stats
                    Text(
                        text = "Destinations",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Unique IPs",
                            value = "~${profile.estimatedUniqueDestinations()}",
                            icon = Icons.Outlined.Language,
                            color = Wisteria400
                        )
                        StatItem(
                            label = "Top Ports",
                            value = profile.topPorts(3).joinToString(", "),
                            icon = Icons.Outlined.Router,
                            color = Wisteria400
                        )
                    }
                    
                    // Active days
                    Text(
                        text = "Active Days",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    DaysOfWeekIndicator(activeDays = profile.activeDaysOfWeek)
                }
            }
            
            // Expand indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HourlyHistogramChart(histogram: com.yuzi.odana.ml.HourlyHistogram) {
    val maxCount = histogram.hours.maxOrNull()?.toFloat() ?: 1f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        histogram.hours.forEachIndexed { hour, count ->
            val height = if (maxCount > 0) (count / maxCount) else 0f
            val isNightHour = hour < 6 || hour >= 22
            
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(height.coerceAtLeast(0.05f))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            if (isNightHour) Wisteria600.copy(alpha = 0.6f)
                            else Wisteria400
                        )
                )
            }
        }
    }
    
    // Hour labels
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Text("6", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Text("12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Text("18", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Text("24", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun RowScope.ProtocolBar(
    label: String,
    percent: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent / 100f)
                .background(color.copy(alpha = 0.7f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label $percent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun DaysOfWeekIndicator(activeDays: Int) {
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        dayLabels.forEachIndexed { index, label ->
            val isActive = (activeDays and (1 shl index)) != 0
            
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) Wisteria400.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

