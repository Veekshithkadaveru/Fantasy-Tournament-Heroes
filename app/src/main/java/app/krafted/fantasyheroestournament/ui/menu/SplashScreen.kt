package app.krafted.fantasyheroestournament.ui.menu

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.krafted.fantasyheroestournament.R
import app.krafted.fantasyheroestournament.tournament.TrialType
import app.krafted.fantasyheroestournament.ui.theme.*
import kotlinx.coroutines.delay

/** The crest reveal runs to here before the menu is allowed to take over. */
private const val MinimumShowMillis = 2000L

/**
 * The title card. It holds for its own reveal and for however long the config
 * takes to load, whichever is longer, so a fast device never flashes the crest.
 */
@Composable
fun SplashScreen(ready: Boolean, onFinished: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        revealed = true
        delay(MinimumShowMillis)
        held = true
    }
    LaunchedEffect(held, ready) { if (held && ready) finish() }

    val crest by animateFloatAsState(if (revealed) 1f else 0f,
        tween(900, easing = FastOutSlowInEasing), label = "crest reveal")
    val loop = rememberInfiniteTransition(label = "splash")
    val shimmer = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "loader")
    val glow = loop.animateFloat(.45f, 1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "crest glow")

    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.splash_bg), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alpha = .38f)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Ink.copy(alpha = .82f), Ink),
                center = Offset(size.width / 2f, size.height * .42f), radius = size.maxDimension * .7f))
        }

        Column(Modifier.padding(horizontal = Space.section), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(120.dp).graphicsLayer {
                scaleX = .6f + crest * .4f
                scaleY = .6f + crest * .4f
                alpha = crest
            }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = .3f * glow.value), Color.Transparent),
                        center = center, radius = size.minDimension * .7f), size.minDimension * .7f, center)
                }
                CrownEmblem(Modifier.size(92.dp))
            }
            FittedText(stringResource(R.string.tournament_brand), Parchment,
                Modifier.fillMaxWidth().padding(top = Space.xxl).graphicsLayer { alpha = crest },
                maxSize = TextSize.title, minSize = TextSize.subhead, letterSpacing = 7.sp)
            Overline(stringResource(R.string.tournament_eyebrow), Gold,
                Modifier.padding(top = Space.md).graphicsLayer { alpha = crest })

            Row(Modifier.padding(top = Space.section), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                TrialType.entries.forEachIndexed { index, trial ->
                    // Portraits arrive one after the other, left to right.
                    val portrait by animateFloatAsState(if (revealed) 1f else 0f,
                        tween(600, delayMillis = 500 + index * 160, easing = FastOutSlowInEasing),
                        label = "hero reveal")
                    HeroPortrait(trial, Modifier.size(46.dp).graphicsLayer {
                        alpha = portrait
                        translationY = (1f - portrait) * 18.dp.toPx()
                    })
                }
            }

            // A travelling gleam rather than a spinner: the app is loading, not stalled.
            Box(Modifier.padding(top = Space.section).width(160.dp).height(2.dp)
                .clip(RoundedCornerShape(1.dp)).background(Well)
                .drawBehind {
                    val head = shimmer.value * (size.width * 1.6f) - size.width * .3f
                    drawRect(Brush.horizontalGradient(
                        listOf(Color.Transparent, Gold, Color.Transparent),
                        startX = head - size.width * .3f, endX = head + size.width * .3f))
                })
            Text(stringResource(R.string.splash_loading), color = Faint, fontSize = TextSize.label,
                lineHeight = LineHeight.label, modifier = Modifier.padding(top = Space.lg))
        }
    }
}
