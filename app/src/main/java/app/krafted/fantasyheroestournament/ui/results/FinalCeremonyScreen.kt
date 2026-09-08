package app.krafted.fantasyheroestournament.ui.results

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.krafted.fantasyheroestournament.R
import app.krafted.fantasyheroestournament.domain.Cue
import app.krafted.fantasyheroestournament.domain.LocalGameFeedback
import app.krafted.fantasyheroestournament.domain.Rank
import app.krafted.fantasyheroestournament.tournament.RecordSaveStatus
import app.krafted.fantasyheroestournament.tournament.RoundResultData
import app.krafted.fantasyheroestournament.tournament.TrialType
import app.krafted.fantasyheroestournament.ui.theme.*
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// The ceremony is choreographed rather than merely animated: one script runs
// the beats in order, and every element on the page waits for its own cue.
// ---------------------------------------------------------------------------

/**
 * The route fades this screen in over its own beat, so the curtain has to
 * outlast that fade: the medal must not be struck behind a half-opaque page.
 */
private const val CurtainMillis = 820L
private const val StrikeMillis = 470L       // where the falling medal lands
private const val TitleMillis = 480L
private const val TotalMillis = 400L
private const val LedgerMillis = 360L

private const val ConfettiMillis = 4600
private const val CountUpMillis = 1500

private const val StageDark = 0
private const val StageMedal = 1
private const val StageTitle = 2
private const val StageTotal = 3
private const val StageLedger = 4
private const val StageDock = 5

/** The medal falls in with an overshoot, so it reads as struck rather than faded up. */
private val MedalDrop = CubicBezierEasing(.16f, 1.42f, .38f, 1f)

private const val LaurelLeaves = 9

/**
 * The last screen of a run: the medal the nine trials earned, the total that
 * earned it, and the run itself laid out round by round. Nothing here can be
 * lost by leaving, so every way off the screen is offered at once.
 */
@Composable
fun FinalCeremonyScreen(
    rank: Rank,
    rounds: List<RoundResultData>,
    grandTotal: Int,
    previousBest: Int,
    saveStatus: RecordSaveStatus,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
    onRetrySaving: () -> Unit
) {
    val tint = medalTintOf(rank)
    // The run is over and banked; back is the menu, not a warning.
    BackHandler(onBack = onMenu)

    val drop = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val shock = remember { Animatable(0f) }
    val confetti = remember { Animatable(0f) }
    var stage by remember { mutableIntStateOf(StageDark) }
    val feedback = LocalGameFeedback.current

    LaunchedEffect(Unit) {
        delay(CurtainMillis)
        stage = StageMedal
        launch { drop.animateTo(1f, tween(760, easing = MedalDrop)) }
        delay(StrikeMillis)
        // The strike: everything that marks the landing fires on the same frame.
        stage = StageTitle
        feedback.play(Cue.MEDAL)
        launch { shock.animateTo(1f, tween(1000, easing = LinearEasing)) }
        launch { burst.animateTo(1f, tween(1300, easing = LinearOutSlowInEasing)) }
        if (rank.ordinal >= Rank.SILVER.ordinal) {
            launch { confetti.animateTo(1f, tween(ConfettiMillis, easing = LinearEasing)) }
        }
        delay(TitleMillis)
        stage = StageTotal
        delay(TotalMillis)
        stage = StageLedger
        delay(LedgerMillis)
        stage = StageDock
    }

    val total by animateIntAsState(if (stage >= StageTotal) grandTotal else 0,
        tween(CountUpMillis, easing = FastOutSlowInEasing), label = "grand total")

    Box(Modifier.fillMaxSize()) {
        CeremonyBackdrop(rank, tint)
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding),
                    horizontalAlignment = Alignment.CenterHorizontally) {

                    Spacer(Modifier.height(Space.lg))
                    Box(Modifier.size(268.dp), contentAlignment = Alignment.Center) {
                        ShockRings(shock.value, tint, Modifier.fillMaxSize())
                        SparkBurst(burst.value, tint, Modifier.fillMaxSize())
                        MedalCrest(rank, drop.value, 236.dp)
                    }

                    AnimatedVisibility(stage >= StageTitle,
                        enter = fadeIn(tween(520)) + slideInVertically(tween(560)) { 20 }) {
                        Citation(rank, tint)
                    }

                    AnimatedVisibility(stage >= StageTotal,
                        enter = fadeIn(tween(480)) + slideInVertically(tween(520)) { 26 }) {
                        TotalPanel(total, grandTotal, rank, previousBest, tint,
                            Modifier.padding(top = Space.xxl))
                    }

                    AnimatedVisibility(stage >= StageLedger,
                        enter = fadeIn(tween(480)) + slideInVertically(tween(520)) { 30 }) {
                        Column(Modifier.padding(top = Space.section, bottom = Space.xxl)) {
                            SectionHeader(stringResource(R.string.ceremony_run_header)) {
                                Overline(stringResource(R.string.tournament_trials_banked,
                                    rounds.sumOf { it.completedTrialCount }, TRIALS_PER_RUN), Faint)
                            }
                            Spacer(Modifier.height(Space.lg))
                            rounds.forEachIndexed { index, data ->
                                LedgerRow(data, 120 + index * 110)
                                if (index != rounds.lastIndex) Spacer(Modifier.height(Space.md))
                            }
                            Spacer(Modifier.height(Space.section))
                            HeroHonours(rounds)
                        }
                    }
                }
            }
            AnimatedVisibility(stage >= StageDock, enter = fadeIn(tween(420)) + slideInVertically(tween(460)) { it / 2 }) {
                CeremonyDock(saveStatus, onPlayAgain, onMenu, onRetrySaving)
            }
        }
        // The fall lands in front of the page; the sky behind it stays in the backdrop.
        if (rank.ordinal >= Rank.SILVER.ordinal) {
            ConfettiFall(confetti.value, Modifier.fillMaxSize(), listOf(Gold, tint, Parchment))
        }
    }
}

