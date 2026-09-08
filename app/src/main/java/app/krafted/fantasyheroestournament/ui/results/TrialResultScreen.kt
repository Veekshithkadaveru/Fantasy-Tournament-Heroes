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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
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
import app.krafted.fantasyheroestournament.tournament.TrialResultData
import app.krafted.fantasyheroestournament.tournament.TrialStat
import app.krafted.fantasyheroestournament.tournament.TrialType
import app.krafted.fantasyheroestournament.ui.theme.*
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where a trial score stops being respectable and starts being a story. */
private const val GreatScore = 800
private const val GoodScore = 500
private const val FairScore = 250

/**
 * The beat between two trials: what this one paid, what the round now stands at,
 * and the single way forward. The score counts up while a burst of the hero's
 * colour lands, so banking points reads as an event rather than a table update.
 */
@Composable
fun TrialResultScreen(
    result: TrialResultData,
    round: TournamentRound,
    roundData: RoundResultData,
    grandTotal: Int,
    personalBest: Boolean,
    onContinue: () -> Unit
) {
    // The only way off this screen is forward; the score is already banked.
    BackHandler(onBack = onContinue)
    val trial = result.trialType
    val tint = heroColor(trial)
    var entered by remember(result) { mutableStateOf(false) }
    LaunchedEffect(result) { entered = true }
    val burst = remember(result) { Animatable(0f) }
    LaunchedEffect(result) { burst.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val score by animateIntAsState(if (entered) result.score else 0,
        tween(900, delayMillis = 120, easing = FastOutSlowInEasing), label = "trial score")

    Box(Modifier.fillMaxSize()) {
        TrialBackdrop(trial)
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding),
                    horizontalAlignment = Alignment.CenterHorizontally) {

                    Spacer(Modifier.height(Space.xxl))
                    AnimatedVisibility(entered,
                        enter = fadeIn(tween(500)) + scaleIn(tween(620, easing = FastOutSlowInEasing), initialScale = .82f)) {
                        Box(contentAlignment = Alignment.Center) {
                            ScoreBurst(burst.value, tint, Modifier.size(230.dp))
                            HeroMedallion(trial, Modifier.size(126.dp), spinning = false)
                        }
                    }

                    AnimatedVisibility(entered, enter = fadeIn(tween(450, 120)) + slideInVertically(tween(500, 120)) { 20 }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Overline(stringResource(R.string.result_trial_complete, heroLabel(trial)
                                .uppercase(Locale.getDefault())), tint, Modifier.padding(top = Space.lg))
                            Text(headlineFor(result.score), color = Parchment, fontFamily = Display,
                                fontSize = TextSize.title, lineHeight = LineHeight.title,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = Space.sm))
                            if (result.details.isNotBlank()) {
                                Spacer(Modifier.height(Space.md))
                                StatusChip(result.details.uppercase(Locale.getDefault()), Muted)
                            }
                        }
                    }

                    AnimatedVisibility(entered, enter = fadeIn(tween(450, 220)) + slideInVertically(tween(500, 220)) { 26 }) {
                        ScorePanel(score, result.score, tint, personalBest, result.stats,
                            Modifier.padding(top = Space.xxl))
                    }

                    AnimatedVisibility(entered, enter = fadeIn(tween(450, 320)) + slideInVertically(tween(500, 320)) { 30 }) {
                        RoundProgress(round, roundData, grandTotal, Modifier.padding(top = Space.section, bottom = Space.xxl))
                    }
                }
            }
            ResultDock(round, roundData, onContinue)
        }
    }
}

/**
 * Rays and rings thrown outward once as the screen lands. Drawn from a single
 * animated value so the whole burst is one draw pass, never a recomposition.
 */
