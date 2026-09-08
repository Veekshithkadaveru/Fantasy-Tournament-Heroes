package app.krafted.fantasyheroestournament.trial.joker

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
import androidx.compose.ui.layout.ContentScale
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
import app.krafted.fantasyheroestournament.domain.Cue
import app.krafted.fantasyheroestournament.domain.LocalGameFeedback
import app.krafted.fantasyheroestournament.tournament.HandOffToTournament
import app.krafted.fantasyheroestournament.tournament.TrialOutcome
import app.krafted.fantasyheroestournament.tournament.TrialSession
import app.krafted.fantasyheroestournament.tournament.TrialStat
import app.krafted.fantasyheroestournament.R
import kotlinx.coroutines.isActive
import kotlin.math.ceil

internal val Night = Color(0xFF120A22)
internal val Panel = Color(0xFF241338)
internal val Gold = Color(0xFFF3CD82)
internal val Candy = Color(0xFFFF6BC7)
internal val Orb = Color(0xFFB07BFF)
internal val Frost = Color(0xFF7FD8FF)
internal val Mint = Color(0xFF6BE7B4)
internal val Wrong = Color(0xFFFF6B7A)
internal val Muted = Color(0xFFB6A3C9)
internal val White = Color(0xFFF6F4EE)

@Composable
fun JokerTrialRoute(
    lifecycle: Lifecycle, viewModel: JokerViewModel = viewModel(),
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
    val feedback = LocalGameFeedback.current
    var handledTaps by rememberSaveable { mutableIntStateOf(0) }
    var handledRules by rememberSaveable { mutableIntStateOf(1) }
    var handledFreezes by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state?.correctTaps, state?.wrongTaps, state?.rules?.size, state?.freezeBonuses) {
        val current = state ?: return@LaunchedEffect
        val taps = current.correctTaps + current.wrongTaps
        // A flip and a wrong sweet both have to land without looking away from the grid.
        if (current.rules.size > handledRules) feedback.play(Cue.RULE_FLIP)
        if (current.freezeBonuses > handledFreezes) feedback.play(Cue.FREEZE)
        if (taps > handledTaps) {
            feedback.play(if (current.lastTap?.correct == true) Cue.TAP else Cue.PENALTY)
        }
        handledTaps = taps
        handledRules = current.rules.size
        handledFreezes = current.freezeBonuses
    }
    // Resolved up front: the hand-off runs from a click, long after composition.
    val timeUp = stringResource(R.string.joker_time_up)
    val survivedLabel = stringResource(R.string.joker_headline_survived)
    val correctLabel = stringResource(R.string.joker_stat_correct)
    val wrongLabel = stringResource(R.string.joker_stat_wrong)
    val freezeLabel = stringResource(R.string.joker_stat_freeze)
    val rulesLabel = stringResource(R.string.joker_stat_rules)
    state?.takeIf { prepared }?.let { value ->
        JokerTrialScreen(value, viewModel::start, viewModel::tap, viewModel::pause,
            viewModel::resume, viewModel::restart, viewModel::selectRound,
            onTournamentComplete = onTournamentComplete?.let { bank ->
                {
                    bank(TrialOutcome(
                        score = value.score,
                        headline = if (value.timedOut) timeUp else survivedLabel,
                        stats = listOf(
                            TrialStat(correctLabel, value.correctTaps.toString()),
                            TrialStat(wrongLabel, value.wrongTaps.toString()),
                            TrialStat(freezeLabel, value.freezeBonuses.toString()),
                            TrialStat(rulesLabel, value.rules.size.toString())
                        )
                    ))
                }
            })
    } ?: Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(color = Gold)
            Text(stringResource(R.string.joker_loading), color = Gold)
        }
    }
}

