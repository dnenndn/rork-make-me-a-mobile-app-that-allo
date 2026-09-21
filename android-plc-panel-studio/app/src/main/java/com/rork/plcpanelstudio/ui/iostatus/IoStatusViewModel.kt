package com.rork.plcpanelstudio.ui.iostatus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.plcpanelstudio.data.BridgeResult
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.IoSections
import com.rork.plcpanelstudio.data.MemoryCheck
import com.rork.plcpanelstudio.data.Panel
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.data.WatchedTag
import com.rork.plcpanelstudio.data.WorkspaceRepository
import com.rork.plcpanelstudio.data.buildIoSections
import com.rork.plcpanelstudio.data.checkMemoryBit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 700L

data class IoStatusUiState(
    val panels: List<Panel> = emptyList(),
    val selectedPanelId: String? = null,
    val panel: Panel? = null,
    val device: PlcDevice? = null,
    val sections: IoSections = IoSections.EMPTY,
    val status: DeviceStatus = DeviceStatus.UNKNOWN,
    /** True while the PLC is answering; false shows every value as "not known". */
    val live: Boolean = false,
    val lastUpdateMillis: Long? = null,
    val error: String? = null
)

/**
 * Backs the I/O view: shows every input, output and watched memory bit of one panel with its
 * live value. It only reads from the PLC; nothing is written from this screen.
 */
class IoStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkspaceRepository.get(application)

    private val _uiState = MutableStateFlow(IoStatusUiState())
    val uiState: StateFlow<IoStatusUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var selectedId: String? = null
    private var values: Map<String, Int> = emptyMap()
    private var live = false
    private var status = DeviceStatus.UNKNOWN
    private var lastUpdate: Long? = null
    private var error: String? = null

    /** Starts (or restarts) reading. Call when the screen appears. */
    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            // Panels or devices edited elsewhere show up right away.
            launch { repository.state.collect { publish() } }
            while (isActive) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Stops reading. Call when the screen goes away. */
    fun stop() {
        job?.cancel()
        job = null
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    fun selectPanel(id: String) {
        if (id == selectedId) return
        selectedId = id
        values = emptyMap()
        live = false
        status = DeviceStatus.UNKNOWN
        error = null
        publish()
    }

    /**
     * Adds memory bit [body] ("byte.bit", the M is added here) to the selected panel.
     * Returns a message to show when it is refused, or null when it was added.
     */
    fun addMemory(body: String, name: String): String? {
        val panel = currentPanel() ?: return "Choose a panel first."
        return when (val check = checkMemoryBit(body, panel)) {
            is MemoryCheck.Error -> check.message
            is MemoryCheck.Ok -> {
                repository.addWatchedMemory(panel.id, WatchedTag(check.address, name.trim()))
                publish()
                null
            }
        }
    }

    fun removeMemory(address: String) {
        val panel = currentPanel() ?: return
        repository.removeWatchedMemory(panel.id, address)
        publish()
    }

    private fun currentPanel(): Panel? {
        val workspace = repository.state.value
        return workspace.panels.firstOrNull { it.id == selectedId }
            ?: workspace.panels.firstOrNull { it.id == workspace.activePanelId }
            ?: workspace.panels.firstOrNull()
    }

    private suspend fun poll() {
        val panel = currentPanel()
        selectedId = panel?.id
        if (panel == null) {
            publish()
            return
        }
        val device = repository.device(panel.deviceId)
        val addresses = buildIoSections(panel, emptyMap()).addresses
        when {
            device == null -> {
                values = emptyMap(); live = false; status = DeviceStatus.UNKNOWN
                error = "No PLC linked to this panel"
            }
            addresses.isEmpty() -> {
                values = emptyMap(); live = false; error = null
            }
            else -> {
                when (val result = repository.bridge.read(device, addresses)) {
                    is BridgeResult.Success -> {
                        // The user may have switched panel while the PLC was answering.
                        if (selectedId != panel.id) return
                        values = result.data; live = true; status = DeviceStatus.CONNECTED
                        lastUpdate = System.currentTimeMillis(); error = null
                    }
                    is BridgeResult.Failure -> {
                        if (selectedId != panel.id) return
                        values = emptyMap(); live = false; status = DeviceStatus.OFFLINE
                        error = result.reason
                    }
                }
            }
        }
        publish()
    }

    private fun publish() {
        val workspace = repository.state.value
        val panel = currentPanel()
        _uiState.value = IoStatusUiState(
            panels = workspace.panels,
            selectedPanelId = panel?.id,
            panel = panel,
            device = repository.device(panel?.deviceId),
            sections = if (panel == null) IoSections.EMPTY else buildIoSections(panel, values),
            status = status,
            live = live,
            lastUpdateMillis = lastUpdate,
            error = error
        )
    }
}
