package com.rork.plcpanelstudio.data

import kotlinx.serialization.Serializable

/** The kind of industrial part placed on a panel. */
@Serializable
enum class ComponentKind {
    SELECTOR,
    LAMP,
    /** Red image-based lamp (same housing as [LAMP]). */
    LAMP_RED,
    /** Green image-based lamp (same housing as [LAMP]). */
    LAMP_GREEN,
    /** Red emergency-style push button (image-based). */
    STOP,
    /** Green push button in the same housing as [STOP]. */
    GREEN,
    /** Yellow push button in the same housing as [STOP]. */
    YELLOW;

    val displayName: String
        get() = when (this) {
            SELECTOR -> "Selector"
            LAMP -> "Yellow Lamp"
            LAMP_RED -> "Red Lamp"
            LAMP_GREEN -> "Green Lamp"
            STOP -> "Red Button"
            GREEN -> "Green Button"
            YELLOW -> "Yellow Button"
        }

    /** True for the three push buttons: Stop (red), Green and Yellow. */
    val isPushButton: Boolean
        get() = this == STOP || this == GREEN || this == YELLOW

    /** True for the image-based buttons that carry a user label on their own plate. */
    val isPlateButton: Boolean
        get() = this == STOP || this == GREEN || this == YELLOW

    /** True for the image-based lamps (amber, red, green). */
    val isLamp: Boolean
        get() = this == LAMP || this == LAMP_RED || this == LAMP_GREEN

    /** True when the part prints the user's label on its own plate instead of a separate nameplate. */
    val hasPlateLabel: Boolean
        get() = isPlateButton || isLamp || this == SELECTOR

    /**
     * Buttons and selectors always write to the PLC; lamps always only display it. This is
     * fixed by what the part is, not a choice the user makes (see [com.rork.plcpanelstudio.data.fixedDirectionFor]).
     */
    val defaultDirection: IoDirection
        get() = if (isLamp) IoDirection.OUTPUT else IoDirection.INPUT
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
    val cover: String = "logo",
    /** Memory bits (M area) the user added to watch in the I/O view, next to the panel's own tags. */
    val watchedMemory: List<WatchedTag> = emptyList()
)

/** A memory bit added by hand to a panel's I/O view, with an optional name, e.g. M0.5 "Cycle done". */
@Serializable
data class WatchedTag(
    val address: String,
    val name: String = ""
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
    val lastError: String? = null,
    /** Optional second address (e.g. a Tailscale address), tried when the main one does not answer. */
    val fallbackHost: String = "",
    /** Port of the backup address; 0 means the same port as the main address. */
    val fallbackPort: Int = 0,
    /** True while the last successful connection went through the backup address. */
    val usingBackup: Boolean = false
) {
    val endpoint: String get() = "$host:$port"

    val hasBackup: Boolean get() = fallbackHost.isNotBlank()

    val backupEndpoint: String get() = "$fallbackHost:${if (fallbackPort > 0) fallbackPort else port}"

    /** Addresses to try: the main one first, then the backup if there is one. */
    fun endpoints(): List<Endpoint> = buildList {
        add(Endpoint(host, port))
        if (hasBackup) add(Endpoint(fallbackHost, if (fallbackPort > 0) fallbackPort else port))
    }
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
