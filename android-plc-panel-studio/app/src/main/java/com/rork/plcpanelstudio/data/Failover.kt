package com.rork.plcpanelstudio.data

/**
 * Decides which of a device's addresses to try, and in what order.
 * Index 0 is the main address (normally the local network), index 1 the backup (for example
 * a Tailscale address).
 *
 * The main address is always tried first for a new device. Once only the backup answers, the
 * planner stays on the backup so every request does not first wait for a dead address, but it
 * looks at the main address again every [recheckMainAfterMs] so the connection moves back to
 * the local network as soon as it is reachable.
 */
class FailoverPlanner(
    private val recheckMainAfterMs: Long = 30_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private class State(var onBackup: Boolean = false, var mainTriedAt: Long = 0L)

    private val states = mutableMapOf<String, State>()

    /** Addresses to try for [deviceId], most preferred first. */
    @Synchronized
    fun order(deviceId: String, hasBackup: Boolean): List<Int> {
        if (!hasBackup) return listOf(0)
        val state = states[deviceId] ?: return listOf(0, 1)
        if (!state.onBackup) return listOf(0, 1)
        return if (clock() - state.mainTriedAt >= recheckMainAfterMs) listOf(0, 1) else listOf(1, 0)
    }

    /** Reports the outcome of trying address [index] of [deviceId]. */
    @Synchronized
    fun record(deviceId: String, index: Int, success: Boolean) {
        val state = states.getOrPut(deviceId) { State() }
        if (index == 0) state.mainTriedAt = clock()
        if (success) state.onBackup = index == 1
    }
}

/** Failure text when a device has two addresses: the main reason first, then the backup's. */
fun combineFailureReasons(main: String, backup: String?): String =
    if (backup == null) main else "$main (backup: $backup)"
