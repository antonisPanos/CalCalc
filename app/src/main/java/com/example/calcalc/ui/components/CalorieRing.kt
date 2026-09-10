package com.example.calcalc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
    size: Dp = 200.dp,
) {
    val over = consumed > target && target > 0
    val remaining = target - consumed

    RingGauge(
        progress = if (target > 0) consumed.toFloat() / target else 0f,
        color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        modifier = modifier,
        size = size,
    ) {
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
                color = if (over) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
        drawRoundRect(color = track, cornerRadius = CornerRadius(radius, radius))
        if (fraction > 0f) {
            drawRoundRect(
                color = fill,
                size = Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}
