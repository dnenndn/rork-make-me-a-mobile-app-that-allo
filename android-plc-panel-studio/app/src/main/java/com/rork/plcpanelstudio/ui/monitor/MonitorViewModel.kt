package com.rork.plcpanelstudio.ui.monitor

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.plcpanelstudio.data.BridgeResult
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.IoDirection
import com.rork.plcpanelstudio.data.Panel
import com.rork.plcpanelstudio.data.PanelComponent
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.data.TagReading
import com.rork.plcpanelstudio.data.WorkspaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MonitorViewModel"
private const val POLL_INTERVAL_MS = 700L

data class MonitorUiState(
    val panel: Panel? = null,
    val device: PlcDevice? = null,
    val values: Map<String, Int> = emptyMap(),
    val readings: List<TagReading> = emptyList(),
    val status: DeviceStatus = DeviceStatus.UNKNOWN,
    val live: Boolean = false,
    val lastUpdateMillis: Long? = null,
    val pingMs: Int? = null,
    val error: String? = null,
    val pressedIds: Set<String> = emptySet(),
    val forcedIds: Set<String> = emptySet()
)

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkspaceRepository.get(application)

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var panelId: String? = null

    fun start(id: String) {
        panelId = id
        refreshPanel()
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        _uiState.value = _uiState.value.copy(live = false)
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private fun refreshPanel() {
        val panel = repository.panel(panelId)
        val device = repository.device(panel?.deviceId)
        _uiState.value = _uiState.value.copy(panel = panel, device = device)
    }

    private suspend fun poll() {
        refreshPanel()
        val state = _uiState.value
        val panel = state.panel ?: return
        val device = state.device ?: run {
            _uiState.value = state.copy(
                live = false,
                status = DeviceStatus.UNKNOWN,
                error = "No PLC linked to this panel"
            )
            return
        }
        val addresses = panel.components
            .flatMap { it.allAddresses }
            .distinct()
        if (addresses.isEmpty()) {
            _uiState.value = state.copy(live = false, error = "No tags assigned yet")
            return
        }

        when (val result = repository.bridge.read(device, addresses)) {
            is BridgeResult.Success -> {
                repository.cacheValues(device.id, result.data)
                val merged = _uiState.value.values.toMutableMap().apply { putAll(result.data) }
                _uiState.value = _uiState.value.copy(
                    values = merged,
                    readings = buildReadings(panel, merged),
                    status = DeviceStatus.CONNECTED,
                    live = true,
                    lastUpdateMillis = System.currentTimeMillis(),
                    pingMs = result.latencyMs,
                    error = null
                )
                repository.upsertDevice(
                    device.copy(
                        status = DeviceStatus.CONNECTED,
                        pingMs = result.latencyMs,
                        lastSeenMillis = System.currentTimeMillis(),
                        lastError = null
                    )
                )
            }
            is BridgeResult.Failure -> {
                Log.w(TAG, "Read failed for ${device.endpoint}: ${result.reason}")
                _uiState.value = _uiState.value.copy(
                    status = DeviceStatus.OFFLINE,
                    live = false,
                    error = result.reason
                )
                repository.upsertDevice(
                    device.copy(status = DeviceStatus.OFFLINE, lastError = result.reason)
                )
            }
        }
    }

    private fun buildReadings(panel: Panel, values: Map<String, Int>): List<TagReading> =
        panel.components
            .flatMap { component ->
                if (component.hasPositionTags) {
                    // One reading per selector position input.
                    (0 until component.positionCount).mapNotNull { index ->
                        val address = component.positionAddress(index)
                        if (address.isBlank()) null else TagReading(
                            address = address,
                            value = values[address] ?: 0,
                            label = "${component.label} · P${index + 1}",
                            direction = IoDirection.INPUT
                        )
                    }
                } else if (component.tagAddress.isNotBlank()) {
                    listOf(
                        TagReading(
                            address = component.tagAddress,
                            value = values[component.tagAddress] ?: 0,
                            label = component.label,
                            direction = component.direction
                        )
                    )
                } else emptyList()
            }
            .distinctBy { it.address }

    /** Press-and-hold on a momentary input writes 1, release writes 0. */
    fun onPressDown(component: PanelComponent) {
        if (component.direction != IoDirection.INPUT) return
        markPressed(component.id, true)
        if (component.kind.isPushButton && component.momentary) {
            write(component, 1)
        }
    }

    /** The selector knob was turned to [position]; write it to the tag. */
    fun setSelector(component: PanelComponent, position: Int) {
        if (component.kind != ComponentKind.SELECTOR || component.direction != IoDirection.INPUT) return
        val target = position.coerceIn(0, component.positionCount - 1)
        if (component.hasPositionTags) {
            writeSelectorPosition(component, target)
        } else {
            // Legacy selector: a single tag holds the position number.
            write(component, target)
        }
    }

    /**
     * Selector with one input per position: raise the input of [target] and drop the others.
     * The other inputs are dropped first (break before make) so two positions are never
     * active at the same time on the PLC.
     */
    private fun writeSelectorPosition(component: PanelComponent, target: Int) {
        val device = _uiState.value.device ?: return
        val tags = (0 until component.positionCount).map { component.positionAddress(it) }
        if (tags.none { it.isNotBlank() }) return

        // Optimistic update so the knob and glow react instantly.
        val optimistic = _uiState.value.values.toMutableMap()
        tags.forEachIndexed { index, tag ->
            if (tag.isNotBlank()) optimistic[tag] = if (index == target) 1 else 0
        }
        val panel = _uiState.value.panel
        _uiState.value = _uiState.value.copy(
            values = optimistic,
            readings = panel?.let { buildReadings(it, optimistic) } ?: _uiState.value.readings
        )

        viewModelScope.launch {
            val writes = tags.withIndex()
                .filter { it.value.isNotBlank() }
                .sortedBy { if (it.index == target) 1 else 0 }
            for ((index, tag) in writes) {
                val value = if (index == target) 1 else 0
                when (val result = repository.bridge.write(device, tag, value)) {
                    is BridgeResult.Success -> repository.cacheValues(device.id, mapOf(tag to result.data))
                    is BridgeResult.Failure -> {
                        Log.w(TAG, "Write to $tag failed: ${result.reason}")
                        _uiState.value = _uiState.value.copy(error = "Write failed: ${result.reason}")
                        return@launch
                    }
                }
            }
        }
    }

    fun onPressUp(component: PanelComponent) {
        markPressed(component.id, false)
        if (component.direction != IoDirection.INPUT) return
        when {
            component.kind.isPushButton && component.momentary -> write(component, 0)
            component.kind.isPushButton -> {
                val next = if ((_uiState.value.values[component.tagAddress] ?: 0) > 0) 0 else 1
                write(component, next)
            }
            component.kind == ComponentKind.SELECTOR -> {
                val current = _uiState.value.values[component.tagAddress] ?: 0
                val next = (current + 1) % component.positions.coerceAtLeast(2)
                write(component, next)
            }
        }
    }

    /**
     * Force-writes a value directly to an output tag (lamp/gauge), bypassing the normal
     * input-only write restriction. Marks the component as "forced" so the UI can flag it —
     * useful for bench-testing wiring/logic without waiting on the real PLC program.
     * Note: this is a plain write, not a true PLC "force" — if the PLC's own program keeps
     * driving that address, the next scan cycle can overwrite it again.
     */
    fun forceWrite(component: PanelComponent, value: Int) {
        val device = _uiState.value.device ?: return
        if (component.tagAddress.isBlank()) return
        _uiState.value = _uiState.value.copy(forcedIds = _uiState.value.forcedIds + component.id)
        write(component, value)
    }

    /** Stops flagging a component as forced; the next poll shows whatever the PLC reports. */
    fun releaseForce(component: PanelComponent) {
        _uiState.value = _uiState.value.copy(forcedIds = _uiState.value.forcedIds - component.id)
    }

    private fun markPressed(id: String, pressed: Boolean) {
        val current = _uiState.value.pressedIds
        _uiState.value = _uiState.value.copy(
            pressedIds = if (pressed) current + id else current - id
        )
    }

    private fun write(component: PanelComponent, value: Int) {
        val device = _uiState.value.device ?: return
        if (component.tagAddress.isBlank()) return
        // Optimistic update so the glow reacts instantly to the touch.
        val optimistic = _uiState.value.values.toMutableMap()
            .apply { put(component.tagAddress, value) }
        val panel = _uiState.value.panel
        _uiState.value = _uiState.value.copy(
            values = optimistic,
            readings = panel?.let { buildReadings(it, optimistic) } ?: _uiState.value.readings
        )
        viewModelScope.launch {
            when (val result = repository.bridge.write(device, component.tagAddress, value)) {
                is BridgeResult.Success -> repository.cacheValues(
                    device.id,
                    mapOf(component.tagAddress to result.data)
                )
                is BridgeResult.Failure -> {
                    Log.w(TAG, "Write to ${component.tagAddress} failed: ${result.reason}")
                    _uiState.value = _uiState.value.copy(error = "Write failed: ${result.reason}")
                }
            }
        }
    }
}