// ---------------------------------------------------------------------------
// Backdrop
// ---------------------------------------------------------------------------

/**
 * The tournament ground under a hall of light: the arena plate held far back, a
 * bank of rotating searchlights, and — for the two highest tiers — a sky that
 * keeps breaking behind the medal.
 */
@Composable
private fun CeremonyBackdrop(rank: Rank, tint: Color) {
    val loop = rememberInfiniteTransition(label = "ceremony hall")
    val sweep = loop.animateFloat(0f, 360f, infiniteRepeatable(tween(38000, easing = LinearEasing)), label = "searchlights")
    val swell = loop.animateFloat(.7f, 1f,
        infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hall glow")
    val drift = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "motes")

    Box(Modifier.fillMaxSize().background(Ink)) {
        Image(painterResource(R.drawable.back_2), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alpha = .14f)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Ink.copy(alpha = .72f), Ink.copy(alpha = .92f), Ink)))

            // Searchlights raking the hall from behind the podium.
            val podium = Offset(size.width * .5f, size.height * .22f)
            rotate(sweep.value, podium) {
                repeat(7) { i ->
                    val angle = (i * 2f * PI / 7f).toFloat()
                    val reach = size.maxDimension
                    drawLine(Brush.radialGradient(
                        listOf(tint.copy(alpha = .12f * swell.value), Color.Transparent),
                        center = podium, radius = reach * .8f),
                        podium, Offset(podium.x + reach * cos(angle), podium.y + reach * sin(angle)),
                        (26 + i * 5).dp.toPx())
                }
            }
            drawCircle(Brush.radialGradient(
                listOf(tint.copy(alpha = .2f * swell.value), Color.Transparent),
                center = podium, radius = size.width * .8f), size.width * .8f, podium)

            repeat(26) { i ->
                val x = ((i * .618034f) % 1f) * size.width
                val y = (1f - ((i * .137f + drift.value * (if (i % 2 == 0) 1f else .6f)) % 1f)) * size.height
                val twinkle = .5f + .5f * sin((drift.value * 2f * PI + i).toFloat())
                drawCircle(Gold.copy(alpha = .05f + twinkle * .18f),
                    (if (i % 5 == 0) 1.7f else 1f).dp.toPx(), Offset(x, y))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .88f)),
                startY = size.height * .55f, endY = size.height))
        }
        // A gold run and up earns a sky. It sits behind the page, never over the copy.
        if (rank.ordinal >= Rank.GOLD.ordinal) {
            Fireworks(tint, Modifier.fillMaxWidth().fillMaxHeight(.52f))
        }
    }
}

// ---------------------------------------------------------------------------
// The medal
// ---------------------------------------------------------------------------

