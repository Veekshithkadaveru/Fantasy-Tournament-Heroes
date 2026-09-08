package app.krafted.fantasyheroestournament.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Celebration draw passes. Every particle in here is derived from its own index
// and one animated progress value, so a field of a hundred pieces is a single
// draw pass that allocates nothing per frame and never recomposes its caller.
// ---------------------------------------------------------------------------

private const val ConfettiPieces = 54
private const val SparkCount = 22
private const val ShellCount = 6
private const val SparksPerShell = 15

/** The golden-ratio walk that spreads indices evenly across a width. */
private fun lane(index: Int): Float = (index * .618034f) % 1f

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

/**
 * A fall of paper slips. [progress] runs 0 to 1 across the whole fall; pieces
 * are staggered against it so the field arrives as a shower rather than a wall.
 */
@Composable
internal fun ConfettiFall(
    progress: Float,
    modifier: Modifier,
    colors: List<Color> = listOf(Gold, Mint, Parchment)
) {
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier) {
        repeat(ConfettiPieces) { i ->
            val lead = (i % 9) * .052f
            val fall = (progress - lead) / (1f - lead)
            if (fall <= 0f) return@repeat
            val x = lane(i) * size.width
            val y = fall * (size.height + 96.dp.toPx()) - 48.dp.toPx()
            val sway = sin((fall * 6f + i).toDouble()).toFloat() * 24.dp.toPx()
            val slip = Size(5.dp.toPx(), (9 + i % 4).dp.toPx())
            rotate(fall * 540f + i * 24f, Offset(x + sway, y)) {
                drawRect(colors[i % colors.size].copy(alpha = (1f - fall).coerceIn(0f, 1f) * .9f),
                    Offset(x + sway - slip.width / 2f, y - slip.height / 2f), slip)
            }
        }
    }
}

/**
 * The moment something lands: two rings thrown outward, a ring of rays, and a
 * scatter of sparks that slow as they go. [progress] is a one-shot 0 to 1.
 */
@Composable
internal fun SparkBurst(progress: Float, tint: Color, modifier: Modifier, rays: Int = 18) {
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val fade = (1f - progress).coerceIn(0f, 1f)

        repeat(2) { ring ->
            val spread = (progress - ring * .18f).coerceIn(0f, 1f)
            if (spread > 0f) {
                drawCircle(tint.copy(alpha = .35f * fade * (1f - ring * .4f)),
                    radius * (.42f + spread * .58f), style = Stroke((2.5f - ring).dp.toPx()))
            }
        }
        repeat(rays) { i ->
            val angle = (i * 2f * PI / rays).toFloat()
            val inner = radius * (.5f + progress * .28f)
            val outer = inner + radius * .16f * (1f - progress * .55f)
            drawLine(tint.copy(alpha = .7f * fade),
                Offset(center.x + inner * cos(angle), center.y + inner * sin(angle)),
                Offset(center.x + outer * cos(angle), center.y + outer * sin(angle)),
                2.dp.toPx(), StrokeCap.Round)
        }
        // Sparks carry further than the rays and drift down as they die.
        repeat(SparkCount) { i ->
            val angle = (i * 2f * PI / SparkCount).toFloat() + i * .21f
            val reach = radius * (.55f + (i % 5) * .13f) * easeOut(progress)
            val sag = progress * progress * radius * .22f
            drawCircle(
                (if (i % 3 == 0) Parchment else tint).copy(alpha = fade * .85f),
                (1.6f + (i % 3) * .7f).dp.toPx() * fade,
                Offset(center.x + reach * cos(angle), center.y + reach * sin(angle) + sag)
            )
        }
    }
}

/** Concentric shock rings, drawn from the strike outward. */
@Composable
internal fun ShockRings(progress: Float, tint: Color, modifier: Modifier) {
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        repeat(3) { ring ->
            val spread = (progress - ring * .14f).coerceIn(0f, 1f)
            if (spread <= 0f) return@repeat
            drawCircle(tint.copy(alpha = (1f - spread) * .3f),
                radius * (.3f + easeOut(spread) * 1.1f), style = Stroke((3f - ring).dp.toPx()))
        }
    }
}

/**
 * Shells that rise, hang, and break. They run off one looping clock with each
 * shell offset against it, so the sky is never empty and never synchronised.
 */
@Composable
internal fun Fireworks(tint: Color, modifier: Modifier) {
    val clock = rememberInfiniteTransition(label = "fireworks").animateFloat(0f, 1f,
        infiniteRepeatable(tween(6200, easing = LinearEasing)), label = "shells")
    Canvas(modifier) {
        repeat(ShellCount) { shell ->
            val phase = (clock.value + shell * .167f) % 1f
            val color = when (shell % 3) { 0 -> Gold; 1 -> tint; else -> Parchment }
            val x = (.12f + lane(shell * 3) * .76f) * size.width
            val apex = (.14f + ((shell * .29f) % 1f) * .3f) * size.height
            if (phase < .34f) drawShellRise(x, apex, phase / .34f, color)
            else drawShellBurst(x, apex, (phase - .34f) / .66f, color, shell)
        }
    }
}

private fun DrawScope.drawShellRise(x: Float, apex: Float, rise: Float, color: Color) {
    val y = size.height - (size.height - apex) * easeOut(rise)
    // A short trail behind the head, thinning as the shell slows near the apex.
    repeat(4) { t ->
        drawCircle(color.copy(alpha = (.5f - t * .11f) * (1f - rise)),
            (2.4f - t * .5f).dp.toPx(), Offset(x, y + t * 9.dp.toPx()))
    }
    drawCircle(color.copy(alpha = .9f), 2.6.dp.toPx(), Offset(x, y))
}

private fun DrawScope.drawShellBurst(x: Float, apex: Float, burst: Float, color: Color, shell: Int) {
    val reach = size.minDimension * (.16f + (shell % 3) * .035f) * easeOut(burst)
    val fade = ((1f - burst) * (1f - burst)).coerceIn(0f, 1f)
    val sag = burst * burst * size.height * .07f
    repeat(SparksPerShell) { i ->
        val angle = (i * 2f * PI / SparksPerShell).toFloat() + shell
        val stretch = if (i % 2 == 0) 1f else .72f
        drawCircle(color.copy(alpha = fade * .9f), 2.2.dp.toPx() * fade,
            Offset(x + reach * stretch * cos(angle), apex + reach * stretch * sin(angle) + sag))
    }
    // The flash at the heart of the break, gone almost at once.
    if (burst < .12f) drawCircle(Parchment.copy(alpha = (1f - burst / .12f) * .8f),
        reach * .3f, Offset(x, apex))
}
