package com.example.calcalc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Deliberately hand-drawn rather than pulled from a chart library: two small read-only
 * charts do not justify the dependency, and this way they match the palette exactly.
 */

data class DayValue(val date: LocalDate, val value: Float)

/** Daily calories, with a dashed line marking the target. */
@Composable
fun CaloriesBarChart(
    days: List<DayValue>,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val underColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.tertiary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val lineColor = MaterialTheme.colorScheme.outline

    ChartFrame(days.isEmpty(), modifier) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val maxValue = maxOf(days.maxOfOrNull { it.value } ?: 0f, target.toFloat(), 1f) * 1.15f
            val slot = size.width / days.size
            val barWidth = (slot * 0.62f).coerceAtMost(28f.dp.toPx())

            days.forEachIndexed { index, day ->
                val barHeight = (day.value / maxValue) * size.height
                val left = index * slot + (slot - barWidth) / 2
                val color = when {
                    day.value <= 0f -> emptyColor
                    target > 0 && day.value > target -> overColor
                    else -> underColor
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, maxOf(barHeight, 2f)),
                    cornerRadius = CornerRadius(barWidth / 3, barWidth / 3),
                )
            }

            if (target > 0) {
                val y = size.height - (target / maxValue) * size.height
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5f.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
        }
    }
}

/**
 * Weight over time. [projection] is drawn dashed after the last real point so the goal
 * trajectory is visually distinct from logged data.
 */
@Composable
fun WeightLineChart(
    points: List<DayValue>,
    projection: List<DayValue> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val projectionColor = MaterialTheme.colorScheme.secondary

    ChartFrame(points.size < 2, modifier, emptyText = "Log a couple of weights to see a trend.") {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val all = points + projection
            val minDay = all.minOf { it.date.toEpochDay() }
            val maxDay = all.maxOf { it.date.toEpochDay() }
            val minValue = all.minOf { it.value }
            val maxValue = all.maxOf { it.value }
            val dayRange = (maxDay - minDay).coerceAtLeast(1L).toFloat()
            val valueRange = (maxValue - minValue).coerceAtLeast(0.5f)

            fun toOffset(point: DayValue) = Offset(
                x = ((point.date.toEpochDay() - minDay) / dayRange) * size.width,
                // Padding keeps the line off the very edge of the canvas.
                y = size.height - ((point.value - minValue) / valueRange) * (size.height * 0.85f) -
                    size.height * 0.075f,
            )

            val path = Path().apply {
                points.forEachIndexed { index, point ->
                    val offset = toOffset(point)
                    if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                }
            }
            drawPath(path, lineColor, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round))
            points.forEach { drawCircle(dotColor, radius = 3.5f.dp.toPx(), center = toOffset(it)) }

            if (projection.isNotEmpty()) {
                val projectionPath = Path().apply {
                    val start = toOffset(points.last())
                    moveTo(start.x, start.y)
                    projection.forEach { val offset = toOffset(it); lineTo(offset.x, offset.y) }
                }
                drawPath(
                    projectionPath,
                    projectionColor,
                    style = Stroke(
                        width = 2f.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ChartFrame(
    isEmpty: Boolean,
    modifier: Modifier,
    emptyText: String = "Nothing to chart yet.",
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        if (isEmpty) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            content()
        }
    }
}
