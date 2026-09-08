package app.krafted.fantasyheroestournament.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.krafted.fantasyheroestournament.R
import app.krafted.fantasyheroestournament.domain.Rank
import app.krafted.fantasyheroestournament.tournament.TournamentRound
import app.krafted.fantasyheroestournament.tournament.TrialType
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Design tokens. Every size, gap and radius on the bracket resolves to one of
// these, so the screen keeps a single rhythm instead of per-call-site numbers.
// ---------------------------------------------------------------------------

/** 4dp spacing grid. */
internal object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val section = 36.dp
}

/** Type ramp. Nothing on this screen renders below [overline]. */
internal object TextSize {
    val display = 44.sp
    val title = 32.sp
    val heading = 25.sp
    val subhead = 21.sp
    val body = 15.sp
    val label = 13.sp
    val overline = 11.sp
}

internal object LineHeight {
    val display = 48.sp
    val title = 38.sp
    val heading = 30.sp
    val subhead = 26.sp
    val body = 22.sp
    val label = 18.sp
    val overline = 14.sp
}

internal object Radius {
    val card = 22.dp
    val panel = 18.dp
    val tile = 14.dp
    val chip = 8.dp
}

// ---------------------------------------------------------------------------
// Palette. Two grounds (page, card) and a fixed set of text tiers, all checked
// for contrast against both grounds so nothing relies on alpha to be readable.
// ---------------------------------------------------------------------------

internal val Ink = Color(0xFF060B16)      // page ground
internal val Well = Color(0xFF0B1425)     // recessed meter / track ground
internal val Slate = Color(0xFF16233C)    // card ground, bottom of gradient
internal val SlateLit = Color(0xFF1E3050) // card ground, top of gradient
internal val Gold = Color(0xFFEBC781)
internal val GoldDeep = Color(0xFFB98B3F)
internal val Parchment = Color(0xFFFFF4DE) // primary text
internal val Muted = Color(0xFFA8B7CD)     // secondary text
internal val Faint = Color(0xFF7E8FA9)     // tertiary text / disabled
internal val Mint = Color(0xFF7FDDB6)
internal val Rose = Color(0xFFF7A0A3)

internal val Bronze = Color(0xFFD9A278)
internal val SilverMetal = Color(0xFFC9D8E6)
internal val Amethyst = Color(0xFFE3B8FF)

internal val Display = FontFamily.Serif

internal fun points(value: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

internal fun heroColor(trial: TrialType): Color = when (trial) {
    TrialType.ZEUS -> Color(0xFF8FCEEE)
    TrialType.PILOT -> Color(0xFFF7A884)
    TrialType.JOKER -> Color(0xFFD8A0FA)
}

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

/**
 * The one card treatment used across the screen: a top-lit gradient, a hairline
 * border, and a highlight along the top edge that reads as light catching a
 * bevel. Opaque grounds keep the animated backdrop from showing through.
 */
internal fun Modifier.panel(
    shape: Shape,
    top: Color = SlateLit,
    bottom: Color = Slate,
    border: Color = Gold.copy(alpha = .16f),
    elevation: Dp = 0.dp,
    shadowTint: Color = Color.Black
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = shadowTint, spotColor = shadowTint) else Modifier)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(top, bottom)))
    .drawBehind {
        val inset = size.width * .12f
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .12f), Color.Transparent)),
            Offset(inset, 1.dp.toPx()), Offset(size.width - inset, 1.dp.toPx()), 1.dp.toPx()
        )
    }
    .border(1.dp, border, shape)

// ---------------------------------------------------------------------------
// Text atoms
// ---------------------------------------------------------------------------

@Composable
internal fun Overline(
    text: String,
    color: Color = Muted,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Unspecified
) {
    Text(text, modifier, color = color, fontSize = TextSize.overline, lineHeight = LineHeight.overline,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, textAlign = align)
}

/**
 * A value that must not break its box. It shrinks to fit instead of wrapping or
 * splitting mid-word, so an eight-letter medal name and a four-digit score sit
 * in the same tile — at any screen width and at any accessibility font scale.
 * At the common width nothing shrinks, so tiles beside each other stay matched.
 */
