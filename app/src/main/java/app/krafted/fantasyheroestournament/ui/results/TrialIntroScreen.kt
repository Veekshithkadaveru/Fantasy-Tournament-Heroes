package app.krafted.fantasyheroestournament.ui.results

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
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
import kotlinx.coroutines.delay
import java.util.Locale

/** How often the auto-enter countdown redraws. Once a second is all the copy needs. */
private const val TickMillis = 1000L

/**
 * The card a hero hands you before their trial: who they are, what the verb is,
 * and what this round costs. Returning players get an auto-enter countdown so a
 * nine-trial run never stalls; a first-timer reads at their own pace.
 */
@Composable
fun TrialIntroScreen(
    trial: TrialType,
    round: TournamentRound,
    roundData: RoundResultData,
    durationSeconds: Int,
    autoEnterSeconds: Int,
    onBegin: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val tint = heroColor(trial)
    var entered by remember(trial) { mutableStateOf(false) }
    var remaining by remember(trial) { mutableIntStateOf(autoEnterSeconds) }
    val begin by rememberUpdatedState(onBegin)
    LaunchedEffect(trial) { entered = true }
    LaunchedEffect(trial, autoEnterSeconds) {
        if (autoEnterSeconds <= 0) return@LaunchedEffect
        while (remaining > 0) {
            delay(TickMillis)
            remaining--
        }
        begin()
    }
    // The bar drains across the whole countdown rather than stepping once a second.
    val countdown by animateFloatAsState(
        if (autoEnterSeconds > 0) remaining / autoEnterSeconds.toFloat() else 0f,
        tween(TickMillis.toInt(), easing = LinearEasing), label = "auto enter"
    )

    Box(Modifier.fillMaxSize()) {
        TrialBackdrop(trial)
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding)) {
                IntroTopBar(round, trial, onBack)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding),
                    horizontalAlignment = Alignment.CenterHorizontally) {

                    AnimatedVisibility(entered,
                        enter = fadeIn(tween(600)) + scaleIn(tween(700, easing = FastOutSlowInEasing), initialScale = .8f)) {
                        HeroMedallion(trial, Modifier.padding(top = Space.sm).size(148.dp))
                    }
                    AnimatedVisibility(entered, enter = fadeIn(tween(500, 120)) + slideInVertically(tween(560, 120)) { 24 }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FittedText(heroLabel(trial), Parchment,
                                Modifier.fillMaxWidth().padding(top = Space.lg),
                                maxSize = TextSize.display, minSize = TextSize.heading,
                                letterSpacing = 6.sp)
                            Overline(heroSubtitle(trial), tint, Modifier.padding(top = Space.sm))
                        }
                    }

                    AnimatedVisibility(entered, enter = fadeIn(tween(500, 220)) + slideInVertically(tween(560, 220)) { 28 }) {
                        Row(Modifier.padding(top = Space.xxl).fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            FactTile(stringResource(R.string.intro_fact_time),
                                stringResource(R.string.intro_fact_seconds, durationSeconds), tint, Modifier.weight(1f))
                            FactTile(stringResource(R.string.intro_fact_worth),
                                points(MAX_TRIAL.toInt()), Gold, Modifier.weight(1f))
                            FactTile(stringResource(R.string.intro_fact_tuning),
                                difficultyLabel(round), Muted, Modifier.weight(1f))
                        }
                    }

                    AnimatedVisibility(entered, enter = fadeIn(tween(500, 320)) + slideInVertically(tween(560, 320)) { 32 }) {
                        Column(Modifier.padding(top = Space.section)) {
                            SectionHeader(stringResource(R.string.intro_how_to_play), color = tint)
                            Spacer(Modifier.height(Space.lg))
                            Column(Modifier.fillMaxWidth().panel(RoundedCornerShape(Radius.card),
                                border = tint.copy(alpha = .2f), elevation = 14.dp).padding(Space.xl),
                                verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                trialBeats(trial).forEachIndexed { index, beat ->
                                    if (index > 0) HairLine()
                                    Beat(index + 1, beat.first, beat.second, tint)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(entered, enter = fadeIn(tween(500, 420)) + slideInVertically(tween(560, 420)) { 32 }) {
                        RoundStakes(round, roundData, Modifier.padding(top = Space.section, bottom = Space.xxl))
                    }
                }
            }
            IntroDock(remaining, countdown, onBegin)
        }
    }
}

