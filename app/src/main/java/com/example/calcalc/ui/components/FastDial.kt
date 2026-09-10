package com.example.calcalc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val HOURS_ON_A_CLOCK_FACE = 24f

/**
 * A duration preset drawn as a 24-hour clock face: the arc sweeping from 12 o'clock covers
 * the share of a day the fast takes, so 8h, 16h and 24h are distinguishable at a glance
 * rather than only by their labels.
 */
@Composable
fun FastDial(
    hours: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
) {
    val arcColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val tickColor = MaterialTheme.colorScheme.outline

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val strokeWidth = size.toPx() * 0.11f
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                val inset = strokeWidth / 2
                val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)

                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (hours / HOURS_ON_A_CLOCK_FACE).coerceAtMost(1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )

                // Quarter ticks, so the face reads as a clock rather than a progress bar.
                val centre = Offset(this.size.width / 2, this.size.height / 2)
                val outer = this.size.width / 2 - strokeWidth
                listOf(0f, 90f, 180f, 270f).forEach { angle ->
                    val radians = Math.toRadians(angle.toDouble() - 90)
                    val direction = Offset(kotlin.math.cos(radians).toFloat(), kotlin.math.sin(radians).toFloat())
                    drawLine(
                        color = tickColor,
                        start = centre + direction * (outer * 0.62f),
                        end = centre + direction * (outer * 0.82f),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
            Text(
                "${hours}h",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun FastDialRow(
    hourOptions: List<Int>,
    selectedHours: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        hourOptions.forEach { hours ->
            FastDial(
                hours = hours,
                selected = hours == selectedHours,
                onClick = { onSelect(hours) },
            )
        }
    }
}
