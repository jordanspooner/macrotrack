package com.macrotrack.ui.log

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.LogEntry
import java.time.LocalDate

/**
 * State of an in-flight drag of a selected snapshot.
 *
 * The high-frequency pointer position is kept in a separate
 * `mutableStateOf<Offset>` in the composable (updated on every move) so that
 * the drag preview relayouts without recomposing `LogContent`.  This state
 * holds the comparatively stable drag metadata — entries, source date, the
 * currently resolved drop target, and the active edge — and only changes when
 * one of those values changes, so recomposition is throttled to the minimum.
 */
data class DragState(
    val entries: List<LogEntry>,
    val sourceDate: LocalDate,
    val activeTarget: DragTarget? = null,
    val isRedundantTarget: Boolean = false,
    val activeEdge: ActiveEdge? = null,
)

/** A valid drop destination resolved from a root-coordinate pointer position. */
sealed interface DragTarget {
    data class Meal(val date: LocalDate, val sectionId: Long, val sectionName: String) : DragTarget

    data class WeekDay(val date: LocalDate) : DragTarget
}

/** Root-coordinate bounds of a meal drop target, registered via onGloballyPositioned. */
data class MealDropTarget(
    val date: LocalDate,
    val sectionId: Long,
    val sectionName: String,
    val bounds: Rect,
)

/** Root-coordinate bounds of a week-strip move target, registered via onGloballyPositioned. */
data class WeekDropTarget(
    val date: LocalDate,
    val bounds: Rect,
)

/**
 * Resolves the drop destination for [position] (root coordinates) against the
 * registered meal and week targets. Meal targets win over week targets when
 * their bounds overlap.
 */
internal fun resolveDropTarget(
    position: Offset,
    mealTargets: Map<Pair<LocalDate, Long>, MealDropTarget>,
    weekTargets: Map<LocalDate, WeekDropTarget>,
): DragTarget? {
    val meal = mealTargets.values.firstOrNull { it.bounds.contains(position) }
    if (meal != null) return DragTarget.Meal(meal.date, meal.sectionId, meal.sectionName)
    val week = weekTargets.values.firstOrNull { it.bounds.contains(position) }
    if (week != null) return DragTarget.WeekDay(week.date)
    return null
}

/** True when every dragged entry already lives at [target]; the move would be a no-op. */
internal fun isRedundantMealTarget(entries: List<LogEntry>, target: DragTarget.Meal): Boolean =
    entries.isNotEmpty() && entries.all { it.date == target.date && it.sectionId == target.sectionId }

/**
 * Which horizontal edge of [bounds] contains [position] (root coordinates),
 * if any. Positions outside the bounds never resolve to an edge.
 */
internal fun edgeZoneFor(position: Offset, bounds: Rect, edgeWidthPx: Float): EdgeZone? {
    if (position.x in bounds.left..(bounds.left + edgeWidthPx)) return EdgeZone.Left
    if (position.x in (bounds.right - edgeWidthPx)..bounds.right) return EdgeZone.Right
    return null
}

enum class EdgeZone { Left, Right }

/** Which scroll surface an [ActiveEdge] pages. */
enum class EdgeSurface { Daily, Week }

/**
 * An edge the dragged pointer dwells in: [surface] picks the pager to move and
 * [zone] the direction.
 */
data class ActiveEdge(
    val surface: EdgeSurface,
    val zone: EdgeZone,
)

object EdgeHold {
    /** Delay before the first page step while an edge is held. */
    const val INITIAL_DELAY_MILLIS = 550L

    /** Interval between repeated page steps while the edge stays held. */
    const val REPEAT_INTERVAL_MILLIS = 650L

    /** Width of the interactive edge zones on each side. */
    val ZONE_WIDTH = 64.dp
}

