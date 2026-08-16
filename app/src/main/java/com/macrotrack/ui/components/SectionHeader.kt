package com.macrotrack.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.macroCaloriesColor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Compact two-tier section header. First row: meal name, kcal
 * actual/goal (when [goalMacros] is provided; actual kcal only otherwise) and
 * the collapse chevron. Second row: a [MealMacroMeter] when per-meal goals are
 * enabled, or an actual-only [MealMacroSummary] when they are not.
 *
 * An empty meal ([hasEntries] = false) keeps only the name and chevron, hides
 * all kcal and macro meter data, and renders its left status bar in a neutral
 * outline color instead of the brand green used for populated meals. Tapping
 * anywhere toggles collapse/expand.
 */
@Composable
fun SectionHeader(
    name: String,
    totalMacros: Macros,
    goalMacros: Macros?,
    hasEntries: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isExpanded) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .animateContentSize()
            .background(background)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (hasEntries) {
                            brandPrimary()
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                    .testTag("section-status-bar")
                    .semantics {
                        contentDescription = if (hasEntries) {
                            "Meal status: populated"
                        } else {
                            "Meal status: empty"
                        }
                    }
            )
            Spacer(modifier = Modifier.width(Spacing.md))

            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            if (hasEntries) {
                if (goalMacros != null) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = macroCaloriesColor())) {
                                append("${totalMacros.kcal.roundToInt()}")
                            }
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(" / ${goalMacros.kcal.roundToInt()} kcal")
                            }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    MealKcalRing(
                        progress = if (goalMacros.kcal > 0) totalMacros.kcal / goalMacros.kcal else 0f,
                    )
                } else {
                    Text(
                        text = "${totalMacros.kcal.roundToInt()} kcal",
                        style = MaterialTheme.typography.titleSmall,
                        color = macroCaloriesColor(),
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(24.dp),
            )
        }

        if (hasEntries) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            if (goalMacros != null) {
                MealMacroMeter(
                    actual = totalMacros,
                    goal = goalMacros,
                )
            } else {
                MealMacroSummary(
                    macros = totalMacros,
                )
            }
        }
    }
}

@Composable
private fun MealKcalRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = macroCaloriesColor()
    Canvas(
        modifier = modifier.size(28.dp),
    ) {
        val stroke = 3.dp.toPx()
        val diameter = min(this.size.width, this.size.height) - stroke
        val topLeft = Offset(
            x = (this.size.width - diameter) / 2f,
            y = (this.size.height - diameter) / 2f,
        )
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (clamped > 0f) {
            drawArc(
                color = fillColor,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

