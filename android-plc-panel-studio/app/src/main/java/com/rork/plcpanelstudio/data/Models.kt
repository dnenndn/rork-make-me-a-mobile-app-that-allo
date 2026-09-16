package com.rork.plcpanelstudio.data

import kotlinx.serialization.Serializable

/** The kind of industrial part placed on a panel. */
@Serializable
enum class ComponentKind {
    BUTTON,
    SELECTOR,
    LAMP,
    GAUGE;

    val displayName: String
        get() = when (this) {
            BUTTON -> "Button"
            SELECTOR -> "Selector"
            LAMP -> "Lamp"
            GAUGE -> "Gauge"
        }

    /** Selectors and buttons drive PLC inputs, lamps and gauges display outputs. */
    val defaultDirection: IoDirection
        get() = when (this) {
            BUTTON, SELECTOR -> IoDirection.INPUT
            LAMP, GAUGE -> IoDirection.OUTPUT
        }
}

/** Whether a tag is written by the panel (input) or only read back (output). */
@Serializable
enum class IoDirection {
    INPUT,
    OUTPUT;

    val displayName: String
        get() = if (this == INPUT) "Input" else "Output"
}

@Serializable
data class PanelComponent(
    val id: String,
    val kind: ComponentKind,
    /** Normalized horizontal position (0..1) of the part's center on the panel canvas. */
    val col: Float,
    /** Normalized vertical position (0..1) of the part's center on the panel canvas. */
    val row: Float,
    val label: String,
    val tagAddress: String,
    val direction: IoDirection,
    /** Number of stable positions for a selector switch (2 or 3). */
    val positions: Int = 2,
    /** Full-scale value used to render a gauge. */
    val scaleMax: Int = 100,
    /** A momentary button returns to 0 on release; a maintained one latches. */
    val momentary: Boolean = true
)

@Serializable
data class Panel(
    val id: String,
    val name: String,
    val description: String,
    val deviceId: String?,
    val components: List<PanelComponent>,
    val updatedAtMillis: Long
)

@Serializable
enum class DeviceStatus {
    CONNECTED,
    OFFLINE,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            CONNECTED -> "Connected"
            OFFLINE -> "Offline"
            UNKNOWN -> "Unknown"
        }
}

/**
 * A PLC reachable through the LAN bridge server.
 * [simulated] devices are served by the built-in demo driver so the app is usable
 * without a bridge on the network.
 */
@Serializable
data class PlcDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val simulated: Boolean,
    val status: DeviceStatus = DeviceStatus.UNKNOWN,
    val pingMs: Int? = null,
    val lastSeenMillis: Long? = null,
    val cpuPercent: Int? = null,
    val ioOk: Int? = null,
    val ioTotal: Int? = null,
    val uptime: String? = null,
    val lastError: String? = null
) {
    val endpoint: String get() = "$host:$port"
}

@Serializable
data class WorkspaceState(
    val panels: List<Panel> = emptyList(),
    val devices: List<PlcDevice> = emptyList(),
    val activePanelId: String? = null
)

/** A single live reading pulled from the bridge server. */
data class TagReading(
    val address: String,
    val value: Int,
    val label: String,
    val direction: IoDirection
)
