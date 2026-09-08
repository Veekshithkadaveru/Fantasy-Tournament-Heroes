package app.krafted.fantasyheroestournament.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * The hall of records: the best medal taken, the best grand total behind it, and
 * the best each hero has ever paid. Everything is measured against the same
 * ceilings the tournament uses, so a number here means what it means there.
 */
@Composable
fun RecordsScreen(records: UserRecords, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val played = records.runsCompleted > 0
    val rank = runCatching { Rank.valueOf(records.bestRank) }.getOrDefault(Rank.NONE)
    // A completed run that banked nothing never earns a medal, so runs played
    // and medal held are two different questions.
    val ranked = rank != Rank.NONE

    PageScaffold(
        title = stringResource(R.string.records_title),
        eyebrow = stringResource(R.string.records_eyebrow),
        onBack = onBack,
        backLabel = stringResource(R.string.records_back)
    ) {
        AnimatedVisibility(entered, enter = fadeIn(tween(600)) + slideInVertically(tween(650)) { 24 }) {
            BestRunPanel(rank, records, ranked)
        }
        Spacer(Modifier.height(Space.section))
        AnimatedVisibility(entered, enter = fadeIn(tween(500, 140)) + slideInVertically(tween(560, 140)) { 28 }) {
            Column {
                SectionHeader(stringResource(R.string.records_trial_bests))
                Spacer(Modifier.height(Space.lg))
                TrialType.entries.forEachIndexed { index, trial ->
                    TrialBestRow(trial, bestFor(trial, records), 200 + index * 110)
                    if (trial != TrialType.entries.last()) Spacer(Modifier.height(Space.md))
                }
            }
        }
        Spacer(Modifier.height(Space.section))
        AnimatedVisibility(entered, enter = fadeIn(tween(500, 260)) + slideInVertically(tween(560, 260)) { 30 }) {
            Column {
                SectionHeader(stringResource(R.string.records_career))
                Spacer(Modifier.height(Space.lg))
                // "Champion" wraps where a run count does not; level the tiles.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CareerTile(stringResource(R.string.records_runs), records.runsCompleted.toString(),
                        Gold, Modifier.weight(1f))
                    CareerTile(stringResource(R.string.records_best_trial),
                        points(maxOf(records.bestZeus, records.bestPilot, records.bestJoker)),
                        Mint, Modifier.weight(1f))
                    CareerTile(stringResource(R.string.records_medal),
                        if (ranked) rankLabel(rank) else stringResource(R.string.records_none),
                        medalTintOf(rank), Modifier.weight(1f))
                }
            }
        }
        if (!played) {
            Spacer(Modifier.height(Space.xl))
            Text(stringResource(R.string.records_empty_hint), color = Faint, fontSize = TextSize.body,
                lineHeight = LineHeight.body, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Space.section))
    }
}

/** The headline record, with the medal that came with it. */
@Composable
private fun BestRunPanel(rank: Rank, records: UserRecords, ranked: Boolean) {
    val tint = medalTintOf(rank)
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val total by animateIntAsState(if (revealed) records.bestGrandTotal else 0,
        tween(1000, easing = FastOutSlowInEasing), label = "best total")
    val glow = rememberInfiniteTransition(label = "medal").animateFloat(.25f, .7f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "medal glow")

    Column(Modifier.fillMaxWidth().panel(RoundedCornerShape(Radius.card),
        border = tint.copy(alpha = if (ranked) .35f else .15f), elevation = 20.dp,
        shadowTint = if (ranked) tint else Color.Black).padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                if (!ranked) return@Canvas
                val radius = size.minDimension / 2f
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .3f * glow.value), Color.Transparent),
                    center = center, radius = radius), radius, center)
            }
            if (rank == Rank.CHAMPION) CrownEmblem(Modifier.size(84.dp), tint)
            else MedalEmblem(Modifier.size(96.dp), tint, ranked)
        }
        Overline(stringResource(R.string.records_best_rank), Faint, Modifier.padding(top = Space.lg))
        FittedText(if (ranked) rankLabel(rank) else stringResource(R.string.records_unranked),
            if (ranked) tint else Muted, Modifier.fillMaxWidth().padding(top = Space.xs),
            maxSize = TextSize.title, minSize = TextSize.subhead)

        Spacer(Modifier.height(Space.xl))
        HairLine()
        Spacer(Modifier.height(Space.xl))

        Overline(stringResource(R.string.records_best_total), Gold)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()
            .padding(top = Space.xs), horizontalArrangement = Arrangement.Center) {
            FittedText(points(total), Gold,
                Modifier.weight(1f, fill = false)
                    .semantics { contentDescription = points(records.bestGrandTotal) },
                maxSize = TextSize.display, minSize = TextSize.subhead, align = TextAlign.End)
            Text(stringResource(R.string.tournament_max_total), color = Faint, fontSize = TextSize.label,
                lineHeight = LineHeight.label, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = Space.sm, bottom = Space.sm))
        }
        Spacer(Modifier.height(Space.lg))
        ScoreTrack(records.bestGrandTotal, MAX_TOTAL, Gold,
            target = if (rank == Rank.CHAMPION) 0 else nextTierThreshold(records.bestGrandTotal))
        Spacer(Modifier.height(Space.md))
        Text(chaseHint(records.bestGrandTotal, ranked), color = Muted, fontSize = TextSize.body,
            lineHeight = LineHeight.body, textAlign = TextAlign.Center)
    }
}

