package app.krafted.fantasyheroestournament.ui.results

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.krafted.fantasyheroestournament.R
import app.krafted.fantasyheroestournament.tournament.RoundResultData
import app.krafted.fantasyheroestournament.tournament.TournamentRound
import app.krafted.fantasyheroestournament.tournament.TrialType
import app.krafted.fantasyheroestournament.ui.theme.*
import java.util.Locale

private const val ConfettiMillis = 2600

/**
 * The verdict on a round: the three trial scores that made it, the total against
 * the bar it had to clear, and the one decision that follows — advance or replay.
 * A cleared round gets a fall of confetti; a missed one keeps its dignity.
 */
@Composable
fun RoundResultScreen(
    round: TournamentRound,
    data: RoundResultData,
    grandTotal: Int,
    nextRound: TournamentRound?,
    onAdvance: () -> Unit,
    onRetry: () -> Unit,
    onViewBracket: () -> Unit
) {
    val passed = data.isPassed
    // Back is the least destructive route out: the bracket, not a silent retry.
    BackHandler(onBack = onViewBracket)
    var entered by remember(round, passed) { mutableStateOf(false) }
    LaunchedEffect(round, passed) { entered = true }
    val accent = if (passed) Mint else Rose
    val confetti = remember(round, passed) { Animatable(0f) }
    LaunchedEffect(round, passed) {
        if (passed) confetti.animateTo(1f, tween(ConfettiMillis, easing = LinearEasing))
    }
    val score by animateIntAsState(if (entered) data.roundScore else 0,
        tween(1000, delayMillis = 200, easing = FastOutSlowInEasing), label = "round total")

    Box(Modifier.fillMaxSize()) {
        TournamentBackdrop()
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding),
                    horizontalAlignment = Alignment.CenterHorizontally) {

                    Spacer(Modifier.height(Space.xxl))
                    AnimatedVisibility(entered,
                        enter = fadeIn(tween(500)) + scaleIn(tween(640, easing = FastOutSlowInEasing), initialScale = .78f)) {
                        VerdictCrest(passed, accent)
                    }
                    AnimatedVisibility(entered, enter = fadeIn(tween(450, 140)) + slideInVertically(tween(500, 140)) { 22 }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Overline(stringResource(R.string.round_complete_eyebrow,
                                roundLabel(round).uppercase(Locale.getDefault())), accent,
                                Modifier.padding(top = Space.xl))
                            Text(stringResource(if (passed) R.string.round_passed_title else R.string.round_failed_title),
                                color = Parchment, fontFamily = Display, fontSize = TextSize.title,
                                lineHeight = LineHeight.title, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.sm))
                            Text(verdictBody(round, data, nextRound), color = Muted, fontSize = TextSize.body,
                                lineHeight = LineHeight.body, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = Space.md))
                        }
                    }

                    AnimatedVisibility(entered, enter = fadeIn(tween(450, 240)) + slideInVertically(tween(500, 240)) { 28 }) {
                        TotalPanel(score, data, accent, grandTotal, Modifier.padding(top = Space.xxl))
                    }

                    AnimatedVisibility(entered, enter = fadeIn(tween(450, 340)) + slideInVertically(tween(500, 340)) { 32 }) {
                        Column(Modifier.padding(top = Space.section, bottom = Space.xxl)) {
                            SectionHeader(stringResource(R.string.round_trial_scores))
                            Spacer(Modifier.height(Space.lg))
                            TrialType.entries.forEachIndexed { index, trial ->
                                TrialRow(trial, data.scoreFor(trial) ?: 0, 400 + index * 90)
                                if (trial != TrialType.entries.last()) Spacer(Modifier.height(Space.md))
                            }
                        }
                    }
                }
            }
            RoundDock(round, data, nextRound, onAdvance, onRetry, onViewBracket)
        }
        if (passed) ConfettiFall(confetti.value, Modifier.fillMaxSize())
    }
}

/** A struck disc carrying the verdict mark, ringed the way the bracket rings its active round. */
@Composable
private fun VerdictCrest(passed: Boolean, accent: Color) {
    val pulse = rememberInfiniteTransition(label = "verdict").animateFloat(.3f, .8f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "crest glow")
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .22f), Color.Transparent),
                center = center, radius = radius), radius, center)
            drawCircle(accent.copy(alpha = pulse.value * .55f), radius * .93f, center, style = Stroke(1.5.dp.toPx()))
            drawCircle(accent.copy(alpha = .25f), radius * .78f, center, style = Stroke(1.dp.toPx()))
        }
        Box(Modifier.size(88.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(SlateLit, Ink)))
            .border(1.5.dp, accent.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) {
            if (passed) CheckEmblem(Modifier.size(44.dp), Mint) else CrossEmblem(Modifier.size(40.dp), Rose)
        }
    }
}