@Composable
fun JokerTrialScreen(
    state: JokerState, onStart: () -> Unit, onTap: (Int) -> Unit, onPause: () -> Unit,
    onResume: () -> Unit, onRestart: () -> Unit, onSelectRound: (Int) -> Unit,
    onTournamentComplete: (() -> Unit)? = null
) {
    var showReadyPause by remember { mutableStateOf(false) }
    BackHandler(state.phase != JokerPhase.READY || onTournamentComplete != null) {
        if (state.phase == JokerPhase.READY) showReadyPause = true
        else if (!state.paused && state.phase != JokerPhase.COMPLETE) onPause()
    }
    Box(Modifier.fillMaxSize().background(Night)) {
        Image(painterResource(R.drawable.back_5), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .24f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night.copy(alpha = .5f), Night.copy(alpha = .88f), Night))))
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            // The floor keeps the sixteen cells at a 48dp touch target; the column scrolls if the
            // screen is too short to hold everything at once.
            val arenaHeight = (maxHeight - 560.dp).coerceIn(300.dp, 470.dp)
            Column(Modifier.widthIn(max = 580.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GameHeader(state, onPause)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Eyebrow(stringResource(R.string.joker_subtitle), Gold)
                        Text(stringResource(R.string.joker_title), color = White, fontSize = 43.sp,
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, lineHeight = 48.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(listOf("I", "II", "III")[state.roundIndex], color = Gold, fontFamily = FontFamily.Serif, fontSize = 25.sp)
                        Text(roundName(state.roundIndex), color = Muted, fontSize = 12.sp)
                    }
                }
                Scoreboard(state)
                RuleBanner(state)
                JokerArena(state, onTap, Modifier.fillMaxWidth().height(arenaHeight))
                FeedbackText(state)
                RuleControl(state, onStart)
                if (state.phase == JokerPhase.READY && onTournamentComplete == null) RoundSelector(state.roundIndex, onSelectRound)
                else Text(stringResource(R.string.joker_center_hint), color = Muted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
        }
        if (state.paused || showReadyPause) TrialPauseDialog(
            onResume = { showReadyPause = false; onResume() },
            onRestart = onRestart, onTournamentComplete = onTournamentComplete)
        if (state.phase == JokerPhase.COMPLETE) {
            // In a tournament the result belongs to the bracket, not to a dialog here.
            if (onTournamentComplete != null) HandOffToTournament(onTournamentComplete)
            else TrialCompleteDialog(state, onRestart, onSelectRound)
        }
    }
}

