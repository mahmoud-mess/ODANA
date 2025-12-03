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
    val selectedFlow: FlowEntity? = null // Full detail when selected
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
        viewModelScope.launch {
            FlowManager.activeFlowsState.collectLatest {
                if (_uiState.value.page == 0 && _uiState.value.searchQuery.isBlank()) {
                     // Optimization: Only auto-refresh on page 0 active
                     // To handle search on active items, we trigger loadData() but don't loop it here for now
                     // Actually, let's just simple refresh for now
                    if (_uiState.value.selectedFlow == null) {
                        loadData(refreshActive = true)
                    }
                }
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
                it.key.dstIp == summary.remoteIp && it.key.dstPort == summary.remotePort && it.key.protocol == summary.protocol
                // Note: ID doesn't exist for active flows yet
            }
            
            if (active != null) {
                val entity = FlowEntity(
                    id = 0, // Transient
                    timestamp = active.startTime,
                    appUid = active.appUid,
                    appName = active.appName,
                    remoteIp = active.key.dstIp,
                    remotePort = active.key.dstPort,
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
                val entity = flowDao.getFlowById(summary.id)
                _uiState.value = _uiState.value.copy(selectedFlow = entity)
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
                        (flow.key.dstIp.contains(query, true)) ||
                        (flow.detectedSni?.contains(query, true) == true) ||
                        (flow.key.dstPort.toString().contains(query))
                }
                filtered.map { flow ->
                    FlowSummary(
                        id = -1, // Indicator for active
                        timestamp = flow.startTime,
                        appUid = flow.appUid,
                        appName = flow.appName,
                        remoteIp = flow.key.dstIp,
                        remotePort = flow.key.dstPort,
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
