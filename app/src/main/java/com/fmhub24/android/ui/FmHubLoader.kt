package com.fmhub24.android.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * The FMHuB24 brand loader: five layers sliding in one after another, then the play triangle,
 * on the design's four-second loop (layers in by 15%, triangle 15–30%, everything fading out
 * after 85%). When the system has animations disabled the static mark is shown.
 */
@Composable
fun FmHubLoader(modifier: Modifier = Modifier.size(96.dp)) {
    val transition = rememberInfiniteTransition(label = "FMHuB24 logo loop")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
        ),
        label = "FMHuB24 logo clock",
    )
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val violet = Brush.linearGradient(listOf(Color(0xFF5A46E6), Color(0xFF3A7BF0)))
    val cyan = Brush.linearGradient(listOf(Color(0xFF22D6F0), Color(0xFF2F78EE)))

    Canvas(modifier) {
        val scale = size.width / 300f
        // The five layers are defined as polygons of the 300x300 logo viewBox.
        val barPolygons = listOf(
            arrayOf(30f to 30f, 165f to 108f, 30f to 108f) to violet,
            arrayOf(30f to 112f, 150f to 112f, 105f to 150f, 30f to 150f) to cyan,
            arrayOf(30f to 160f, 82f to 180f, 30f to 200f) to cyan,
            arrayOf(30f to 208f, 112f to 208f, 140f to 224f, 30f to 250f) to violet,
            arrayOf(30f to 254f, 135f to 254f, 30f to 286f) to cyan,
        )
        val triangle = arrayOf(170f to 100f, 282f to 165f, 158f to 225f)
        val highlight = arrayOf(170f to 100f, 282f to 165f, 214f to 158f, 170f to 134f)

        if (!animationsEnabled) {
            barPolygons.forEach { (points, brush) ->
                drawPath(polygon(scale, points, offsetX = 0f), brush)
            }
            drawPath(polygon(scale, triangle, offsetX = 0f), cyan)
            drawPath(
                polygon(scale, highlight, offsetX = 0f),
                Color.White.copy(alpha = 0.18f),
            )
        } else {
            barPolygons.forEachIndexed { index, (points, brush) ->
                val start = index * 0.015f
                val entered = FastOutSlowInEasing.transform(segment(progress, start, 0.15f))
                val alpha = entered * (1f - segment(progress, 0.85f, 1f))
                val offsetX = -60f * (1f - entered) * scale
                drawPath(polygon(scale, points, offsetX), brush, alpha = alpha)
            }

            val triangleAlpha = segment(progress, 0.15f, 0.30f) *
                (1f - segment(progress, 0.85f, 1f))
            drawPath(polygon(scale, triangle, offsetX = 0f), cyan, alpha = triangleAlpha)
            drawPath(
                polygon(scale, highlight, offsetX = 0f),
                Color.White,
                alpha = 0.18f * triangleAlpha,
            )
        }
    }
}

private fun segment(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun polygon(
    scale: Float,
    points: Array<Pair<Float, Float>>,
    offsetX: Float,
): Path = Path().apply {
    moveTo(points.first().first * scale + offsetX, points.first().second * scale)
    points.drop(1).forEach { point ->
        lineTo(point.first * scale + offsetX, point.second * scale)
    }
    close()
}
