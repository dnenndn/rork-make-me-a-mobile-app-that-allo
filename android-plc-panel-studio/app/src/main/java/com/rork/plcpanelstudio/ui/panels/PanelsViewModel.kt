package com.rork.plcpanelstudio.ui.panels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.Panel
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.data.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

data class PanelRow(
    val panel: Panel,
    val device: PlcDevice?
) {
    val tagCount: Int get() = panel.components.count { it.isWired }
    val status: DeviceStatus get() = device?.status ?: DeviceStatus.UNKNOWN
}

data class PanelsUiState(
    val rows: List<PanelRow> = emptyList(),
    val devices: List<PlcDevice> = emptyList()
)

class PanelsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkspaceRepository.get(application)

    private val _uiState = MutableStateFlow(PanelsUiState())
    val uiState: StateFlow<PanelsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.state, repository.tagValues) { state, _ -> state }
                .collect { state ->
                    _uiState.value = PanelsUiState(
                        rows = state.panels.map { panel ->
                            PanelRow(panel, state.devices.firstOrNull { it.id == panel.deviceId })
                        },
                        devices = state.devices
                    )
                }
        }
        // Resolve unknown device statuses once so panels show live colours immediately.
        viewModelScope.launch {
            repository.state.value.devices
                .filter { it.status == DeviceStatus.UNKNOWN }
                .forEach { repository.testDeviceHealth(it.id) }
        }
    }

    fun createPanel(
        name: String,
        description: String,
        deviceId: String?,
        cover: String
    ): String {
        val panel = Panel(
            id = "panel-${UUID.randomUUID()}",
            name = name.ifBlank { "Untitled Panel" },
            description = description,
            deviceId = deviceId,
            components = emptyList(),
            updatedAtMillis = System.currentTimeMillis(),
            cover = cover
        )
        repository.upsertPanel(panel)
        repository.setActivePanel(panel.id)
        return panel.id
    }

    fun openInEditor(panelId: String) {
        repository.setActivePanel(panelId)
    }

    fun deletePanel(panelId: String) {
        repository.deletePanel(panelId)
    }

    fun duplicatePanel(panelId: String) {
        val source = repository.panel(panelId) ?: return
        repository.upsertPanel(
            source.copy(
                id = "panel-${UUID.randomUUID()}",
                name = "${source.name} copy",
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }
}
