package com.yuzi.odana

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzi.odana.data.BlockList
import com.yuzi.odana.data.FlowEntity
import com.yuzi.odana.data.FlowSummary
import com.yuzi.odana.ui.components.*
import com.yuzi.odana.ui.formatBytes
import com.yuzi.odana.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        FlowManager.initialize(applicationContext)
        val dao = FlowManager.db!!.flowDao()

        setContent {
            ODANATheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(dao)
                )
                AppNavigation(
                    viewModel = viewModel,
                    onStartVpn = { checkAndStartVpn() },
                    onStopVpn = { stopVpnService() }
                )
            }
        }
    }

    private fun checkAndStartVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, OdanaVpnService::class.java)
        intent.action = OdanaVpnService.ACTION_START
        startForegroundService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, OdanaVpnService::class.java)
        intent.action = OdanaVpnService.ACTION_STOP
        startService(intent)
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    if (uiState.selectedFlow != null) {
        FlowDetailScreen(
            flow = uiState.selectedFlow!!,
            onBack = { viewModel.onFlowDeselected() }
        )
    } else {
        MainTabScreen(viewModel, onStartVpn, onStopVpn)
    }
}

@Composable
fun MainTabScreen(
    viewModel: MainViewModel,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()
    
    // VPN state lifted here so it persists across tab switches
    // Initialize based on whether there are active flows (indicates VPN is running)
    var isVpnActive by remember { mutableStateOf(uiState.activeFlowsCount > 0) }
    
    // Sync with actual state - if we see active flows, VPN must be on
    LaunchedEffect(uiState.activeFlowsCount) {
        if (uiState.activeFlowsCount > 0) {
            isVpnActive = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ModernNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                activeConnections = uiState.activeFlowsCount
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel)
                1 -> MonitorScreen(
                    viewModel = viewModel,
                    isVpnActive = isVpnActive,
                    onVpnToggle = { active ->
                        isVpnActive = active
                        if (active) onStartVpn() else onStopVpn()
                    },
                    onFlowClick = { viewModel.onFlowSelected(it) }
                )
                2 -> StatsScreen(viewModel)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MODERN NAVIGATION BAR
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ModernNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    activeConnections: Int
) {
    val items = listOf(
        NavItem(Icons.Outlined.Dashboard, Icons.Filled.Dashboard, "Home"),
        NavItem(Icons.Outlined.Sensors, Icons.Filled.Sensors, "Monitor"),
        NavItem(Icons.Outlined.PieChart, Icons.Filled.PieChart, "Stats")
    )
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                ModernNavItem(
                    item = item,
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    badge = if (index == 1 && activeConnections > 0) activeConnections else null
                )
            }
        }
    }
}

private data class NavItem(
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
    val label: String
)

@Composable
private fun ModernNavItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    badge: Int? = null
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = tween(200),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) Wisteria500.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Icon(
                    imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                    contentDescription = item.label,
                    tint = if (selected) Wisteria400 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = animatedAlpha),
                    modifier = Modifier.size(24.dp)
                )
                
                // Badge
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-4).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(CyberPink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (badge > 99) "99+" else badge.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Wisteria400
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MONITOR SCREEN - Redesigned
// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    viewModel: MainViewModel,
    isVpnActive: Boolean,
    onVpnToggle: (Boolean) -> Unit,
    onFlowClick: (FlowSummary) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Monitor",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.items.size} flows",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Search button
                        IconButton(
                            onClick = { showSearch = !showSearch },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showSearch) Wisteria500.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = if (showSearch) Wisteria400 
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Clear button
                        IconButton(
                            onClick = { viewModel.onClearStorage() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // VPN Toggle
                        VpnToggleButton(
                            isActive = isVpnActive,
                            onToggle = onVpnToggle
                        )
                    }
                }
                
                // Search bar
                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { 
                            Text(
                                "Search IP, App, SNI...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ) 
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = Wisteria400
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Wisteria400,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                }
            }
            
            // Flow List
            if (uiState.isLoading && uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Wisteria400)
                }
            } else if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WifiOff,
                            contentDescription = null,
                            tint = Wisteria400.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No Flows Found",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Enable VPN to start monitoring",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.items) { flow ->
                        FlowCard(flow = flow, onClick = { onFlowClick(flow) })
                    }
                }
            }
            
            // Pagination
            if (uiState.totalPages > 1) {
                PaginationBar(
                    currentPage = uiState.page,
                    totalPages = uiState.totalPages,
                    onPageChange = { viewModel.onPageChange(it) }
                )
            }
        }
    }
}

