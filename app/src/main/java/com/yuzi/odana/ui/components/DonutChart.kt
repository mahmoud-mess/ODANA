package com.yuzi.odana.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzi.odana.ui.theme.*

data class ChartData(
    val value: Float,
    val color: Color,
    val label: String
)

// ═══════════════════════════════════════════════════════════════════════════════
// ENHANCED DONUT CHART - With glow effects and smooth animations
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun DonutChart(
    data: List<ChartData>,
    centerText: String,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 48f,
    showGlow: Boolean = true
) {
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    
    // Animation for the chart drawing
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            )
        )
    }
    
    // Subtle rotation animation for glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowRotation"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
        ) {
            val canvasSize = size.minDimension
            val radius = (canvasSize - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)
            
            if (data.isEmpty() || total == 0f) {
                // Empty state - draw subtle ring
                drawArc(
                    color = Wisteria700.copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                return@Canvas
            }
            
            // Draw glow layer behind (subtle, rotating)
            if (showGlow) {
                rotate(glowRotation, center) {
                    var glowStartAngle = -90f
                    data.forEach { segment ->
                        val sweepAngle = (segment.value / total) * 360f
                        drawArc(
                            color = segment.color.copy(alpha = 0.15f),
                            startAngle = glowStartAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius - 8, center.y - radius - 8),
                            size = Size(radius * 2 + 16, radius * 2 + 16),
                            style = Stroke(width = strokeWidth + 16, cap = StrokeCap.Butt)
                        )
                        glowStartAngle += sweepAngle
                    }
                }
            }
            
            // Main chart segments
            var startAngle = -90f
            data.forEach { segment ->
                val sweepAngle = (segment.value / total) * 360f * animatedProgress.value
                
                // Segment
                drawArc(
                    color = segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Butt
                    )
                )
                
                startAngle += (segment.value / total) * 360f * animatedProgress.value
            }
            
            // Draw segment separators for crisp look
            startAngle = -90f
            data.forEach { segment ->
                val sweepAngle = (segment.value / total) * 360f
                if (sweepAngle > 5f) { // Only draw separator for visible segments
                    startAngle += sweepAngle
                }
            }
        }
        
        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = centerText,
                style = DataDisplaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MINI DONUT - Compact version for dashboard cards
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun MiniDonut(
    progress: Float, // 0f to 1f
    color: Color,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        )
    }
    
    Canvas(modifier = modifier.size(48.dp)) {
        val strokeWidth = 6.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        val arcSize = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        
        // Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Progress
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress.value,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
