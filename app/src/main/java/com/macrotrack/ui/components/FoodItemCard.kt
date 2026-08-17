package com.macrotrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.ui.theme.MotionTokens
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.macroCaloriesColor
import kotlinx.coroutines.withTimeoutOrNull

private enum class LongPressWindowResult {
    Released,
    Abort
}

/**
 * Food entry card driven by a single explicit gesture state machine ([pointerInput]) so
 * that tap, long-press selection, and long-press-then-drag can never fire simultaneously
 * or out of order:
 *
 *  - clean release before the long-press timeout with no meaningful movement and no
 *    consumed change -> [onClick] (exactly once)
 *  - hold past the long-press timeout                 -> [onLongPress] (exactly once,
 *    immediately; never [onClick])
 *  - movement beyond the touch slop before the timeout, or a consumed/cancelled gesture
 *    -> neither [onClick] nor [onLongPress]
 *  - first post-long-press movement beyond the touch slop -> [onDragStart], then
 *    [onDragMove]; release after such movement -> [onDragEnd]; a stationary long-press
 *    release sends no drag callbacks
 *
 * Long-press detection uses a manual `withTimeoutOrNull` window over `awaitPointerEvent`
 * (not `awaitLongPressOrCancellation` / `waitForUpOrCancellation`) so a clean short tap can
 * be distinguished from a cancelled gesture. [onClick] and [onLongPress] are also exposed
 * through the semantics node below for accessibility. The pointer input is attached outside
 * the card's visual padding so the whole visible card, including its padding, is a valid hit
 * target. [onDragStart] and [onDragMove] receive the pointer position in root coordinates so
 * the caller can place and update the drag preview. All callbacks are kept fresh via
 * [rememberUpdatedState], and [gesturesEnabled] gates new gestures without restarting the
 * in-flight drag coroutine.
 */
@Composable
fun FoodItemCard(
    entry: LogEntry,
    isSelected: Boolean,
    isDragSource: Boolean = false,
    gesturesEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = MotionTokens.medium),
    )
    val dragScale by animateFloatAsState(
        targetValue = if (isDragSource) 0.97f else 1f,
        animationSpec = tween(durationMillis = MotionTokens.medium),
    )
    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragSource) 0.55f else 1f,
        animationSpec = tween(durationMillis = MotionTokens.medium),
    )
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    val latestGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragMove by rememberUpdatedState(onDragMove)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootOffset = it.boundsInRoot().topLeft }
            .pointerInput(entry.id) {
                awaitEachGesture {
                    if (!latestGesturesEnabled) {
                        // awaitEachGesture immediately invokes the block again
                        // after a normal return. Suspending here avoids a
                        // main-thread busy loop while another card is dragged.
                        awaitPointerEvent()
                        return@awaitEachGesture
                    }

                    val down = awaitFirstDown(requireUnconsumed = true)
                    val slop = viewConfiguration.touchSlop

                    // Phase 1 - long-press detection window. A clean release with no
                    // meaningful movement before the timeout is a tap; movement beyond the
                    // slop or a consumed/cancelled pointer aborts the gesture with no
                    // callbacks.
                    var anchor = down.position
                    val windowResult: LongPressWindowResult? = withTimeoutOrNull(
                        viewConfiguration.longPressTimeoutMillis
                    ) {
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: continue
                            if (change.isConsumed) {
                                return@withTimeoutOrNull LongPressWindowResult.Abort
                            }
                            anchor = change.position
                            if (!change.pressed) {
                                return@withTimeoutOrNull LongPressWindowResult.Released
                            }
                            if ((anchor - down.position).getDistance() > slop) {
                                return@withTimeoutOrNull LongPressWindowResult.Abort
                            }
                        }
                        LongPressWindowResult.Abort
                    }

                    when (windowResult) {
                        LongPressWindowResult.Released -> {
                            latestOnClick()
                            return@awaitEachGesture
                        }
                        LongPressWindowResult.Abort -> return@awaitEachGesture
                        null -> Unit // timeout reached - long press
                    }

                    // Phase 2 - long press succeeded.
                    latestOnLongPress()

                    // Phase 3 - wait for real post-long-press movement beyond the touch
                    // slop before starting the drag, then track the pointer until it goes
                    // up or is cancelled. Drag end is only reported if movement started; a
                    // stationary long-press release sends no drag callbacks.
                    var dragStarted = false
                    var lastRoot = rootOffset + anchor
                    while (true) {
                        val change = awaitPointerEvent().changes
                            .firstOrNull { it.id == down.id } ?: continue
                        if (change.isConsumed) {
                            if (dragStarted) latestOnDragEnd(lastRoot)
                            return@awaitEachGesture
                        }
                        if (!change.pressed) {
                            if (dragStarted) latestOnDragEnd(rootOffset + change.position)
                            return@awaitEachGesture
                        }
                        val root = rootOffset + change.position
                        if (!dragStarted) {
                            if ((change.position - anchor).getDistance() <= slop) {
                                continue
                            }
                            dragStarted = true
                            change.consume()
                            latestOnDragStart(root)
                            latestOnDragMove(root)
                        } else {
                            change.consume()
                            lastRoot = root
                            latestOnDragMove(root)
                        }
                    }
                }
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                alpha = dragAlpha
            }
            .semantics(mergeDescendants = true) {
                selected = isSelected
                onClick("Activate") {
                    onClick()
                    true
                }
                onLongClick("Select") {
                    onLongPress()
                    true
                }
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = when {
                isDragSource -> 8.dp
                isSelected -> 3.dp
                else -> 1.dp
            }
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(macroCaloriesColor())
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                val portionText = if (!entry.portionLabel.isNullOrBlank()) {
                    "${entry.portionLabel} · ${entry.portionG.toInt()}g"
                } else {
                    "${entry.portionG.toInt()}g"
                }
                Text(
                    text = portionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                NutritionRow(macros = entry.macros)
            }
        }
    }
}
