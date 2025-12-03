package com.yuzi.odana.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzi.odana.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// GLASSMORPHISM CARD - The signature component
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == VioletAbyss
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            Wisteria400.copy(alpha = 0.3f),
                            Wisteria700.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            Wisteria300.copy(alpha = 0.5f),
                            Wisteria100.copy(alpha = 0.2f)
                        )
                    }
                ),
                shape = RoundedCornerShape(cornerRadius)
            ),
        color = if (isDark) {
            SurfaceDarkElevated.copy(alpha = 0.85f)
        } else {
            Color.White.copy(alpha = 0.9f)
        },
        shape = RoundedCornerShape(cornerRadius),
        tonalElevation = 0.dp,
        shadowElevation = if (isDark) 0.dp else 8.dp
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GRADIENT GLASS CARD - With animated gradient border
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun GradientGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    animated: Boolean = true,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val isDark = MaterialTheme.colorScheme.background == VioletAbyss
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .drawBehind {
                if (animated) {
                    drawAnimatedGradientBorder(rotation, cornerRadius.toPx())
                }
            }
    ) {
        Surface(
            modifier = Modifier
                .padding(2.dp)
                .clip(RoundedCornerShape(cornerRadius - 2.dp)),
            color = if (isDark) {
                SurfaceDarkElevated.copy(alpha = 0.95f)
            } else {
                Color.White.copy(alpha = 0.95f)
            },
            shape = RoundedCornerShape(cornerRadius - 2.dp)
        ) {
            content()
        }
    }
}

private fun DrawScope.drawAnimatedGradientBorder(rotation: Float, cornerRadius: Float) {
    val colors = listOf(
        Wisteria400,
        CyberPink,
        Wisteria600,
        CyberMint,
        Wisteria400
    )
    
    val angleRad = rotation * PI.toFloat() / 180f
    val startX = size.width / 2 + cos(angleRad) * size.width
    val startY = size.height / 2 + sin(angleRad) * size.height
    val endX = size.width / 2 - cos(angleRad) * size.width
    val endY = size.height / 2 - sin(angleRad) * size.height
    
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = colors,
            start = Offset(startX, startY),
            end = Offset(endX, endY)
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// STAT PILL - Compact stat display
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun StatPill(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROTOCOL BADGE - TCP/UDP indicator
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun ProtocolBadge(
    protocol: Int,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (protocol) {
        6 -> TcpColor to "TCP"
        17 -> UdpColor to "UDP"
        else -> Wisteria400 to "???"
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GLOW DOT - Animated status indicator
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun GlowDot(
    color: Color,
    size: Dp = 12.dp,
    isAnimated: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val glowAlpha = if (isAnimated) alpha else 1f
    
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                // Outer glow
                drawCircle(
                    color = color.copy(alpha = glowAlpha * 0.3f),
                    radius = this.size.minDimension / 2 * 1.5f
                )
            }
            .clip(CircleShape)
            .background(color.copy(alpha = glowAlpha))
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHIMMER MODIFIER - Loading effect
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun Modifier.shimmer(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    
    return this.drawBehind {
        val shimmerColors = listOf(
            Wisteria400.copy(alpha = 0f),
            Wisteria400.copy(alpha = 0.3f),
            Wisteria400.copy(alpha = 0f)
        )
        
        val brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(size.width * offset - size.width, 0f),
            end = Offset(size.width * offset, size.height)
        )
        
        drawRect(brush = brush)
    }
}

