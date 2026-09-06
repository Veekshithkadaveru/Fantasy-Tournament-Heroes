package app.krafted.fantasyheroestournament.trial.pilot

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.krafted.fantasyheroestournament.R
import kotlinx.coroutines.isActive
import kotlin.math.ceil

internal val Night = Color(0xFF10060D)
internal val Panel = Color(0xFF241019)
internal val Gold = Color(0xFFF3CD82)
internal val Sky = Color(0xFF8FDCFF)
internal val Ember = Color(0xFFFF7A62)
internal val Muted = Color(0xFFC0A3AB)
internal val White = Color(0xFFF6F4EE)

@Composable
fun PilotTrialRoute(lifecycle: Lifecycle, viewModel: PilotViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); viewModel.pause() }
    }
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                viewModel.frame((now - previous) / 1_000_000_000f)
                previous = now
            }
        }
    }
    val haptics = LocalHapticFeedback.current
    var handledBonuses by rememberSaveable { mutableIntStateOf(0) }
    var handledCrash by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state?.coinStreak, state?.isDead) {
        val current = state ?: return@LaunchedEffect
        val bonuses = current.coinStreak / PilotEngine.STREAK_LENGTH
        if (preferences.vibrateOn) {
            if (bonuses > handledBonuses) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (current.isDead && !handledCrash) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        handledBonuses = bonuses
        handledCrash = current.isDead
    }
    state?.let { value ->
        PilotTrialScreen(value, viewModel::start, viewModel::steer, viewModel::steerTo,
            viewModel::pause, viewModel::resume, viewModel::restart, viewModel::selectRound)
    } ?: Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(color = Gold)
            Text(stringResource(R.string.pilot_loading), color = Gold)
        }
    }
}

@Composable
fun PilotTrialScreen(
    state: PilotState, onStart: () -> Unit, onSteer: (Float) -> Unit, onSteerTo: (Float) -> Unit,
    onPause: () -> Unit, onResume: () -> Unit, onRestart: () -> Unit, onSelectRound: (Int) -> Unit
) {
    BackHandler(state.phase != PilotPhase.READY) {
        if (!state.paused && state.phase != PilotPhase.COMPLETE) onPause()
    }
    Box(Modifier.fillMaxSize().background(Night)) {
        Image(painterResource(R.drawable.back_1), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .24f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night.copy(alpha = .5f), Night.copy(alpha = .88f), Night))))
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            val arenaHeight = (maxHeight - 520.dp).coerceIn(240.dp, 500.dp)
            Column(Modifier.widthIn(max = 580.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GameHeader(state, onPause)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Eyebrow(stringResource(R.string.pilot_subtitle), Gold)
                        Text(stringResource(R.string.pilot_title), color = White, fontSize = 43.sp,
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, lineHeight = 48.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(listOf("I", "II", "III")[state.roundIndex], color = Gold, fontFamily = FontFamily.Serif, fontSize = 25.sp)
                        Text(roundName(state.roundIndex), color = Muted, fontSize = 12.sp)
                    }
                }
                Scoreboard(state)
                PilotArena(state, onSteer, Modifier.fillMaxWidth().height(arenaHeight))
                StreakTrack(state)
                FeedbackText(state)
                SteeringControl(state, onStart, onSteerTo)
                if (state.phase == PilotPhase.READY) RoundSelector(state.roundIndex, onSelectRound)
                else Text(stringResource(R.string.pilot_center_hint), color = Muted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
        }
        if (state.paused) TrialPauseDialog(onResume, onRestart)
        if (state.phase == PilotPhase.COMPLETE) TrialCompleteDialog(state, onRestart, onSelectRound)
    }
}