/**
 * Derives the active drag edge for [position] (root coordinates).
 *
 * - Daily wins: any position inside the daily-log body's edge zone pages days,
 *   even over a hovered meal target (a sustained dwell takes precedence).
 * - The week strip's physical edge zones always page weeks, even when they
 *   overlap the outer day cells; only central day-cell positions stay move
 *   targets. Resolving a drop target is handled separately, and [shouldCancelDrop]
 *   cancels a release inside any edge zone so edge hold stays navigation only.
 * - Returns null when the position is outside every zone.
 */
internal fun activeEdgeFor(
    position: Offset,
    weekStripBounds: Rect?,
    dailyBodyBounds: Rect?,
    edgeWidthPx: Float,
): ActiveEdge? {
    val dailyZone = dailyBodyBounds?.let { edgeZoneFor(position, it, edgeWidthPx) }
    if (dailyZone != null) return ActiveEdge(EdgeSurface.Daily, dailyZone)
    val weekZone = weekStripBounds?.let { edgeZoneFor(position, it, edgeWidthPx) }
    if (weekZone != null) return ActiveEdge(EdgeSurface.Week, weekZone)
    return null
}

/**
 * True when a drag released at [position] (root coordinates) must cancel instead
 * of dropping. Releasing inside either the week strip's or the daily body's edge
 * zone must never move into the edge-overlapped target, because edge hold is
 * navigation only; only central meal/week-day releases keep their routing.
 */
internal fun shouldCancelDrop(
    position: Offset,
    weekStripBounds: Rect?,
    dailyBodyBounds: Rect?,
    edgeWidthPx: Float,
): Boolean =
    activeEdgeFor(position, weekStripBounds, dailyBodyBounds, edgeWidthPx) != null

/** Navigation delta applied by a single page step on [edge]. */
internal data class EdgeStep(
    val dayDelta: Long = 0L,
    val weekDelta: Long = 0L,
)

/** Direction an [ActiveEdge] pages: -1/+1 days or weeks, never both. */
internal fun edgeStepFor(edge: ActiveEdge): EdgeStep = when (edge.surface) {
    EdgeSurface.Daily -> when (edge.zone) {
        EdgeZone.Left -> EdgeStep(dayDelta = -1)
        EdgeZone.Right -> EdgeStep(dayDelta = 1)
    }
    EdgeSurface.Week -> when (edge.zone) {
        EdgeZone.Left -> EdgeStep(weekDelta = -1)
        EdgeZone.Right -> EdgeStep(weekDelta = 1)
    }
}

/**
 * The payload a drag starts with when the already-selected [entry] is
 * long-pressed and then moved: the current [SelectionMode.Selecting] snapshot
 * paired with its [sourceDate]. Returns null when no drag may start.
 *
 * A drag may only start from an entry already in the active selecting-mode
 * snapshot. A long-press on an unselected food only toggles selection and
 * never arms a drag, so the snapshot must already contain [entry]. Destination
 * picking disables drags entirely (the pinned bar owns that flow), and with
 * the selection off there is nothing to drag.
 */
internal fun dragStartAnchor(
    mode: SelectionMode,
    entry: LogEntry,
): Pair<List<LogEntry>, LocalDate>? = when (mode) {
    is SelectionMode.Selecting ->
        if (mode.selectedEntries.any { it.id == entry.id }) {
            mode.selectedEntries to mode.sourceDate
        } else {
            null
        }
    SelectionMode.Off -> null
    is SelectionMode.ChoosingDestination -> null
}

/**
 * Resolves a drag payload when long-press selection and drag start happen in
 * the same pointer gesture. The selection state passed through composition can
 * still be one frame behind the synchronous ViewModel update, so [pending]
 * provides the snapshot produced by that long-press as a fallback.
 */
internal fun dragStartAnchorWithPending(
    mode: SelectionMode,
    entry: LogEntry,
    pending: Pair<List<LogEntry>, LocalDate>?,
): Pair<List<LogEntry>, LocalDate>? {
    if (mode is SelectionMode.ChoosingDestination) return null
    return dragStartAnchor(mode, entry)
        ?: pending?.takeIf { snapshot -> snapshot.first.any { it.id == entry.id } }
}