/**
 * The struck medal: a laurel wreath and ribbon behind a bevelled disc, with a
 * specular sweep travelling across the metal. [drop] carries it in — the value
 * overshoots one, which is what makes the landing read as a strike.
 */
@Composable
private fun MedalCrest(rank: Rank, drop: Float, crestSize: Dp) {
    val tint = medalTintOf(rank)
    val lit = lerp(tint, Parchment, .62f)
    val deep = lerp(tint, Ink, .58f)
    val loop = rememberInfiniteTransition(label = "medal")
    val halo = loop.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "halo")
    val gleam = loop.animateFloat(-.5f, 1.5f,
        infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), initialStartOffset = StartOffset(900)),
        label = "gleam")
    val breathe = loop.animateFloat(.72f, 1f,
        infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "aura")
    val label = stringResource(R.string.ceremony_awarded, rankLabel(rank).uppercase(Locale.getDefault()))

    Box(Modifier.size(crestSize).graphicsLayer {
        val settle = drop.coerceAtLeast(0f)
        alpha = (settle * 2.4f).coerceIn(0f, 1f)
        scaleX = .55f + settle * .45f
        scaleY = .55f + settle * .45f
        translationY = (1f - settle) * 56.dp.toPx()
        rotationZ = (1f - settle) * -13f
    }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val hub = center
            val r = size.minDimension * .295f

            drawCircle(Brush.radialGradient(
                listOf(tint.copy(alpha = .34f * breathe.value), Color.Transparent),
                center = hub, radius = size.minDimension * .5f),
                size.minDimension * .5f, hub)

            rotate(halo.value, hub) {
                drawCircle(Brush.sweepGradient(listOf(
                    Color.Transparent, tint.copy(alpha = .55f), Gold.copy(alpha = .35f),
                    Color.Transparent, Color.Transparent), hub),
                    r * 1.09f, hub, style = Stroke(3.dp.toPx()))
            }
            drawCircle(tint.copy(alpha = .18f), r * 1.2f, hub, style = Stroke(1.dp.toPx()))

            drawRibbon(hub, r, tint, deep)
            drawLaurel(hub, r * 1.26f)

            // The disc: a rim lit from the top left, a domed field, and a groove.
            drawCircle(Color.Black.copy(alpha = .4f), r, Offset(hub.x, hub.y + r * .06f))
            drawCircle(Brush.linearGradient(listOf(lit, tint, deep),
                start = Offset(hub.x - r, hub.y - r), end = Offset(hub.x + r, hub.y + r)), r, hub)
            drawCircle(Brush.linearGradient(listOf(deep, lerp(tint, Ink, .2f), lit),
                start = Offset(hub.x - r, hub.y - r), end = Offset(hub.x + r, hub.y + r)), r * .87f, hub)
            drawCircle(Brush.radialGradient(
                listOf(Parchment.copy(alpha = .18f), Color.Transparent),
                center = Offset(hub.x - r * .3f, hub.y - r * .34f), radius = r), r * .87f, hub)
            drawCircle(Ink.copy(alpha = .45f), r * .74f, hub, style = Stroke(1.4.dp.toPx()))
            drawCircle(lit.copy(alpha = .35f), r * .74f - 1.4.dp.toPx(), hub, style = Stroke(1.dp.toPx()))

            // The engraving is cut before it is filled, so it reads as struck metal.
            val motif = Offset(hub.x, hub.y - r * .24f)
            val motifSize = r * .58f
            val engraving = if (rank == Rank.CHAMPION) crownPath(motif, motifSize)
            else starPath(motif, motifSize * .55f, motifSize * .23f)
            drawPath(shifted(engraving, 1.6.dp.toPx()), Ink.copy(alpha = .45f))
            drawPath(engraving, Brush.verticalGradient(listOf(Parchment, lit),
                startY = motif.y - motifSize, endY = motif.y + motifSize))

            clipPath(Path().apply { addOval(Rect(hub.x - r, hub.y - r, hub.x + r, hub.y + r)) }) {
                val x = hub.x - r + gleam.value * 2f * r
                val band = r * .34f
                rotate(-24f, Offset(x, hub.y)) {
                    drawRect(Brush.horizontalGradient(
                        listOf(Color.Transparent, Parchment.copy(alpha = .4f), Color.Transparent),
                        startX = x - band, endX = x + band),
                        Offset(x - band, hub.y - r * 1.6f), Size(band * 2f, r * 3.2f))
                }
            }
        }
        // The rank is engraved on the lower field, under the motif.
        FittedText(rankLabel(rank).uppercase(Locale.getDefault()), Ink.copy(alpha = .78f),
            Modifier.width(crestSize * .38f).offset(y = crestSize * .085f),
            maxSize = TextSize.label, minSize = 9.sp, letterSpacing = 2.2.sp, weight = FontWeight.Black)
    }
}

