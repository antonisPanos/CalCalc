package com.example.calcalc.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Consumed-vs-target ring. Past 100% the ring switches colour rather than overflowing,
 * so being over budget is obvious at a glance.
 */
@Composable
fun CalorieRing(
    consumed: Int,
    target: Int,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val fraction = if (target > 0) consumed.toFloat() / target else 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "ringSweep")
    val over = consumed > target && target > 0

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val ringColor = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val remaining = target - consumed

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - stroke.width,
                this.size.height - stroke.width,
            )
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = abs(remaining).toString(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (remaining >= 0) "kcal left" else "kcal over",
                style = MaterialTheme.typography.labelLarge,
                color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$consumed / $target",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Thin horizontal bar used in the journal's day rows. */
@Composable
fun CalorieBar(
    consumed: Int,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (target > 0) (consumed.toFloat() / target).coerceIn(0f, 1f) else 0f
    val over = target > 0 && consumed > target
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill: Color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val radius = size.height / 2
        drawRoundRect(
            color = track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
        if (fraction > 0f) {
            drawRoundRect(
                color = fill,
                size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
        }
    }
}