@Composable
private fun GameHeader(state: JokerState, onPause: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            JesterIcon(Gold, Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(stringResource(R.string.joker_brand), color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(stringResource(R.string.joker_tournament), color = Muted, fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
        }
        val pauseLabel = stringResource(R.string.joker_pause)
        val enabled = state.phase != JokerPhase.READY && state.phase != JokerPhase.COMPLETE
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
private fun Scoreboard(state: JokerState) {
    // The score kicks each time it moves, so points landing is felt without reading the number.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state.score) {
        if (state.score > 0) {
            pop.snapTo(1.16f)
            pop.animateTo(1f, spring(dampingRatio = .38f, stiffness = Spring.StiffnessMedium))
        }
    }
    val urgent = state.remainingSeconds <= 5f && state.phase == JokerPhase.PLAYING
    val clock = rememberInfiniteTransition(label = "clock")
    val tick by clock.animateFloat(1f, if (urgent) .55f else 1f,
        infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "final seconds")

    Surface(color = Panel.copy(alpha = .86f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .16f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Eyebrow(stringResource(R.string.joker_score))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(state.score, label = "score") { score ->
                        Text(stringResource(R.string.joker_points_value, score), color = Gold, fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.testTag("score"))
                    }
                    Text(stringResource(R.string.joker_score_max), color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 5.dp, bottom = 5.dp))
                }
            }
            Box(Modifier.height(42.dp).width(1.dp).background(Muted.copy(alpha = .15f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow(stringResource(R.string.joker_time))
                Text(stringResource(R.string.joker_time_value, ceil(state.remainingSeconds).toInt()),
                    color = if (urgent) Wrong else White, fontSize = 25.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.graphicsLayer { alpha = if (urgent) tick else 1f }.testTag("timer"))
            }
            Box(Modifier.height(42.dp).width(1.dp).background(Muted.copy(alpha = .15f)))
            Column(horizontalAlignment = Alignment.End) {
                Eyebrow(stringResource(R.string.joker_correct))
                AnimatedContent(state.correctTaps, transitionSpec = {
                    (fadeIn(tween(140)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(110)) + slideOutVertically { -it / 2 })
                }, label = "correct taps") { taps ->
                    Text(stringResource(R.string.joker_correct_value, taps), color = White, fontSize = 22.sp,
                        modifier = Modifier.padding(top = 2.dp).testTag("correct"))
                }
            }
        }
    }
}

/**
 * The plan's prominent rule banner. It flares on every flip and drains a bar toward the next one,
 * so the rule and its remaining life are one object rather than two things to watch.
 */
@Composable
private fun RuleBanner(state: JokerState) {
    val accent = ruleAccent(state.rule)
    // Engine time drives the flare, so a pause freezes it exactly where the grid froze.
    val flare = if (state.phase == JokerPhase.PLAYING)
        1f - (state.ruleElapsed / RuleEngine.SWITCH_SECONDS).coerceIn(0f, 1f) else 0f
    val urgent = state.phase == JokerPhase.PLAYING && state.ruleRemaining <= 1.5f
    val label = stringResource(R.string.joker_rule_accessibility,
        stringResource(ruleBanner(state.rule)), ceil(state.ruleRemaining).toInt())
    val shape = RoundedCornerShape(18.dp)

    Column(Modifier.fillMaxWidth().graphicsLayer { val pop = 1f + .022f * flare; scaleX = pop; scaleY = pop }
        .clip(shape).background(Brush.horizontalGradient(listOf(
            Panel.copy(alpha = .92f), accent.copy(alpha = .14f + .2f * flare), Panel.copy(alpha = .92f))))
        .border((1f + 2f * flare).dp, accent.copy(alpha = .4f + .5f * flare), shape)
        .semantics { contentDescription = label; liveRegion = LiveRegionMode.Polite }.testTag("banner")
        .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(stringResource(R.string.joker_rule_banner), accent.copy(alpha = .8f))
        AnimatedContent(state.rule, transitionSpec = {
            (fadeIn(tween(160)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
        }, label = "active rule") { rule ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(ruleBanner(rule)), color = if (flare > .5f) White else accent,
                    fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    textAlign = TextAlign.Center, lineHeight = 25.sp)
                Text(stringResource(ruleHint(rule)), color = Muted, fontSize = 10.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
            }
        }
        // The bar is the rule's remaining life: full on a flip, gone when the banner changes.
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val y = size.height / 2f
            drawLine(Muted.copy(alpha = .2f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
            val left = size.width * state.ruleProgress
            if (left < size.width) {
                drawLine(if (urgent) Wrong else accent, Offset(left, y), Offset(size.width, y), size.height, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun FeedbackText(state: JokerState) {
    val tapped = state.lastTap?.takeIf { state.tapSeconds < RuleEngine.TAP_SECONDS && state.phase == JokerPhase.PLAYING }
    val flipped = state.phase == JokerPhase.PLAYING && state.ruleElapsed < RuleEngine.SWITCH_SECONDS && state.rules.size > 1
    val title = when {
        state.phase == JokerPhase.READY -> R.string.joker_ready_title
        tapped?.correct == true -> R.string.joker_correct_title
        tapped != null -> R.string.joker_wrong_title
        flipped -> R.string.joker_flip_title
        state.isFreeze -> R.string.joker_freeze_title
        else -> R.string.joker_play_title
    }
    val hint = when {
        state.phase == JokerPhase.READY -> R.string.joker_ready_hint
        tapped?.correct == true -> R.string.joker_correct_hint
        tapped != null -> R.string.joker_wrong_hint
        flipped -> R.string.joker_flip_hint
        state.isFreeze -> R.string.joker_freeze_hint
        else -> R.string.joker_play_hint
    }
    val color = when {
        tapped?.correct == true -> Mint
        tapped != null -> Wrong
        state.isFreeze && state.phase == JokerPhase.PLAYING -> Mint
        else -> Gold
    }
    AnimatedContent(title to hint, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) }, label = "chaos message") { copy ->
        Column(Modifier.fillMaxWidth().heightIn(min = 46.dp).semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(copy.first), color = color, fontFamily = FontFamily.Serif,
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
    1 -> R.string.joker_semifinal; 2 -> R.string.joker_final; else -> R.string.joker_qualifier
})

/** Each rule owns a colour, so the banner, the phase pips and the bursts all agree. */
internal fun ruleAccent(rule: JokerRule): Color = when (rule) {
    JokerRule.TAP_MATCH -> Gold
    JokerRule.TAP_INVERSE -> Frost
    JokerRule.TAP_COLOR -> Candy
    JokerRule.TAP_PURPLE -> Orb
    JokerRule.TAP_NONE -> Mint
}

internal fun ruleBanner(rule: JokerRule): Int = when (rule) {
    JokerRule.TAP_MATCH -> R.string.joker_rule_match
    JokerRule.TAP_INVERSE -> R.string.joker_rule_inverse
    JokerRule.TAP_COLOR -> R.string.joker_rule_color
    JokerRule.TAP_PURPLE -> R.string.joker_rule_purple
    JokerRule.TAP_NONE -> R.string.joker_rule_none
}

internal fun ruleHint(rule: JokerRule): Int = when (rule) {
    JokerRule.TAP_MATCH -> R.string.joker_rule_match_hint
    JokerRule.TAP_INVERSE -> R.string.joker_rule_inverse_hint
    JokerRule.TAP_COLOR -> R.string.joker_rule_color_hint
    JokerRule.TAP_PURPLE -> R.string.joker_rule_purple_hint
    JokerRule.TAP_NONE -> R.string.joker_rule_none_hint
}

internal fun itemDrawable(itemType: Int): Int = when (itemType) {
    RuleEngine.LOLLIPOP -> R.drawable.elem_1
    RuleEngine.PINK_GEM -> R.drawable.elem_3
    RuleEngine.PURPLE_ORB -> R.drawable.elem_4
    else -> R.drawable.elem_6
}

internal fun itemAccent(itemType: Int): Color = when (itemType) {
    RuleEngine.LOLLIPOP -> Gold
    RuleEngine.PINK_GEM -> Candy
    RuleEngine.PURPLE_ORB -> Orb
    else -> Frost
}

internal fun itemName(itemType: Int): Int = when (itemType) {
    RuleEngine.LOLLIPOP -> R.string.joker_item_lollipop
    RuleEngine.PINK_GEM -> R.string.joker_item_pink
    RuleEngine.PURPLE_ORB -> R.string.joker_item_purple
    else -> R.string.joker_item_candy
}

/** A jester's cap with a bell on every point: the Joker's mark at any size. */
@Composable
internal fun JesterIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * .3f); lineTo(size.width * .26f, size.height * .62f)
            lineTo(size.width * .5f, size.height * .08f); lineTo(size.width * .74f, size.height * .62f)
            lineTo(size.width, size.height * .3f); lineTo(size.width * .88f, size.height)
            lineTo(size.width * .12f, size.height); close()
        }
        drawPath(path, color)
        val bell = size.minDimension * .1f
        drawCircle(color, bell, Offset(0f, size.height * .3f))
        drawCircle(color, bell, Offset(size.width, size.height * .3f))
        drawCircle(color, bell, Offset(size.width * .5f, size.height * .08f))
    }
}
