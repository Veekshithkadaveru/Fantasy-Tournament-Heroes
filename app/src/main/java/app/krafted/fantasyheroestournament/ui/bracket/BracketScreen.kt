package app.krafted.fantasyheroestournament.ui.bracket

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.krafted.fantasyheroestournament.R
import app.krafted.fantasyheroestournament.data.TournamentConfig
import app.krafted.fantasyheroestournament.domain.Rank
import app.krafted.fantasyheroestournament.tournament.*
import java.util.Locale

/** A perfect run: three rounds, three trials each, a thousand points a trial. */
private const val MAX_TOTAL = 9000f
private const val MAX_ROUND = 3000f
private const val TRIALS_PER_RUN = 9

private val ContentWidth = 620.dp
private val PagePadding = 20.dp
private val RailColumn = 48.dp

@Composable
fun BracketScreen(
    state: TournamentUiState,
    config: TournamentConfig,
    onStart: () -> Unit,
    onPlay: (TrialType) -> Unit,
    onAdvance: () -> Unit,
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    onRetrySaving: () -> Unit
) {
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var inspectedRound by rememberSaveable(state.runId, state.currentRound) {
        mutableIntStateOf(state.currentRound.roundIndex)
    }
    var entered by remember(state.runId) { mutableStateOf(false) }
    LaunchedEffect(state.runId) { entered = true }
    BackHandler(state.isRunActive) { showLeave = true }
    val completed = state.finalRank != null
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        TournamentBackdrop()
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The masthead stays put; only the bracket beneath it travels.
            Box(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding)) {
                TopBar(state.isRunActive, { showLeave = true }, { showRules = true })
            }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding)) {
                        AnimatedVisibility(entered, enter = fadeIn(tween(650)) + slideInVertically(tween(650)) { 28 }) {
                            Column {
                                TitleBlock(state)
                                Spacer(Modifier.height(Space.xl))
                                TotalPanel(state)
                                Spacer(Modifier.height(Space.section))
                                SectionHeader(stringResource(R.string.tournament_path)) {
                                    Overline(stringResource(R.string.tournament_round_count,
                                        state.roundsData.values.count { it.isPassed }), Faint)
                                }
                                Spacer(Modifier.height(Space.lg))
                            }
                        }
                        TournamentRound.entries.forEach { round ->
                            val data = state.roundsData.getValue(round)
                            val locked = round.roundIndex > state.currentRound.roundIndex
                            val active = round == state.currentRound && !completed
                            AnimatedVisibility(entered,
                                enter = fadeIn(tween(500, 100 + round.roundIndex * 100)) +
                                    slideInVertically(tween(550, 100 + round.roundIndex * 100)) { 36 }) {
                                RoundNode(
                                    data = data, active = active, locked = locked,
                                    expanded = inspectedRound == round.roundIndex,
                                    duration = ((config.rounds.getOrNull(round.roundIndex)?.trialDurationMs ?: 20000L) / 1000).toInt(),
                                    isLast = round == TournamentRound.FINAL,
                                    onInspect = { inspectedRound = if (inspectedRound == round.roundIndex) -1 else round.roundIndex }
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.section))
                        MedalChase(state)
                        Spacer(Modifier.height(Space.xxl))
                    }
                }
                // Softens the cut where the scrolling bracket meets the dock.
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Space.xxl)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
            }
            ActionDock(state, onStart, onPlay, onAdvance, onRetry, onRetrySaving)
        }
        if (showRules) BracketDialog(onDismiss = { showRules = false }) {
            CrownEmblem(Modifier.size(54.dp))
            DialogTitle(stringResource(R.string.tournament_rules_title))
            Text(stringResource(R.string.tournament_rules_body), color = Muted,
                fontSize = TextSize.body, lineHeight = LineHeight.body)
            TournamentButton(stringResource(R.string.tournament_got_it), { showRules = false })
        }
        if (showLeave) BracketDialog(onDismiss = { showLeave = false }) {
            DialogTitle(stringResource(R.string.tournament_leave_title))
            Text(stringResource(R.string.tournament_leave_body), color = Muted, fontSize = TextSize.body,
                lineHeight = LineHeight.body, textAlign = TextAlign.Center)
            TournamentButton(stringResource(R.string.tournament_keep_playing), { showLeave = false })
            Text(stringResource(R.string.tournament_leave_confirm), color = Rose, fontSize = TextSize.label,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                    showLeave = false
                    onLeave()
                }.padding(horizontal = Space.xl, vertical = Space.lg))
        }
    }
}

