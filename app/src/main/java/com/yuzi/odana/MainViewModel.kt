package com.yuzi.odana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuzi.odana.data.FlowDao
import com.yuzi.odana.data.FlowEntity
import com.yuzi.odana.data.FlowSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val items: List<FlowSummary> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedFlow: FlowEntity? = null, // Full detail when selected
    val activeFlowsCount: Int = 0,
    val totalDataTransferred: Long = 0
)

class MainViewModel(private val flowDao: FlowDao) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val appUsageStats = flowDao.getAppUsageStats()
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val pageSize = 50
    private var searchJob: Job? = null

    init {
        // Observe active flows for live updates
        viewModelScope.launch {
            FlowManager.activeFlowsState.collectLatest { activeFlows ->
                _uiState.value = _uiState.value.copy(activeFlowsCount = activeFlows.size)
                if (_uiState.value.page == 0 && _uiState.value.searchQuery.isBlank()) {
                    if (_uiState.value.selectedFlow == null) {
                        loadData(refreshActive = true)
                    }
                }
            }
        }
        
        // Observe flush completion to refresh data after VPN stops
        viewModelScope.launch {
            var wasFlusing = false
            FlowManager.isFlushing.collect { isFlushing ->
                if (wasFlusing && !isFlushing) {
                    // Flush just completed - reload to show persisted flows
                    delay(100) // Small delay to ensure DB writes are committed
                    loadData()
                }
                wasFlusing = isFlushing
            }
        }
        
        viewModelScope.launch {
            appUsageStats.collect { usageList ->
                val totalBytes = usageList.sumOf { it.totalBytes }
                _uiState.value = _uiState.value.copy(totalDataTransferred = totalBytes)
            }
        }
        loadData()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, page = 0)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadData()
        }
    }

    fun onPageChange(newPage: Int) {
        if (newPage < 0 || newPage >= _uiState.value.totalPages) return
        _uiState.value = _uiState.value.copy(page = newPage)
        loadData()
    }

    fun onClearStorage() {
        viewModelScope.launch {
            flowDao.deleteAll()
            loadData()
        }
    }
    
    fun onFlowSelected(summary: FlowSummary) {
        viewModelScope.launch {
            // 1. Check active flows first
            val active = FlowManager.activeFlowsState.value.find { 
                // Match key heuristic (IP/Port)
                it.key.destIp == summary.remoteIp && it.key.destPort == summary.remotePort && it.key.protocol == summary.protocol
                // Note: ID doesn't exist for active flows yet
            }
            
            if (active != null) {
                val entity = FlowEntity(
                    id = 0, // Transient
                    timestamp = active.startTime,
                    appUid = active.appUid,
                    appName = active.appName,
                    remoteIp = active.key.destIp,
                    remotePort = active.key.destPort,
                    protocol = active.key.protocol,
                    bytes = active.bytes,
                    packets = active.packets,
                    durationMs = active.lastUpdated - active.startTime,
                    sni = active.detectedSni,
                    payloadHex = active.getPayloadHex(),
                    payloadText = active.getPayloadText()
                )
                _uiState.value = _uiState.value.copy(selectedFlow = entity)
            } else {
                // 2. Fetch from DB
                try {
                    val entity = flowDao.getFlowById(summary.id)
                    _uiState.value = _uiState.value.copy(selectedFlow = entity)
                } catch (e: Exception) {
                    // Handle CursorWindow or other DB errors
                    val errorEntity = FlowEntity(
                        id = summary.id,
                        timestamp = summary.timestamp,
                        appUid = summary.appUid,
                        appName = summary.appName,
                        remoteIp = summary.remoteIp,
                        remotePort = summary.remotePort,
                        protocol = summary.protocol,
                        bytes = summary.bytes,
                        packets = summary.packets,
                        durationMs = summary.durationMs,
                        sni = summary.sni,
                        payloadHex = "Error loading payload: ${e.message}",
                        payloadText = null
                    )
                    _uiState.value = _uiState.value.copy(selectedFlow = errorEntity)
                }
            }
        }
    }
    
    fun onFlowDeselected() {
        _uiState.value = _uiState.value.copy(selectedFlow = null)
    }

    private fun loadData(refreshActive: Boolean = false) {
        viewModelScope.launch {
            // Avoid spinner flicker on background refresh
            if (!refreshActive) _uiState.value = _uiState.value.copy(isLoading = true)
            
            val query = _uiState.value.searchQuery
            val page = _uiState.value.page
            
            val totalHistoryCount = flowDao.countFlows(query)
            val historyItems = flowDao.searchFlows(query, pageSize, page * pageSize)
            
            // Active Items (Page 0 only)
            val activeSummaries = if (page == 0) {
                val rawActive = FlowManager.activeFlowsState.value
                val filtered = if (query.isBlank()) rawActive else rawActive.filter { flow ->
                        (flow.appName?.contains(query, true) == true) ||
                        (flow.key.destIp.contains(query, true)) ||
                        (flow.detectedSni?.contains(query, true) == true) ||
                        (flow.key.destPort.toString().contains(query))
                }
                filtered.map { flow ->
                    FlowSummary(
                        id = -1, // Indicator for active
                        timestamp = flow.startTime,
                        appUid = flow.appUid,
                        appName = flow.appName,
                        remoteIp = flow.key.destIp,
                        remotePort = flow.key.destPort,
                        protocol = flow.key.protocol,
                        bytes = flow.bytes,
                        packets = flow.packets,
                        durationMs = flow.lastUpdated - flow.startTime,
                        sni = flow.detectedSni
                    )
                }
            } else {
                emptyList()
            }

            val totalPages = if (totalHistoryCount == 0) 1 else (totalHistoryCount + pageSize - 1) / pageSize
            
            _uiState.value = _uiState.value.copy(
                items = activeSummaries + historyItems,
                totalPages = totalPages,
                isLoading = false
            )
        }
    }

    class Factory(private val dao: FlowDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(dao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