@Composable
private fun VpnToggleButton(
    isActive: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vpn")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (isActive) {
                    Brush.radialGradient(
                        colors = listOf(
                            SuccessGreen.copy(alpha = pulseAlpha * 0.3f),
                            SuccessGreen.copy(alpha = 0.1f)
                        )
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            )
            .border(
                width = 2.dp,
                color = if (isActive) SuccessGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable { onToggle(!isActive) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isActive) Icons.Filled.Shield else Icons.Outlined.Shield,
            contentDescription = "VPN Toggle",
            tint = if (isActive) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onPageChange(currentPage - 1) },
                enabled = currentPage > 0
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous",
                    tint = if (currentPage > 0) Wisteria400 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            
            Text(
                text = "${currentPage + 1} / $totalPages",
                style = MonoMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            IconButton(
                onClick = { onPageChange(currentPage + 1) },
                enabled = currentPage < totalPages - 1
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next",
                    tint = if (currentPage < totalPages - 1) Wisteria400 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FLOW CARD - Modern design
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun FlowCard(flow: FlowSummary, onClick: () -> Unit) {
    val isActive = flow.id == -1L
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App icon with status ring
            Box(contentAlignment = Alignment.Center) {
                if (isActive) {
                    // Animated ring for active connections
                    val infiniteTransition = rememberInfiniteTransition(label = "active")
                    val ringAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0.8f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "ring"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = SuccessGreen.copy(alpha = ringAlpha),
                                shape = CircleShape
                            )
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        packageName = flow.appName,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            // Flow info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = flow.appName ?: "Unknown",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    ProtocolBadge(protocol = flow.protocol)
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Target
                Text(
                    text = "${flow.remoteIp}:${flow.remotePort}",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
                
                // SNI if available
                if (!flow.sni.isNullOrEmpty()) {
                    Text(
                        text = flow.sni,
                        style = MaterialTheme.typography.bodySmall,
                        color = Wisteria400,
                        maxLines = 1
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatChip(
                            icon = Icons.Outlined.SwapVert,
                            value = formatBytes(flow.bytes)
                        )
                        StatChip(
                            icon = Icons.Outlined.Layers,
                            value = "${flow.packets} pkt"
                        )
                    }
                    
                    Text(
                        text = formatTime(flow.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FLOW DETAIL SCREEN - Redesigned
// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowDetailScreen(flow: FlowEntity, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedTab by remember { mutableIntStateOf(0) }
    var isBlocked by remember { mutableStateOf(flow.appUid?.let { BlockList.isUidBlocked(it) } ?: false) }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (flow.appUid != null && flow.appUid != -1) {
                        IconButton(
                            onClick = {
                                BlockList.toggleBlockUid(flow.appUid)
                                isBlocked = !isBlocked
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isBlocked) ErrorRed.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Filled.Block else Icons.Outlined.Block,
                                contentDescription = if (isBlocked) "Unblock" else "Block",
                                tint = if (isBlocked) ErrorRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App header
                item {
                    GradientGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(flow.appName, Modifier.size(48.dp))
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = flow.appName ?: "Unknown",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            if (isBlocked) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ErrorRed.copy(alpha = 0.15f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Block,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "BLOCKED",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ErrorRed
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Connection details
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Connection Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            
                            DetailRow("Target", "${flow.remoteIp}:${flow.remotePort}")
                            DetailRow("Protocol", if (flow.protocol == 6) "TCP" else "UDP")
                            DetailRow("Timestamp", formatTime(flow.timestamp))
                            DetailRow("Duration", "${flow.durationMs} ms")
                            DetailRow("Data", "${formatBytes(flow.bytes)} (${flow.packets} packets)")
                            
                            if (!flow.sni.isNullOrEmpty()) {
                                DetailRow("SNI", flow.sni)
                            }
                        }
                    }
                }
                
                // Payload section
                if (!flow.payloadHex.isNullOrEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Payload Preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Tab selector
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    listOf("Hex", "Text").forEachIndexed { index, label ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (selectedTab == index) Wisteria500.copy(alpha = 0.2f)
                                                    else Color.Transparent
                                                )
                                                .clickable { selectedTab = index }
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedTab == index) Wisteria400 
                                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Payload content
                    val displayText = if (selectedTab == 0) flow.payloadHex else flow.payloadText
                    val lines = displayText?.split("\n") ?: emptyList()
                    
                    items(lines.take(100)) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                    
                    if (lines.size > 100) {
                        item {
                            Text(
                                text = "... and ${lines.size - 100} more lines",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                } else {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Outlined.DataArray,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Payload Captured",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MonoSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITIES
// ═══════════════════════════════════════════════════════════════════════════════
fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun AppIcon(packageName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, 
        key1 = packageName
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                if (packageName != null && 
                    !packageName.startsWith("UID:") && 
                    !packageName.startsWith("DualApp")) {
                    context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = "App Icon",
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = "Default Icon",
            modifier = modifier,
            tint = Wisteria400.copy(alpha = 0.7f)
        )
    }
}