@Composable
internal fun FittedText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    maxSize: TextUnit = TextSize.subhead,
    minSize: TextUnit = TextSize.overline,
    family: FontFamily? = Display,
    weight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    align: TextAlign = TextAlign.Center
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontFamily = family, fontWeight = weight,
            letterSpacing = letterSpacing, textAlign = align),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minSize, maxSize, 1.sp)
    )
}

@Composable
internal fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.chip)
    Text(text, modifier.background(color.copy(alpha = .12f), shape).border(1.dp, color.copy(alpha = .3f), shape)
        .padding(horizontal = 9.dp, vertical = 5.dp),
        color = color, fontSize = TextSize.overline, lineHeight = LineHeight.overline,
        fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
}

/** Section rule: an overline flanked by a hairline that fades out to the right. */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Gold,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Overline(title, color)
        Box(Modifier.weight(1f).height(1.dp).background(
            Brush.horizontalGradient(listOf(color.copy(alpha = .25f), Color.Transparent))))
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------
// Backdrop
// ---------------------------------------------------------------------------

/** Animation is read in the draw pass, so floating motes never recompose the bracket. */
@Composable
internal fun TournamentBackdrop() {
    val loop = rememberInfiniteTransition(label = "tournament atmosphere")
    val drift = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "motes")
    Canvas(Modifier.fillMaxSize().background(Ink)) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF16253D), Color(0xFF0A1223), Ink)))

        // Arena spotlight, high and to the right, with faint concentric rings.
        val center = Offset(size.width * .86f, size.height * .14f)
        drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = .085f), Color.Transparent),
            center = center, radius = size.width * .95f), radius = size.width * .95f, center = center)
        repeat(4) { ring ->
            drawCircle(Gold.copy(alpha = .04f - ring * .006f), size.width * (.40f + ring * .11f), center,
                style = Stroke(1.dp.toPx()))
        }

        repeat(30) { i ->
            val x = ((i * .618034f) % 1f) * size.width
            val y = (1f - ((i * .137f + drift.value * (if (i % 2 == 0) 1f else .6f)) % 1f)) * size.height
            val twinkle = .5f + .5f * sin((drift.value * 2f * PI + i).toFloat())
            drawCircle(Gold.copy(alpha = .06f + twinkle * .2f), (if (i % 5 == 0) 1.6f else .9f).dp.toPx(), Offset(x, y))
        }

        // Sink the lower third so the action dock reads as resting on the page.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .85f)),
            startY = size.height * .62f, endY = size.height))
    }
}

// ---------------------------------------------------------------------------
// Emblems — small vector marks stay crisp at every density.
// ---------------------------------------------------------------------------

@Composable
internal fun CrownEmblem(modifier: Modifier = Modifier, color: Color = Gold) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val crown = Path().apply {
            moveTo(w * .17f, h * .69f); lineTo(w * .09f, h * .28f)
            lineTo(w * .32f, h * .43f); lineTo(w * .5f, h * .12f)
            lineTo(w * .68f, h * .43f); lineTo(w * .91f, h * .28f)
            lineTo(w * .83f, h * .69f); close()
        }
        drawPath(crown, Brush.verticalGradient(listOf(Parchment, color, color.copy(alpha = .65f))))
        drawLine(color, Offset(w * .2f, h * .81f), Offset(w * .8f, h * .81f), w * .055f, StrokeCap.Round)
        drawCircle(Ink.copy(alpha = .65f), w * .045f, Offset(w * .5f, h * .5f))
        listOf(Offset(.09f, .24f), Offset(.5f, .08f), Offset(.91f, .24f)).forEach {
            drawCircle(color, w * .035f, Offset(w * it.x, h * it.y))
        }
    }
}

