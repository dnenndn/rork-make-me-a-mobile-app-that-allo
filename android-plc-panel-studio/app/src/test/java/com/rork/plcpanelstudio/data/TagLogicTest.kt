package com.rork.plcpanelstudio.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagLogicTest {

    private class Clock(var now: Long = 1_000L) {
        fun tick(ms: Long) { now += ms }
    }

    // ---------------------------------------------------------------- reading the PLC

    @Test
    fun poll_showsWhatThePlcReports() {
        val store = TagValueStore()
        store.applyPoll(mapOf("M0.1" to 1, "Q0.1" to 0), pollStartedAt = 0L)
        assertEquals(1, store.values["M0.1"])
        assertEquals(0, store.values["Q0.1"])
        // Input reset in the PLC -> the display follows on the next poll.
        store.applyPoll(mapOf("M0.1" to 0), pollStartedAt = 1L)
        assertEquals(0, store.values["M0.1"])
    }

    // ---------------------------------------------------------------- writing (button / selector)

    @Test
    fun write_isShownImmediately() {
        val store = TagValueStore()
        store.beginWrite("M0.3", 1)
        assertEquals(1, store.values["M0.3"])
    }

    @Test
    fun poll_thatWasSentBeforeTheWrite_doesNotFlipTheValueBack() {
        val clock = Clock()
        val store = TagValueStore { clock.now }
        val pollStartedAt = store.now()          // poll request goes out with the old value
        clock.tick(50)
        store.beginWrite("M0.3", 1)              // operator presses the button
        clock.tick(50)
        store.applyPoll(mapOf("M0.3" to 0), pollStartedAt)   // old reply arrives while writing
        assertEquals(1, store.values["M0.3"])
        clock.tick(50)
        store.endWrite("M0.3", confirmed = 1)
        clock.tick(50)
        store.applyPoll(mapOf("M0.3" to 0), pollStartedAt)   // ... or right after it finished
        assertEquals(1, store.values["M0.3"])
    }

    @Test
    fun poll_sentAfterTheWriteFinished_isTrusted() {
        val clock = Clock()
        val store = TagValueStore { clock.now }
        store.beginWrite("M0.3", 1)
        clock.tick(100)
        store.endWrite("M0.3", confirmed = 1)
        clock.tick(100)
        val pollStartedAt = store.now()
        clock.tick(50)
        // The PLC program reset the input in the meantime: the display must follow the PLC.
        store.applyPoll(mapOf("M0.3" to 0), pollStartedAt)
        assertEquals(0, store.values["M0.3"])
    }

    @Test
    fun poll_duringAWriteInFlight_isIgnoredEvenIfItStartedLater() {
        val clock = Clock()
        val store = TagValueStore { clock.now }
        store.beginWrite("M0.3", 1)
        clock.tick(30)
        val pollStartedAt = store.now()          // started after the write was sent, before it finished
        clock.tick(30)
        store.applyPoll(mapOf("M0.3" to 0), pollStartedAt)
        assertEquals(1, store.values["M0.3"])
    }

    @Test
    fun confirmedValueFromThePlc_replacesTheGuess() {
        val store = TagValueStore()
        store.beginWrite("M0.3", 1)
        store.endWrite("M0.3", confirmed = 0)    // PLC refused / forced it back
        assertEquals(0, store.values["M0.3"])
    }

    @Test
    fun failedWrite_isCorrectedByTheNextPoll() {
        val clock = Clock()
        val store = TagValueStore { clock.now }
        store.beginWrite("M0.3", 1)
        clock.tick(100)
        store.endWrite("M0.3", confirmed = null)
        clock.tick(10)
        val pollStartedAt = store.now()
        store.applyPoll(mapOf("M0.3" to 0), pollStartedAt)
        assertEquals(0, store.values["M0.3"])
    }

    @Test
    fun otherTags_areNotHeldBackByAWrite() {
        val clock = Clock()
        val store = TagValueStore { clock.now }
        val pollStartedAt = store.now()
        clock.tick(10)
        store.beginWrite("M0.3", 1)
        store.applyPoll(mapOf("M0.3" to 0, "Q0.1" to 1), pollStartedAt)
        assertEquals(1, store.values["M0.3"])
        assertEquals(1, store.values["Q0.1"])    // lamp output still follows the PLC
    }

    // ---------------------------------------------------------------- selector

    @Test
    fun selectorPlan_dropsOthersThenRaisesTarget() {
        assertEquals(
            listOf("M0.0" to 0, "M0.2" to 0, "M0.1" to 1),
            selectorWritePlan(listOf("M0.0", "M0.1", "M0.2"), target = 1)
        )
        assertEquals(
            listOf("M0.1" to 0, "M0.0" to 1),
            selectorWritePlan(listOf("M0.0", "M0.1"), target = 0)
        )
    }

    @Test
    fun selectorPlan_positionWithoutInput_onlyDropsTheOthers() {
        assertEquals(
            listOf("M0.0" to 0, "M0.2" to 0),
            selectorWritePlan(listOf("M0.0", "", "M0.2"), target = 1)
        )
    }

    @Test
    fun selectorPlan_skipsBlankAddresses() {
        assertEquals(
            listOf("M0.2" to 0, "M0.0" to 1),
            selectorWritePlan(listOf("M0.0", "", "M0.2"), target = 0)
        )
    }

    @Test
    fun selector_movesToNewPosition_andIsNotFlippedBackByAStalePoll() {
        val clock = Clock()
        val store = TagValueStore { clock.now }
        val tags = listOf("M0.0", "M0.1", "M0.2")
        store.applyPoll(mapOf("M0.0" to 1, "M0.1" to 0, "M0.2" to 0), 0L)   // at position 1
        val pollStartedAt = store.now()
        clock.tick(20)
        val plan = selectorWritePlan(tags, target = 2)
        plan.forEach { (a, v) -> store.beginWrite(a, v) }
        // Stale poll (old contact pattern) arrives mid-turn.
        store.applyPoll(mapOf("M0.0" to 1, "M0.1" to 0, "M0.2" to 0), pollStartedAt)
        assertEquals(listOf(0, 0, 1), tags.map { store.values[it] })
        plan.forEach { (a, v) -> clock.tick(20); store.endWrite(a, v) }
        clock.tick(20)
        // A later poll reports the contacts as the PLC really has them.
        store.applyPoll(mapOf("M0.0" to 0, "M0.1" to 0, "M0.2" to 1), store.now())
        assertEquals(listOf(0, 0, 1), tags.map { store.values[it] })
    }
}
