package com.example.calcalc.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import kotlin.math.roundToInt

/**
 * Route transitions for [androidx.navigation3.ui.NavDisplay].
 *
 * The library defaults have two problems on a back gesture. The predictive spec is
 * `scaleOut(0.7f)` with no fade at all, so the screen you are leaving ends the animation at
 * 70% size and *full opacity* — it hangs there, fully painted, until AnimatedContent finally
 * disposes it. And the committed pop is a 700 ms cross-fade, which stretches that last
 * opaque frame out long enough to read as a stall.
 *
 * These specs always drive the outgoing screen to alpha 0, and do it in roughly a third of
 * the time.
 */
object NavTransitions {

    // Material 3 emphasized easing: slow to leave, fast to arrive.
    private val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    private const val PUSH_MS = 300
    private const val POP_MS = 260

    /**
     * How far into a back gesture the leaving screen stays fully opaque. Fading from the
     * first pixel of drag would make a half-finished, cancellable gesture look committed —
     * but only just enough for that, because the screen should be gone well before the
     * shrink finishes.
     */
    private const val PREDICTIVE_FADE_DELAY_MS = 50
    private const val PREDICTIVE_FADE_MS = 150
    private const val PREDICTIVE_MS = 320

    /** Fraction of the width the leaving screen slides toward the gesture's edge. */
    private const val PREDICTIVE_SLIDE = 0.08f

    /** Going deeper: the new screen grows in over the old one. */
    fun <T : Any> push(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        ContentTransform(
            targetContentEnter = fadeIn(tween(220, delayMillis = 40)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(PUSH_MS, easing = Emphasized)),
            initialContentExit = fadeOut(tween(160, easing = EmphasizedAccelerate)) +
                scaleOut(targetScale = 1.05f, animationSpec = tween(PUSH_MS, easing = Emphasized)),
            targetContentZIndex = 1f,
            sizeTransform = null,
        )
    }

    /** Coming back without a gesture — the back arrow, or a tab switch home. */
    fun <T : Any> pop(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        ContentTransform(
            targetContentEnter = fadeIn(tween(200, delayMillis = 40)) +
                scaleIn(initialScale = 1.05f, animationSpec = tween(POP_MS, easing = Emphasized)),
            // Out well before the scale finishes, so the shrink is uncovering the screen
            // below rather than dragging a visible ghost along with it.
            initialContentExit = fadeOut(tween(110, easing = EmphasizedAccelerate)) +
                scaleOut(targetScale = 0.92f, animationSpec = tween(POP_MS, easing = Emphasized)),
            // Negative so the screen being dismissed stays on top and uncovers the one
            // beneath it, rather than being painted over.
            targetContentZIndex = -1f,
            sizeTransform = null,
        )
    }

    /**
     * The back gesture. This transition is seeked by the drag, so the shape of each curve is
     * what the finger feels: shrink and slide immediately, hold opacity while the gesture is
     * still cancellable, then fade out over the tail.
     */
    fun <T : Any> predictivePop():
        AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = { edge ->
        // Swiping in from the left edge pushes the leaving screen right, and vice versa.
        val direction = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1

        ContentTransform(
            targetContentEnter = fadeIn(tween(120)),
            initialContentExit = scaleOut(
                targetScale = 0.86f,
                animationSpec = tween(PREDICTIVE_MS, easing = Emphasized),
            ) + slideOutHorizontally(
                animationSpec = tween(PREDICTIVE_MS, easing = Emphasized),
            ) { width -> (width * PREDICTIVE_SLIDE * direction).roundToInt() } + fadeOut(
                tween(
                    durationMillis = PREDICTIVE_FADE_MS,
                    delayMillis = PREDICTIVE_FADE_DELAY_MS,
                    easing = EmphasizedAccelerate,
                )
            ),
            targetContentZIndex = -1f,
            sizeTransform = null,
        )
    }
}