@Composable
internal fun CheckEmblem(modifier: Modifier, color: Color = Mint) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * .2f, size.height * .5f)
            lineTo(size.width * .42f, size.height * .72f)
            lineTo(size.width * .82f, size.height * .25f)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun LockEmblem(modifier: Modifier, color: Color = Faint) {
    Canvas(modifier) {
        drawRoundRect(color, Offset(size.width * .23f, size.height * .44f),
            Size(size.width * .54f, size.height * .4f), CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        drawArc(color, 180f, 180f, false, Offset(size.width * .33f, size.height * .13f),
            Size(size.width * .34f, size.height * .57f), style = Stroke(1.5.dp.toPx()))
    }
}

/** A struck medal: rim, field, and a star. Champion wears the crown instead. */
@Composable
internal fun MedalEmblem(modifier: Modifier, tint: Color, earned: Boolean) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val alpha = if (earned) 1f else .5f
        drawCircle(Brush.linearGradient(
            listOf(tint.copy(alpha = .55f * alpha), tint.copy(alpha = .16f * alpha)),
            start = Offset(0f, 0f), end = Offset(size.width, size.height)), radius * .84f, center)
        drawCircle(tint.copy(alpha = .9f * alpha), radius * .84f, center, style = Stroke(1.4.dp.toPx()))
        drawPath(starPath(center, radius * .46f, radius * .19f), tint.copy(alpha = alpha))
    }
}

private fun DrawScope.starPath(center: Offset, outer: Float, inner: Float): Path = Path().apply {
    repeat(10) { i ->
        val radius = if (i % 2 == 0) outer else inner
        val angle = (-PI / 2 + i * PI / 5).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
internal fun ChevronEmblem(modifier: Modifier, color: Color, pointsUp: Boolean) {
    Canvas(modifier) {
        val edge = if (pointsUp) size.height * .66f else size.height * .34f
        val middle = size.height - edge
        drawLine(color, Offset(0f, edge), Offset(size.width / 2f, middle), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width / 2f, middle), Offset(size.width, edge), 1.6.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
internal fun HeroPortrait(trial: TrialType, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val asset = when (trial) {
        TrialType.ZEUS -> R.drawable.zeus_portrait
        TrialType.PILOT -> R.drawable.pilot_portrait
        TrialType.JOKER -> R.drawable.joker_portrait
    }
    val tint = heroColor(trial)
    // These supplied portraits have baked backgrounds. A close face crop keeps the frame clean.
    Box(modifier.clip(CircleShape).background(tint.copy(alpha = .1f))
        .border(1.5.dp, tint.copy(alpha = if (dimmed) .22f else .55f), CircleShape)) {
        Image(painterResource(asset), null, Modifier.fillMaxSize().graphicsLayer {
            val zoom = if (trial == TrialType.JOKER) 4.2f else 3.5f
            val faceX = when (trial) { TrialType.ZEUS -> .6f; TrialType.PILOT -> .49f; TrialType.JOKER -> .54f }
            val faceY = when (trial) { TrialType.ZEUS -> .3f; TrialType.PILOT -> .275f; TrialType.JOKER -> .35f }
            scaleX = zoom; scaleY = zoom
            translationX = size.width * (.5f - faceX) * zoom
            translationY = size.height * (.5f - faceY) * zoom
            alpha = if (dimmed) .45f else 1f
        }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color.Transparent, Ink.copy(alpha = .5f)))))
    }
}

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

@Composable
internal fun TournamentButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "button press")
    val sheen = rememberInfiniteTransition(label = "button sheen").animateFloat(-1f, 3f,
        infiniteRepeatable(tween(4800, easing = LinearEasing)), label = "sheen sweep")
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = GoldDeep, spotColor = GoldDeep)
        .clip(shape)
        .background(Brush.verticalGradient(
            if (enabled) listOf(Parchment, Gold, Color(0xFFC9984A)) else listOf(Slate, Color(0xFF101A2C))))
        .drawWithContent {
            drawContent()
            if (enabled) {
                val x = sheen.value * size.width
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .2f), Color.Transparent),
                    Offset(x, 0f), Offset(x + size.width * .3f, size.height)))
            }
        }.border(1.dp, if (enabled) Parchment.copy(alpha = .85f) else Faint.copy(alpha = .25f), shape)
        .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .heightIn(min = 62.dp).padding(horizontal = Space.xl, vertical = Space.lg),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(label, Modifier.weight(1f), color = if (enabled) Ink else Faint, fontSize = TextSize.label,
            lineHeight = LineHeight.label, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center)
        Canvas(Modifier.size(20.dp)) {
            val color = if (enabled) Ink else Faint
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.8.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(size.width * .63f, size.height * .14f), Offset(size.width, size.height / 2), 1.8.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(size.width * .63f, size.height * .86f), Offset(size.width, size.height / 2), 1.8.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** A circular icon button whose 44dp target stays well above the drawn glyph. */
@Composable
internal fun IconAction(label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Slate.copy(alpha = .55f))
        .border(1.dp, Gold.copy(alpha = .16f), CircleShape)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) { glyph() }
}

