package com.rork.plcpanelstudio.ui.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.data.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class DevicesUiState(
    val devices: List<PlcDevice> = emptyList(),
    val testingIds: Set<String> = emptySet(),
    val message: String? = null
)

class DevicesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkspaceRepository.get(application)

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { state ->
                _uiState.value = _uiState.value.copy(devices = state.devices)
            }
        }
        refreshAll()
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun addDevice(name: String, host: String, port: Int, simulated: Boolean) {
        val device = PlcDevice(
            id = "dev-${UUID.randomUUID()}",
            name = name.ifBlank { "PLC $host" },
            host = host.trim(),
            port = port,
            simulated = simulated,
            status = DeviceStatus.UNKNOWN
        )
        repository.upsertDevice(device)
        testConnection(device.id)
    }

    fun updateDevice(device: PlcDevice) {
        repository.upsertDevice(device)
    }

    fun deleteDevice(id: String) {
        repository.deleteDevice(id)
    }

    fun refreshAll() {
        _uiState.value.devices.forEach { testConnection(it.id) }
        if (_uiState.value.devices.isEmpty()) {
            viewModelScope.launch {
                repository.state.value.devices.forEach { testConnection(it.id) }
            }
        }
    }

    fun testConnection(id: String) {
        if (repository.device(id) == null) return
        _uiState.value = _uiState.value.copy(testingIds = _uiState.value.testingIds + id)
        viewModelScope.launch {
            repository.testDeviceHealth(id)
            _uiState.value = _uiState.value.copy(testingIds = _uiState.value.testingIds - id)
        }
    }
}