@Composable
private fun ScoreBurst(progress: Float, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (progress <= 0f) return@Canvas
        val radius = size.minDimension / 2f
        val fade = (1f - progress).coerceIn(0f, 1f)
        repeat(2) { ring ->
            val spread = (progress - ring * .18f).coerceIn(0f, 1f)
            if (spread > 0f) {
                drawCircle(tint.copy(alpha = .35f * fade * (1f - ring * .4f)),
                    radius * (.42f + spread * .58f), style = Stroke((2.5f - ring).dp.toPx()))
            }
        }
        repeat(16) { i ->
            val angle = (i * 2f * PI / 16f).toFloat()
            val inner = radius * (.5f + progress * .28f)
            val outer = inner + radius * .16f * (1f - progress * .55f)
            drawLine(tint.copy(alpha = .7f * fade),
                Offset(center.x + inner * cos(angle), center.y + inner * sin(angle)),
                Offset(center.x + outer * cos(angle), center.y + outer * sin(angle)),
                2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun ScorePanel(
    animatedScore: Int,
    finalScore: Int,
    tint: Color,
    personalBest: Boolean,
    stats: List<TrialStat>,
    modifier: Modifier
) {
    Column(modifier.fillMaxWidth()
        .panel(RoundedCornerShape(Radius.card), border = tint.copy(alpha = .28f), elevation = 20.dp,
            shadowTint = tint)
        .padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Overline(stringResource(R.string.result_banked_label), Faint)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()
            .padding(top = Space.xs), horizontalArrangement = Arrangement.Center) {
            // The number yields before the unit label does.
            FittedText(points(animatedScore), tint,
                Modifier.weight(1f, fill = false).semantics { contentDescription = points(finalScore) },
                maxSize = 60.sp, minSize = TextSize.title, align = TextAlign.End)
            Text(stringResource(R.string.result_of_trial_max), color = Faint, fontSize = TextSize.label,
                lineHeight = LineHeight.label, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = Space.sm, bottom = Space.md))
        }
        ScoreTrack(finalScore, MAX_TRIAL, tint, Modifier.padding(top = Space.md), height = 10.dp)
        if (personalBest) {
            Spacer(Modifier.height(Space.lg))
            StatusChip(stringResource(R.string.result_personal_best), Gold)
        }
        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            HairLine()
            Spacer(Modifier.height(Space.lg))
            // Two columns keep three or four stats on one line on a phone.
            stats.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    pair.forEach { stat -> StatCell(stat.label, stat.value, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(Radius.tile)
    Column(modifier.clip(shape).background(Well).border(1.dp, Color.White.copy(alpha = .06f), shape)
        .semantics(mergeDescendants = true) { contentDescription = "$label $value" }
        .padding(horizontal = Space.md, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Overline(label, Faint, Modifier.fillMaxWidth())
        FittedText(value, Parchment, Modifier.fillMaxWidth(), align = TextAlign.Start)
    }
}

/** Where the round now stands, with the three hero slots read at a glance. */
@Composable
private fun RoundProgress(round: TournamentRound, data: RoundResultData, grandTotal: Int, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.result_round_standing)) {
            Overline(stringResource(R.string.result_grand_so_far, points(grandTotal)), Faint)
        }
        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TrialType.entries.forEach { trial ->
                SlotTile(trial, data.scoreFor(trial), data.nextTrial == trial, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Space.xl))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(points(data.roundScore), color = Gold, fontFamily = Display, fontSize = TextSize.heading,
                lineHeight = LineHeight.heading, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(stringResource(R.string.tournament_score_max), color = Faint, fontSize = TextSize.label,
                lineHeight = LineHeight.label, maxLines = 1,
                modifier = Modifier.padding(start = Space.xs, bottom = 4.dp))
            // The Final has no bar to clear, and saying so is longer than any
            // threshold; it takes the leftover width rather than pushing past it.
            FittedText(
                if (round == TournamentRound.FINAL) stringResource(R.string.result_gate_final)
                else stringResource(R.string.tournament_gate, points(data.threshold)),
                Muted, Modifier.weight(1f).padding(start = Space.md, bottom = 4.dp),
                maxSize = TextSize.overline, minSize = 9.sp, family = null,
                letterSpacing = 1.5.sp, align = TextAlign.End)
        }
        Spacer(Modifier.height(Space.md))
        ScoreTrack(data.roundScore, MAX_ROUND, Gold, target = data.threshold)
        Spacer(Modifier.height(Space.md))
        Text(standingHint(round, data), color = Muted, fontSize = TextSize.body, lineHeight = LineHeight.body)
    }
}

@Composable
private fun SlotTile(trial: TrialType, score: Int?, next: Boolean, modifier: Modifier) {
    val tint = heroColor(trial)
    val shape = RoundedCornerShape(Radius.tile)
    val status = when {
        score != null -> points(score)
        next -> stringResource(R.string.tournament_next)
        else -> stringResource(R.string.tournament_waiting)
    }
    val description = stringResource(R.string.tournament_hero_accessibility, heroLabel(trial), status)
    Column(modifier.fillMaxHeight().clip(shape)
        .background(if (score != null || next)
            Brush.verticalGradient(listOf(tint.copy(alpha = .16f), tint.copy(alpha = .03f)))
        else Brush.verticalGradient(listOf(Well, Well)))
        .border(1.dp, tint.copy(alpha = if (score != null || next) .4f else .1f), shape)
        .semantics(mergeDescendants = true) { contentDescription = description }
        .padding(vertical = Space.md, horizontal = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        HeroPortrait(trial, Modifier.size(40.dp), dimmed = score == null && !next)
        FittedText(heroLabel(trial), if (score == null && !next) Muted else Parchment,
            Modifier.fillMaxWidth(), maxSize = TextSize.overline, minSize = 9.sp,
            family = null, letterSpacing = .8.sp)
        FittedText(status, if (score != null) Gold else if (next) tint else Faint,
            Modifier.fillMaxWidth(), maxSize = TextSize.label, minSize = 9.sp)
    }
}

@Composable
private fun ResultDock(round: TournamentRound, data: RoundResultData, onContinue: () -> Unit) {
    val next = data.nextTrial
    val label = when {
        next != null -> stringResource(R.string.result_next_trial, heroLabel(next).uppercase(Locale.getDefault()))
        round == TournamentRound.FINAL -> stringResource(R.string.result_claim_medal)
        else -> stringResource(R.string.result_round_summary)
    }
    val remaining = TrialType.entries.size - data.completedTrialCount
    val hint = when {
        next != null -> pluralStringResource(R.plurals.result_trials_left, remaining, remaining)
        round == TournamentRound.FINAL -> stringResource(R.string.result_all_nine_done)
        else -> stringResource(R.string.result_round_done, roundLabel(round))
    }
    Column(Modifier.fillMaxWidth().background(Ink)
        .drawBehind {
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = .35f), Color.Transparent)),
                Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        }
        .padding(start = PagePadding, end = PagePadding, top = Space.lg, bottom = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TournamentButton(label, onContinue)
            Text(hint, color = Faint, fontSize = TextSize.label, lineHeight = LineHeight.label,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.md))
        }
    }
}

@Composable
private fun headlineFor(score: Int): String = stringResource(when {
    score >= GreatScore -> R.string.result_headline_great
    score >= GoodScore -> R.string.result_headline_good
    score >= FairScore -> R.string.result_headline_fair
    else -> R.string.result_headline_soft
})

@Composable
private fun standingHint(round: TournamentRound, data: RoundResultData): String = when {
    round == TournamentRound.FINAL -> stringResource(R.string.result_hint_final)
    data.roundScore >= data.threshold && data.isCompleted -> stringResource(R.string.result_hint_cleared)
    data.roundScore >= data.threshold -> stringResource(R.string.result_hint_on_pace)
    data.isCompleted -> stringResource(R.string.result_hint_short,
        points(data.threshold - data.roundScore))
    else -> stringResource(R.string.result_hint_needs,
        points(data.threshold - data.roundScore), roundLabel(round))
}
