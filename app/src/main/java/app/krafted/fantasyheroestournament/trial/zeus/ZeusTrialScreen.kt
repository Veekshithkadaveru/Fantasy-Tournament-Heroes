package app.krafted.fantasyheroestournament.trial.zeus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import app.krafted.fantasyheroestournament.data.TournamentConfig
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.tournament.HandOffToTournament
import app.krafted.fantasyheroestournament.tournament.TrialOutcome
import app.krafted.fantasyheroestournament.tournament.TrialSession
import app.krafted.fantasyheroestournament.tournament.TrialStat
import app.krafted.fantasyheroestournament.R
import kotlinx.coroutines.isActive
import kotlin.math.ceil

internal val Night = Color(0xFF080F21)
internal val Panel = Color(0xFF111C32)
internal val Gold = Color(0xFFF3CD82)
internal val Ice = Color(0xFF82E6FF)
internal val Muted = Color(0xFF9DACC4)
internal val White = Color(0xFFF6F4EE)
internal val Miss = Color(0xFFDBA4A3)

@Composable
fun ZeusTrialRoute(
    lifecycle: Lifecycle, viewModel: ZeusViewModel = viewModel(),
    session: TrialSession? = null,
    config: TournamentConfig = TrialConfigLoader.defaultConfig,
    onTournamentComplete: ((TrialOutcome) -> Unit)? = null
) {
    var prepared by remember(session?.id) { mutableStateOf(session == null) }
    LaunchedEffect(session?.id) {
        if (session != null) viewModel.prepareTournament(session, config)
        prepared = true
    }
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
    var handledStrikes by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state?.strikes?.size) {
        val current = state ?: return@LaunchedEffect
        if (current.strikes.size > handledStrikes && preferences.vibrateOn) {
            haptics.performHapticFeedback(if (current.lastStrike?.isHit == true)
                HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        }
        handledStrikes = current.strikes.size
    }
    // Resolved up front: the hand-off runs from a click, long after composition.
    val timeUp = stringResource(R.string.zeus_time_up)
    val allThrown = stringResource(R.string.zeus_headline_thrown)
    val perfectLabel = stringResource(R.string.zeus_stat_perfect)
    val hitLabel = stringResource(R.string.zeus_stat_hits)
    val missLabel = stringResource(R.string.zeus_stat_misses)
    val bestLabel = stringResource(R.string.zeus_stat_best)
    state?.takeIf { prepared }?.let { value ->
        ZeusTrialScreen(value, viewModel::start, viewModel::hold, viewModel::release,
            viewModel::cancelCharge, viewModel::pause, viewModel::resume,
            viewModel::restart, viewModel::selectRound,
            onTournamentComplete = onTournamentComplete?.let { bank ->
                {
                    bank(TrialOutcome(
                        score = value.score,
                        headline = if (value.timedOut) timeUp else allThrown,
                        stats = listOf(
                            TrialStat(perfectLabel, value.perfectCount.toString()),
                            TrialStat(hitLabel, value.strikes.count { it.isHit && !it.isPerfect }.toString()),
                            TrialStat(missLabel, value.strikes.count { !it.isHit }.toString()),
                            TrialStat(bestLabel, (value.strikes.maxOfOrNull { it.points } ?: 0).toString())
                        )
                    ))
                }
            })
    } ?: Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(color = Gold)
            Text(stringResource(R.string.zeus_loading), color = Gold)
        }
    }
}

@Composable
fun ZeusTrialScreen(
    state: ZeusState, onStart: () -> Unit, onHold: () -> Unit, onRelease: () -> Unit,
    onCancelCharge: () -> Unit, onPause: () -> Unit, onResume: () -> Unit,
    onRestart: () -> Unit, onSelectRound: (Int) -> Unit,
    onTournamentComplete: (() -> Unit)? = null
) {
    var showReadyPause by remember { mutableStateOf(false) }
    BackHandler(state.phase != ZeusPhase.READY || onTournamentComplete != null) {
        if (state.phase == ZeusPhase.READY) showReadyPause = true
        else if (!state.paused && state.phase != ZeusPhase.COMPLETE) onPause()
    }
    Box(Modifier.fillMaxSize().background(Night)) {
        Image(painterResource(R.drawable.back_3), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .24f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night.copy(alpha = .5f), Night.copy(alpha = .88f), Night))))
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            val arenaHeight = (maxHeight - 530.dp).coerceIn(220.dp, 480.dp)
            Column(Modifier.widthIn(max = 580.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GameHeader(state, onPause)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Eyebrow(stringResource(R.string.zeus_subtitle), Gold)
                        Text(stringResource(R.string.zeus_title), color = White, fontSize = 43.sp,
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, lineHeight = 48.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(listOf("I", "II", "III")[state.roundIndex], color = Gold, fontFamily = FontFamily.Serif, fontSize = 25.sp)
                        Text(roundName(state.roundIndex), color = Muted, fontSize = 12.sp)
                    }
                }
                Scoreboard(state)
                ZeusArena(state, Modifier.fillMaxWidth().height(arenaHeight))
                StrikeHistory(state)
                FeedbackText(state)
                ChargeControl(state, onStart, onHold, onRelease, onCancelCharge)
                if (state.phase == ZeusPhase.READY && onTournamentComplete == null) RoundSelector(state.roundIndex, onSelectRound)
                else Text(stringResource(R.string.zeus_center_hint), color = Muted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
        }
        if (state.paused || showReadyPause) TrialPauseDialog(
            onResume = { showReadyPause = false; onResume() },
            onRestart = onRestart, onTournamentComplete = onTournamentComplete)
        if (state.phase == ZeusPhase.COMPLETE) {
            // In a tournament the result belongs to the bracket, not to a dialog here.
            if (onTournamentComplete != null) HandOffToTournament(onTournamentComplete)
            else TrialCompleteDialog(state, onRestart, onSelectRound)
        }
    }
}