// ---------------------------------------------------------------------------
// Run shape. Every screen that draws a meter measures against these, so a bar
// on the bracket and a bar on a result screen mean the same thing.
// ---------------------------------------------------------------------------

/** A perfect run: three rounds, three trials each, a thousand points a trial. */
internal const val MAX_TOTAL = 9000f
internal const val MAX_ROUND = 3000f
internal const val MAX_TRIAL = 1000f
internal const val TRIALS_PER_RUN = 9

internal val ContentWidth = 620.dp
internal val PagePadding = 20.dp

// ---------------------------------------------------------------------------
// Copy. Labels live here so the bracket, the intros and the results never drift
// apart on what a round or a hero is called.
// ---------------------------------------------------------------------------

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
internal fun heroSubtitle(trial: TrialType): String = stringResource(when (trial) {
    TrialType.ZEUS -> R.string.zeus_subtitle
    TrialType.PILOT -> R.string.pilot_subtitle
    TrialType.JOKER -> R.string.joker_subtitle
})

@Composable
internal fun difficultyLabel(round: TournamentRound): String = stringResource(when (round) {
    TournamentRound.QUALIFIER -> R.string.tournament_easy
    TournamentRound.SEMIFINAL -> R.string.tournament_medium
    TournamentRound.FINAL -> R.string.tournament_hard
})

@Composable
internal fun rankLabel(rank: Rank): String = stringResource(when (rank) {
    Rank.SILVER -> R.string.tournament_rank_silver
    Rank.GOLD -> R.string.tournament_rank_gold
    Rank.CHAMPION -> R.string.tournament_rank_champion
    else -> R.string.tournament_rank_bronze
})

internal fun medalTintOf(rank: Rank): Color = when (rank) {
    Rank.SILVER -> SilverMetal
    Rank.GOLD -> Gold
    Rank.CHAMPION -> Amethyst
    else -> Bronze
}

// ---------------------------------------------------------------------------
// Hero-tinted backdrop, used by the screens that belong to one hero rather than
// to the tournament as a whole.
// ---------------------------------------------------------------------------

/**
 * The tournament backdrop pulled toward one hero's colour, with the trial's own
 * arena art held far back. The supplied arena plates have characters baked into
 * them, so they stay under a heavy scrim where they read as texture and never
 * sit beside a generated portrait as a second, differently lit hero.
 */