@Composable
private fun GameHeader(state: PilotState, onPause: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            PlaneIcon(Gold, Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(stringResource(R.string.pilot_brand), color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(stringResource(R.string.pilot_tournament), color = Muted, fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
        }
        val pauseLabel = stringResource(R.string.pilot_pause)
        val enabled = state.phase != PilotPhase.READY && state.phase != PilotPhase.COMPLETE
        Box(Modifier.size(48.dp).clip(CircleShape).background(Panel.copy(alpha = .8f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onPause)
            .semantics { contentDescription = pauseLabel }.testTag("pause"), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(16.dp)) {
                val color = if (enabled) White else Muted.copy(alpha = .4f)
                drawLine(color, Offset(size.width * .3f, 0f), Offset(size.width * .3f, size.height), 3.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * .7f, 0f), Offset(size.width * .7f, size.height), 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun Scoreboard(state: PilotState) {
    // The score kicks each time it moves, so points landing is felt without reading the number.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state.totalScore) {
        if (state.totalScore > 0) {
            pop.snapTo(1.16f)
            pop.animateTo(1f, spring(dampingRatio = .38f, stiffness = Spring.StiffnessMedium))
        }
    }
    val urgent = state.remainingSeconds <= 5f && state.phase == PilotPhase.FLYING
    val clock = rememberInfiniteTransition(label = "clock")
    val tick by clock.animateFloat(1f, if (urgent) .55f else 1f,
        infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "final seconds")

    Surface(color = Panel.copy(alpha = .86f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .16f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Eyebrow(stringResource(R.string.pilot_score))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(state.totalScore, label = "score") { score ->
                        Text(stringResource(R.string.pilot_points_value, score), color = Gold, fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.testTag("score"))
                    }
                    Text(stringResource(R.string.pilot_score_max), color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 5.dp, bottom = 5.dp))
                }
            }
            Box(Modifier.height(42.dp).width(1.dp).background(Muted.copy(alpha = .15f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow(stringResource(R.string.pilot_time))
                Text(stringResource(R.string.pilot_time_value, ceil(state.remainingSeconds).toInt()),
                    color = if (urgent) Ember else White, fontSize = 25.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.graphicsLayer { alpha = if (urgent) tick else 1f }.testTag("timer"))
            }
            Box(Modifier.height(42.dp).width(1.dp).background(Muted.copy(alpha = .15f)))
            Column(horizontalAlignment = Alignment.End) {
                Eyebrow(stringResource(R.string.pilot_coins))
                AnimatedContent(state.coinsCollected, transitionSpec = {
                    (fadeIn(tween(140)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(110)) + slideOutVertically { -it / 2 })
                }, label = "coins") { coins ->
                    Text(stringResource(R.string.pilot_coins_value, coins), color = White, fontSize = 22.sp,
                        modifier = Modifier.padding(top = 2.dp).testTag("coins"))
                }
            }
        }
    }
}

/** Eight pips counting down to the next streak bonus, the flight answer to Zeus's strike history. */
@Composable
private fun StreakTrack(state: PilotState) {
    val filled = if (state.coinStreak > 0 && state.streakProgress == 0) PilotEngine.STREAK_LENGTH else state.streakProgress
    val label = stringResource(R.string.pilot_streak_accessibility, filled, PilotEngine.STREAK_LENGTH, PilotEngine.STREAK_BONUS)
    // Filling springs across the row rather than snapping, so a streak building is visible motion.
    val progress by animateFloatAsState(filled.toFloat(),
        spring(dampingRatio = .5f, stiffness = Spring.StiffnessMediumLow), label = "streak fill")
    Row(Modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(PilotEngine.STREAK_LENGTH) { index ->
            val complete = index < filled
            val last = index == PilotEngine.STREAK_LENGTH - 1
            val color = when { complete && last -> Gold; complete -> Sky; else -> Muted.copy(alpha = .4f) }
            // 0 until this pip is reached, 1 once the spring has settled past it.
            val fill = (progress - index).coerceIn(0f, 1f)
            Row(Modifier.weight(1f).height(35.dp).clip(RoundedCornerShape(9.dp))
                .background(if (complete) color.copy(alpha = .1f + .1f * fill) else Panel.copy(alpha = .75f))
                .border((1f + fill).dp, color.copy(alpha = .25f + fill * .45f), RoundedCornerShape(9.dp)),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                CoinPip(color, Modifier.size(9.dp + 5.dp * fill)
                    .graphicsLayer { val overshoot = 1f + .3f * fill * (1f - fill) * 4f; scaleX = overshoot; scaleY = overshoot })
            }
        }
    }
}

@Composable
private fun FeedbackText(state: PilotState) {
    val crashed = state.impact != PilotImpact.NONE
    val title = when {
        state.phase == PilotPhase.READY -> R.string.pilot_ready_title
        state.impact == PilotImpact.WALL -> R.string.pilot_wall_title
        state.impact == PilotImpact.CLOUD -> R.string.pilot_cloud_title
        state.speedStep >= 2 -> R.string.pilot_fast_title
        else -> R.string.pilot_fly_title
    }
    val hint = when {
        state.phase == PilotPhase.READY -> R.string.pilot_ready_hint
        crashed -> R.string.pilot_crash_hint
        state.speedStep >= 2 -> R.string.pilot_fast_hint
        else -> R.string.pilot_fly_hint
    }
    AnimatedContent(title to hint, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) }, label = "flight message") { copy ->
        Column(Modifier.fillMaxWidth().heightIn(min = 46.dp).semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(copy.first), color = if (crashed) Ember else Gold, fontFamily = FontFamily.Serif,
                fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(stringResource(copy.second), color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun Eyebrow(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
}

@Composable
internal fun roundName(index: Int): String = stringResource(when (index) {
    1 -> R.string.pilot_semifinal; 2 -> R.string.pilot_final; else -> R.string.pilot_qualifier
})

@Composable
internal fun PlaneIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * .5f, 0f)
            lineTo(size.width * .62f, size.height * .42f)
            lineTo(size.width, size.height * .66f)
            lineTo(size.width, size.height * .78f)
            lineTo(size.width * .62f, size.height * .68f)
            lineTo(size.width * .58f, size.height * .9f)
            lineTo(size.width * .74f, size.height)
            lineTo(size.width * .26f, size.height)
            lineTo(size.width * .42f, size.height * .9f)
            lineTo(size.width * .38f, size.height * .68f)
            lineTo(0f, size.height * .78f)
            lineTo(0f, size.height * .66f)
            lineTo(size.width * .38f, size.height * .42f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
internal fun CoinPip(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(color.copy(alpha = .35f), size.minDimension / 2f)
        drawCircle(color, size.minDimension / 2f * .62f)
    }
}