@Composable
private fun GameHeader(state: ZeusState, onPause: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            BoltIcon(Gold, Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(stringResource(R.string.zeus_brand), color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(stringResource(R.string.zeus_tournament), color = Muted, fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
        }
        val pauseLabel = stringResource(R.string.zeus_pause)
        val enabled = state.phase != ZeusPhase.READY && state.phase != ZeusPhase.COMPLETE
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
private fun Scoreboard(state: ZeusState) {
    Surface(color = Panel.copy(alpha = .86f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .16f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Eyebrow(stringResource(R.string.zeus_score))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(state.score, label = "score") { score ->
                        Text(stringResource(R.string.zeus_points_value, score), color = Gold, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("score"))
                    }
                    Text(stringResource(R.string.zeus_score_max), color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 5.dp, bottom = 5.dp))
                }
            }
            Box(Modifier.height(42.dp).width(1.dp).background(Muted.copy(alpha = .15f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow(stringResource(R.string.zeus_time))
                Text(stringResource(R.string.zeus_time_value, ceil(state.remainingSeconds).toInt()),
                    color = if (state.remainingSeconds <= 5f) Miss else White, fontSize = 25.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("timer"))
            }
            Box(Modifier.height(42.dp).width(1.dp).background(Muted.copy(alpha = .15f)))
            Column(horizontalAlignment = Alignment.End) {
                Eyebrow(stringResource(R.string.zeus_throw))
                Text(stringResource(R.string.zeus_throw_value,
                    (state.strikes.size + if (state.phase == ZeusPhase.STRIKE) 0 else 1).coerceAtMost(state.totalThrows), state.totalThrows),
                    color = White, fontSize = 22.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun StrikeHistory(state: ZeusState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(state.totalThrows) { index ->
            val result = state.strikes.getOrNull(index)
            val current = index == state.strikes.size && state.phase != ZeusPhase.COMPLETE
            val color = when { result?.isPerfect == true -> Gold; result?.isHit == true -> Ice; result != null -> Miss; current -> Gold; else -> Muted.copy(alpha = .4f) }
            val label = if (result == null) stringResource(R.string.zeus_strike_pending, index + 1)
                else stringResource(R.string.zeus_strike_scored, index + 1, result.points)
            Row(Modifier.weight(1f).height(35.dp).clip(RoundedCornerShape(9.dp))
                .background(if (current) Gold.copy(alpha = .1f) else Panel.copy(alpha = .75f))
                .border(1.dp, color.copy(alpha = if (current) .6f else .25f), RoundedCornerShape(9.dp))
                .semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (result == null) BoltIcon(color, Modifier.size(14.dp))
                else Text(if (result.points == 0) "—" else result.points.toString(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FeedbackText(state: ZeusState) {
    val title = when (state.phase) {
        ZeusPhase.READY -> R.string.zeus_ready_title
        ZeusPhase.CHARGING -> R.string.zeus_charge_title
        ZeusPhase.STRIKE -> when { state.lastStrike?.isPerfect == true -> R.string.zeus_perfect; state.lastStrike?.isHit == true -> R.string.zeus_hit; else -> R.string.zeus_miss }
        else -> R.string.zeus_aim_title
    }
    val hint = when (state.phase) {
        ZeusPhase.READY -> R.string.zeus_ready_hint
        ZeusPhase.CHARGING -> R.string.zeus_charge_hint
        ZeusPhase.STRIKE -> when { state.lastStrike?.isPerfect == true -> R.string.zeus_perfect_hint; state.lastStrike?.isHit == true -> R.string.zeus_hit_hint; else -> R.string.zeus_miss_hint }
        else -> R.string.zeus_aim_hint
    }
    AnimatedContent(title to hint, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) }, label = "strike message") { copy ->
        Column(Modifier.fillMaxWidth().heightIn(min = 46.dp).semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(copy.first), color = if (state.phase == ZeusPhase.CHARGING) Ice else Gold, fontFamily = FontFamily.Serif,
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
    1 -> R.string.zeus_semifinal; 2 -> R.string.zeus_final; else -> R.string.zeus_qualifier
})

@Composable
internal fun BoltIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * .58f, 0f); lineTo(size.width * .12f, size.height * .57f)
            lineTo(size.width * .46f, size.height * .57f); lineTo(size.width * .34f, size.height)
            lineTo(size.width * .91f, size.height * .38f); lineTo(size.width * .56f, size.height * .38f); close()
        }
        drawPath(path, color)
    }
}