@Composable
private fun TopBar(canLeave: Boolean, onLeave: () -> Unit, onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.lg),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Box(Modifier.size(44.dp).panel(RoundedCornerShape(14.dp), border = Gold.copy(alpha = .3f)),
            contentAlignment = Alignment.Center) {
            CrownEmblem(Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tournament_brand), color = Parchment, fontSize = TextSize.label,
                lineHeight = LineHeight.label, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Overline(stringResource(R.string.tournament_eyebrow), Gold, Modifier.padding(top = 2.dp))
        }
        if (canLeave) {
            IconAction(stringResource(R.string.tournament_leave), onLeave) {
                Canvas(Modifier.size(15.dp)) {
                    drawLine(Muted, Offset(0f, 0f), Offset(size.width, size.height), 1.6.dp.toPx(), StrokeCap.Round)
                    drawLine(Muted, Offset(size.width, 0f), Offset(0f, size.height), 1.6.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        IconAction(stringResource(R.string.tournament_info), onInfo) {
            Text("i", color = Muted, fontFamily = Display, fontSize = TextSize.subhead,
                lineHeight = LineHeight.subhead, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TitleBlock(state: TournamentUiState) {
    val complete = state.finalRank != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (complete) R.string.tournament_completed_title else R.string.tournament_title),
                color = Parchment, fontFamily = Display, fontSize = TextSize.title, lineHeight = LineHeight.title,
                fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
            Text(stringResource(if (complete) R.string.tournament_all_banked else R.string.tournament_description),
                color = Muted, fontSize = TextSize.body, lineHeight = LineHeight.body,
                modifier = Modifier.padding(top = Space.sm))
        }
        if (complete) CrownEmblem(Modifier.padding(start = Space.md).size(68.dp))
    }
}

/** Grand total, plus a nine-segment meter that mirrors the run's shape: 3 rounds of 3 trials. */
@Composable
private fun TotalPanel(state: TournamentUiState) {
    var revealed by remember(state.runId) { mutableStateOf(false) }
    LaunchedEffect(state.runId) { revealed = true }
    val previousTotal = (state.grandTotalScore - (state.lastCompletedTrialResult?.score ?: 0)).coerceAtLeast(0)
    val score by animateIntAsState(if (revealed) state.grandTotalScore else previousTotal,
        tween(850, easing = FastOutSlowInEasing), label = "grand total")
    val banked = state.roundsData.values.sumOf { it.completedTrialCount }

    Column(Modifier.fillMaxWidth()
        .panel(RoundedCornerShape(Radius.card), border = Gold.copy(alpha = .24f), elevation = 20.dp)
        .padding(Space.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Overline(stringResource(R.string.tournament_grand_total), Gold)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Space.xs)) {
                    Text(points(score), color = Gold, fontSize = TextSize.display, lineHeight = LineHeight.display,
                        fontWeight = FontWeight.Bold, fontFamily = Display,
                        modifier = Modifier.semantics { contentDescription = points(state.grandTotalScore) })
                    Text(stringResource(R.string.tournament_max_total), color = Faint, fontSize = TextSize.label,
                        lineHeight = LineHeight.label, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = Space.sm, bottom = Space.sm))
                }
            }
            CrownEmblem(Modifier.padding(top = Space.xs).size(40.dp), Gold.copy(alpha = .55f))
        }

        Spacer(Modifier.height(Space.lg))
        TrialMeter(state)
        Spacer(Modifier.height(Space.md))
        Overline(stringResource(R.string.tournament_trials_banked, banked, TRIALS_PER_RUN), Faint)

        state.finalRank?.let { rank ->
            Spacer(Modifier.height(Space.md))
            StatusChip(stringResource(R.string.tournament_rank_earned,
                rankLabel(rank).uppercase(Locale.getDefault())), Mint)
        }
        if (state.finalRank == null && state.lastCompletedTrialResult != null) {
            val result = state.lastCompletedTrialResult
            AnimatedContent(result, transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith fadeOut(tween(100))
            }, label = "banked score") { item ->
                Row(Modifier.padding(top = Space.md), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CheckEmblem(Modifier.size(15.dp))
                    Text(heroLabel(item.trialType) + " · " + stringResource(R.string.tournament_banked, points(item.score)),
                        color = Mint, fontSize = TextSize.label, lineHeight = LineHeight.label,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Nine pips in three clusters — one cluster per round, one pip per trial. */
@Composable
private fun TrialMeter(state: TournamentUiState) {
    val nextTrial = state.currentRoundData.nextTrial
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        TournamentRound.entries.forEach { round ->
            val data = state.roundsData.getValue(round)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                TrialType.entries.forEach { trial ->
                    val bankedScore = data.scoreFor(trial)
                    val isNext = state.isRunActive && round == state.currentRound && trial == nextTrial
                    val fill by animateFloatAsState(if (bankedScore != null) 1f else 0f,
                        tween(500, easing = FastOutSlowInEasing), label = "pip fill")
                    Box(Modifier.weight(1f).height(7.dp).clip(CircleShape).background(Well)
                        .drawBehind {
                            val corner = CornerRadius(size.height / 2f)
                            if (fill > 0f) drawRoundRect(Brush.horizontalGradient(listOf(GoldDeep, Gold)),
                                size = Size(size.width * fill, size.height), cornerRadius = corner)
                            // Empty pips still need an edge, or the meter reads as blank space.
                            drawRoundRect(if (isNext) Gold.copy(alpha = .6f) else Color.White.copy(alpha = .12f),
                                cornerRadius = corner, style = Stroke(1.dp.toPx()))
                        })
                }
            }
        }
    }
}

@Composable
private fun RoundNode(
    data: RoundResultData, active: Boolean, locked: Boolean, expanded: Boolean,
    duration: Int, isLast: Boolean, onInspect: () -> Unit
) {
    val status = when {
        locked -> stringResource(R.string.tournament_locked)
        data.status == RoundStatus.PASSED -> stringResource(R.string.tournament_passed)
        data.status == RoundStatus.FAILED -> stringResource(R.string.tournament_failed)
        data.status == RoundStatus.IN_PROGRESS -> stringResource(R.string.tournament_active)
        else -> stringResource(R.string.tournament_ready)
    }
    val accent = when {
        locked -> Faint
        data.status == RoundStatus.PASSED -> Mint
        data.status == RoundStatus.FAILED -> Rose
        else -> Gold
    }
    val pulse = rememberInfiniteTransition(label = "round beacon").animateFloat(.25f, .75f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beacon glow")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else 1f, spring(), label = "round card press")
    val description = stringResource(R.string.tournament_round_accessibility,
        roundLabel(data.round), status, data.roundScore, data.completedTrialCount)

    Row(Modifier.fillMaxWidth().drawBehind {
        if (!isLast) {
            // Runs from just under this node into the top of the next one, so the
            // spine reads as continuous rather than as three detached stubs.
            val x = RailColumn.toPx() / 2f
            val top = 60.dp.toPx()
            val bottom = size.height + 10.dp.toPx()
            val rail = if (data.isPassed) Mint else Gold
            drawLine(Brush.verticalGradient(listOf(rail.copy(alpha = .45f), rail.copy(alpha = .08f)),
                startY = top, endY = bottom),
                Offset(x, top), Offset(x, bottom), 2.dp.toPx(), StrokeCap.Round)
        }
    }, horizontalArrangement = Arrangement.spacedBy(Space.md)) {

        Box(Modifier.padding(top = 10.dp).size(RailColumn).drawBehind {
            if (active) {
                drawCircle(accent.copy(alpha = pulse.value * .15f), size.minDimension * .5f)
                drawCircle(accent.copy(alpha = pulse.value), size.minDimension * .45f, style = Stroke(1.5.dp.toPx()))
            }
        }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(SlateLit, Ink)))
                .border(1.5.dp, accent.copy(alpha = if (locked) .3f else .6f), CircleShape),
                contentAlignment = Alignment.Center) {
                when {
                    locked -> LockEmblem(Modifier.size(19.dp), accent)
                    data.round == TournamentRound.FINAL -> CrownEmblem(Modifier.size(24.dp))
                    data.isPassed -> CheckEmblem(Modifier.size(21.dp))
                    else -> Text(if (data.round == TournamentRound.QUALIFIER) "I" else "II",
                        color = accent, fontFamily = Display, fontSize = TextSize.body,
                        lineHeight = LineHeight.body, fontWeight = FontWeight.Bold)
                }
            }
        }

        val shape = RoundedCornerShape(Radius.card)
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else Space.md)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .panel(shape,
                top = when {
                    active -> Color(0xFF24375C)
                    locked -> Color(0xFF121C2E)
                    else -> SlateLit
                },
                bottom = when {
                    active -> Color(0xFF111D33)
                    locked -> Color(0xFF0B1322)
                    else -> Slate
                },
                border = accent.copy(alpha = if (active) .5f else if (locked) .14f else .2f),
                elevation = if (active) 18.dp else 6.dp,
                shadowTint = if (active) accent else Color.Black)
            .clickable(enabled = !locked, interactionSource = interaction, indication = null,
                role = Role.Button,
                onClickLabel = stringResource(if (expanded) R.string.tournament_collapse else R.string.tournament_expand),
                onClick = onInspect)
            .semantics { contentDescription = description; selected = expanded }
            .animateContentSize(tween(360, easing = FastOutSlowInEasing))
            .padding(Space.lg)) {

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Overline(stringResource(R.string.tournament_round_number, data.round.roundIndex + 1),
                    if (locked) Faint else Muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    StatusChip(status, accent)
                    if (!locked) ChevronEmblem(Modifier.size(12.dp), Muted, expanded)
                }
            }
            Text(roundLabel(data.round), color = if (locked) Faint else Parchment, fontFamily = Display,
                fontSize = if (active) TextSize.heading else TextSize.subhead,
                lineHeight = if (active) LineHeight.heading else LineHeight.subhead,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.sm))
            Text(stringResource(R.string.tournament_round_meta, difficultyLabel(data.round), duration),
                color = Faint, fontSize = TextSize.overline, lineHeight = LineHeight.overline,
                letterSpacing = .9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = Space.xs))

            AnimatedVisibility(expanded && !locked, enter = fadeIn(tween(300)), exit = fadeOut(tween(160))) {
                Column {
                    Spacer(Modifier.height(Space.lg))
                    HairLine()
                    Spacer(Modifier.height(Space.lg))
                    RoundScore(data, accent)
                    Spacer(Modifier.height(Space.lg))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        TrialType.entries.forEach { trial ->
                            HeroTile(trial, data.scoreFor(trial), active && data.nextTrial == trial, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(Space.lg))
                    Text(roundHint(data), color = if (data.status == RoundStatus.FAILED) Rose else Muted,
                        fontSize = TextSize.body, lineHeight = LineHeight.body)
                }
            }
            if (!expanded || locked) {
                Spacer(Modifier.height(Space.md))
                HairLine()
                Spacer(Modifier.height(Space.md))
                Text(gateLabel(data), color = if (locked) Faint else accent, fontSize = TextSize.overline,
                    lineHeight = LineHeight.overline, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RoundScore(data: RoundResultData, accent: Color) {
    var revealed by remember(data.round) { mutableStateOf(false) }
    LaunchedEffect(data.round) { revealed = true }
    val score by animateIntAsState(if (revealed) data.roundScore else 0, tween(650), label = "round score")
    val progress by animateFloatAsState(if (revealed) data.roundScore / MAX_ROUND else 0f,
        tween(850), label = "round progress")
    val target = data.threshold / MAX_ROUND
    Column {
        Overline(stringResource(R.string.tournament_round_score))
        Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(points(score), color = accent, fontSize = TextSize.heading, lineHeight = LineHeight.heading,
                fontWeight = FontWeight.Bold, fontFamily = Display)
            Text(stringResource(R.string.tournament_score_max), color = Faint, fontSize = TextSize.label,
                lineHeight = LineHeight.label, modifier = Modifier.padding(bottom = 4.dp))
        }
        Canvas(Modifier.padding(top = Space.md, bottom = Space.sm).fillMaxWidth().height(12.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(data.roundScore.toFloat(), 0f..MAX_ROUND) }) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(Well, size = size, cornerRadius = radius)
            drawRoundRect(Color.Black.copy(alpha = .4f), size = size, cornerRadius = radius, style = Stroke(1.dp.toPx()))
            if (progress > 0f) {
                val width = (size.width * progress).coerceAtLeast(size.height)
                drawRoundRect(Brush.horizontalGradient(listOf(accent.copy(alpha = .55f), accent), endX = width),
                    size = Size(width, size.height), cornerRadius = radius)
            }
            // The bar to clear, marked on the track it belongs to.
            if (target > 0f) {
                val x = size.width * target
                drawLine(Ink, Offset(x, 0f), Offset(x, size.height), 3.5.dp.toPx())
                drawLine(Parchment, Offset(x, 1.5.dp.toPx()), Offset(x, size.height - 1.5.dp.toPx()),
                    1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        Text(gateLabel(data), color = accent, fontSize = TextSize.overline, lineHeight = LineHeight.overline,
            letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroTile(trial: TrialType, score: Int?, next: Boolean, modifier: Modifier) {
    val color = heroColor(trial)
    val status = when {
        score != null -> stringResource(R.string.tournament_banked, points(score))
        next -> stringResource(R.string.tournament_next)
        else -> stringResource(R.string.tournament_waiting)
    }
    val description = stringResource(R.string.tournament_hero_accessibility, heroLabel(trial), status)
    val shape = RoundedCornerShape(Radius.tile)
    Column(modifier.clip(shape)
        .background(if (next) Brush.verticalGradient(listOf(color.copy(alpha = .18f), color.copy(alpha = .04f)))
            else Brush.verticalGradient(listOf(Well, Well)))
        .border(1.dp, if (next) color.copy(alpha = .45f) else Color.White.copy(alpha = .06f), shape)
        .semantics(mergeDescendants = true) { contentDescription = description }
        .padding(horizontal = Space.xs, vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(contentAlignment = Alignment.BottomEnd) {
            HeroPortrait(trial, Modifier.size(52.dp), dimmed = score == null && !next)
            if (score != null) Box(Modifier.size(18.dp).clip(CircleShape).background(Ink)
                .border(1.dp, Mint.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                CheckEmblem(Modifier.size(11.dp))
            }
        }
        Text(heroLabel(trial), color = if (score == null && !next) Muted else Parchment,
            fontSize = TextSize.label, lineHeight = LineHeight.label, fontWeight = FontWeight.Bold,
            letterSpacing = .5.sp)
        Text(status, color = if (score != null) Mint else if (next) color else Faint,
            fontSize = TextSize.overline, lineHeight = LineHeight.overline, textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold)
    }
}

/**
 * The medal ladder, read against one 0–9,000 track. Tiers sit at their real
 * thresholds on the track, so the tiles above it are a legend rather than a
 * second, differently-scaled progress bar.
 */
@Composable
private fun MedalChase(state: TournamentUiState) {
    val tiers = listOf(Rank.BRONZE, Rank.SILVER, Rank.GOLD, Rank.CHAMPION)
    val total = state.grandTotalScore
    val nextTier = tiers.firstOrNull { it.threshold > total }
    val progress by animateFloatAsState(total / MAX_TOTAL, tween(850, easing = FastOutSlowInEasing),
        label = "medal progress")

    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.tournament_medal_path)) {
            val earned = state.finalRank
            when {
                earned != null ->
                    StatusChip(rankLabel(earned).uppercase(Locale.getDefault()), medalTintOf(earned))
                nextTier != null -> StatusChip(stringResource(R.string.tournament_medal_next,
                    rankLabel(nextTier).uppercase(Locale.getDefault())), medalTintOf(nextTier))
                else -> StatusChip(stringResource(R.string.tournament_medal_pace), Amethyst)
            }
        }
        Spacer(Modifier.height(Space.lg))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            tiers.forEach { tier ->
                MedalTile(tier,
                    earned = state.finalRank?.let { it.ordinal >= tier.ordinal } == true,
                    // Bronze's threshold is zero, so an unscored run would light every tier.
                    reached = total > 0 && total >= tier.threshold,
                    modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(Space.lg))
        Canvas(Modifier.fillMaxWidth().height(12.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(total.toFloat(), 0f..MAX_TOTAL) }) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(Well, size = size, cornerRadius = radius)
            drawRoundRect(Color.Black.copy(alpha = .4f), size = size, cornerRadius = radius, style = Stroke(1.dp.toPx()))
            if (progress > 0f) {
                val width = (size.width * progress).coerceAtLeast(size.height)
                drawRoundRect(Brush.horizontalGradient(listOf(GoldDeep, Gold), endX = width),
                    size = Size(width, size.height), cornerRadius = radius)
            }
            tiers.filter { it.threshold > 0 }.forEach { tier ->
                val x = size.width * (tier.threshold / MAX_TOTAL)
                drawCircle(Ink, size.height * .34f, Offset(x, size.height / 2f))
                drawCircle(medalTintOf(tier), size.height * .2f, Offset(x, size.height / 2f))
            }
        }
        Spacer(Modifier.height(Space.md))
        Text(medalHint(state, nextTier), color = Muted, fontSize = TextSize.body, lineHeight = LineHeight.body)
    }
}

@Composable
private fun MedalTile(tier: Rank, earned: Boolean, reached: Boolean, modifier: Modifier) {
    val tint = medalTintOf(tier)
    val lit = earned || reached
    val shape = RoundedCornerShape(Radius.tile)
    Column(modifier.clip(shape)
        .background(if (lit) Brush.verticalGradient(listOf(tint.copy(alpha = .16f), tint.copy(alpha = .04f)))
            else Brush.verticalGradient(listOf(Well, Well)))
        .border(1.dp, tint.copy(alpha = if (lit) .45f else .12f), shape)
        .padding(horizontal = Space.xs, vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (tier == Rank.CHAMPION) CrownEmblem(Modifier.size(28.dp), if (lit) tint else tint.copy(alpha = .45f))
            else MedalEmblem(Modifier.size(30.dp), tint, lit)
        }
        Text(rankLabel(tier), color = if (lit) tint else Faint, fontSize = TextSize.label,
            lineHeight = LineHeight.label, fontWeight = FontWeight.Bold)
        Text(if (tier == Rank.BRONZE) stringResource(R.string.tournament_bronze_requirement) else points(tier.threshold),
            color = Faint, fontSize = TextSize.overline, lineHeight = LineHeight.overline)
    }
}

@Composable
private fun ActionDock(
    state: TournamentUiState, onStart: () -> Unit, onPlay: (TrialType) -> Unit,
    onAdvance: () -> Unit, onRetry: () -> Unit, onRetrySaving: () -> Unit
) {
    val round = state.currentRoundData
    val next = round.nextTrial
    val nextRound = TournamentRound.entries.getOrNull(state.currentRound.roundIndex + 1)
    val complete = state.finalRank != null
    val saving = state.recordSaveStatus == RecordSaveStatus.SAVING
    val saveFailed = state.recordSaveStatus == RecordSaveStatus.FAILED
    val label = when {
        saveFailed -> stringResource(R.string.tournament_save_retry)
        complete -> stringResource(R.string.tournament_new_run)
        !state.isRunActive -> stringResource(R.string.tournament_start)
        round.status == RoundStatus.FAILED -> stringResource(R.string.tournament_retry, roundLabel(state.currentRound).uppercase(Locale.getDefault()))
        round.isPassed && nextRound != null -> stringResource(R.string.tournament_advance, roundLabel(nextRound).uppercase(Locale.getDefault()))
        next != null -> stringResource(R.string.tournament_play, heroLabel(next).uppercase(Locale.getDefault()))
        else -> stringResource(R.string.tournament_start)
    }
    val hint = when {
        saving -> stringResource(R.string.tournament_saving)
        saveFailed -> stringResource(R.string.tournament_save_failed)
        complete -> stringResource(R.string.tournament_saved)
        !state.isRunActive -> stringResource(R.string.tournament_first_hint)
        round.isCompleted -> stringResource(R.string.tournament_progress, 3)
        else -> stringResource(R.string.tournament_trial_hint, (next?.trialIndex ?: 0) + 1, roundLabel(state.currentRound))
    }
    Column(Modifier.fillMaxWidth().background(Ink)
        .drawBehind {
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = .35f), Color.Transparent)),
                Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        }
        .padding(start = PagePadding, end = PagePadding, top = Space.lg, bottom = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(label, transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 3 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
            }, label = "next tournament action") { text ->
                TournamentButton(text, enabled = !saving, onClick = {
                    when {
                        saveFailed -> onRetrySaving()
                        !state.isRunActive -> onStart()
                        round.status == RoundStatus.FAILED -> onRetry()
                        round.isPassed -> onAdvance()
                        next != null -> onPlay(next)
                    }
                })
            }
            Text(hint, color = if (saveFailed) Rose else Faint, fontSize = TextSize.label,
                lineHeight = LineHeight.label, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.md))
        }
    }
}

@Composable
private fun HairLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
}

