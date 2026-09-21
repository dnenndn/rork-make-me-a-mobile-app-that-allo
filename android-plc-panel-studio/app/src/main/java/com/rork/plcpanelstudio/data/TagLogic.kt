package com.rork.plcpanelstudio.data

/**
 * Holds the tag values shown on the Monitor screen and keeps them in step with the PLC.
 *
 * The screen shows what the PLC reports. Two things have to be handled so it never disagrees
 * with the PLC for longer than one poll:
 *  - When the operator writes a tag, the new value is shown at once ([beginWrite]) and replaced
 *    by the value the PLC confirms ([endWrite]).
 *  - A poll reply that was produced before one of our own writes reached the PLC must not put
 *    the old value back on screen. Such replies are ignored for that tag ([applyPoll]); the next
 *    poll, which starts after the write finished, shows the real PLC value.
 *
 * [clock] returns the time in milliseconds; it is injectable so the timing can be tested.
 */
class TagValueStore(private val clock: () -> Long = { System.currentTimeMillis() }) {

    private val current = mutableMapOf<String, Int>()
    private val lastWriteAt = mutableMapOf<String, Long>()
    private val inFlight = mutableMapOf<String, Int>()

    /** Snapshot of every value currently shown. */
    val values: Map<String, Int> get() = current.toMap()

    /** Current time from the store's clock; take it just before sending a poll request. */
    fun now(): Long = clock()

    /** A write of [value] to [address] is about to be sent: show it immediately. */
    fun beginWrite(address: String, value: Int) {
        inFlight[address] = (inFlight[address] ?: 0) + 1
        lastWriteAt[address] = clock()
        current[address] = value
    }

    /**
     * The write to [address] finished. [confirmed] is the value the PLC reported back, or null
     * if the write failed (the shown value then stays until the next poll corrects it).
     */
    fun endWrite(address: String, confirmed: Int?) {
        val left = (inFlight[address] ?: 1) - 1
        if (left <= 0) inFlight.remove(address) else inFlight[address] = left
        lastWriteAt[address] = clock()
        if (confirmed != null) current[address] = confirmed
    }

    /**
     * Applies a poll reply that was requested at [pollStartedAt]. Returns the values that were
     * actually taken over (replies for tags with a newer write are skipped).
     */
    fun applyPoll(polled: Map<String, Int>, pollStartedAt: Long): Map<String, Int> {
        val applied = mutableMapOf<String, Int>()
        for ((address, value) in polled) {
            if (address in inFlight) continue
            val writtenAt = lastWriteAt[address]
            if (writtenAt != null && writtenAt >= pollStartedAt) continue
            current[address] = value
            applied[address] = value
        }
        return applied
    }
}

/**
 * Ordered writes that move a selector with one input per position to [target]: the inputs of
 * the other positions are dropped first, then the input of [target] is raised (break before
 * make), so two positions are never active at the same time on the PLC.
 * Positions without an input (blank address) are skipped; if the target position has no input
 * the result only drops the others.
 */
fun selectorWritePlan(addresses: List<String>, target: Int): List<Pair<String, Int>> {
    val drops = addresses.withIndex()
        .filter { it.index != target && it.value.isNotBlank() }
        .map { it.value to 0 }
    val raise = addresses.getOrNull(target)
        ?.takeIf { it.isNotBlank() }
        ?.let { listOf(it to 1) }
        .orEmpty()
    return drops + raise
}
