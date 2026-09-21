package com.rork.plcpanelstudio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagAddressTest {

    @Test
    fun parses_theThreeAreas() {
        assertEquals(TagAddress(TagArea.INPUT, 3, 4), parseTagAddress("I3.4"))
        assertEquals(TagAddress(TagArea.OUTPUT, 3, 2), parseTagAddress("Q3.2"))
        assertEquals(TagAddress(TagArea.MEMORY, 5, 4), parseTagAddress("M5.4"))
    }

    @Test
    fun parsing_isCaseAndSpaceTolerant() {
        assertEquals(TagAddress(TagArea.MEMORY, 5, 4), parseTagAddress("  m5.4 "))
    }

    @Test
    fun rejects_whatIsNotAnAddress() {
        assertNull(parseTagAddress(""))
        assertNull(parseTagAddress("X1.0"))   // unknown area
        assertNull(parseTagAddress("I34"))    // no dot
        assertNull(parseTagAddress("I3.8"))   // bit above 7
        assertNull(parseTagAddress("I3.4.1")) // too many parts
        assertNull(parseTagAddress("MW20"))   // word, not a bit
        assertNull(parseTagAddress("I999.1")) // byte above 255
    }

    @Test
    fun bodyValidation_matchesTheParser() {
        assertTrue(isValidTagBody("3.4"))
        assertTrue(isValidTagBody("0.0"))
        assertFalse(isValidTagBody("3"))
        assertFalse(isValidTagBody("3."))
        assertFalse(isValidTagBody("3.9"))
    }

    @Test
    fun typing_isFilteredDownToByteDotBit() {
        assertEquals("3", filterTagBody("3"))
        assertEquals("3.4", filterTagBody("3.4"))
        assertEquals("34", filterTagBody("3q4"))        // letters dropped
        assertEquals("3.4", filterTagBody("3..4"))      // one dot only
        assertEquals("3.4", filterTagBody("3.45"))      // one bit digit only
        assertEquals("255.7", filterTagBody("2557.7"))  // three byte digits at most
        assertEquals("5", filterTagBody(".5"))          // a leading dot is ignored
    }

    @Test
    fun fullAddress_isTheAreaPlusTheBody() {
        assertEquals("M5.4", tagAddressOf(TagArea.MEMORY, "5.4"))
        assertEquals("", tagAddressOf(TagArea.INPUT, "  "))
        assertTrue(isValidTagAddress(tagAddressOf(TagArea.OUTPUT, "3.2")))
    }

    @Test
    fun buttonsAndSelectorAreAlwaysInput_lampsAreAlwaysOutput() {
        // Every push button and the selector: input, no other option.
        assertEquals(TagArea.INPUT, fixedAreaFor(ComponentKind.STOP))
        assertEquals(TagArea.INPUT, fixedAreaFor(ComponentKind.GREEN))
        assertEquals(TagArea.INPUT, fixedAreaFor(ComponentKind.YELLOW))
        assertEquals(TagArea.INPUT, fixedAreaFor(ComponentKind.SELECTOR))
        // Every lamp: output, no other option.
        assertEquals(TagArea.OUTPUT, fixedAreaFor(ComponentKind.LAMP))
        assertEquals(TagArea.OUTPUT, fixedAreaFor(ComponentKind.LAMP_RED))
        assertEquals(TagArea.OUTPUT, fixedAreaFor(ComponentKind.LAMP_GREEN))
    }

    @Test
    fun directionFollowsTheFixedArea() {
        assertEquals(IoDirection.INPUT, fixedDirectionFor(ComponentKind.STOP))
        assertEquals(IoDirection.INPUT, fixedDirectionFor(ComponentKind.GREEN))
        assertEquals(IoDirection.INPUT, fixedDirectionFor(ComponentKind.YELLOW))
        assertEquals(IoDirection.INPUT, fixedDirectionFor(ComponentKind.SELECTOR))
        assertEquals(IoDirection.OUTPUT, fixedDirectionFor(ComponentKind.LAMP))
        assertEquals(IoDirection.OUTPUT, fixedDirectionFor(ComponentKind.LAMP_RED))
        assertEquals(IoDirection.OUTPUT, fixedDirectionFor(ComponentKind.LAMP_GREEN))
    }

    @Test
    fun bodyIgnoringArea_readsTheDigitsWhateverLetterWasThere() {
        // A part saved under a different letter before areas were fixed by kind still
        // prefills correctly: only the byte.bit part is kept, the old letter is dropped.
        assertEquals("0.3", tagBodyIgnoringArea("M0.3"))
        assertEquals("3.4", tagBodyIgnoringArea("I3.4"))
        assertEquals("", tagBodyIgnoringArea(""))
        assertEquals("", tagBodyIgnoringArea("MW20")) // a word address has no byte.bit body
    }

    @Test
    fun buttonDisplayNames_matchTheirColour() {
        assertEquals("Red Button", ComponentKind.STOP.displayName)
        assertEquals("Green Button", ComponentKind.GREEN.displayName)
        assertEquals("Yellow Button", ComponentKind.YELLOW.displayName)
    }
}
