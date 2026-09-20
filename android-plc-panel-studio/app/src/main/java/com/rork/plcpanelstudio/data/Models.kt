package com.rork.plcpanelstudio.data

import kotlinx.serialization.Serializable

/** The kind of industrial part placed on a panel. */
@Serializable
enum class ComponentKind {
    BUTTON,
    SELECTOR,
    LAMP,
    GAUGE,
    /** Red image-based lamp (same housing as [LAMP]). */
    LAMP_RED,
    /** Green image-based lamp (same housing as [LAMP]). */
    LAMP_GREEN,
    /** Red emergency-style push button (image-based). Behaves like a [BUTTON]. */
    STOP,
    /** Green push button in the same housing as [STOP]. Behaves like a [BUTTON]. */
    GREEN,
    /** Yellow push button in the same housing as [STOP]. Behaves like a [BUTTON]. */
    YELLOW;

    val displayName: String
        get() = when (this) {
            BUTTON -> "Button"
            SELECTOR -> "Selector"
            LAMP -> "Yellow Lamp"
            LAMP_RED -> "Red Lamp"
            LAMP_GREEN -> "Green Lamp"
            GAUGE -> "Gauge"
            STOP -> "Red Button"
            GREEN -> "Green Button"
            YELLOW -> "Yellow Button"
        }

    /** True for parts that are pressed like a push button (BUTTON and STOP). */
    val isPushButton: Boolean
        get() = this == BUTTON || this == STOP || this == GREEN || this == YELLOW

    /** True for the image-based buttons that carry a user label on their own plate. */
    val isPlateButton: Boolean
        get() = this == STOP || this == GREEN || this == YELLOW

    /** True for the image-based lamps (amber, red, green). */
    val isLamp: Boolean
        get() = this == LAMP || this == LAMP_RED || this == LAMP_GREEN

    /** True when the part prints the user's label on its own plate instead of a separate nameplate. */
    val hasPlateLabel: Boolean
        get() = isPlateButton || isLamp || this == SELECTOR

    /** Selectors and buttons drive PLC inputs, lamps and gauges display outputs. */
    val defaultDirection: IoDirection
        get() = when (this) {
            BUTTON, STOP, GREEN, YELLOW, SELECTOR -> IoDirection.INPUT
            LAMP, LAMP_RED, LAMP_GREEN, GAUGE -> IoDirection.OUTPUT
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
    val momentary: Boolean = true,
    /**
     * Selector only: the input address wired to each position (index 0 = position 1).
     * Turning the selector to a position sets that position's input to 1 and the others to 0.
     * A blank entry means that position has no input (e.g. a centre / off position).
     * When every entry is blank the selector falls back to the legacy single [tagAddress]
     * that holds the position number.
     */
    val positionTags: List<String> = emptyList()
) {
    /** Number of selector positions (2 or 3). */
    val positionCount: Int get() = positions.coerceIn(2, 3)

    /** Address wired to selector position [index], or "" when that position has no input. */
    fun positionAddress(index: Int): String = positionTags.getOrNull(index)?.trim().orEmpty()

    /** True when this selector has its own input per position. */
    val hasPositionTags: Boolean
        get() = kind == ComponentKind.SELECTOR && (0 until positionCount).any { positionAddress(it).isNotBlank() }

    /** Every PLC address this part reads or writes. */
    val allAddresses: List<String>
        get() = when {
            hasPositionTags -> (0 until positionCount).map { positionAddress(it) }.filter { it.isNotBlank() }
            tagAddress.isNotBlank() -> listOf(tagAddress)
            else -> emptyList()
        }

    /** True when the part is wired to at least one tag. */
    val isWired: Boolean get() = allAddresses.isNotEmpty()

    /**
     * True for the parts an operator pushes or turns: buttons and selectors used as inputs.
     * Lamps, gauges and parts set to "Output" only display and are never locked.
     */
    val isOperatorControl: Boolean
        get() = direction == IoDirection.INPUT && (kind.isPushButton || kind == ComponentKind.SELECTOR)

    /**
     * True when the part must not react to the operator right now. Every operator control is
     * locked until the password is entered, whether or not it already has a tag assigned.
     */
    fun isLocked(controlUnlocked: Boolean): Boolean = isOperatorControl && !controlUnlocked

    /** Short text listing the part's addresses, e.g. "M0.1 · M0.2". */
    val tagSummary: String get() = allAddresses.joinToString(" · ")
}

@Serializable
data class Panel(
    val id: String,
    val name: String,
    val description: String,
    val deviceId: String?,
    val components: List<PanelComponent>,
    val updatedAtMillis: Long,
    /** Id of the cover picture shown in the panels list (see ui.components.PANEL_COVERS). */
    val cover: String = "logo"
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