/** One hero's ceiling, drawn against the thousand a single trial can pay. */
@Composable
private fun TrialBestRow(trial: TrialType, best: Int, delayMillis: Int) {
    val tint = heroColor(trial)
    var revealed by remember(trial) { mutableStateOf(false) }
    LaunchedEffect(trial) { revealed = true }
    val fill by animateFloatAsState(if (revealed) (best / MAX_TRIAL).coerceIn(0f, 1f) else 0f,
        tween(850, delayMillis = delayMillis, easing = FastOutSlowInEasing), label = "trial best fill")
    val shape = RoundedCornerShape(Radius.panel)
    Row(Modifier.fillMaxWidth().panel(shape, border = tint.copy(alpha = .2f))
        .semantics(mergeDescendants = true) { contentDescription = points(best) }
        .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        HeroPortrait(trial, Modifier.size(48.dp), dimmed = best == 0)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(heroLabel(trial), color = Parchment, fontSize = TextSize.label,
                        lineHeight = LineHeight.label, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        maxLines = 1)
                    Overline(heroSubtitle(trial), Faint, Modifier.padding(top = 2.dp))
                }
                Text(points(best), color = if (best == 0) Faint else tint, fontFamily = Display,
                    fontSize = TextSize.heading, lineHeight = LineHeight.heading,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.padding(start = Space.sm))
            }
            Canvas(Modifier.padding(top = Space.sm).fillMaxWidth().height(6.dp)) {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(Well, size = size, cornerRadius = radius)
                if (fill > 0f) drawRoundRect(Brush.horizontalGradient(listOf(tint.copy(alpha = .5f), tint)),
                    size = Size(size.width * fill, size.height), cornerRadius = radius)
            }
        }
    }
}

@Composable
private fun CareerTile(label: String, value: String, tint: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(Radius.tile)
    Column(modifier.fillMaxHeight().clip(shape).background(Well)
        .border(1.dp, Color.White.copy(alpha = .06f), shape)
        .semantics(mergeDescendants = true) { contentDescription = "$label $value" }
        .padding(horizontal = Space.sm, vertical = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        // "Champion" is twice the width of a run count in the same third of a row.
        FittedText(value, tint, Modifier.fillMaxWidth())
        Overline(label, Faint, Modifier.fillMaxWidth().padding(top = 2.dp), TextAlign.Center)
    }
}

private fun bestFor(trial: TrialType, records: UserRecords): Int = when (trial) {
    TrialType.ZEUS -> records.bestZeus
    TrialType.PILOT -> records.bestPilot
    TrialType.JOKER -> records.bestJoker
}

private fun nextTierThreshold(total: Int): Int =
    listOf(Rank.SILVER, Rank.GOLD, Rank.CHAMPION).firstOrNull { it.threshold > total }?.threshold ?: 0

@Composable
private fun chaseHint(total: Int, ranked: Boolean): String {
    if (!ranked) return stringResource(R.string.records_empty_chase)
    val next = listOf(Rank.SILVER, Rank.GOLD, Rank.CHAMPION).firstOrNull { it.threshold > total }
    return if (next == null) stringResource(R.string.records_champion_chase)
    else stringResource(R.string.records_chase, points(next.threshold - total), rankLabel(next))
}
