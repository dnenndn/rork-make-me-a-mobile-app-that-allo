package com.rork.plcpanelstudio.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.plcpanelstudio.data.ComponentKind
import com.rork.plcpanelstudio.data.IoDirection
import com.rork.plcpanelstudio.data.Panel
import com.rork.plcpanelstudio.data.PanelComponent
import com.rork.plcpanelstudio.data.PlcDevice
import com.rork.plcpanelstudio.data.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/** Number of snap points across each canvas axis (1/40 steps feel free but land tidy). */
private const val SNAP_STEPS = 40

/** Clamps a normalized coordinate into 0..1 and snaps it to a fine grid. */
fun snap(value: Float): Float = (value.coerceIn(0f, 1f) * SNAP_STEPS).roundToInt() / SNAP_STEPS.toFloat()

data class EditorUiState(
    val panel: Panel? = null,
    val panels: List<Panel> = emptyList(),
    val devices: List<PlcDevice> = emptyList(),
    val device: PlcDevice? = null,
    val selectedId: String? = null,
    val dirty: Boolean = false,
    val message: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkspaceRepository.get(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var panelId: String? = null

    init {
        viewModelScope.launch {
            repository.state.collect { state ->
                val targetId = panelId ?: state.activePanelId ?: state.panels.firstOrNull()?.id
                panelId = targetId
                val panel = state.panels.firstOrNull { it.id == targetId }
                _uiState.value = _uiState.value.copy(
                    panel = panel,
                    panels = state.panels,
                    devices = state.devices,
                    device = state.devices.firstOrNull { it.id == panel?.deviceId }
                )
            }
        }
    }

    fun load(id: String?) {
        if (id == null || id == panelId) return
        panelId = id
        repository.setActivePanel(id)
        val panel = repository.panel(id)
        _uiState.value = _uiState.value.copy(
            panel = panel,
            device = repository.device(panel?.deviceId),
            selectedId = null,
            dirty = false
        )
    }

    fun select(componentId: String?) {
        _uiState.value = _uiState.value.copy(selectedId = componentId)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Places a brand new part at the given canvas position. */
    fun addComponent(kind: ComponentKind, x: Float, y: Float) {
        val panel = _uiState.value.panel ?: return
        val index = panel.components.count { it.kind == kind } + 1
        val component = PanelComponent(
            id = "cmp-${UUID.randomUUID()}",
            kind = kind,
            col = snap(x),
            row = snap(y),
            // Plate parts print their label on the plate, so use a neutral default name.
            label = when {
                kind.isPlateButton -> "Button $index"
                kind.isLamp -> "Lamp $index"
                else -> "${kind.displayName} $index"
            },
            tagAddress = "",
            direction = kind.defaultDirection,
            positions = if (kind == ComponentKind.SELECTOR) 2 else 1
        )
        mutate(panel.copy(components = panel.components + component))
        _uiState.value = _uiState.value.copy(selectedId = component.id)
    }

    /** Moves a placed part to a new canvas position. */
    fun moveComponent(componentId: String, x: Float, y: Float) {
        val panel = _uiState.value.panel ?: return
        val moving = panel.components.firstOrNull { it.id == componentId } ?: return
        val targetX = snap(x)
        val targetY = snap(y)
        if (moving.col == targetX && moving.row == targetY) return
        mutate(
            panel.copy(
                components = panel.components.map { component ->
                    if (component.id == componentId) component.copy(col = targetX, row = targetY) else component
                }
            )
        )
    }

    fun updateComponent(updated: PanelComponent) {
        val panel = _uiState.value.panel ?: return
        mutate(
            panel.copy(
                components = panel.components.map { if (it.id == updated.id) updated else it }
            )
        )
    }

    fun deleteComponent(componentId: String) {
        val panel = _uiState.value.panel ?: return
        mutate(panel.copy(components = panel.components.filterNot { it.id == componentId }))
        _uiState.value = _uiState.value.copy(selectedId = null)
    }

    fun renamePanel(name: String, description: String) {
        val panel = _uiState.value.panel ?: return
        mutate(panel.copy(name = name, description = description))
    }

    fun assignDevice(deviceId: String?) {
        val panel = _uiState.value.panel ?: return
        mutate(panel.copy(deviceId = deviceId))
    }

    /** Sets the cover picture shown for this panel in the panels list. */
    fun setCover(coverId: String) {
        val panel = _uiState.value.panel ?: return
        if (panel.cover == coverId) return
        mutate(panel.copy(cover = coverId))
    }

    fun save() {
        val panel = _uiState.value.panel ?: return
        repository.upsertPanel(panel.copy(updatedAtMillis = System.currentTimeMillis()))
        _uiState.value = _uiState.value.copy(dirty = false, message = "Panel saved")
    }

    fun switchPanel(id: String) {
        panelId = id
        repository.setActivePanel(id)
        val panel = repository.panel(id)
        _uiState.value = _uiState.value.copy(panel = panel, selectedId = null, dirty = false)
    }

    /** Suggests the next free address for a direction, e.g. M0.3 or Q0.1. */
    fun suggestAddress(direction: IoDirection, exclude: Set<String> = emptySet()): String {
        val panel = _uiState.value.panel ?: return if (direction == IoDirection.INPUT) "M0.0" else "Q0.0"
        val prefix = if (direction == IoDirection.INPUT) "M0." else "Q0."
        val used = (panel.components.flatMap { it.allAddresses } + exclude).mapNotNull { address ->
            address.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull()
        }.toSet()
        val next = (0..255).firstOrNull { it !in used } ?: 0
        return "$prefix$next"
    }

    private fun mutate(panel: Panel) {
        val stamped = panel.copy(updatedAtMillis = System.currentTimeMillis())
        repository.upsertPanel(stamped)
        _uiState.value = _uiState.value.copy(panel = stamped, dirty = false)
    }
}
