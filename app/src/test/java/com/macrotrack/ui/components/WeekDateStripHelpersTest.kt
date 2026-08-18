package com.macrotrack.ui.components

import androidx.compose.ui.geometry.Offset
import com.macrotrack.ui.log.macroGoalProgress
import com.macrotrack.ui.log.macroGoalShares
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekDateStripHelpersTest {

    @Test
    fun macroGoalProgressIsUncappedWhenExceeded() {
        assertEquals(1.5f, macroGoalProgress(actualG = 150f, goalG = 100), 0f)
    }

    @Test
    fun macroGoalProgressIsFractionalUnderGoal() {
        assertEquals(0.5f, macroGoalProgress(actualG = 50f, goalG = 100), 0f)
    }

    @Test
    fun macroGoalProgressWithZeroGoalIsZero() {
        assertEquals(0f, macroGoalProgress(actualG = 80f, goalG = 0), 0f)
    }

    @Test
    fun macroGoalProgressWithNothingLoggedIsZero() {
        assertEquals(0f, macroGoalProgress(actualG = 0f, goalG = 100), 0f)
    }

    @Test
    fun sharesWeightMacrosByTheirKcalContribution() {
        val shares = macroGoalShares(proteinG = 100, carbsG = 200, fatG = 50)
        // 100*4 + 200*4 + 50*9 = 400 + 800 + 450 = 1650 kcal
        assertEquals(400f / 1650f, shares.protein, 0.0001f)
        assertEquals(800f / 1650f, shares.carbs, 0.0001f)
        assertEquals(450f / 1650f, shares.fat, 0.0001f)
    }

    @Test
    fun sharesSumToOne() {
        val shares = macroGoalShares(proteinG = 150, carbsG = 250, fatG = 65)
        assertEquals(1f, shares.protein + shares.carbs + shares.fat, 0.0001f)
    }

    @Test
    fun sharesWithZeroGoalsAreAllZero() {
        val shares = macroGoalShares(proteinG = 0, carbsG = 0, fatG = 0)
        assertEquals(0f, shares.protein, 0f)
        assertEquals(0f, shares.carbs, 0f)
        assertEquals(0f, shares.fat, 0f)
    }

    @Test
    fun perimeterSegmentsUseShareProportionalLengths() {
        val segments = perimeterSegments(
            proteinShare = 0.5f, carbsShare = 0.3f, fatShare = 0.2f,
            proteinProgress = 0f, carbsProgress = 0f, fatProgress = 0f,
        )
        assertEquals(3, segments.size)
        assertEquals(0f, segments[0].startFraction, 0f)
        assertEquals(0.5f, segments[0].lengthFraction, 0f)
        assertEquals(0.5f, segments[1].startFraction, 0f)
        assertEquals(0.3f, segments[1].lengthFraction, 0f)
        assertEquals(0.8f, segments[2].startFraction, 0f)
        assertEquals(0.2f, segments[2].lengthFraction, 0f)
    }

    @Test
    fun perimeterSegmentsNormalizeSharesThatDoNotSumToOne() {
        val segments = perimeterSegments(
            proteinShare = 1f, carbsShare = 1f, fatShare = 1f,
            proteinProgress = 0f, carbsProgress = 0f, fatProgress = 0f,
        )
        assertEquals(1f / 3f, segments[0].lengthFraction, 0f)
        assertEquals(1f / 3f, segments[1].lengthFraction, 0f)
        assertEquals(1f / 3f, segments[2].lengthFraction, 0f)
    }

    @Test
    fun perimeterSegmentFillsByProgressUnderGoal() {
        val segment = perimeterSegments(
            proteinShare = 1f, carbsShare = 0f, fatShare = 0f,
            proteinProgress = 0.5f, carbsProgress = 0f, fatProgress = 0f,
        )[0]
        assertEquals(0.5f, segment.fillFraction, 0f)
        assertFalse(segment.isOverage)
    }

    @Test
    fun perimeterSegmentAtExactlyGoalFillsFullyWithoutOverage() {
        val segment = perimeterSegments(
            proteinShare = 1f, carbsShare = 0f, fatShare = 0f,
            proteinProgress = 1f, carbsProgress = 0f, fatProgress = 0f,
        )[0]
        assertEquals(1f, segment.fillFraction, 0f)
        assertFalse(segment.isOverage)
    }

    @Test
    fun perimeterSegmentOverGoalIsFullyFilledOverage() {
        val segment = perimeterSegments(
            proteinShare = 1f, carbsShare = 0f, fatShare = 0f,
            proteinProgress = 1.5f, carbsProgress = 0f, fatProgress = 0f,
        )[0]
        assertEquals(1f, segment.fillFraction, 0f)
        assertTrue(segment.isOverage)
    }

    @Test
    fun perimeterSegmentsWithZeroSharesDrawNothing() {
        val segments = perimeterSegments(
            proteinShare = 0f, carbsShare = 0f, fatShare = 0f,
            proteinProgress = 1f, carbsProgress = 1f, fatProgress = 1f,
        )
        segments.forEach {
            assertEquals(0f, it.lengthFraction, 0f)
        }
    }

    @Test
    fun perimeterSpansKeepHalfGapOnEachBoundary() {
        val segments = perimeterSegments(
            proteinShare = 1f, carbsShare = 0f, fatShare = 0f,
            proteinProgress = 0f, carbsProgress = 0f, fatProgress = 0f,
        )
        val usableLength = 90f
        val gap = 5f
        val spans = perimeterSpans(segments, usableLength, gap)

        assertEquals(gap / 2f, spans[0].start, 0f)
        assertEquals(90f, spans[0].length, 0f)
        assertEquals(
            usableLength + (segments.size - 1) * gap + gap / 2f,
            spans.last().start + spans.last().length,
            0f,
        )
    }

    @Test
    fun perimeterSpansInsertFullGapsBetweenSegments() {
        val segments = perimeterSegments(
            proteinShare = 0.5f, carbsShare = 0.3f, fatShare = 0.2f,
            proteinProgress = 0f, carbsProgress = 0f, fatProgress = 0f,
        )
        val usableLength = 97f
        val gap = 3f
        val spans = perimeterSpans(segments, usableLength, gap)

        spans.forEachIndexed { index, span ->
            if (index > 0) {
                val previousEnd = spans[index - 1].start + spans[index - 1].length
                assertEquals(gap, span.start - previousEnd, 0.001f)
            }
        }
    }

    @Test
    fun perimeterSpansKeepLengthsShareProportional() {
        val segments = perimeterSegments(
            proteinShare = 0.5f, carbsShare = 0.3f, fatShare = 0.2f,
            proteinProgress = 0f, carbsProgress = 0f, fatProgress = 0f,
        )
        val usableLength = 100f
        val gap = 2f
        val spans = perimeterSpans(segments, usableLength, gap)

        assertEquals(50f, spans[0].length, 0.001f)
        assertEquals(30f, spans[1].length, 0.001f)
        assertEquals(20f, spans[2].length, 0.001f)
    }

    @Test
    fun perimeterSpansTileWholePerimeterWithoutOverlap() {
        val segments = perimeterSegments(
            proteinShare = 0.4f, carbsShare = 0.35f, fatShare = 0.25f,
            proteinProgress = 0f, carbsProgress = 0f, fatProgress = 0f,
        )
        val usableLength = 88f
        val gap = 4f
        val totalLength = usableLength + gap * segments.size
        val spans = perimeterSpans(segments, usableLength, gap)

        assertEquals(gap / 2f, spans.first().start, 0f)
        assertEquals(totalLength - gap / 2f, spans.last().start + spans.last().length, 0f)
        assertEquals(usableLength.toDouble(), spans.sumOf { it.length.toDouble() }, 0.0001)
    }

    @Test
    fun dayDescriptionFlagsTodayOnly() {
        val description = weekDayContentDescription(
            dayName = "Mon",
            dayNumber = 8,
            isToday = true,
            isSelected = false,
        )
        assertEquals("Mon 8, Today", description)
    }

    @Test
    fun dayDescriptionFlagsSelectedOnly() {
        val description = weekDayContentDescription(
            dayName = "Tue",
            dayNumber = 9,
            isToday = false,
            isSelected = true,
        )
        assertEquals("Tue 9, Selected", description)
    }

    @Test
    fun dayDescriptionCombinesTodayAndSelected() {
        val description = weekDayContentDescription(
            dayName = "Mon",
            dayNumber = 8,
            isToday = true,
            isSelected = true,
        )
        assertEquals("Mon 8, Today, Selected", description)
    }

    @Test
    fun dayDescriptionHasNoFlagsForPlainDay() {
        val description = weekDayContentDescription(
            dayName = "Wed",
            dayNumber = 10,
            isToday = false,
            isSelected = false,
        )
        assertEquals("Wed 10", description)
    }

    @Test
    fun progressDescriptionReportsRoundedPercentages() {
        assertEquals(
            "Protein 100%, Carbs 50%, Fat 133%",
            weekDayProgressDescription(protein = 1f, carbs = 0.5f, fat = 1.333f),
        )
    }

    @Test
    fun roundedRectPerimeterStartsExactlyAtTopCenter() {
        val geometry = roundedRectPerimeter(width = 100f, height = 200f, cornerRadius = 16f)
        assertEquals(Offset(50f, 0f), geometry.start)
    }

    @Test
    fun roundedRectPerimeterSegmentsTraverseClockwiseFromTop() {
        val geometry = roundedRectPerimeter(width = 100f, height = 200f, cornerRadius = 16f)
        val arc = PI.toFloat() * 16f / 2f
        // top edge → top-right arc → right edge → bottom-right arc →
        // bottom edge → bottom-left arc → left edge → top-left arc
        assertEquals(100f - 32f, geometry.segments[0], 0.001f)
        assertEquals(arc, geometry.segments[1], 0.001f)
        assertEquals(200f - 32f, geometry.segments[2], 0.001f)
        assertEquals(arc, geometry.segments[3], 0.001f)
        assertEquals(100f - 32f, geometry.segments[4], 0.001f)
        assertEquals(arc, geometry.segments[5], 0.001f)
        assertEquals(200f - 32f, geometry.segments[6], 0.001f)
        assertEquals(arc, geometry.segments[7], 0.001f)
    }

    @Test
    fun roundedRectPerimeterTotalLengthMatchesRoundedRectFormula() {
        val geometry = roundedRectPerimeter(width = 100f, height = 200f, cornerRadius = 16f)
        val expected =
            2f * (100f - 32f) + 2f * (200f - 32f) + 2f * PI.toFloat() * 16f
        assertEquals(expected, geometry.totalLength, 0.001f)
        assertEquals(geometry.segments.sum(), geometry.totalLength, 0f)
    }

    @Test
    fun roundedRectPerimeterQuarterArcIsPiTimesRadiusOverTwo() {
        val geometry = roundedRectPerimeter(width = 100f, height = 200f, cornerRadius = 16f)
        assertEquals(PI.toFloat() * 16f / 2f, geometry.cornerArcLength, 0.001f)
    }

    @Test
    fun roundedRectPerimeterClampsCornerRadiusToHalfMinDimension() {
        val geometry = roundedRectPerimeter(width = 10f, height = 200f, cornerRadius = 50f)
        assertEquals(5f, geometry.cornerRadius, 0f)
        assertEquals(0f, geometry.segments[0], 0f)
        assertEquals(190f, geometry.segments[2], 0.001f)
        assertEquals(0f, geometry.segments[4], 0f)
        assertEquals(190f, geometry.segments[6], 0.001f)
    }

    @Test
    fun roundedRectPerimeterWithZeroCornerRadiusIsPlainRectangle() {
        val geometry = roundedRectPerimeter(width = 100f, height = 200f, cornerRadius = 0f)
        assertEquals(100f, geometry.segments[0], 0f)
        assertEquals(0f, geometry.segments[1], 0f)
        assertEquals(200f, geometry.segments[2], 0f)
        assertEquals(100f, geometry.segments[4], 0f)
        assertEquals(200f, geometry.segments[6], 0f)
        assertEquals(0f, geometry.cornerArcLength, 0f)
    }

    @Test
    fun roundedRectPerimeterDegenerateSizeKeepsTopCenterStartAndZeroLength() {
        val geometry = roundedRectPerimeter(width = 0f, height = 0f, cornerRadius = 8f)
        assertEquals(Offset(0f, 0f), geometry.start)
        assertEquals(0f, geometry.cornerRadius, 0f)
        assertEquals(0f, geometry.totalLength, 0f)
    }
}
