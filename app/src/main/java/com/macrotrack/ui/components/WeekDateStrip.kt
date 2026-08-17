package com.macrotrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.log.WeekDay
import com.macrotrack.ui.theme.MacroTrackShapes
import com.macrotrack.ui.theme.MotionTokens
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandOnPrimary
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroCarbsOverageColor
import com.macrotrack.ui.theme.macroFatOverageColor
import com.macrotrack.ui.theme.macroProteinColor
import com.macrotrack.ui.theme.macroProteinOverageColor
import com.macrotrack.ui.theme.restingSurfaceColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min

/** Corner radius used by the day-cell perimeter geometry. */
private val DayCellCornerRadius = 16.dp

/**
 * Week strip with drag support: while a drag is active, day taps are disabled
 * and days register as root-coordinate move targets, with the hovered day
 * highlighting. Edge-driven week paging is handled by the drag gesture itself.
 */
@Composable
fun WeekDateStrip(
    weekDays: List<WeekDay>,
    onDateSelected: (WeekDay) -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    dragActive: Boolean = false,
    dragCount: Int = 0,
    activeDragDate: LocalDate? = null,
    onRegisterDayTarget: (LocalDate, Rect) -> Unit = { _, _ -> },
    onUnregisterDayTarget: (LocalDate) -> Unit = {},
) {
    val firstDay = weekDays.firstOrNull() ?: return
    Surface(
        color = restingSurfaceColor(),
        shape = MacroTrackShapes.large,
        modifier = modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !dragActive, onClick = { onOpenCalendar() })
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = firstDay.date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
            ) {
                weekDays.forEach { day ->
                    DayItem(
                        day = day,
                        onClick = { onDateSelected(day) },
                        dragActive = dragActive,
                        dragCount = dragCount,
                        isActiveDropTarget = dragActive && activeDragDate == day.date,
                        onRegisterBounds = { onRegisterDayTarget(day.date, it) },
                        onUnregisterBounds = { onUnregisterDayTarget(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayItem(
    day: WeekDay,
    onClick: () -> Unit,
    dragActive: Boolean,
    dragCount: Int,
    isActiveDropTarget: Boolean,
    onRegisterBounds: (Rect) -> Unit,
    onUnregisterBounds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = day.isSelected
    val isToday = day.isToday
    val numberColor = when {
        isSelected -> brandOnPrimary()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val cellShape = RoundedCornerShape(DayCellCornerRadius)
    val containerColor by animateColorAsState(
        targetValue = when {
            isActiveDropTarget -> brandPrimary().copy(alpha = 0.3f)
            isSelected -> brandPrimary().copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        animationSpec = tween(MotionTokens.medium),
    )
    val moveLabel = if (dragActive) {
        "Move ${dragCount.coerceAtLeast(1)} to ${day.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}"
    } else {
        null
    }

    Box(
        modifier = modifier
            .clip(cellShape)
            .background(containerColor)
            .clickable(enabled = !dragActive, onClick = onClick)
            .onGloballyPositioned { onRegisterBounds(it.boundsInRoot()) }
            .testTag("week-day-${day.date}")
            .semantics {
                contentDescription = moveLabel ?: weekDayContentDescription(
                    dayName = day.dayName,
                    dayNumber = day.dayNumber,
                    isToday = isToday,
                    isSelected = isSelected,
                )
            },
    ) {
        MacroPerimeterRing(
            proteinProgress = day.proteinProgress,
            carbsProgress = day.carbsProgress,
            fatProgress = day.fatProgress,
            proteinShare = day.proteinShare,
            carbsShare = day.carbsShare,
            fatShare = day.fatShare,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                day.dayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Surface(
                shape = CircleShape,
                color = if (isSelected) brandPrimary() else Color.Transparent,
                border = if (isToday && !isSelected) BorderStroke(2.dp, brandPrimary()) else null,
                modifier = Modifier.size(32.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.dayNumber.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = numberColor,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }

    DisposableEffect(day.date) {
        onDispose(onUnregisterBounds)
    }
}

/**
 * One tall rounded-rectangle perimeter ring around the whole day cell, divided
 * into three segments (protein/carbs/fat) whose lengths are proportional to
 * each macro's share of the daily goal kcal. Each segment draws a low-alpha
 * track and a fill for actual grams / daily goal grams; past 100% the whole
 * segment switches to its macro-specific overage tint for a strongly
 * contrasting overage signal. Values animate toward their target (clamped at
 * 200% for legibility). The perimeter starts at 12 o'clock and every fill
 * advances clockwise.
 */
@Composable
private fun MacroPerimeterRing(
    proteinProgress: Float,
    carbsProgress: Float,
    fatProgress: Float,
    proteinShare: Float,
    carbsShare: Float,
    fatShare: Float,
    modifier: Modifier = Modifier,
) {
    val proteinAnim by animateFloatAsState(
        targetValue = proteinProgress.coerceIn(0f, 2f),
        animationSpec = tween(MotionTokens.medium),
    )
    val carbsAnim by animateFloatAsState(
        targetValue = carbsProgress.coerceIn(0f, 2f),
        animationSpec = tween(MotionTokens.medium),
    )
    val fatAnim by animateFloatAsState(
        targetValue = fatProgress.coerceIn(0f, 2f),
        animationSpec = tween(MotionTokens.medium),
    )

    val segments = perimeterSegments(
        proteinShare = proteinShare,
        carbsShare = carbsShare,
        fatShare = fatShare,
        proteinProgress = proteinAnim,
        carbsProgress = carbsAnim,
        fatProgress = fatAnim,
    )
    val colors = listOf(macroProteinColor(), macroCarbsColor(), macroFatColor())
    val overageColors = listOf(
        macroProteinOverageColor(),
        macroCarbsOverageColor(),
        macroFatOverageColor(),
    )

    Canvas(
        modifier = modifier.semantics {
            contentDescription = weekDayProgressDescription(
                protein = proteinProgress,
                carbs = carbsProgress,
                fat = fatProgress,
            )
        }
    ) {
        val stroke = size.minDimension * 0.05f
        val gap = size.minDimension * 0.04f
        val corner = DayCellCornerRadius.toPx()
        val inner = Rect(
            Offset(stroke, stroke),
            Size(size.width - stroke * 2f, size.height - stroke * 2f),
        )
        val geometry = roundedRectPerimeter(
            width = inner.width,
            height = inner.height,
            cornerRadius = corner,
        )
        val radius = geometry.cornerRadius
        val path = Path().apply {
            moveTo(inner.left + geometry.start.x, inner.top + geometry.start.y)
            lineTo(inner.right - radius, inner.top)
            arcTo(
                rect = Rect(inner.right - 2f * radius, inner.top, inner.right, inner.top + 2f * radius),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(inner.right, inner.bottom - radius)
            arcTo(
                rect = Rect(inner.right - 2f * radius, inner.bottom - 2f * radius, inner.right, inner.bottom),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(inner.left + radius, inner.bottom)
            arcTo(
                rect = Rect(inner.left, inner.bottom - 2f * radius, inner.left + 2f * radius, inner.bottom),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(inner.left, inner.top + radius)
            arcTo(
                rect = Rect(inner.left, inner.top, inner.left + 2f * radius, inner.top + 2f * radius),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(inner.left + geometry.start.x, inner.top)
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val totalLength = measure.length
        if (totalLength <= 0f) return@Canvas
        val usableLength = (totalLength - gap * segments.size).coerceAtLeast(0f)

        perimeterSpans(segments = segments, usableLength = usableLength, gap = gap)
            .forEachIndexed { index, span ->
                val trackPath = Path()
                measure.getSegment(span.start, span.start + span.length, trackPath, true)
                drawPath(
                    path = trackPath,
                    color = colors[index].copy(alpha = 0.16f),
                    style = Stroke(width = stroke),
                )

                if (segments[index].fillFraction > 0f) {
                    val fillPath = Path()
                    measure.getSegment(
                        span.start,
                        span.start + span.length * segments[index].fillFraction,
                        fillPath,
                        true,
                    )
                    drawPath(
                        path = fillPath,
                        color = if (segments[index].isOverage) overageColors[index] else colors[index],
                        style = Stroke(width = stroke),
                    )
                }
            }
    }
}

/**
 * One macro's slice of the perimeter: [startFraction]/[lengthFraction] are
 * positions within the usable (gap-free) perimeter, [fillFraction] is the
 * 0..1 portion of the segment filled by actual progress, and [isOverage]
 * marks a fully filled segment past 100% of its goal.
 */
internal data class PerimeterSegment(
    val startFraction: Float,
    val lengthFraction: Float,
    val fillFraction: Float,
    val isOverage: Boolean,
)

internal fun perimeterSegments(
    proteinShare: Float,
    carbsShare: Float,
    fatShare: Float,
    proteinProgress: Float,
    carbsProgress: Float,
    fatProgress: Float,
): List<PerimeterSegment> {
    val shares = listOf(proteinShare, carbsShare, fatShare)
    val progress = listOf(proteinProgress, carbsProgress, fatProgress)
    val totalShare = shares.sum()
    var cursor = 0f
    return shares.mapIndexed { index, share ->
        val length = if (totalShare > 0f) (share / totalShare).coerceIn(0f, 1f) else 0f
        val segment = PerimeterSegment(
            startFraction = cursor,
            lengthFraction = length,
            fillFraction = progress[index].coerceIn(0f, 1f),
            isOverage = progress[index] > 1f,
        )
        cursor += length
        segment
    }
}

/**
 * One segment's placement on the actual perimeter path: [start] is the path
 * offset and [length] the arc length it occupies.
 */
internal data class PerimeterSpan(
    val start: Float,
    val length: Float,
)

/**
 * Clockwise rounded-rectangle perimeter geometry whose first point is exactly
 * the top-center (12 o'clock) position. [segments] lists the arc length of
 * each straight edge and 90° corner in traversal order: top edge → top-right
 * corner → right edge → bottom-right corner → bottom edge → bottom-left
 * corner → left edge → top-left corner, closing back at [start]. Each corner
 * contributes [cornerArcLength] = π·r/2; [totalLength] is the full perimeter.
 * Mirrors the path drawn by [MacroPerimeterRing] so the ring's start-at-top /
 * clockwise contract stays testable on the JVM.
 */
internal data class RoundedRectPerimeter(
    val start: Offset,
    val cornerRadius: Float,
    val segments: List<Float>,
) {
    val cornerArcLength: Float
        get() = (PI * cornerRadius / 2.0).toFloat()

    val totalLength: Float
        get() = segments.sum()
}

internal fun roundedRectPerimeter(width: Float, height: Float, cornerRadius: Float): RoundedRectPerimeter {
    val radius = cornerRadius.coerceIn(0f, min(width, height) / 2f)
    val edge = { length: Float -> (length - 2f * radius).coerceAtLeast(0f) }
    val arc = (PI * radius / 2.0).toFloat()
    return RoundedRectPerimeter(
        start = Offset(width / 2f, 0f),
        cornerRadius = radius,
        segments = listOf(
            edge(width),  // top edge, advancing right
            arc,          // top-right corner
            edge(height), // right edge, advancing down
            arc,          // bottom-right corner
            edge(width),  // bottom edge, advancing left
            arc,          // bottom-left corner
            edge(height), // left edge, advancing up
            arc,          // top-left corner
        ),
    )
}

/**
 * Maps each segment's usable-perimeter fractions to actual path offsets,
 * inserting one full [gap] between consecutive segments and splitting the
 * closing seam gap into a half-[gap] on each path boundary. Lengths stay
 * share-proportional within [usableLength]; spans never overlap and, together
 * with the [segments.size] gaps, tile [usableLength] + [segments.size] * [gap].
 */
internal fun perimeterSpans(
    segments: List<PerimeterSegment>,
    usableLength: Float,
    gap: Float,
): List<PerimeterSpan> = segments.mapIndexed { index, segment ->
    PerimeterSpan(
        start = gap / 2f + index * gap + segment.startFraction * usableLength,
        length = segment.lengthFraction * usableLength,
    )
}

internal fun weekDayContentDescription(
    dayName: String,
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
): String = buildString {
    append(dayName)
    append(' ')
    append(dayNumber)
    if (isToday) append(", Today")
    if (isSelected) append(", Selected")
}

internal fun weekDayProgressDescription(protein: Float, carbs: Float, fat: Float): String {
    fun percent(value: Float): Int = (value * 100).toInt()
    return "Protein ${percent(protein)}%, Carbs ${percent(carbs)}%, Fat ${percent(fat)}%"
}
