package com.yuzi.odana

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzi.odana.ui.theme.*
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * ONBOARDING SCREEN
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * First-time user onboarding flow explaining what ODANA does and requesting
 * necessary permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    advanceFromVpnPage: Boolean = false,
    onVpnPageAdvanced: () -> Unit = {},
    notificationPermissionHandled: Boolean = false,
    onNotificationHandled: () -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    
    // Auto-advance when VPN permission is granted
    LaunchedEffect(advanceFromVpnPage) {
        if (advanceFromVpnPage && pagerState.currentPage == 2) {
            pagerState.animateScrollToPage(3)
            onVpnPageAdvanced()
        }
    }
    
    // Auto-complete when notification permission is handled
    LaunchedEffect(notificationPermissionHandled) {
        if (notificationPermissionHandled && pagerState.currentPage == 3) {
            onNotificationHandled()
            onComplete()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> HowItWorksPage()
                    2 -> VpnPermissionPage(onRequestPermission = onRequestVpnPermission)
                    3 -> NotificationPermissionPage()
                }
            }
            
            // Bottom section with indicators and buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 24.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) Wisteria400
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                )
                        )
                    }
                }
                
                // Navigation buttons
                when (pagerState.currentPage) {
                    0, 1 -> {
                        // Next button
                        Button(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Wisteria500
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                    2 -> {
                        // VPN permission button - page advances via callback when permission granted
                        Button(
                            onClick = onRequestVpnPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Wisteria500
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Filled.VpnKey, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enable VPN Access",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    3 -> {
                        // Notification permission buttons
                        Button(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Wisteria500
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enable Notifications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextButton(onClick = onComplete) {
                            Text(
                                text = "Skip for now",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated logo placeholder
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Wisteria400.copy(alpha = 0.3f),
                            Wisteria600.copy(alpha = 0.1f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = Wisteria400,
                modifier = Modifier.size(72.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Welcome to ODANA",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Your AI-powered network guardian",
            style = MaterialTheme.typography.titleMedium,
            color = Wisteria400,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "ODANA uses on-device AI to monitor your network traffic and detect suspicious activity — all without sending any data to the cloud.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}

@Composable
private fun HowItWorksPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "How It Works",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Step 1
        OnboardingStep(
            number = 1,
            icon = Icons.Outlined.Apps,
            title = "Monitor Traffic",
            description = "ODANA sees which apps connect to the internet and where"
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Step 2
        OnboardingStep(
            number = 2,
            icon = Icons.Outlined.Psychology,
            title = "AI Learns Patterns",
            description = "Our AI learns each app's normal behavior over time"
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Step 3
        OnboardingStep(
            number = 3,
            icon = Icons.Outlined.NotificationsActive,
            title = "Alerts You",
            description = "Get notified when something looks suspicious"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Privacy note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SuccessGreen.copy(alpha = 0.1f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "100% on-device. Your data never leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = SuccessGreen,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OnboardingStep(
    number: Int,
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Number badge
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Wisteria500.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Wisteria400,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun VpnPermissionPage(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // VPN icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Wisteria500.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.VpnKey,
                contentDescription = null,
                tint = Wisteria400,
                modifier = Modifier.size(56.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "VPN Permission",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "ODANA needs VPN access to monitor network traffic. This creates a local VPN on your device — no external servers involved.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // What we DON'T do
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "We never:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            
            PermissionNote(text = "Read your messages or passwords", isNegative = true)
            PermissionNote(text = "Send data to external servers", isNegative = true)
            PermissionNote(text = "Slow down your connection", isNegative = true)
        }
    }
}

@Composable
private fun PermissionNote(text: String, isNegative: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isNegative) Icons.Filled.Close else Icons.Filled.Check,
            contentDescription = null,
            tint = if (isNegative) ErrorRed else SuccessGreen,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun NotificationPermissionPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Notification icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(CyberGold.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = CyberGold,
                modifier = Modifier.size(56.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Stay Informed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Get notified when ODANA detects suspicious network activity on your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}