@Composable
internal fun TrialBackdrop(trial: TrialType, modifier: Modifier = Modifier) {
    val tint = heroColor(trial)
    val plate = when (trial) {
        TrialType.ZEUS -> R.drawable.back_3
        TrialType.PILOT -> R.drawable.back_1
        TrialType.JOKER -> R.drawable.back_5
    }
    val loop = rememberInfiniteTransition(label = "trial atmosphere")
    val drift = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(21000, easing = LinearEasing)), label = "embers")
    val swell = loop.animateFloat(.72f, 1f,
        infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "aura")

    Box(modifier.fillMaxSize().background(Ink)) {
        Image(painterResource(plate), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .16f)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Ink.copy(alpha = .55f), Ink.copy(alpha = .9f), Ink)))

            // A wash of the hero's colour behind where their portrait sits.
            val center = Offset(size.width * .5f, size.height * .3f)
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .17f * swell.value), Color.Transparent),
                center = center, radius = size.width * .85f), size.width * .85f, center)
            repeat(3) { ring ->
                drawCircle(tint.copy(alpha = (.10f - ring * .028f) * swell.value),
                    size.width * (.30f + ring * .14f), center, style = Stroke(1.dp.toPx()))
            }

            repeat(22) { i ->
                val x = ((i * .618034f) % 1f) * size.width
                val y = (1f - ((i * .143f + drift.value * (if (i % 2 == 0) 1f else .55f)) % 1f)) * size.height
                val twinkle = .5f + .5f * sin((drift.value * 2f * PI + i).toFloat())
                drawCircle(tint.copy(alpha = .05f + twinkle * .18f), (if (i % 4 == 0) 1.7f else 1f).dp.toPx(), Offset(x, y))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .9f)),
                startY = size.height * .58f, endY = size.height))
        }
    }
}

/**
 * A hero portrait in a struck frame: a rotating gradient ring outside a still
 * bezel, so the mark reads as ceremonial without the face appearing to spin.
 */
@Composable
internal fun HeroMedallion(trial: TrialType, modifier: Modifier = Modifier, spinning: Boolean = true) {
    val tint = heroColor(trial)
    val spin = rememberInfiniteTransition(label = "medallion").animateFloat(0f, 360f,
        infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "ring sweep")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .22f), Color.Transparent),
                center = center, radius = radius), radius, center)
            rotate(if (spinning) spin.value else 0f) {
                drawCircle(Brush.sweepGradient(listOf(tint.copy(alpha = .05f), tint, Gold,
                    tint.copy(alpha = .05f), tint.copy(alpha = .05f)), center),
                    radius * .93f, center, style = Stroke(2.dp.toPx()))
            }
            drawCircle(tint.copy(alpha = .3f), radius * .78f, center, style = Stroke(1.dp.toPx()))
            // Four cardinal studs, the way a struck coin is punched.
            listOf(0f, 90f, 180f, 270f).forEach { angle ->
                val radians = (angle - 90f) * PI.toFloat() / 180f
                drawCircle(Gold.copy(alpha = .8f), 2.2.dp.toPx(),
                    Offset(center.x + radius * .93f * cos(radians), center.y + radius * .93f * sin(radians)))
            }
        }
        HeroPortrait(trial, Modifier.fillMaxSize(.7f))
    }
}

// ---------------------------------------------------------------------------
// More controls
// ---------------------------------------------------------------------------

/** The quieter sibling of [TournamentButton]: same target, panel ground, no gold. */
@Composable
internal fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Gold,
    glyph: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "secondary press")
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
        .panel(shape, border = accent.copy(alpha = .3f), elevation = 6.dp)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .heightIn(min = 58.dp).padding(horizontal = Space.xl, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        glyph?.invoke()
        Text(label, Modifier.weight(1f), color = Parchment, fontSize = TextSize.label,
            lineHeight = LineHeight.label, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            textAlign = if (glyph == null) TextAlign.Center else TextAlign.Start)
    }
}

/** A bare text action for the least important choice on a screen. */
@Composable
internal fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(label, modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = Space.xl, vertical = Space.lg),
        color = color, fontSize = TextSize.label, lineHeight = LineHeight.label,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
