package com.macrotrack.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.macrotrack.R
import com.macrotrack.domain.model.macroProgressSegments
import kotlin.math.roundToInt

internal fun renderKcalRing(
    context: android.content.Context,
    progress: Float,
    sizePx: Int,
    loggedKcal: Float,
    goalKcal: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = sizePx * (10f / 120f)
    val trackStroke = sizePx * (6f / 120f)
    // Keep the rounded stroke caps inside the bitmap; ImageView/Glance may clip edge pixels.
    val arcInset = stroke / 2f + (sizePx / 120f).coerceAtLeast(1f)
    val arcBounds = RectF(
        arcInset,
        arcInset,
        sizePx - arcInset,
        sizePx - arcInset,
    )
    val segments = macroProgressSegments(progress)

    drawArc(
        canvas = canvas,
        bounds = arcBounds,
        color = ContextCompat.getColor(context, R.color.widget_surface_variant),
        strokeWidth = trackStroke,
        sweep = 360f,
    )
    drawArc(
        canvas = canvas,
        bounds = arcBounds,
        color = ContextCompat.getColor(context, R.color.widget_calories),
        strokeWidth = stroke,
        sweep = segments.goalFraction * 360f,
    )
    if (segments.overageFraction > 0f) {
        drawArc(
            canvas = canvas,
            bounds = arcBounds,
            color = ContextCompat.getColor(context, R.color.widget_calories_overage),
            strokeWidth = stroke,
            sweep = segments.overageFraction * 360f,
        )
    }

    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.widget_on_surface)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sizePx * (25f / 120f)
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.widget_on_surface_variant)
        textAlign = Paint.Align.CENTER
        textSize = sizePx * (17f / 120f)
    }
    val valueText = loggedKcal.roundToInt().toString()
    val labelText = "/ $goalKcal"
    val valueMetrics = valuePaint.fontMetrics
    val labelMetrics = labelPaint.fontMetrics
    val valueHeight = valueMetrics.descent - valueMetrics.ascent
    val labelHeight = labelMetrics.descent - labelMetrics.ascent
    val lineGap = sizePx * (3f / 120f)
    val center = sizePx / 2f
    val blockTop = center - (valueHeight + lineGap + labelHeight) / 2f
    val valueBaseline = blockTop - valueMetrics.ascent
    val labelTop = blockTop + valueHeight + lineGap
    val labelBaseline = labelTop - labelMetrics.ascent
    canvas.drawText(
        valueText,
        center,
        valueBaseline,
        valuePaint,
    )
    canvas.drawText(
        labelText,
        center,
        labelBaseline,
        labelPaint,
    )
    return bitmap
}

private fun drawArc(
    canvas: Canvas,
    bounds: RectF,
    color: Int,
    strokeWidth: Float,
    sweep: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = strokeWidth
    }
    canvas.drawArc(bounds, -90f, sweep, false, paint)
}