/** The two tails a struck medal hangs from, notched at the bottom. */
private fun DrawScope.drawRibbon(hub: Offset, r: Float, tint: Color, deep: Color) {
    listOf(-1f, 1f).forEach { side ->
        val tail = Path().apply {
            moveTo(hub.x + side * .10f * r, hub.y + .34f * r)
            lineTo(hub.x + side * .54f * r, hub.y + .28f * r)
            lineTo(hub.x + side * 1.00f * r, hub.y + 1.62f * r)
            lineTo(hub.x + side * .70f * r, hub.y + 1.32f * r)
            lineTo(hub.x + side * .38f * r, hub.y + 1.70f * r)
            close()
        }
        drawPath(tail, Brush.verticalGradient(listOf(tint.copy(alpha = .8f), deep),
            startY = hub.y, endY = hub.y + 1.8f * r))
        drawPath(tail, Ink.copy(alpha = .4f), style = Stroke(1.dp.toPx()))
    }
}

/** Two branches of laurel, leaves shortening toward the tips the way they grow. */
private fun DrawScope.drawLaurel(hub: Offset, radius: Float) {
    listOf(true, false).forEach { left ->
        val from = if (left) 118f else 62f
        val to = if (left) 214f else -34f
        drawArc(Gold.copy(alpha = .3f), from, to - from, false,
            Offset(hub.x - radius, hub.y - radius), Size(radius * 2f, radius * 2f),
            style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        repeat(LaurelLeaves) { i ->
            val along = i / (LaurelLeaves - 1f)
            val angle = (from + (to - from) * along) * PI.toFloat() / 180f
            val base = Offset(hub.x + radius * cos(angle), hub.y + radius * sin(angle))
            val lean = angle + (if (left) 52f else -52f) * PI.toFloat() / 180f
            drawPath(leafPath(base, lean, radius * (.27f - .12f * along)),
                Gold.copy(alpha = .24f + .32f * (1f - along)))
        }
    }
}

/** An almond leaf: out to the tip along one bulge, back along the other. */
private fun leafPath(base: Offset, angle: Float, length: Float): Path {
    val tip = Offset(base.x + length * cos(angle), base.y + length * sin(angle))
    val mid = Offset((base.x + tip.x) / 2f, (base.y + tip.y) / 2f)
    val bulge = length * .3f
    val nx = -sin(angle) * bulge
    val ny = cos(angle) * bulge
    return Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(mid.x + nx, mid.y + ny, tip.x, tip.y)
        quadraticTo(mid.x - nx, mid.y - ny, base.x, base.y)
        close()
    }
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path = Path().apply {
    repeat(10) { i ->
        val radius = if (i % 2 == 0) outer else inner
        val angle = (-PI / 2 + i * PI / 5).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun crownPath(center: Offset, size: Float): Path {
    val w = size
    val h = size * .82f
    val left = center.x - w / 2f
    val top = center.y - h / 2f
    return Path().apply {
        moveTo(left + w * .13f, top + h * .84f)
        lineTo(left + w * .02f, top + h * .18f)
        lineTo(left + w * .3f, top + h * .46f)
        lineTo(left + w * .5f, top)
        lineTo(left + w * .7f, top + h * .46f)
        lineTo(left + w * .98f, top + h * .18f)
        lineTo(left + w * .87f, top + h * .84f)
        close()
    }
}

/** The same path dropped down the page — the cut under an engraving. */
private fun shifted(path: Path, dy: Float): Path = Path().apply { addPath(path, Offset(0f, dy)) }

// ---------------------------------------------------------------------------
// The page
// ---------------------------------------------------------------------------

@Composable
private fun Citation(rank: Rank, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Overline(stringResource(R.string.ceremony_eyebrow), Faint, Modifier.padding(top = Space.lg))
        Text(stringResource(when (rank) {
            Rank.CHAMPION -> R.string.ceremony_title_champion
            Rank.GOLD -> R.string.ceremony_title_gold
            Rank.SILVER -> R.string.ceremony_title_silver
            else -> R.string.ceremony_title_bronze
        }), color = Parchment, fontFamily = Display, fontSize = TextSize.title,
            lineHeight = LineHeight.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.sm))
        Spacer(Modifier.height(Space.md))
        StatusChip(stringResource(R.string.ceremony_awarded,
            rankLabel(rank).uppercase(Locale.getDefault())), tint)
        Text(stringResource(when (rank) {
            Rank.CHAMPION -> R.string.ceremony_citation_champion
            Rank.GOLD -> R.string.ceremony_citation_gold
            Rank.SILVER -> R.string.ceremony_citation_silver
            else -> R.string.ceremony_citation_bronze
        }), color = Muted, fontSize = TextSize.body, lineHeight = LineHeight.body,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.lg))
    }
}

