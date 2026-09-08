package app.krafted.fantasyheroestournament.ui.menu

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.krafted.fantasyheroestournament.R
import app.krafted.fantasyheroestournament.data.UserRecords
import app.krafted.fantasyheroestournament.domain.Rank
import app.krafted.fantasyheroestournament.tournament.TrialType
import app.krafted.fantasyheroestournament.ui.theme.*
import java.util.Locale

/**
 * The front door. One clear way in — start, or pick up the run already in play —
 * with the player's standing record underneath it and the three hosts on show.
 */
@Composable
fun MainMenuScreen(
    records: UserRecords,
    runActive: Boolean,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Box(Modifier.fillMaxSize()) {
        TournamentBackdrop()
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding),
                horizontalAlignment = Alignment.CenterHorizontally) {

                Spacer(Modifier.height(Space.section))
                AnimatedVisibility(entered,
                    enter = fadeIn(tween(700)) + scaleIn(tween(800, easing = FastOutSlowInEasing), initialScale = .8f)) {
                    Crest()
                }
                AnimatedVisibility(entered, enter = fadeIn(tween(500, 150)) + slideInVertically(tween(560, 150)) { 20 }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Fourteen letters plus tracking is wider than a small phone.
                        FittedText(stringResource(R.string.tournament_brand), Parchment,
                            Modifier.fillMaxWidth().padding(top = Space.xl),
                            maxSize = TextSize.title, minSize = TextSize.subhead, letterSpacing = 6.sp)
                        Overline(stringResource(R.string.tournament_eyebrow), Gold, Modifier.padding(top = Space.sm))
                        Text(stringResource(R.string.tournament_description), color = Muted,
                            fontSize = TextSize.body, lineHeight = LineHeight.body, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = Space.md))
                    }
                }

                AnimatedVisibility(entered, enter = fadeIn(tween(500, 260)) + slideInVertically(tween(560, 260)) { 26 }) {
                    HostRow(Modifier.padding(top = Space.section))
                }

                AnimatedVisibility(entered, enter = fadeIn(tween(500, 360)) + slideInVertically(tween(560, 360)) { 30 }) {
                    Column(Modifier.padding(top = Space.section)) {
                        if (runActive) {
                            TournamentButton(stringResource(R.string.menu_resume), onResume)
                            Text(stringResource(R.string.menu_resume_hint), color = Faint,
                                fontSize = TextSize.label, lineHeight = LineHeight.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = Space.md))
                        } else {
                            TournamentButton(stringResource(R.string.tournament_start), onStart)
                            Text(stringResource(R.string.menu_start_hint), color = Faint,
                                fontSize = TextSize.label, lineHeight = LineHeight.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = Space.md))
                        }
                        Row(Modifier.padding(top = Space.lg).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                            SecondaryButton(stringResource(R.string.menu_records), onRecords, Modifier.weight(1f))
                            SecondaryButton(stringResource(R.string.menu_settings), onSettings, Modifier.weight(1f))
                        }
                    }
                }

                AnimatedVisibility(entered, enter = fadeIn(tween(500, 460)) + slideInVertically(tween(560, 460)) { 32 }) {
                    StandingPanel(records, Modifier.padding(top = Space.section))
                }

                Spacer(Modifier.height(Space.section))
            }
        }
    }
}

/** The crown on a slowly turning laurel of light. */
@Composable
private fun Crest() {
    val loop = rememberInfiniteTransition(label = "crest")
    val glow = loop.animateFloat(.35f, .9f,
        infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "crest glow")
    val spin = loop.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "laurel")
    Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = .26f * glow.value), Color.Transparent),
                center = center, radius = radius), radius, center)
            rotate(spin.value) {
                drawCircle(Brush.sweepGradient(listOf(Gold.copy(alpha = .05f), Gold, Parchment,
                    Gold.copy(alpha = .05f), Gold.copy(alpha = .05f)), center),
                    radius * .9f, center, style = Stroke(1.5.dp.toPx()))
            }
        }
        CrownEmblem(Modifier.size(66.dp))
    }
}

/** The three hosts, drifting on their own cycles so the row never feels stamped. */
@Composable
private fun HostRow(modifier: Modifier) {
    val loop = rememberInfiniteTransition(label = "hosts")
    // Intrinsic height keeps the three cards level when a subtitle wraps.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        TrialType.entries.forEach { trial ->
            val float = loop.animateFloat(-3f, 3f, infiniteRepeatable(
                tween(2600 + trial.trialIndex * 520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "host float")
            val tint = heroColor(trial)
            val shape = RoundedCornerShape(Radius.tile)
            Column(Modifier.weight(1f).fillMaxHeight().clip(shape).background(Well)
                .border(1.dp, tint.copy(alpha = .22f), shape)
                .semantics(mergeDescendants = true) { contentDescription = trial.displayName }
                .padding(vertical = Space.lg, horizontal = Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                HeroPortrait(trial, Modifier.size(56.dp).graphicsLayer { translationY = float.value })
                FittedText(heroLabel(trial), Parchment, Modifier.fillMaxWidth(),
                    maxSize = TextSize.label, minSize = 10.sp, family = null, letterSpacing = 1.sp)
                Text(heroSubtitle(trial), color = tint, fontSize = TextSize.overline,
                    lineHeight = LineHeight.overline, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

/** What the player has to their name so far, or an invitation if that is nothing. */
@Composable
private fun StandingPanel(records: UserRecords, modifier: Modifier) {
    val rank = runCatching { Rank.valueOf(records.bestRank) }.getOrDefault(Rank.NONE)
    val played = records.runsCompleted > 0
    // Runs played and a medal held are separate claims: a scoreless run earns none.
    val ranked = rank != Rank.NONE
    Column(modifier.fillMaxWidth().panel(RoundedCornerShape(Radius.card),
        border = Gold.copy(alpha = .2f), elevation = 14.dp).padding(Space.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (rank == Rank.CHAMPION) CrownEmblem(Modifier.size(40.dp))
                else MedalEmblem(Modifier.size(42.dp), medalTintOf(rank), ranked)
            }
            Column(Modifier.weight(1f)) {
                Overline(stringResource(R.string.menu_your_standing), Faint)
                FittedText(
                    if (ranked) rankLabel(rank) else stringResource(R.string.menu_no_runs),
                    if (ranked) Parchment else Muted, Modifier.fillMaxWidth().padding(top = Space.xs),
                    align = TextAlign.Start)
            }
            if (played) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(points(records.bestGrandTotal), color = Gold, fontFamily = Display,
                        fontSize = TextSize.subhead, lineHeight = LineHeight.subhead,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                    Overline(stringResource(R.string.menu_best_total), Faint)
                }
            }
        }
        if (played) {
            Spacer(Modifier.height(Space.lg))
            ScoreTrack(records.bestGrandTotal, MAX_TOTAL, Gold, height = 8.dp)
            Spacer(Modifier.height(Space.md))
            Overline(pluralStringResource(R.plurals.menu_runs_completed, records.runsCompleted,
                records.runsCompleted).uppercase(Locale.getDefault()), Faint)
        } else {
            Spacer(Modifier.height(Space.md))
            Text(stringResource(R.string.menu_no_runs_body), color = Faint, fontSize = TextSize.label,
                lineHeight = LineHeight.body)
        }
    }
}
