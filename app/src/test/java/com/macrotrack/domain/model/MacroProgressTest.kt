package com.macrotrack.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MacroProgressTest {

    @Test
    fun `progress below goal only fills goal segment`() {
        assertThat(macroProgressSegments(0.5f)).isEqualTo(
            MacroProgressSegments(goalFraction = 0.5f, overageFraction = 0f)
        )
    }

    @Test
    fun `progress over goal splits goal and overage segments`() {
        assertThat(macroProgressSegments(1.5f)).isEqualTo(
            MacroProgressSegments(goalFraction = 1f, overageFraction = 0.5f)
        )
    }

    @Test
    fun `progress is clamped at two times the goal`() {
        assertThat(macroProgressSegments(3f)).isEqualTo(
            MacroProgressSegments(goalFraction = 1f, overageFraction = 1f)
        )
    }
}