@Composable
private fun IntroTopBar(round: TournamentRound, trial: TrialType, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.lg),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        BackAction(stringResource(R.string.intro_back), onBack)
        Column(Modifier.weight(1f)) {
            FittedText(stringResource(R.string.intro_round_line, round.roundIndex + 1,
                roundLabel(round).uppercase(Locale.getDefault())), Parchment, Modifier.fillMaxWidth(),
                maxSize = TextSize.label, family = null, weight = FontWeight.Black,
                letterSpacing = 1.6.sp, align = TextAlign.Start)
            Overline(stringResource(R.string.intro_trial_counter, trial.trialIndex + 1),
                heroColor(trial), Modifier.padding(top = 2.dp))
        }
        StatusChip(stringResource(R.string.intro_up_next), heroColor(trial))
    }
}

/** One of the three beats that make up a trial's verb. */
@Composable
private fun Beat(index: Int, title: String, body: String, tint: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = .14f))
            .border(1.dp, tint.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) {
            Text(index.toString(), color = tint, fontFamily = Display, fontSize = TextSize.body,
                lineHeight = LineHeight.body, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Parchment, fontSize = TextSize.body, lineHeight = LineHeight.body,
                fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = TextSize.label, lineHeight = LineHeight.body,
                modifier = Modifier.padding(top = Space.xs))
        }
    }
}

@Composable
private fun FactTile(label: String, value: String, tint: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(Radius.tile)
    Column(modifier.fillMaxHeight().clip(shape).background(Well)
        .border(1.dp, Color.White.copy(alpha = .06f), shape)
        .semantics(mergeDescendants = true) { contentDescription = "$label $value" }
        .padding(horizontal = Space.sm, vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Overline(label, Faint, Modifier.fillMaxWidth(), align = TextAlign.Center)
        // "MEDIUM" is a third wider than "HARD" and cannot be hyphenated.
        FittedText(value, tint, Modifier.fillMaxWidth())
    }
}

/** What this round still needs, on the same track the bracket uses. */
@Composable
private fun RoundStakes(round: TournamentRound, data: RoundResultData, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.intro_stakes)) {
            Overline(stringResource(R.string.tournament_banked, points(data.roundScore)), Gold)
        }
        Spacer(Modifier.height(Space.lg))
        ScoreTrack(data.roundScore, MAX_ROUND, Gold, target = data.threshold)
        Spacer(Modifier.height(Space.md))
        Text(
            when {
                round == TournamentRound.FINAL -> stringResource(R.string.intro_stakes_final)
                data.roundScore >= data.threshold -> stringResource(R.string.intro_stakes_clear)
                else -> stringResource(R.string.intro_stakes_gap,
                    points(data.threshold - data.roundScore), roundLabel(round))
            },
            color = Muted, fontSize = TextSize.body, lineHeight = LineHeight.body
        )
    }
}

@Composable
private fun IntroDock(remaining: Int, countdown: Float, onBegin: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink)
        .drawBehind {
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = .35f), Color.Transparent)),
                Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        }
        .padding(start = PagePadding, end = PagePadding, top = Space.lg, bottom = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TournamentButton(stringResource(R.string.intro_begin), onBegin)
            if (remaining > 0) {
                // A visible drain, so an intro that leaves on its own never surprises.
                Box(Modifier.padding(top = Space.md).fillMaxWidth(.55f).height(2.dp)
                    .clip(RoundedCornerShape(1.dp)).background(Well)
                    .drawBehind { drawRect(Gold.copy(alpha = .6f), size = size.copy(width = size.width * countdown)) })
                Text(stringResource(R.string.intro_auto_enter, remaining), color = Faint,
                    fontSize = TextSize.label, lineHeight = LineHeight.label, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.sm).semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                Text(stringResource(R.string.intro_begin_hint), color = Faint, fontSize = TextSize.label,
                    lineHeight = LineHeight.label, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.md))
            }
        }
    }
}

/** The three beats that teach a trial's verb, in the order a player meets them. */
@Composable
private fun trialBeats(trial: TrialType): List<Pair<String, String>> = when (trial) {
    TrialType.ZEUS -> listOf(
        stringResource(R.string.intro_zeus_1) to stringResource(R.string.intro_zeus_1_body),
        stringResource(R.string.intro_zeus_2) to stringResource(R.string.intro_zeus_2_body),
        stringResource(R.string.intro_zeus_3) to stringResource(R.string.intro_zeus_3_body)
    )
    TrialType.PILOT -> listOf(
        stringResource(R.string.intro_pilot_1) to stringResource(R.string.intro_pilot_1_body),
        stringResource(R.string.intro_pilot_2) to stringResource(R.string.intro_pilot_2_body),
        stringResource(R.string.intro_pilot_3) to stringResource(R.string.intro_pilot_3_body)
    )
    TrialType.JOKER -> listOf(
        stringResource(R.string.intro_joker_1) to stringResource(R.string.intro_joker_1_body),
        stringResource(R.string.intro_joker_2) to stringResource(R.string.intro_joker_2_body),
        stringResource(R.string.intro_joker_3) to stringResource(R.string.intro_joker_3_body)
    )
}
