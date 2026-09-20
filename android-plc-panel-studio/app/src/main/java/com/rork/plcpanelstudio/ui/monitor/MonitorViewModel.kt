package com.rork.plcpanelstudio.ui.monitor

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.plcpanelstudio.data.BridgeResult
import com.rork.plcpanelstudio.data.ControlLock
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.data.DeviceStatus
import com.rork.plcpanelstudio.data.IoDirection
import com.rork.plcpanelstudio.data.Panel
import com.rork.plcpanelstudio.data.PanelComponent
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.data.TagReading
import com.rork.plcpanelstudio.data.TagValueStore
import com.rork.plcpanelstudio.data.WorkspaceRepository
import com.rork.plcpanelstudio.data.errorMessage
import com.rork.plcpanelstudio.data.selectorWritePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val forcedIds: Set<String> = emptySet(),
    /** True while the operator controls (buttons, selectors, force) may be used. */
    val controlUnlocked: Boolean = false,
    /** True once a control password has been created. */
    val hasControlPassword: Boolean = false
)

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkspaceRepository.get(application)

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    /** Values shown on screen: what the PLC reports, plus our own writes until the PLC confirms them. */
    private val tags = TagValueStore()

    /** Password protection for everything that writes to the PLC. */
    private val controlLock = ControlLock.get(application)

    private var pollJob: Job? = null
    private var panelId: String? = null

    init {
        publishControlState()
    }

    fun start(id: String) {
        panelId = id
        refreshPanel()
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                publishControlState() // also picks up the lock timing out by itself
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
        controlLock.lock()
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

        val pollStartedAt = tags.now()
        when (val result = repository.bridge.read(device, addresses)) {
            is BridgeResult.Success -> {
                // A reply that predates one of our own writes must not put the old value back.
                val applied = tags.applyPoll(result.data, pollStartedAt)
                repository.cacheValues(device.id, applied)
                val merged = tags.values
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
        // Locked: nothing is pressed and nothing is written.
        if (!canControl()) return
        markPressed(component.id, true)
        if (component.kind.isPushButton && component.momentary) {
            write(component, 1)
        }
    }

    /** The selector knob was turned to [position]; write it to the tag. */
    fun setSelector(component: PanelComponent, position: Int) {
        if (component.kind != ComponentKind.SELECTOR || component.direction != IoDirection.INPUT) return
        if (!canControl()) return
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
        if (!controlLock.isUnlocked) return
        val device = _uiState.value.device ?: return
        val plan = selectorWritePlan(
            (0 until component.positionCount).map { component.positionAddress(it) },
            target
        )
        if (plan.isEmpty()) return

        // Show the new position at once; each input then takes the value the PLC confirms.
        plan.forEach { (address, value) -> tags.beginWrite(address, value) }
        publishValues()

        viewModelScope.launch {
            val pending = plan.map { it.first }.toMutableList()
            try {
                for ((address, value) in plan) {
                    val result = repository.bridge.write(device, address, value)
                    pending.remove(address)
                    when (result) {
                        is BridgeResult.Success -> {
                            tags.endWrite(address, result.data)
                            publishValues()
                            repository.cacheValues(device.id, mapOf(address to result.data))
                        }
                        is BridgeResult.Failure -> {
                            tags.endWrite(address, null)
                            Log.w(TAG, "Write to $address failed: ${result.reason}")
                            _uiState.value = _uiState.value.copy(error = "Write failed: ${result.reason}")
                            return@launch
                        }
                    }
                }
            } finally {
                // Writes that never ran (after a failure) are no longer in flight.
                pending.forEach { tags.endWrite(it, null) }
            }
        }
    }

    fun onPressUp(component: PanelComponent) {
        val wasPressed = component.id in _uiState.value.pressedIds
        markPressed(component.id, false)
        if (component.direction != IoDirection.INPUT || !wasPressed) return
        when {
            // A momentary button that was pressed must always be released, even if the lock
            // timed out while it was held; otherwise the PLC input would stay at 1.
            component.kind.isPushButton && component.momentary -> write(component, 0, bypassLock = true)
            component.kind.isPushButton -> {
                if (!canControl()) return
                val next = if ((_uiState.value.values[component.tagAddress] ?: 0) > 0) 0 else 1
                write(component, next)
            }
            component.kind == ComponentKind.SELECTOR -> {
                if (!canControl()) return
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
        if (!canControl()) return
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

    private fun write(component: PanelComponent, value: Int, bypassLock: Boolean = false) {
        // Last line of defence: no write reaches the PLC while locked (except releasing a held button).
        if (!bypassLock && !controlLock.isUnlocked) return
        val device = _uiState.value.device ?: return
        val address = component.tagAddress
        if (address.isBlank()) return
        // Show the new value at once; the value the PLC confirms replaces it when the write returns.
        tags.beginWrite(address, value)
        publishValues()
        viewModelScope.launch {
            when (val result = repository.bridge.write(device, address, value)) {
                is BridgeResult.Success -> {
                    tags.endWrite(address, result.data)
                    publishValues()
                    repository.cacheValues(device.id, mapOf(address to result.data))
                }
                is BridgeResult.Failure -> {
                    tags.endWrite(address, null)
                    Log.w(TAG, "Write to $address failed: ${result.reason}")
                    _uiState.value = _uiState.value.copy(error = "Write failed: ${result.reason}")
                }
            }
        }
    }

    /** Pushes the values held in [tags] to the UI state. */
    private fun publishValues() {
        val state = _uiState.value
        val values = tags.values
        _uiState.value = state.copy(
            values = values,
            readings = state.panel?.let { buildReadings(it, values) } ?: state.readings
        )
    }

    // ------------------------------------------------------------------ control lock

    /** Checks the lock before a control is used; using a control keeps the session open. */
    private fun canControl(): Boolean {
        val allowed = controlLock.isUnlocked
        if (allowed) controlLock.touch()
        publishControlState()
        return allowed
    }

    private fun publishControlState() {
        val unlocked = controlLock.isUnlocked
        val hasPassword = controlLock.hasPassword
        val state = _uiState.value
        if (state.controlUnlocked != unlocked || state.hasControlPassword != hasPassword) {
            _uiState.value = state.copy(controlUnlocked = unlocked, hasControlPassword = hasPassword)
        }
    }

    /** Unlocks the controls. Returns an error message to show, or null when unlocked. */
    suspend fun unlockControl(password: String): String? {
        val result = withContext(Dispatchers.Default) { controlLock.unlock(password) }
        publishControlState()
        return result.errorMessage()
    }

    /** Creates the first password (and unlocks). Returns an error message, or null. */
    suspend fun createControlPassword(password: String, confirm: String): String? {
        val error = withContext(Dispatchers.Default) { controlLock.createPassword(password, confirm) }
        publishControlState()
        return error
    }

    /** Changes the password. Returns an error message, or null. */
    suspend fun changeControlPassword(current: String, new: String, confirm: String): String? {
        val error = withContext(Dispatchers.Default) { controlLock.changePassword(current, new, confirm) }
        publishControlState()
        return error
    }

    fun lockControl() {
        controlLock.lock()
        publishControlState()
    }
}
