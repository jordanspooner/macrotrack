package com.macrotrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor

@Composable
fun MacroDonut(
    macros: Macros,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    centerText: String? = null,
) {
    val proteinKcal = macros.proteinG * 4f
    val carbsKcal = macros.carbsG * 4f
    val fatKcal = macros.fatG * 9f
    val total = proteinKcal + carbsKcal + fatKcal
    val track = MaterialTheme.colorScheme.surfaceVariant
    val proteinColor = macroProteinColor()
    val carbsColor = macroCarbsColor()
    val fatColor = macroFatColor()

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(diameter)) {
        Canvas(modifier = Modifier.size(diameter)) {
            if (total <= 0f) return@Canvas
            val stroke = size.minDimension * 0.16f
            val radius = (size.minDimension - stroke) / 2f
            val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val segments = listOf(
                proteinKcal to proteinColor,
                carbsKcal to carbsColor,
                fatKcal to fatColor,
            )
            var startAngle = -90f
            for ((value, color) in segments) {
                val sweep = (value / total) * 360f
                if (sweep > 0f) {
                    drawArc(color, startAngle, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                    startAngle += sweep
                }
            }
        }
        centerText?.let {
            Text(it, style = MaterialTheme.typography.titleLarge)
        }
    }
}
