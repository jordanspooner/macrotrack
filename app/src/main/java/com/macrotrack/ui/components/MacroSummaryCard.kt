package com.macrotrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.DailySummary
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.macroCaloriesColor
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor
import com.macrotrack.ui.theme.overageColor
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MacroSummaryCard(
    summary: DailySummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null) return

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val kcalColor = macroCaloriesColor()
    val overColor = overageColor()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: kcal progress ring (120 x 120).
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(120.dp)) {
                    val stroke = 12.dp.toPx()
                    val trackStroke = 8.dp.toPx()
                    val diameter = size.minDimension
                    val radius = (diameter - stroke) / 2f
                    val topLeft = Offset(
                        (size.width - radius * 2) / 2f,
                        (size.height - radius * 2) / 2f
                    )
                    val arcSize = Size(radius * 2, radius * 2)

                    drawArc(
                        color = trackColor,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = trackStroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = kcalColor,
                        startAngle = 135f,
                        sweepAngle = min(summary.kcalPercent, 1f) * 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    if (summary.kcalPercent > 1f) {
                        val overageSweep = min(summary.kcalPercent - 1f, 0.5f) * 270f
                        drawArc(
                            color = overColor,
                            startAngle = 135f + 270f,
                            sweepAngle = overageSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${summary.logged.kcal.roundToInt()}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "/ ${summary.goals.kcal} kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Right column: stacked macro rows.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MacroRow(
                    label = "Protein",
                    logged = summary.logged.proteinG,
                    goal = summary.goals.proteinG,
                    percent = summary.proteinPercent,
                    color = macroProteinColor()
                )
                MacroRow(
                    label = "Carbs",
                    logged = summary.logged.carbsG,
                    goal = summary.goals.carbsG,
                    percent = summary.carbsPercent,
                    color = macroCarbsColor()
                )
                MacroRow(
                    label = "Fat",
                    logged = summary.logged.fatG,
                    goal = summary.goals.fatG,
                    percent = summary.fatPercent,
                    color = macroFatColor()
                )
            }
        }
    }
}

@Composable
private fun MacroRow(
    label: String,
    logged: Float,
    goal: Int,
    percent: Float,
    color: Color,
) {
    val isOver = percent > 1f
    val resolvedColor = if (isOver) overageColor() else color
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(resolvedColor, CircleShape))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${logged.roundToInt()} / ${goal}g",
                style = MaterialTheme.typography.labelLarge,
                color = if (isOver) overageColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.sm))
            Surface(
                color = resolvedColor.copy(alpha = 0.15f),
                shape = MacroTrackPillShape,
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .padding(horizontal = Spacing.xs),
            ) {
                Text(
                    "${(percent * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = resolvedColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(horizontal = Spacing.sm, vertical = (Spacing.xs / 2))
                        .align(Alignment.CenterVertically),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        MacroBar(
            progress = percent,
            color = color,
            overageColor = overageColor(),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(start = Spacing.lg, end = Spacing.sm),
        )
    }
}
