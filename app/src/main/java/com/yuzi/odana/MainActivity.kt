package com.yuzi.odana

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.yuzi.odana.ui.theme.ODANATheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.List
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Monitor") },
                    label = { Text("Monitor") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "Stats") },
                    label = { Text("Stats") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) {
                MainScreen(
                    viewModel = viewModel,
                    onStartVpn = onStartVpn,
                    onStopVpn = onStopVpn,
                    onFlowClick = { viewModel.onFlowSelected(it) }
                )
            } else {
                StatsScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onFlowClick: (FlowSummary) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isVpnActive by remember { mutableStateOf(false) } 

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("ODANA Monitor") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.onClearStorage() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History")
                        }
                        
                        Switch(
                            checked = isVpnActive,
                            onCheckedChange = { active ->
                                isVpnActive = active
                                if (active) onStartVpn() else onStopVpn()
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                )
                
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    placeholder = { Text("Search IP, App, SNI, Port...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.onPageChange(uiState.page - 1) },
                        enabled = uiState.page > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev")
                    }
                    
                    Text("Page ${uiState.page + 1} / ${uiState.totalPages}")
                    
                    IconButton(
                        onClick = { viewModel.onPageChange(uiState.page + 1) },
                        enabled = uiState.page < uiState.totalPages - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading && uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching flows found.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.items) { flow ->
                        FlowItem(flow, onClick = { onFlowClick(flow) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowDetailScreen(flow: FlowEntity, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Hex, 1: Text
    val tabs = listOf("Hex Dump", "Text View")
    
    var isBlocked by remember { mutableStateOf(flow.appUid?.let { BlockList.isUidBlocked(it) } ?: false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flow Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (flow.appUid != null && flow.appUid != -1) {
                        IconButton(onClick = {
                            BlockList.toggleBlockUid(flow.appUid)
                            isBlocked = !isBlocked
                        }) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = if (isBlocked) "Unblock App" else "Block App",
                                tint = if (isBlocked) Color.Red else Color.Gray
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(flow.appName, Modifier.size(48.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = flow.appName ?: "Unknown", 
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (isBlocked) {
                            Text("BLOCKED", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Target: ${flow.remoteIp}:${flow.remotePort}", style = MaterialTheme.typography.bodyLarge)
                Text("Protocol: ${if (flow.protocol == 6) "TCP" else "UDP"}", style = MaterialTheme.typography.bodyLarge)
                Text("Time: ${formatTime(flow.timestamp)}", style = MaterialTheme.typography.bodyLarge)
                Text("Duration: ${flow.durationMs} ms", style = MaterialTheme.typography.bodyLarge)
                Text("Stats: ${flow.packets} pkts, ${flow.bytes} bytes", style = MaterialTheme.typography.bodyLarge)
                
                if (!flow.sni.isNullOrEmpty()) {
                    Text("SNI: ${flow.sni}", style = MaterialTheme.typography.bodyLarge)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Payload Capture (Max 1MB):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (flow.payloadHex.isNullOrEmpty()) {
                    Text("No Payload Captured", color = Color.Gray)
                } else {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val displayText = if (selectedTab == 0) flow.payloadHex else flow.payloadText
                    
                    Text(
                        text = displayText ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FlowItem(flow: FlowSummary, onClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = flow.appName,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 12.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = flow.appName ?: "Unknown App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (flow.protocol == 6) "TCP" else "UDP",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (flow.protocol == 6) Color(0xFF00695C) else Color(0xFFAD1457), 
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${flow.remoteIp}:${flow.remotePort}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (!flow.sni.isNullOrEmpty()) {
                    Text(
                        text = "SNI: ${flow.sni}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${flow.packets} pkts • ${formatBytes(flow.bytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = formatTime(flow.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun AppIcon(packageName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                if (packageName != null && !packageName.startsWith("UID:") && !packageName.startsWith("DualApp")) {
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
            imageVector = Icons.Default.Info,
            contentDescription = "Default Icon",
            modifier = modifier,
            tint = Color.Gray
        )
    }
}