@Composable
private fun TotalPanel(
    animatedScore: Int, data: RoundResultData, accent: Color, grandTotal: Int, modifier: Modifier
) {
    Column(modifier.fillMaxWidth()
        .panel(RoundedCornerShape(Radius.card), border = accent.copy(alpha = .3f), elevation = 20.dp,
            shadowTint = accent)
        .padding(Space.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Overline(stringResource(R.string.tournament_round_score), accent)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Space.xs)) {
                    FittedText(points(animatedScore), accent,
                        Modifier.weight(1f, fill = false)
                            .semantics { contentDescription = points(data.roundScore) },
                        maxSize = TextSize.display, minSize = TextSize.subhead, align = TextAlign.Start)
                    Text(stringResource(R.string.tournament_score_max), color = Faint, fontSize = TextSize.label,
                        lineHeight = LineHeight.label, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = Space.sm, bottom = Space.sm))
                }
            }
            StatusChip(
                if (data.isPassed) stringResource(R.string.tournament_passed)
                else stringResource(R.string.tournament_failed), accent)
        }
        Spacer(Modifier.height(Space.lg))
        ScoreTrack(data.roundScore, MAX_ROUND, accent, target = data.threshold)
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            FittedText(stringResource(R.string.tournament_gate, points(data.threshold)), Faint,
                Modifier.weight(1f), maxSize = TextSize.overline, minSize = 9.sp, family = null,
                letterSpacing = 1.5.sp, align = TextAlign.Start)
            FittedText(stringResource(R.string.round_grand_total, points(grandTotal)), Gold,
                Modifier.weight(1f), maxSize = TextSize.overline, minSize = 9.sp, family = null,
                letterSpacing = 1.5.sp, align = TextAlign.End)
        }
    }
}

/** One hero's contribution, with its own share of the thousand it could have paid. */
@Composable
private fun TrialRow(trial: TrialType, score: Int, delayMillis: Int) {
    val tint = heroColor(trial)
    var revealed by remember(trial, score) { mutableStateOf(false) }
    LaunchedEffect(trial, score) { revealed = true }
    val fill by animateFloatAsState(if (revealed) (score / MAX_TRIAL).coerceIn(0f, 1f) else 0f,
        tween(800, delayMillis = delayMillis, easing = FastOutSlowInEasing), label = "trial fill")
    val shape = RoundedCornerShape(Radius.panel)
    Row(Modifier.fillMaxWidth().panel(shape, border = tint.copy(alpha = .2f))
        .semantics(mergeDescendants = true) { contentDescription = points(score) }
        .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        HeroPortrait(trial, Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(heroLabel(trial), color = Parchment, fontSize = TextSize.label,
                    lineHeight = LineHeight.label, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    maxLines = 1)
                Text(points(score), color = tint, fontFamily = Display, fontSize = TextSize.subhead,
                    lineHeight = LineHeight.subhead, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Canvas(Modifier.padding(top = Space.sm).fillMaxWidth().height(6.dp)) {
                val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                drawRoundRect(Well, size = size, cornerRadius = radius)
                if (fill > 0f) drawRoundRect(Brush.horizontalGradient(listOf(tint.copy(alpha = .5f), tint)),
                    size = Size(size.width * fill, size.height), cornerRadius = radius)
            }
        }
    }
}

@Composable
private fun RoundDock(
    round: TournamentRound, data: RoundResultData, nextRound: TournamentRound?,
    onAdvance: () -> Unit, onRetry: () -> Unit, onViewBracket: () -> Unit
) {
    val passed = data.isPassed
    val label = when {
        passed && nextRound != null -> stringResource(R.string.tournament_advance,
            roundLabel(nextRound).uppercase(Locale.getDefault()))
        passed -> stringResource(R.string.round_view_bracket)
        else -> stringResource(R.string.tournament_retry, roundLabel(round).uppercase(Locale.getDefault()))
    }
    Column(Modifier.fillMaxWidth().background(Ink)
        .drawBehind {
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = .35f), Color.Transparent)),
                Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        }
        .padding(start = PagePadding, end = PagePadding, top = Space.lg, bottom = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TournamentButton(label, onClick = {
                when {
                    passed && nextRound != null -> onAdvance()
                    passed -> onViewBracket()
                    else -> onRetry()
                }
            })
            GhostButton(stringResource(R.string.round_view_bracket), onViewBracket,
                Modifier.padding(top = Space.xs))
        }
    }
}

@Composable
private fun verdictBody(round: TournamentRound, data: RoundResultData, nextRound: TournamentRound?): String = when {
    data.isPassed && nextRound != null -> stringResource(R.string.round_passed_body,
        points(data.roundScore - data.threshold), roundLabel(nextRound))
    data.isPassed -> stringResource(R.string.round_passed_body_last, points(data.roundScore - data.threshold))
    else -> stringResource(R.string.round_failed_body,
        points(data.threshold - data.roundScore), roundLabel(round))
}