internal fun BackAction(label: String, onClick: () -> Unit) {
    IconAction(label, onClick) {
        Canvas(Modifier.size(16.dp)) {
            val midY = size.height / 2f
            drawLine(Muted, Offset(size.width, midY), Offset(size.width * .12f, midY), 1.8.dp.toPx(), StrokeCap.Round)
            drawLine(Muted, Offset(size.width * .48f, midY - size.height * .3f), Offset(size.width * .12f, midY),
                1.8.dp.toPx(), StrokeCap.Round)
            drawLine(Muted, Offset(size.width * .48f, midY + size.height * .3f), Offset(size.width * .12f, midY),
                1.8.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
internal fun CloseAction(label: String, onClick: () -> Unit) {
    IconAction(label, onClick) {
        Canvas(Modifier.size(15.dp)) {
            drawLine(Muted, Offset(0f, 0f), Offset(size.width, size.height), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(Muted, Offset(size.width, 0f), Offset(0f, size.height), 1.6.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
internal fun CrossEmblem(modifier: Modifier, color: Color = Rose) {
    Canvas(modifier) {
        val inset = size.width * .24f
        drawLine(color, Offset(inset, inset), Offset(size.width - inset, size.height - inset),
            2.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset),
            2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
internal fun HairLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
}

// ---------------------------------------------------------------------------
// Meters
// ---------------------------------------------------------------------------

/**
 * The one score track in the app. [target] marks the bar to clear, drawn on the
 * track it belongs to rather than described in prose beside it.
 */
@Composable
internal fun ScoreTrack(
    value: Int,
    max: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    target: Int = 0,
    animate: Boolean = true,
    height: Dp = 12.dp
) {
    var revealed by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { revealed = true }
    val progress by animateFloatAsState(if (revealed) (value / max).coerceIn(0f, 1f) else 0f,
        tween(850, easing = FastOutSlowInEasing), label = "score track")
    Canvas(modifier.fillMaxWidth().height(height)
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..max) }) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(Well, size = size, cornerRadius = radius)
        drawRoundRect(Color.Black.copy(alpha = .4f), size = size, cornerRadius = radius, style = Stroke(1.dp.toPx()))
        if (progress > 0f) {
            val width = (size.width * progress).coerceAtLeast(size.height)
            drawRoundRect(Brush.horizontalGradient(listOf(accent.copy(alpha = .55f), accent), endX = width),
                size = Size(width, size.height), cornerRadius = radius)
        }
        if (target > 0) {
            val x = size.width * (target / max).coerceIn(0f, 1f)
            drawLine(Ink, Offset(x, 0f), Offset(x, size.height), 3.5.dp.toPx())
            drawLine(Parchment, Offset(x, 1.5.dp.toPx()), Offset(x, size.height - 1.5.dp.toPx()),
                1.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------------------
// Dialog
// ---------------------------------------------------------------------------

@Composable
internal fun DialogTitle(title: String) {
    Text(title, color = Parchment, fontSize = TextSize.heading, lineHeight = LineHeight.heading,
        fontFamily = Display, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
internal fun TournamentDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(Space.xl).widthIn(max = 440.dp).fillMaxWidth(), color = Ink,
            shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .4f))) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xl), content = content)
        }
    }
}

// ---------------------------------------------------------------------------
// Page shell
// ---------------------------------------------------------------------------

/**
 * The standing shape of a secondary page: a fixed masthead, a scrolling body
 * capped at [ContentWidth], and an optional dock that never scrolls away. The
 * body is faded into the dock so the cut between them never reads as a seam.
 */
@Composable
internal fun PageScaffold(
    title: String,
    eyebrow: String,
    onBack: () -> Unit,
    backLabel: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    dock: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.fillMaxSize()) {
        TournamentBackdrop()
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = ContentWidth).fillMaxWidth()
                .padding(start = PagePadding, end = PagePadding, top = Space.md, bottom = Space.lg),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                BackAction(backLabel, onBack)
                Column(Modifier.weight(1f)) {
                    Text(title, color = Parchment, fontSize = TextSize.label, lineHeight = LineHeight.label,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Overline(eyebrow, Gold, Modifier.padding(top = 2.dp))
                }
                trailing?.invoke()
            }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = PagePadding),
                        content = content)
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Space.xxl)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
            }
            dock?.let {
                Column(Modifier.fillMaxWidth().background(Ink)
                    .drawBehind {
                        drawLine(Brush.horizontalGradient(
                            listOf(Color.Transparent, Gold.copy(alpha = .35f), Color.Transparent)),
                            Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                    }
                    .padding(start = PagePadding, end = PagePadding, top = Space.lg, bottom = Space.md),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally) { it() }
                }
            }
        }
    }
}
