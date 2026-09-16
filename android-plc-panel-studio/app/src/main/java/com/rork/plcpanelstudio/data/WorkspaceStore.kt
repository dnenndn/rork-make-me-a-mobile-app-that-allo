package com.rork.plcpanelstudio.data

import android.content.Context
import kotlinx.serialization.json.Json

/** Persists panels and devices to SharedPreferences as JSON. */
class WorkspaceStore(context: Context) {

    private val prefs = context.getSharedPreferences("plc_panel_studio", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): WorkspaceState {
        val raw = prefs.getString(KEY_STATE, null) ?: return SeedData.initialState()
        return try {
            migrate(json.decodeFromString(WorkspaceState.serializer(), raw))
        } catch (error: Exception) {
            SeedData.initialState()
        }
    }

    /**
     * Panels saved before free placement stored 3x5 grid indices in col/row.
     * Values above 1.0 can only come from the old format, so convert them to
     * the normalized 0..1 coordinates the editor uses now.
     */
    private fun migrate(state: WorkspaceState): WorkspaceState {
        var changed = false
        val panels = state.panels.map { panel ->
            val isLegacy = panel.components.any { it.col > 1.001f || it.row > 1.001f }
            if (!isLegacy) {
                panel
            } else {
                changed = true
                panel.copy(
                    components = panel.components.map { component ->
                        component.copy(
                            col = ((component.col + 0.5f) / 3f).coerceIn(0f, 1f),
                            row = ((component.row + 0.5f) / 5f).coerceIn(0f, 1f)
                        )
                    }
                )
            }
        }
        return if (changed) state.copy(panels = panels) else state
    }

    fun save(state: WorkspaceState) {
        val raw = json.encodeToString(WorkspaceState.serializer(), state)
        prefs.edit().putString(KEY_STATE, raw).apply()
    }

    private companion object {
        const val KEY_STATE = "workspace_state_v1"
    }
}
