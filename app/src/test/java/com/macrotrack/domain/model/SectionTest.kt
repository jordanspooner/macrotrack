package com.macrotrack.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test

class SectionTest {

    private val sections = listOf(
        Section(id = 1L, name = "Breakfast", timeOfDay = LocalTime.of(8, 0)),
        Section(id = 2L, name = "Lunch", timeOfDay = LocalTime.of(12, 0)),
        Section(id = 3L, name = "Dinner", timeOfDay = LocalTime.of(18, 0)),
    )

    @Test
    fun `selects latest section at or before current time`() {
        assertThat(defaultSectionIdForTime(sections, LocalTime.of(14, 0))).isEqualTo(2L)
    }

    @Test
    fun `falls back to latest section before first section`() {
        assertThat(defaultSectionIdForTime(sections, LocalTime.of(6, 0))).isEqualTo(3L)
    }

    @Test
    fun `empty sections return zero`() {
        assertThat(defaultSectionIdForTime(emptyList(), LocalTime.NOON)).isEqualTo(0L)
    }
}