@Composable
internal fun roundLabel(round: TournamentRound): String = stringResource(when (round) {
    TournamentRound.QUALIFIER -> R.string.tournament_qualifier
    TournamentRound.SEMIFINAL -> R.string.tournament_semifinal
    TournamentRound.FINAL -> R.string.tournament_final
})

@Composable
internal fun heroLabel(trial: TrialType): String = stringResource(when (trial) {
    TrialType.ZEUS -> R.string.zeus_title
    TrialType.PILOT -> R.string.pilot_title
    TrialType.JOKER -> R.string.joker_title
})

@Composable
private fun difficultyLabel(round: TournamentRound): String = stringResource(when (round) {
    TournamentRound.QUALIFIER -> R.string.tournament_easy
    TournamentRound.SEMIFINAL -> R.string.tournament_medium
    TournamentRound.FINAL -> R.string.tournament_hard
})

@Composable
private fun rankLabel(rank: Rank): String = stringResource(when (rank) {
    Rank.SILVER -> R.string.tournament_rank_silver
    Rank.GOLD -> R.string.tournament_rank_gold
    Rank.CHAMPION -> R.string.tournament_rank_champion
    else -> R.string.tournament_rank_bronze
})

private fun medalTintOf(rank: Rank): Color = when (rank) {
    Rank.SILVER -> SilverMetal
    Rank.GOLD -> Gold
    Rank.CHAMPION -> Amethyst
    else -> Bronze
}

