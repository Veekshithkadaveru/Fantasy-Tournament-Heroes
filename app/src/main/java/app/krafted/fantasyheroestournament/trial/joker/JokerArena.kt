package app.krafted.fantasyheroestournament.trial.joker

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.krafted.fantasyheroestournament.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun JokerArena(state: JokerState, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "arena")
    val ambient by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "confetti")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "freeze pulse")
    val frozen = state.isFreeze && state.phase == JokerPhase.PLAYING
    val description = stringResource(R.string.joker_grid_accessibility, stringResource(ruleBanner(state.rule)))
    val shape = RoundedCornerShape(24.dp)

    Box(modifier.clip(shape).background(Night).border(1.dp, Gold.copy(alpha = .26f), shape)
        .semantics { contentDescription = description }.testTag("arena")) {
        Image(painterResource(R.drawable.back_4), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .34f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Night.copy(alpha = .82f), Night.copy(alpha = .62f), Night.copy(alpha = .9f)))))
        Canvas(Modifier.matchParentSize()) { drawConfetti(if (state.paused) 0f else ambient) }
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Eyebrow(stringResource(R.string.joker_court), Gold)
                    Text(stringResource(R.string.joker_domain), color = White.copy(alpha = .6f), fontSize = 7.sp, letterSpacing = 1.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    LegendDot(Mint, stringResource(R.string.joker_legend_correct))
                    LegendDot(Wrong, stringResource(R.string.joker_legend_wrong))
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                JokerGrid(state, onTap)
            }
            PhasePips(state)
        }
        // A freeze is the one rule the grid itself has to announce: nothing here is tappable.
        if (frozen) Box(Modifier.matchParentSize().border((2f + 2f * pulse).dp, Mint.copy(alpha = .28f + .32f * pulse), shape))
    }
}

@Composable
private fun JokerGrid(state: JokerState, onTap: (Int) -> Unit) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side)) {
            Column(Modifier.fillMaxSize()) {
                repeat(state.gridSize) { row ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        repeat(state.gridSize) { column ->
                            val index = row * state.gridSize + column
                            val cell = state.cells.getOrNull(index)
                            if (cell == null) Spacer(Modifier.weight(1f))
                            else JokerTile(state, cell, index, row, column, onTap, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            TapPopup(state, side)
            FreezePayout(state)
        }
    }
}

@Composable
private fun JokerTile(
    state: JokerState, cell: JokerCell, index: Int, row: Int, column: Int,
    onTap: (Int) -> Unit, modifier: Modifier
) {
    val accent = itemAccent(cell.itemType)
    val taken = cell.mark == JokerMark.CORRECT
    val flash = cell.flash
    // A fresh id means the board dealt something new here, so the sweet springs into its cell.
    val appear = remember(cell.id) { Animatable(0f) }
    LaunchedEffect(cell.id) { appear.animateTo(1f, spring(dampingRatio = .5f, stiffness = Spring.StiffnessMediumLow)) }
    val name = stringResource(itemName(cell.itemType))
    val label = if (taken && flash > .5f) stringResource(R.string.joker_cell_taken, row + 1, column + 1)
    else stringResource(R.string.joker_cell, name, row + 1, column + 1)
    val border = when (cell.mark) {
        JokerMark.CORRECT -> Mint.copy(alpha = .9f * (1f - flash) + .2f)
        JokerMark.WRONG -> Wrong.copy(alpha = .9f * (1f - flash) + .2f)
        else -> accent.copy(alpha = .24f)
    }
    val shape = RoundedCornerShape(14.dp)

    Box(modifier.padding(3.dp)) {
        Box(Modifier.fillMaxSize().clip(shape).background(Panel.copy(alpha = .68f))
            .border(if (cell.locked) 2.dp else 1.dp, border, shape)
            .clickable(enabled = state.canTap, role = Role.Button) { onTap(index) }
            .semantics { contentDescription = label }.testTag("cell_$index")) {
            Image(painterResource(itemDrawable(cell.itemType)), null,
                Modifier.fillMaxSize().padding(7.dp).graphicsLayer {
                    // A taken sweet swells as it leaves; a wrong one flinches in place.
                    val leaving = if (taken) 1f + flash * .45f else 1f
                    val land = appear.value.coerceIn(0f, 1.15f)
                    scaleX = land * leaving; scaleY = land * leaving
                    alpha = if (taken) 1f - flash else appear.value.coerceAtMost(1f)
                    if (cell.mark == JokerMark.WRONG) translationX = sin(flash * 32f) * (1f - flash) * 5.dp.toPx()
                })
            if (cell.locked) Canvas(Modifier.matchParentSize()) { drawTapMark(taken, flash) }
        }
    }
}

/** The plan's tap feedback: a green ring burst for a correct sweet, a red cross for a wrong one. */
private fun DrawScope.drawTapMark(correct: Boolean, flash: Float) {
    val fade = 1f - flash
    val center = Offset(size.width / 2f, size.height / 2f)
    if (correct) {
        drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = fade * .3f), Color.Transparent), center, size.minDimension * .6f),
            size.minDimension * .6f, center)
        drawCircle(Mint.copy(alpha = fade * .85f), size.minDimension * (.2f + flash * .5f), center,
            style = Stroke((3.5f * fade + .8f).dp.toPx()))
        repeat(8) { spark ->
            val angle = spark * (PI.toFloat() / 4f)
            val radius = size.minDimension * (.2f + flash * .44f)
            drawCircle(Mint.copy(alpha = fade), (1.8f * fade).dp.toPx(),
                center + Offset(cos(angle) * radius, sin(angle) * radius))
        }
    } else {
        drawRect(Wrong.copy(alpha = fade * .18f))
        val arm = size.minDimension * (.2f + flash * .12f)
        val stroke = (4.5f * fade + 1f).dp.toPx()
        drawLine(Wrong.copy(alpha = fade), center + Offset(-arm, -arm), center + Offset(arm, arm), stroke, StrokeCap.Round)
        drawLine(Wrong.copy(alpha = fade), center + Offset(arm, -arm), center + Offset(-arm, arm), stroke, StrokeCap.Round)
    }
}

