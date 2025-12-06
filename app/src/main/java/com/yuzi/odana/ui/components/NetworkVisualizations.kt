package com.yuzi.odana.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzi.odana.ui.theme.*
import kotlin.math.*
import kotlin.random.Random
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════════
// NETWORK PULSE - Central pulsing visualization showing activity
// Color changes based on alert severity (null = normal wisteria)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun NetworkPulse(
    activeConnections: Int,
    modifier: Modifier = Modifier,
    alertColor: Color? = null  // null = normal, red = HIGH, orange = MEDIUM
) {
    // Animated color transition for smooth severity changes
    val pulseColor by animateColorAsState(
        targetValue = alertColor ?: Wisteria400,
        animationSpec = tween(500),
        label = "pulseColor"
    )
    val coreColorLight by animateColorAsState(
        targetValue = alertColor?.copy(alpha = 0.8f) ?: Wisteria300,
        animationSpec = tween(500),
        label = "coreLight"
    )
    val coreColorDark by animateColorAsState(
        targetValue = alertColor ?: Wisteria500,
        animationSpec = tween(500),
        label = "coreDark"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Multiple pulse waves for layered effect
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    
    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse3"
    )
    
    // Core glow intensity based on activity
    val coreGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreGlow"
    )
    
    val activityMultiplier = (1f + (activeConnections.coerceAtMost(20) / 20f)).coerceAtMost(2f)
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2
            
            // Pulse rings - color based on alert severity
            listOf(pulse1, pulse2, pulse3).forEachIndexed { index, pulse ->
                val radius = maxRadius * 0.3f + (maxRadius * 0.7f * pulse)
                val alpha = (1f - pulse) * 0.5f * activityMultiplier
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            pulseColor.copy(alpha = alpha),
                            pulseColor.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
            
            // Core circle
            val coreRadius = maxRadius * 0.25f
            
            // Core glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        pulseColor.copy(alpha = coreGlow * 0.6f),
                        pulseColor.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = coreRadius * 2.5f
                ),
                radius = coreRadius * 2.5f,
                center = center
            )
            
            // Core solid - uses animated core colors
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColorLight,
                        coreColorDark
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )
            
            // Core highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = coreRadius * 0.3f,
                center = Offset(center.x - coreRadius * 0.2f, center.y - coreRadius * 0.2f)
            )
        }
        
        // Connection count in center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = activeConnections.toString(),
                style = DataDisplay,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 2.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BANDWIDTH WAVE - Animated waveform showing data flow
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun BandwidthWave(
    modifier: Modifier = Modifier,
    color: Color = CyberMint,
    waveCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        for (wave in 0 until waveCount) {
            val wavePhase = phase + (wave * PI.toFloat() / waveCount)
            val amplitude = height * 0.15f * (1f - wave * 0.2f)
            val alpha = 1f - (wave * 0.25f)
            
            val path = Path().apply {
                moveTo(0f, centerY)
                
                for (x in 0..width.toInt() step 4) {
                    val normalizedX = x / width * 4 * PI.toFloat()
                    val y = centerY + sin(normalizedX + wavePhase) * amplitude
                    lineTo(x.toFloat(), y)
                }
            }
            
            drawPath(
                path = path,
                color = color.copy(alpha = alpha * 0.8f),
                style = Stroke(
                    width = 3f - wave,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ORBITAL RING - Shows protocol distribution with orbiting particles
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun OrbitalRing(
    tcpPercent: Float,
    udpPercent: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 16
        val strokeWidth = 8f
        
        // Background ring
        drawCircle(
            color = Wisteria800.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        
        // TCP arc
        drawArc(
            color = TcpColor,
            startAngle = -90f,
            sweepAngle = 360f * tcpPercent,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // UDP arc
        drawArc(
            color = UdpColor,
            startAngle = -90f + 360f * tcpPercent,
            sweepAngle = 360f * udpPercent,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Orbiting particle
        rotate(rotation, center) {
            val particlePos = Offset(center.x + radius, center.y)
            
            // Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Wisteria300.copy(alpha = 0.6f),
                        Wisteria300.copy(alpha = 0f)
                    ),
                    center = particlePos,
                    radius = 20f
                ),
                radius = 20f,
                center = particlePos
            )
            
            // Particle
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = particlePos
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROGRESS BAR - Beautiful gradient progress
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Wisteria500, CyberMint),
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Int = 8
) {
    val animatedProgress = remember { Animatable(0f) }
    
    // Guard against NaN values (can happen with 0/0 division)
    val safeProgress = if (progress.isNaN() || progress.isInfinite()) 0f else progress.coerceIn(0f, 1f)
    
    LaunchedEffect(safeProgress) {
        animatedProgress.animateTo(
            targetValue = safeProgress,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    }
    
    Box(
        modifier = modifier
            .height(height.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress.value)
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(colors)
                )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SPARKLINE - Mini trend chart
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun Sparkline(
    data: List<Float>,
    color: Color = Wisteria400,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, tween(800))
    }
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val maxValue = data.maxOrNull() ?: 1f
        val minValue = data.minOrNull() ?: 0f
        val range = (maxValue - minValue).coerceAtLeast(1f)
        
        val stepX = width / (data.size - 1).coerceAtLeast(1)
        
        val path = Path().apply {
            data.forEachIndexed { index, value ->
                val x = index * stepX
                val normalizedY = (value - minValue) / range
                val y = height - (normalizedY * height * 0.8f) - height * 0.1f
                
                if (index == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x * animatedProgress.value + (1 - animatedProgress.value) * width / 2, y)
                }
            }
        }
        
        // Line
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
        
        // Gradient fill underneath
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width * animatedProgress.value, height)
            lineTo(0f, height)
            close()
        }
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.3f),
                    color.copy(alpha = 0f)
                )
            )
        )
    }
}


