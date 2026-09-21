package com.rork.plcpanelstudio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IoStatusTest {

    private fun part(
        id: String,
        kind: ComponentKind,
        label: String,
        tag: String,
        direction: IoDirection
    ) = PanelComponent(id = id, kind = kind, col = 0.5f, row = 0.5f, label = label, tagAddress = tag, direction = direction)

    private fun panel(parts: List<PanelComponent>, watched: List<WatchedTag> = emptyList()) = Panel(
        id = "p1", name = "Line 3", description = "", deviceId = "d1",
        components = parts, updatedAtMillis = 0L, watchedMemory = watched
    )

    private val start = part("c1", ComponentKind.GREEN, "Start", "I0.3", IoDirection.INPUT)
    private val stop = part("c2", ComponentKind.STOP, "Stop", "I0.4", IoDirection.INPUT)
    private val run = part("c3", ComponentKind.LAMP, "Motor Run", "Q0.1", IoDirection.OUTPUT)
    private val fault = part("c4", ComponentKind.LAMP_RED, "Fault", "Q0.2", IoDirection.OUTPUT)

    @Test
    fun tags_areSplitByAreaLetter() {
        val s = buildIoSections(panel(listOf(run, start, fault, stop)), emptyMap())
        assertEquals(listOf("I0.3", "I0.4"), s.inputs.map { it.address })
        assertEquals(listOf("Q0.1", "Q0.2"), s.outputs.map { it.address })
        assertTrue(s.memory.isEmpty())
        assertEquals(listOf("Start", "Stop"), s.inputs.map { it.name })
        assertEquals(ComponentKind.GREEN, s.inputs[0].partKind)
    }

    @Test
    fun sorting_isByByteAndBitNumbers_notAlphabetical() {
        val a = part("a", ComponentKind.GREEN, "A", "I10.0", IoDirection.INPUT)
        val b = part("b", ComponentKind.GREEN, "B", "I2.7", IoDirection.INPUT)
        val c = part("c", ComponentKind.GREEN, "C", "I2.1", IoDirection.INPUT)
        val s = buildIoSections(panel(listOf(a, b, c)), emptyMap())
        assertEquals(listOf("I2.1", "I2.7", "I10.0"), s.inputs.map { it.address })
    }

    @Test
    fun values_areKnownOrNotYetKnown() {
        val s = buildIoSections(panel(listOf(start, run)), mapOf("I0.3" to 1, "Q0.1" to 0))
        assertEquals(1, s.inputs[0].value)
        assertEquals(0, s.outputs[0].value)
        val empty = buildIoSections(panel(listOf(start)), emptyMap())
        assertNull(empty.inputs[0].value)          // "not known" is not the same as "off"
    }

    @Test
    fun watchedMemory_goesToMemory_andCanBeRemoved() {
        val s = buildIoSections(
            panel(listOf(start), listOf(WatchedTag("M0.5", "Cycle done"), WatchedTag("M0.1"))),
            mapOf("M0.5" to 1)
        )
        assertEquals(listOf("M0.1", "M0.5"), s.memory.map { it.address })
        assertEquals(listOf("", "Cycle done"), s.memory.map { it.name })
        assertTrue(s.memory.all { it.removable })
        assertFalse(s.inputs[0].removable)          // a part's own tag cannot be removed here
        assertNull(s.memory[0].partKind)
        assertEquals(1, s.memory[1].value)
    }

    @Test
    fun selectorWithOneInputPerPosition_givesOneRowPerPosition() {
        val selector = PanelComponent(
            id = "s", kind = ComponentKind.SELECTOR, col = 0.5f, row = 0.5f, label = "Mode",
            tagAddress = "", direction = IoDirection.INPUT, positions = 3,
            positionTags = listOf("I1.0", "", "I1.2")
        )
        val s = buildIoSections(panel(listOf(selector)), emptyMap())
        assertEquals(listOf("I1.0", "I1.2"), s.inputs.map { it.address })
        assertEquals(listOf("Mode · P1", "Mode · P3"), s.inputs.map { it.name })
    }

    @Test
    fun tagWithoutAreaLetter_fallsBackToTheDirection() {
        val legacyIn = part("x", ComponentKind.GREEN, "Old in", "5.0", IoDirection.INPUT)
        val legacyOut = part("y", ComponentKind.LAMP, "Old out", "6.0", IoDirection.OUTPUT)
        val s = buildIoSections(panel(listOf(legacyIn, legacyOut)), emptyMap())
        assertEquals(listOf("5.0"), s.inputs.map { it.address })
        assertEquals(listOf("6.0"), s.outputs.map { it.address })
    }

    @Test
    fun anAddressAppearsOnlyOnce_andBlankTagsAreSkipped() {
        val twin = part("t", ComponentKind.YELLOW, "Twin", "I0.3", IoDirection.INPUT)
        val blank = part("b", ComponentKind.LAMP, "No tag", "", IoDirection.OUTPUT)
        val s = buildIoSections(panel(listOf(start, twin, blank), listOf(WatchedTag("I0.3"))), emptyMap())
        assertEquals(1, s.all.size)
        assertEquals("Start", s.inputs[0].name)
        assertEquals(listOf("I0.3"), s.addresses)
    }

    @Test
    fun addressesToRead_coverEverythingOnce() {
        val s = buildIoSections(panel(listOf(start, run), listOf(WatchedTag("M0.5"))), emptyMap())
        assertEquals(listOf("I0.3", "Q0.1", "M0.5"), s.addresses)
    }

    // ---------------------------------------------------------------- adding a memory bit

    @Test
    fun memoryBit_isAcceptedAndCleaned() {
        val p = panel(listOf(start))
        assertEquals(MemoryCheck.Ok("M0.5"), checkMemoryBit("0.5", p))
        assertEquals(MemoryCheck.Ok("M5.3"), checkMemoryBit(" 05.3 ", p))
        assertEquals(MemoryCheck.Ok("M255.7"), checkMemoryBit("255.7", p))
    }

    @Test
    fun memoryBit_badInputIsRefusedWithAReason() {
        val p = panel(listOf(start))
        assertTrue(checkMemoryBit("", p) is MemoryCheck.Error)
        assertTrue(checkMemoryBit("5", p) is MemoryCheck.Error)        // no bit
        assertTrue(checkMemoryBit("5.", p) is MemoryCheck.Error)
        assertTrue(checkMemoryBit("5.8", p) is MemoryCheck.Error)      // bit above 7
        assertTrue(checkMemoryBit("256.0", p) is MemoryCheck.Error)    // byte above 255
        assertTrue(checkMemoryBit("M5.1", p) is MemoryCheck.Error)     // the M is added by the app
    }

    @Test
    fun memoryBit_alreadyOnThePanelIsRefused() {
        val withWatch = panel(listOf(start), listOf(WatchedTag("M0.5")))
        assertEquals(MemoryCheck.Error("M0.5 is already on this panel."), checkMemoryBit("0.5", withWatch))
        assertEquals(MemoryCheck.Error("M0.5 is already on this panel."), checkMemoryBit("00.5", withWatch))
        val legacyPart = part("m", ComponentKind.LAMP, "Old", "M2.0", IoDirection.OUTPUT)   // a part still wired to M
        assertTrue(checkMemoryBit("2.0", panel(listOf(legacyPart))) is MemoryCheck.Error)
        assertEquals(MemoryCheck.Ok("M0.6"), checkMemoryBit("0.6", withWatch))
    }
}