/** The score a tap earned, floating up from the cell it was taken in. */
@Composable
private fun BoxScope.TapPopup(state: JokerState, side: Dp) {
    val tap = state.lastTap ?: return
    val life = (state.tapSeconds / RuleEngine.TAP_SECONDS).coerceIn(0f, 1f)
    if (life >= 1f || state.phase != JokerPhase.PLAYING) return
    val cell = side / state.gridSize
    Text(stringResource(if (tap.correct) R.string.joker_gain else R.string.joker_loss, tap.points),
        color = if (tap.correct) Mint else Wrong, fontSize = 24.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.align(Alignment.TopStart)
            .offset(x = cell * (tap.index % state.gridSize) + cell / 2, y = cell * (tap.index / state.gridSize) + cell / 2)
            .graphicsLayer {
                alpha = 1f - life * life
                val pop = 1f + .3f * (1f - (1f - life).let { it * it })
                scaleX = pop; scaleY = pop
                translationX = -size.width / 2f
                translationY = -size.height / 2f - life * 46.dp.toPx()
            })
}

/** A survived freeze is the trial's biggest single payout, so it lands across the whole board. */
@Composable
private fun BoxScope.FreezePayout(state: JokerState) {
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(state.freezeBonuses) {
        if (state.freezeBonuses > 0) { reveal.snapTo(0f); reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    }
    if (reveal.value >= 1f) return
    Text(stringResource(R.string.joker_freeze_popup, RuleEngine.FREEZE_BONUS), color = Mint,
        fontSize = 30.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.align(Alignment.Center).graphicsLayer {
            val life = reveal.value
            alpha = (1f - life * life) * (life * 6f).coerceAtMost(1f)
            val pop = 1f + .35f * life
            scaleX = pop; scaleY = pop
            translationY = -life * 40.dp.toPx()
        })
}

/** One pip per rule the round has room for: those survived, the one running, those still to come. */
@Composable
private fun PhasePips(state: JokerState) {
    val current = (state.rules.size - 1).coerceIn(0, state.totalPhases - 1)
    val label = stringResource(R.string.joker_phase_accessibility, current + 1, state.totalPhases)
    Row(Modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(state.totalPhases) { index ->
            val rule = state.rules.getOrNull(index)
            val active = index == current && state.phase == JokerPhase.PLAYING
            val color = rule?.let { ruleAccent(it) } ?: Muted
            // The running pip drains with its rule, so the countdown is readable without the banner.
            val fill = when {
                rule == null -> 0f
                active -> 1f - state.ruleProgress
                else -> 1f
            }
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Muted.copy(alpha = .18f))) {
                Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(color.copy(alpha = if (active) .95f else .6f)))
            }
        }
    }
}

private fun DrawScope.drawConfetti(ambient: Float) {
    val colors = listOf(Candy, Orb, Gold, Frost)
    repeat(22) { index ->
        val progress = (ambient + index * .045f) % 1f
        val x = ((index * .137f) % 1f) * size.width + sin(progress * 2f * PI.toFloat() + index) * 12f
        val y = size.height * (1f - progress)
        val fade = sin(progress * PI.toFloat()) * .45f
        val half = (1.4f + index % 3).dp.toPx()
        // Diamonds, not dots: the harlequin pattern the Joker wears.
        rotate(progress * 320f + index * 27f, Offset(x, y)) {
            drawRect(colors[index % colors.size].copy(alpha = fade), Offset(x - half, y - half), Size(half * 2f, half * 2f))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(4.dp).background(color, CircleShape))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}