@Composable
private fun TotalPanel(
    animated: Int, grandTotal: Int, rank: Rank, previousBest: Int, tint: Color, modifier: Modifier
) {
    val nextTier = MedalTiers.firstOrNull { it.threshold > grandTotal }
    val record = previousBest > 0 && grandTotal > previousBest
    Column(modifier.fillMaxWidth()
        .panel(RoundedCornerShape(Radius.card), border = tint.copy(alpha = .32f), elevation = 22.dp,
            shadowTint = tint)
        .padding(Space.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Overline(stringResource(R.string.tournament_grand_total), tint)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Space.xs)) {
                    FittedText(points(animated), tint,
                        Modifier.weight(1f, fill = false).semantics {
                            contentDescription = points(grandTotal)
                        },
                        maxSize = TextSize.display, minSize = TextSize.subhead, align = TextAlign.Start)
                    Text(stringResource(R.string.tournament_max_total), color = Faint,
                        fontSize = TextSize.label, lineHeight = LineHeight.label, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = Space.sm, bottom = Space.sm))
                }
            }
            Box(Modifier.padding(top = Space.xs).size(42.dp), contentAlignment = Alignment.Center) {
                if (rank == Rank.CHAMPION) CrownEmblem(Modifier.size(34.dp), tint)
                else MedalEmblem(Modifier.size(38.dp), tint, earned = true)
            }
        }
        Spacer(Modifier.height(Space.lg))
        MedalTrack(grandTotal, durationMillis = CountUpMillis)
        Spacer(Modifier.height(Space.lg))
        HairLine()
        Spacer(Modifier.height(Space.lg))
        if (record) {
            StatusChip(stringResource(R.string.ceremony_new_record), Gold)
            Spacer(Modifier.height(Space.md))
        }
        Text(recordLine(previousBest, grandTotal, record), color = Muted, fontSize = TextSize.body,
            lineHeight = LineHeight.body)
        Text(if (nextTier == null) stringResource(R.string.ceremony_top_tier)
        else stringResource(R.string.ceremony_next_tier,
            points(nextTier.threshold - grandTotal), rankLabel(nextTier)),
            color = Faint, fontSize = TextSize.label, lineHeight = LineHeight.label,
            modifier = Modifier.padding(top = Space.xs))
    }
}

@Composable
private fun recordLine(previousBest: Int, grandTotal: Int, record: Boolean): String = when {
    previousBest <= 0 -> stringResource(R.string.ceremony_record_first)
    record -> stringResource(R.string.ceremony_record_beaten, points(previousBest))
    else -> stringResource(R.string.ceremony_record_standing,
        points(previousBest - grandTotal), points(previousBest))
}

