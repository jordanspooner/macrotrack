package com.macrotrack.ui.add

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import com.macrotrack.ui.theme.MacroCarbs
import com.macrotrack.ui.theme.MacroFat
import com.macrotrack.ui.theme.MacroProtein

/**
 * A simple donut/pie chart showing how the three macros contribute to the kcal
 * of the given [macros]. Calories remain empty (background) when there are none.
 */
@Composable
fun MacroPieChart(
    macros: Macros,
    modifier: Modifier = Modifier,
    diameter: Dp = 140.dp,
    centerText: String? = null
) {
    val proteinKcal = macros.proteinG * 4f
    val carbsKcal = macros.carbsG * 4f
    val fatKcal = macros.fatG * 9f
    val total = proteinKcal + carbsKcal + fatKcal

    Canvas(modifier = modifier.size(diameter)) {
        if (total <= 0f) return@Canvas
        val stroke = size.minDimension * 0.22f
        val radius = (size.minDimension - stroke) / 2f
        val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
        val arcSize = Size(radius * 2, radius * 2)

        val segments = listOf(
            proteinKcal to MacroProtein,
            carbsKcal to MacroCarbs,
            fatKcal to MacroFat
        )
        var startAngle = -90f
        for ((value, color) in segments) {
            val sweep = (value / total) * 360f
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
                startAngle += sweep
            }
        }
    }
}
