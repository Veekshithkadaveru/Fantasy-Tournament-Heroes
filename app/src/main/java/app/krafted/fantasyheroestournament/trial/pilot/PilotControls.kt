package app.krafted.fantasyheroestournament.trial.pilot

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.krafted.fantasyheroestournament.R
import kotlin.math.abs

/** Begin button before take-off, flight stick during the run: one slot, like Zeus's charge control. */
@Composable
internal fun SteeringControl(state: PilotState, onStart: () -> Unit, onSteerTo: (Float) -> Unit) {
    val ready = state.phase == PilotPhase.READY
    val enabled = state.canSteer
    val scale by animateFloatAsState(if (enabled) 1f else .985f, tween(160), label = "stick press")
    val currentSteerTo by rememberUpdatedState(onSteerTo)
    val currentState by rememberUpdatedState(state)
    val description = stringResource(R.string.pilot_steer_accessibility)
    val shape = RoundedCornerShape(18.dp)

    if (ready) {
        // The waiting button breathes and its plane strains upward: the trial is ready to launch.
        val idle = rememberInfiniteTransition(label = "launch")
        val breathe by idle.animateFloat(1f, 1.018f,
            infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
        val lift by idle.animateFloat(0f, -3f,
            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lift")
        Box(Modifier.fillMaxWidth().heightIn(min = 66.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }
            .clip(shape).background(Brush.verticalGradient(listOf(Color(0xFFF6DDA0), Color(0xFFD9AA58))))
            .border(1.dp, Gold, shape).clickable(role = Role.Button, onClick = onStart).testTag("launch"),
            contentAlignment = Alignment.Center) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaneIcon(Night, Modifier.size(24.dp).graphicsLayer { translationY = lift })
                Text(stringResource(R.string.pilot_begin), color = Night, fontSize = 13.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape).background(Panel.copy(alpha = .9f))
        .border(1.dp, if (enabled) Sky.copy(alpha = .55f) else Muted.copy(alpha = .2f), shape)
        .pointerInput(Unit) {
            // Stable key: the corridor recomposes every frame and must not cancel an active touch.
            // One gesture pass handles tap and drag alike, so the plane goes exactly where you press.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!currentState.canSteer) return@awaitEachGesture
                val track = size.width.coerceAtLeast(1)
                down.consume()
                currentSteerTo(down.position.x / track)
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        if (change.pressed) { currentSteerTo(change.position.x / track); change.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .semantics {
            contentDescription = description
            if (!enabled) disabled()
            progressBarRangeInfo = ProgressBarRangeInfo(currentState.planeX, 0f..1f)
            setProgress { value -> if (enabled) { currentSteerTo(value); true } else false }
        }
        .onKeyEvent { event ->
            val nudge = when (event.key) {
                Key.DirectionLeft, Key.A -> -STEER_KEY_STEP
                Key.DirectionRight, Key.D -> STEER_KEY_STEP
                else -> return@onKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown && currentState.canSteer) {
                currentSteerTo(currentState.targetX + nudge)
            }
            true
        }.focusable(enabled).padding(vertical = 14.dp, horizontal = 16.dp).testTag("stick"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(stringResource(if (state.isDead) R.string.pilot_down else R.string.pilot_steer),
            color = if (state.isDead) Ember else Sky, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        // How hard the stick is over, used to swell the knob while a turn is being held.
        val pull = (abs(state.targetX - state.planeX) * 6f).coerceIn(0f, 1f)
        val swell by animateFloatAsState(if (enabled) pull else 0f, tween(120), label = "stick pull")
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val y = size.height / 2f
            val inset = 10.dp.toPx()
            val span = size.width - inset * 2f
            drawLine(Muted.copy(alpha = .22f), Offset(inset, y), Offset(size.width - inset, y), 5.dp.toPx(), StrokeCap.Round)
            val left = inset + state.corridorLeft * span
            val right = inset + state.corridorRight * span
            drawLine(Sky.copy(alpha = if (enabled) .5f else .2f), Offset(left, y), Offset(right, y), 5.dp.toPx(), StrokeCap.Round)
            val knob = inset + state.planeX * span
            val target = inset + state.targetX * span
            // The gap between where the plane is and where it is heading, drawn as the turn itself.
            if (enabled && swell > .02f) {
                drawLine(Gold.copy(alpha = .45f * swell), Offset(knob, y), Offset(target, y), 3.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(Gold.copy(alpha = .18f + swell * .22f), (12f + swell * 5f).dp.toPx(), Offset(knob, y))
            drawCircle(if (enabled) Gold else Muted, (7f + swell * 1.5f).dp.toPx(), Offset(knob, y))
            if (enabled) drawCircle(White.copy(alpha = .55f), 3.dp.toPx(), Offset(target, y))
        }
    }
}

@Composable
internal fun RoundSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val isSelected = index == selected
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Gold.copy(alpha = .1f) else Color.Transparent)
                .border(1.dp, if (isSelected) Gold.copy(alpha = .35f) else Muted.copy(alpha = .15f), RoundedCornerShape(12.dp))
                .clickable(role = Role.Tab) { onSelect(index) }.semantics { this.selected = isSelected }, contentAlignment = Alignment.Center) {
                Text(roundName(index), color = if (isSelected) Gold else Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
internal fun TrialPauseDialog(onResume: () -> Unit, onRestart: () -> Unit) {
    GameDialog(onDismiss = onResume) {
        PlaneIcon(Gold, Modifier.size(42.dp))
        Eyebrow(stringResource(R.string.pilot_paused), Gold)
        Text(stringResource(R.string.pilot_pause_hint), color = White, textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 24.sp)
        GoldButton(stringResource(R.string.pilot_resume), onResume)
        Text(stringResource(R.string.pilot_restart), color = Muted, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onRestart).padding(16.dp))
    }
}

@Composable
internal fun TrialCompleteDialog(state: PilotState, onRestart: () -> Unit, onSelectRound: (Int) -> Unit) {
    GameDialog(onDismiss = onRestart) {
        Box(Modifier.size(78.dp).background(Gold.copy(alpha = .08f), CircleShape).border(1.dp, Gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
            PlaneIcon(Gold, Modifier.size(42.dp))
        }
        Eyebrow(stringResource(when {
            state.impact == PilotImpact.WALL -> R.string.pilot_result_wall
            state.impact == PilotImpact.CLOUD -> R.string.pilot_result_storm
            else -> R.string.pilot_complete
        }), if (state.isDead) Ember else Gold)
        Text(stringResource(when { state.totalScore >= 800 -> R.string.pilot_result_great
            state.totalScore >= 390 -> R.string.pilot_result_good; else -> R.string.pilot_result_try }),
            color = White, fontSize = 26.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.pilot_points_value, state.totalScore), color = Gold, fontSize = 64.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.testTag("final_score"))
            Text(stringResource(R.string.pilot_score_max), color = Muted, fontSize = 14.sp)
        }
        Text(stringResource(R.string.pilot_result_detail, state.survivalTimeSeconds.toInt(),
            state.survivalPoints, state.coinsCollected, state.coinPoints), color = White, fontSize = 13.sp, textAlign = TextAlign.Center)
        val bonuses = state.coinStreak / PilotEngine.STREAK_LENGTH
        if (bonuses > 0) Text(pluralStringResource(R.plurals.pilot_streak_bonus, bonuses, bonuses), color = Gold, fontSize = 12.sp)
        GoldButton(stringResource(R.string.pilot_replay), onRestart)
        Eyebrow(stringResource(R.string.pilot_difficulty))
        RoundSelector(state.roundIndex, onSelectRound)
    }
}

@Composable
private fun GameDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(max = 430.dp).fillMaxWidth(), color = Night,
            shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .4f))) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
        }
    }
}

@Composable
private fun GoldButton(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFFF6DDA0), Color(0xFFD9AA58))))
        .clickable(role = Role.Button, onClick = onClick).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Night, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

private const val STEER_KEY_STEP = .07f