/** One round of the run: what each hero paid into it, and what it came to. */
@Composable
private fun LedgerRow(data: RoundResultData, delayMillis: Int) {
    var revealed by remember(data) { mutableStateOf(false) }
    LaunchedEffect(data) { revealed = true }
    val fill by animateFloatAsState(if (revealed) (data.roundScore / MAX_ROUND).coerceIn(0f, 1f) else 0f,
        tween(900, delayMillis = delayMillis, easing = FastOutSlowInEasing), label = "round fill")

    Column(Modifier.fillMaxWidth().panel(RoundedCornerShape(Radius.panel), border = Gold.copy(alpha = .18f))
        .padding(horizontal = Space.lg, vertical = Space.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(roundLabel(data.round), color = Parchment, fontSize = TextSize.label,
                    lineHeight = LineHeight.label, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    maxLines = 1)
                Overline(stringResource(R.string.ceremony_round_meta,
                    stringResource(R.string.tournament_round_number, data.round.roundIndex + 1),
                    difficultyLabel(data.round)), Faint, Modifier.padding(top = 2.dp))
            }
            Text(points(data.roundScore), color = Gold, fontFamily = Display, fontSize = TextSize.subhead,
                lineHeight = LineHeight.subhead, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Canvas(Modifier.padding(top = Space.md).fillMaxWidth().height(6.dp)) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(Well, size = size, cornerRadius = radius)
            if (fill > 0f) drawRoundRect(Brush.horizontalGradient(listOf(GoldDeep, Gold)),
                size = Size(size.width * fill, size.height), cornerRadius = radius)
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TrialType.entries.forEach { trial ->
                val score = data.scoreFor(trial) ?: 0
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    HeroPortrait(trial, Modifier.size(26.dp))
                    FittedText(points(score), heroColor(trial), Modifier.weight(1f),
                        maxSize = TextSize.label, minSize = 9.sp, family = null, align = TextAlign.Start)
                }
            }
        }
    }
}

/** Which hero paid the most across the whole run, and what each one contributed. */
@Composable
private fun HeroHonours(rounds: List<RoundResultData>) {
    val totals = TrialType.entries.associateWith { trial -> rounds.sumOf { it.scoreFor(trial) ?: 0 } }
    val best = totals.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.ceremony_honours_header))
        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TrialType.entries.forEach { trial ->
                HonourTile(trial, totals.getValue(trial), trial == best, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HonourTile(trial: TrialType, total: Int, top: Boolean, modifier: Modifier) {
    val tint = heroColor(trial)
    val shape = RoundedCornerShape(Radius.tile)
    val label = stringResource(R.string.ceremony_hero_accessibility, heroLabel(trial), points(total))
    Column(modifier.panel(shape, border = if (top) Gold.copy(alpha = .5f) else tint.copy(alpha = .2f))
        .semantics(mergeDescendants = true) { contentDescription = label }
        .padding(horizontal = Space.sm, vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            HeroPortrait(trial, Modifier.fillMaxSize())
            if (top) Box(Modifier.align(Alignment.TopEnd)) { CrownEmblem(Modifier.size(18.dp)) }
        }
        FittedText(heroLabel(trial), Parchment, Modifier.fillMaxWidth(),
            maxSize = TextSize.label, minSize = 9.sp, family = null)
        FittedText(points(total), tint, Modifier.fillMaxWidth(),
            maxSize = TextSize.subhead, minSize = TextSize.label)
        FittedText(
            if (top) stringResource(R.string.ceremony_top_hero) else stringResource(R.string.ceremony_hero_max),
            if (top) Gold else Faint, Modifier.fillMaxWidth(),
            maxSize = TextSize.overline, minSize = 8.sp, family = null, weight = FontWeight.Normal)
    }
}

@Composable
private fun CeremonyDock(
    saveStatus: RecordSaveStatus,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
    onRetrySaving: () -> Unit
) {
    val saving = saveStatus == RecordSaveStatus.SAVING
    val failed = saveStatus == RecordSaveStatus.FAILED
    val hint = when {
        saving -> stringResource(R.string.tournament_saving)
        failed -> stringResource(R.string.tournament_save_failed)
        else -> stringResource(R.string.tournament_saved)
    }
    Column(Modifier.fillMaxWidth().background(Ink)
        .drawBehind {
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = .35f), Color.Transparent)),
                Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        }
        .padding(start = PagePadding, end = PagePadding, top = Space.lg, bottom = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // A failed save owns the primary action until it succeeds or is abandoned.
            TournamentButton(
                if (failed) stringResource(R.string.tournament_save_retry)
                else stringResource(R.string.tournament_new_run),
                enabled = !saving,
                onClick = { if (failed) onRetrySaving() else onPlayAgain() })
            Text(hint, color = if (failed) Rose else Faint, fontSize = TextSize.label,
                lineHeight = LineHeight.label, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.md))
            GhostButton(stringResource(R.string.ceremony_menu), onMenu)
        }
    }
}
