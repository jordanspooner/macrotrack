package com.macrotrack.ui.log

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DragDropRoutingTest {

    private val date = LocalDate.of(2026, 8, 10)
    private val mealBounds = Rect(0f, 200f, 400f, 260f)
    private val weekBounds = Rect(0f, 100f, 400f, 190f)

    private fun mealTargets(vararg targets: MealDropTarget) =
        targets.associateBy { it.date to it.sectionId }

    private fun weekTargets(vararg targets: WeekDropTarget) =
        targets.associateBy { it.date }

    @Test
    fun `pointer inside meal target resolves to that meal`() {
        val target = resolveDropTarget(
            Offset(200f, 230f),
            mealTargets(MealDropTarget(date, 1, "Breakfast", mealBounds)),
            weekTargets(),
        )
        assertEquals(DragTarget.Meal(date, 1, "Breakfast"), target)
    }

    @Test
    fun `pointer inside week target resolves to that day`() {
        val target = resolveDropTarget(
            Offset(200f, 150f),
            mealTargets(),
            weekTargets(WeekDropTarget(date, weekBounds)),
        )
        assertEquals(DragTarget.WeekDay(date), target)
    }

    @Test
    fun `meal targets win over overlapping week targets`() {
        val overlap = Rect(0f, 100f, 400f, 300f)
        val target = resolveDropTarget(
            Offset(200f, 250f),
            mealTargets(MealDropTarget(date, 2, "Lunch", overlap)),
            weekTargets(WeekDropTarget(date, overlap)),
        )
        assertEquals(DragTarget.Meal(date, 2, "Lunch"), target)
    }

    @Test
    fun `pointer outside every target resolves to null`() {
        assertNull(resolveDropTarget(Offset(600f, 600f), mealTargets(), weekTargets()))
        assertNull(resolveDropTarget(Offset(200f, 50f), mealTargets(), weekTargets()))
    }

    @Test
    fun `multiple targets resolve the topmost meal by position`() {
        val first = Rect(0f, 200f, 400f, 230f)
        val second = Rect(0f, 230f, 400f, 260f)
        val targets = mealTargets(
            MealDropTarget(date, 1, "Breakfast", first),
            MealDropTarget(date, 2, "Lunch", second),
        )
        assertEquals(DragTarget.Meal(date, 1, "Breakfast"), resolveDropTarget(Offset(200f, 215f), targets, weekTargets()))
        assertEquals(DragTarget.Meal(date, 2, "Lunch"), resolveDropTarget(Offset(200f, 245f), targets, weekTargets()))
    }

    @Test
    fun `left and right edge zones are detected`() {
        val bounds = Rect(0f, 0f, 400f, 1000f)
        assertEquals(EdgeZone.Left, edgeZoneFor(Offset(5f, 500f), bounds, 20f))
        assertEquals(EdgeZone.Right, edgeZoneFor(Offset(395f, 500f), bounds, 20f))
    }

    @Test
    fun `center of bounds is not an edge zone`() {
        val bounds = Rect(0f, 0f, 400f, 1000f)
        assertNull(edgeZoneFor(Offset(200f, 500f), bounds, 20f))
    }

    @Test
    fun `positions outside the bounds are not edge zones`() {
        val bounds = Rect(0f, 0f, 400f, 1000f)
        assertNull(edgeZoneFor(Offset(-50f, 500f), bounds, 20f))
        assertNull(edgeZoneFor(Offset(450f, 500f), bounds, 20f))
    }

    @Test
    fun `active edge resolves inside the daily body edges`() {
        val daily = Rect(0f, 220f, 400f, 1000f)
        assertEquals(
            ActiveEdge(EdgeSurface.Daily, EdgeZone.Left),
            activeEdgeFor(Offset(5f, 500f), null, daily, 64f),
        )
        assertEquals(
            ActiveEdge(EdgeSurface.Daily, EdgeZone.Right),
            activeEdgeFor(Offset(395f, 500f), null, daily, 64f),
        )
    }

    @Test
    fun `active edge resolves inside the week strip edges`() {
        val week = Rect(0f, 100f, 400f, 200f)
        assertEquals(
            ActiveEdge(EdgeSurface.Week, EdgeZone.Left),
            activeEdgeFor(Offset(5f, 150f), week, null, 64f),
        )
        assertEquals(
            ActiveEdge(EdgeSurface.Week, EdgeZone.Right),
            activeEdgeFor(Offset(395f, 150f), week, null, 64f),
        )
    }

    @Test
    fun `daily edge wins when week and daily bounds overlap`() {
        val overlap = Rect(0f, 100f, 400f, 200f)
        val edge = activeEdgeFor(Offset(5f, 150f), overlap, overlap, 64f)
        assertEquals(ActiveEdge(EdgeSurface.Daily, EdgeZone.Left), edge)
    }

    @Test
    fun `week edge wins over the overlapped outer day cell`() {
        val week = Rect(0f, 100f, 400f, 200f)
        assertEquals(
            ActiveEdge(EdgeSurface.Week, EdgeZone.Left),
            activeEdgeFor(Offset(5f, 150f), week, null, 64f),
        )
        assertEquals(
            ActiveEdge(EdgeSurface.Week, EdgeZone.Right),
            activeEdgeFor(Offset(395f, 150f), week, null, 64f),
        )
    }

    @Test
    fun `central week day position resolves to no edge`() {
        val week = Rect(0f, 100f, 400f, 200f)
        assertNull(activeEdgeFor(Offset(200f, 150f), week, null, 64f))
    }

    @Test
    fun `daily edge still wins when week and daily bounds overlap`() {
        val week = Rect(0f, 100f, 400f, 200f)
        val daily = Rect(0f, 220f, 400f, 1000f)
        assertEquals(
            ActiveEdge(EdgeSurface.Daily, EdgeZone.Left),
            activeEdgeFor(Offset(5f, 500f), week, daily, 64f),
        )
    }

    @Test
    fun `release in a week edge zone cancels the drop`() {
        val week = Rect(0f, 100f, 400f, 200f)
        assertTrue(shouldCancelDrop(Offset(5f, 150f), week, null, 64f))
        assertTrue(shouldCancelDrop(Offset(395f, 150f), week, null, 64f))
    }

    @Test
    fun `release in a daily edge zone cancels the drop`() {
        val week = Rect(0f, 100f, 400f, 200f)
        val daily = Rect(0f, 220f, 400f, 1000f)
        assertTrue(shouldCancelDrop(Offset(5f, 500f), week, daily, 64f))
        assertTrue(shouldCancelDrop(Offset(395f, 500f), week, daily, 64f))
        assertTrue(shouldCancelDrop(Offset(5f, 500f), null, daily, 64f))
    }

    @Test
    fun `release at a central position does not cancel`() {
        val week = Rect(0f, 100f, 400f, 200f)
        val daily = Rect(0f, 220f, 400f, 1000f)
        assertFalse(shouldCancelDrop(Offset(200f, 150f), week, null, 64f))
        assertFalse(shouldCancelDrop(Offset(200f, 500f), week, daily, 64f))
    }

    @Test
    fun `release outside any bounds does not cancel`() {
        assertFalse(shouldCancelDrop(Offset(600f, 600f), null, null, 64f))
        assertFalse(shouldCancelDrop(Offset(200f, 50f), null, null, 64f))
    }

    @Test
    fun `edge-overlapped day stays a copy target but release cancels`() {
        val week = Rect(0f, 100f, 400f, 200f)
        val dayBounds = week
        val target = resolveDropTarget(
            Offset(5f, 150f),
            mealTargets(),
            weekTargets(WeekDropTarget(date, dayBounds)),
        )
        assertEquals(DragTarget.WeekDay(date), target)
        assertTrue(shouldCancelDrop(Offset(5f, 150f), week, null, 64f))
    }

    @Test
    fun `center and missing bounds resolve to no edge`() {
        val daily = Rect(0f, 220f, 400f, 1000f)
        assertNull(activeEdgeFor(Offset(200f, 500f), null, daily, 64f))
        assertNull(activeEdgeFor(Offset(200f, 500f), null, null, 64f))
    }

    @Test
    fun `daily edge steps page one day per direction`() {
        assertEquals(EdgeStep(dayDelta = -1), edgeStepFor(ActiveEdge(EdgeSurface.Daily, EdgeZone.Left)))
        assertEquals(EdgeStep(dayDelta = 1), edgeStepFor(ActiveEdge(EdgeSurface.Daily, EdgeZone.Right)))
    }

    @Test
    fun `week edge steps page one week per direction`() {
        assertEquals(EdgeStep(weekDelta = -1), edgeStepFor(ActiveEdge(EdgeSurface.Week, EdgeZone.Left)))
        assertEquals(EdgeStep(weekDelta = 1), edgeStepFor(ActiveEdge(EdgeSurface.Week, EdgeZone.Right)))
    }

    @Test
    fun `redundant meal target when every entry already lives there`() {
        val target = DragTarget.Meal(date, 1, "Breakfast")
        assertTrue(isRedundantMealTarget(listOf(entry(1, date, 1), entry(2, date, 1)), target))
    }

    @Test
    fun `meal target is not redundant when entries differ`() {
        val target = DragTarget.Meal(date, 2, "Lunch")
        assertFalse(isRedundantMealTarget(listOf(entry(1, date, 1)), target))
        assertFalse(isRedundantMealTarget(listOf(entry(1, date.plusDays(1), 2)), target))
    }

    @Test
    fun `empty snapshot is never redundant`() {
        assertFalse(isRedundantMealTarget(emptyList(), DragTarget.Meal(date, 1, "Breakfast")))
    }

    @Test
    fun `drag start is null when the selection is off`() {
        assertNull(dragStartAnchor(SelectionMode.Off, entry(4, date, 1)))
    }

    @Test
    fun `drag start requires an already-selected entry`() {
        val mode = SelectionMode.Selecting(date, listOf(entry(1, date, 1)))
        assertNull(dragStartAnchor(mode, entry(2, date, 1)))
    }

    @Test
    fun `drag start returns the full snapshot for a selected entry`() {
        val snapshot = listOf(entry(1, date, 1), entry(2, date, 1))
        val mode = SelectionMode.Selecting(date, snapshot)
        assertEquals(snapshot to date, dragStartAnchor(mode, entry(1, date, 1)))
        assertEquals(snapshot to date, dragStartAnchor(mode, entry(2, date, 1)))
    }

    @Test
    fun `drag start is disabled while choosing a copy destination`() {
        val snapshot = listOf(entry(1, date, 1))
        val mode = SelectionMode.ChoosingDestination(date, snapshot, Action.Copy)
        assertNull(dragStartAnchor(mode, entry(1, date, 1)))
    }

    @Test
    fun `drag start source date comes from the selecting snapshot`() {
        val mode = SelectionMode.Selecting(date, listOf(entry(1, date, 1)))
        assertEquals(date, dragStartAnchor(mode, entry(1, date, 1))?.second)
    }

    @Test
    fun `drag start payload keeps the source snapshot order`() {
        val existing = entry(2, date, 1, sortOrder = 5)
        val earlier = entry(3, date, 1, sortOrder = 1)
        val mode = SelectionMode.Selecting(date, listOf(existing, earlier))
        assertEquals(listOf(2L, 3L), dragStartAnchor(mode, earlier)?.first?.map { it.id })
    }

    @Test
    fun `pending selection arms a drag while composed mode is still off`() {
        val pending = listOf(entry(1, date, 1)) to date

        assertEquals(
            pending,
            dragStartAnchorWithPending(SelectionMode.Off, entry(1, date, 1), pending),
        )
    }

    @Test
    fun `pending selection only arms the matching entry`() {
        val pending = listOf(entry(1, date, 1)) to date

        assertNull(dragStartAnchorWithPending(SelectionMode.Off, entry(2, date, 1), pending))
    }

    @Test
    fun `pending selection is ignored while choosing a copy destination`() {
        val pending = listOf(entry(1, date, 1)) to date
        val mode = SelectionMode.ChoosingDestination(date, pending.first, Action.Copy)

        assertNull(dragStartAnchorWithPending(mode, entry(1, date, 1), pending))
    }

    @Test
    fun `current selection wins over a pending selection`() {
        val pending = listOf(entry(1, date, 1)) to date
        val current = listOf(entry(1, date, 1), entry(2, date, 1))
        val mode = SelectionMode.Selecting(date, current)

        assertEquals(
            current to date,
            dragStartAnchorWithPending(mode, entry(2, date, 1), pending),
        )
    }

    private fun entry(id: Long, date: LocalDate, sectionId: Long, sortOrder: Int = 0) = LogEntry(
        id = id,
        date = date,
        sectionId = sectionId,
        foodItemId = 7L,
        name = "Food $id",
        brand = null,
        portionG = 100f,
        portionLabel = null,
        macros = Macros(100f, 10f, 10f, 10f),
        sortOrder = sortOrder,
        createdAt = Instant.EPOCH,
    )
}
