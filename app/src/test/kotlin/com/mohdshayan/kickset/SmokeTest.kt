package com.mohdshayan.kickset

import com.mohdshayan.kickset.ui.theme.RadiusLg
import com.mohdshayan.kickset.ui.theme.RadiusMd
import com.mohdshayan.kickset.ui.theme.RadiusSm
import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun radiusScaleMatchesTheBlueprint() {
        assertEquals(4f, RadiusSm.value)
        assertEquals(8f, RadiusMd.value)
        assertEquals(16f, RadiusLg.value)
    }
}
