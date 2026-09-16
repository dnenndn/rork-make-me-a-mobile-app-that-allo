package com.rork.plcpanelstudio.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for panels, devices and the live tag cache.
 * Held as a process singleton so every screen observes the same state.
 */
class WorkspaceRepository private constructor(context: Context) {

    private val store = WorkspaceStore(context.applicationContext)
    private val simulator = PlcSimulator()
    val bridge = PlcBridgeClient(simulator)

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    /** Latest values keyed by "deviceId|address". */
    private val _tagValues = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tagValues: StateFlow<Map<String, Int>> = _tagValues.asStateFlow()

    fun panel(id: String?): Panel? = _state.value.panels.firstOrNull { it.id == id }

    fun device(id: String?): PlcDevice? = _state.value.devices.firstOrNull { it.id == id }

    fun upsertPanel(panel: Panel) {
        update { current ->
            val existing = current.panels.indexOfFirst { it.id == panel.id }
            val panels = if (existing >= 0) {
                current.panels.toMutableList().apply { set(existing, panel) }
            } else {
                current.panels + panel
            }
            current.copy(panels = panels)
        }
    }

    fun deletePanel(id: String) {
        update { current ->
            val panels = current.panels.filterNot { it.id == id }
            current.copy(
                panels = panels,
                activePanelId = if (current.activePanelId == id) panels.firstOrNull()?.id else current.activePanelId
            )
        }
    }

    fun setActivePanel(id: String) {
        update { it.copy(activePanelId = id) }
    }

    fun upsertDevice(device: PlcDevice) {
        update { current ->
            val existing = current.devices.indexOfFirst { it.id == device.id }
            val devices = if (existing >= 0) {
                current.devices.toMutableList().apply { set(existing, device) }
            } else {
                current.devices + device
            }
            current.copy(devices = devices)
        }
    }

    fun deleteDevice(id: String) {
        update { current ->
            current.copy(
                devices = current.devices.filterNot { it.id == id },
                panels = current.panels.map { panel ->
                    if (panel.deviceId == id) panel.copy(deviceId = null) else panel
                }
            )
        }
    }

    fun cacheValues(deviceId: String, values: Map<String, Int>) {
        _tagValues.value = _tagValues.value.toMutableMap().apply {
            values.forEach { (address, value) -> put(cacheKey(deviceId, address), value) }
        }
    }

    fun cachedValue(deviceId: String?, address: String): Int? =
        deviceId?.let { _tagValues.value[cacheKey(it, address)] }

    private fun cacheKey(deviceId: String, address: String) = "$deviceId|$address"

    /** Runs a health check and persists the resolved status so every screen sees it. */
    suspend fun testDeviceHealth(id: String) {
        val device = device(id) ?: return
        when (val result = bridge.health(device)) {
            is BridgeResult.Success -> upsertDevice(
                device.copy(
                    status = DeviceStatus.CONNECTED,
                    pingMs = result.latencyMs,
                    lastSeenMillis = System.currentTimeMillis(),
                    cpuPercent = result.data.cpu,
                    ioOk = result.data.ioOk,
                    ioTotal = result.data.ioTotal,
                    uptime = result.data.uptime,
                    lastError = null
                )
            )
            is BridgeResult.Failure -> upsertDevice(
                device.copy(
                    status = DeviceStatus.OFFLINE,
                    pingMs = null,
                    cpuPercent = null,
                    ioOk = null,
                    ioTotal = null,
                    uptime = null,
                    lastError = result.reason
                )
            )
        }
    }

    private fun update(transform: (WorkspaceState) -> WorkspaceState) {
        val next = transform(_state.value)
        _state.value = next
        store.save(next)
    }

    companion object {
        @Volatile
        private var instance: WorkspaceRepository? = null

        fun get(context: Context): WorkspaceRepository =
            instance ?: synchronized(this) {
                instance ?: WorkspaceRepository(context).also { instance = it }
            }
    }
}
