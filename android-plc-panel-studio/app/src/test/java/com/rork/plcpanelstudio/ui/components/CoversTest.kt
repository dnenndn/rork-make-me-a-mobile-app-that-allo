package com.rork.plcpanelstudio.ui.components

import com.rork.plcpanelstudio.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CoversTest {
    @Test
    fun logoCoverIsRemovedFromSelectionList() {
        assertFalse(PANEL_COVERS.any { it.id == "logo" })
        assertEquals("preparation", PANEL_COVERS.first().id)
        assertEquals(R.drawable.logo, coverRes(null))
        assertEquals(R.drawable.logo, coverRes("logo"))
    }
}