@Composable
private fun medalHint(state: TournamentUiState, nextTier: Rank?): String {
    val earned = state.finalRank
    return when {
        earned != null -> stringResource(R.string.tournament_medal_earned_hint, rankLabel(earned))
        state.grandTotalScore == 0 -> stringResource(R.string.tournament_medal_hint)
        nextTier != null -> stringResource(R.string.tournament_medal_gap,
            points(nextTier.threshold - state.grandTotalScore), rankLabel(nextTier))
        else -> stringResource(R.string.tournament_medal_pace_hint)
    }
}

@Composable
private fun gateLabel(data: RoundResultData): String = when {
    data.isCompleted -> stringResource(R.string.tournament_banked, points(data.roundScore))
    data.round == TournamentRound.FINAL -> stringResource(R.string.tournament_final_gate)
    else -> stringResource(R.string.tournament_gate, points(data.threshold))
}

@Composable
private fun roundHint(data: RoundResultData): String = when {
    data.status == RoundStatus.FAILED -> stringResource(R.string.tournament_round_failed, points((data.threshold - data.roundScore).coerceAtLeast(0)))
    data.isPassed -> stringResource(R.string.tournament_round_passed)
    data.round == TournamentRound.FINAL -> stringResource(R.string.tournament_final_hint)
    data.roundScore >= data.threshold -> stringResource(R.string.tournament_target_reached)
    else -> stringResource(R.string.tournament_points_needed, points(data.threshold - data.roundScore))
}

@Composable
private fun DialogTitle(title: String) {
    Text(title, color = Parchment, fontSize = TextSize.heading, lineHeight = LineHeight.heading,
        fontFamily = Display, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun BracketDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(Space.xl).widthIn(max = 440.dp).fillMaxWidth(), color = Ink,
            shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .4f))) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xl), content = content)
        }
    }
}
