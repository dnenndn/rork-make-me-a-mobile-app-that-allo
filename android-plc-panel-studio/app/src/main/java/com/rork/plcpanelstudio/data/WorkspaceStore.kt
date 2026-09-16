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
            json.decodeFromString(WorkspaceState.serializer(), raw)
        } catch (error: Exception) {
            SeedData.initialState()
        }
    }

    fun save(state: WorkspaceState) {
        val raw = json.encodeToString(WorkspaceState.serializer(), state)
        prefs.edit().putString(KEY_STATE, raw).apply()
    }

    private companion object {
        const val KEY_STATE = "workspace_state_v1"
    }
}
