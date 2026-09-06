package app.krafted.fantasyheroestournament.trial.zeus

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.krafted.fantasyheroestournament.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun ZeusArena(state: ZeusState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "arena")
    val ambient by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "embers")
    val breath by transition.animateFloat(1f, 1.025f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "hero breathing")
    val charge by animateFloatAsState(if (state.phase == ZeusPhase.CHARGING) 1f else 0f, tween(180), label = "charge glow")
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Night).border(1.dp, Gold.copy(alpha = .26f), RoundedCornerShape(24.dp))) {
        Image(painterResource(R.drawable.zeus_arena), null,
            Modifier.fillMaxSize().graphicsLayer { scaleX = breath; scaleY = breath }, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, Night.copy(alpha = .06f), Night.copy(alpha = .82f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night.copy(alpha = .22f), Color.Transparent, Night.copy(alpha = .96f)))))
        ArenaEffects(state, if (state.paused) 0f else ambient, charge, Modifier.matchParentSize())
        Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Eyebrow(stringResource(R.string.zeus_olympus), Gold)
            Text(stringResource(R.string.zeus_domain), color = White.copy(alpha = .6f), fontSize = 7.sp, letterSpacing = 1.sp)
        }
        PowerMeter(state, Modifier.align(Alignment.CenterEnd).padding(end = 15.dp, top = 16.dp, bottom = 16.dp).width(75.dp).fillMaxHeight())
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 16.dp)) {
            Image(painterResource(R.drawable.elem_2), null, Modifier.size(50.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(Gold, stringResource(R.string.zeus_legend_perfect))
                LegendDot(Ice, stringResource(R.string.zeus_legend_hit))
            }
        }
        if (state.phase == ZeusPhase.STRIKE) {
            val result = state.lastStrike!!
            val alpha = (1f - state.feedbackSeconds / ZeusEngine.STRIKE_SECONDS).coerceIn(0f, 1f)
            Text(stringResource(R.string.zeus_bonus, result.points), fontSize = 48.sp, fontWeight = FontWeight.Black,
                color = if (result.isPerfect) Gold else if (result.isHit) Ice else Miss,
                modifier = Modifier.align(Alignment.Center).padding(end = 60.dp).graphicsLayer {
                    this.alpha = alpha
                    translationY = -state.feedbackSeconds * 55.dp.toPx()
                    scaleX = 1f + (1f - alpha) * .2f; scaleY = scaleX
                })
        }
    }
}

@Composable
private fun PowerMeter(state: ZeusState, modifier: Modifier) {
    val description = stringResource(R.string.zeus_power_description, (state.power * 100).toInt(),
        (state.bandStart * 100).toInt(), (state.bandEnd * 100).toInt())
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Night.copy(alpha = .8f))
        .border(1.dp, Ice.copy(alpha = .24f), RoundedCornerShape(18.dp)).padding(vertical = 12.dp, horizontal = 9.dp)
        .semantics { contentDescription = description; progressBarRangeInfo = ProgressBarRangeInfo(state.power, 0f..1f) },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(stringResource(R.string.zeus_power), Ice)
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            val trackLeft = size.width * .32f
            val trackWidth = size.width * .36f
            drawRoundRect(Color(0xFF1F314C), Offset(trackLeft, 0f), Size(trackWidth, size.height), CornerRadius(12f))
            repeat(21) { tick ->
                val y = size.height * tick / 20f
                val major = tick % 5 == 0
                drawLine(Muted.copy(alpha = if (major) .6f else .25f), Offset(0f, y), Offset(if (major) trackLeft - 5f else trackLeft - 10f, y), 1.dp.toPx())
                drawLine(Muted.copy(alpha = if (major) .6f else .25f), Offset(size.width - trackLeft + 5f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val bandTop = (1f - state.bandEnd) * size.height
            val bandHeight = (state.bandEnd - state.bandStart) * size.height
            drawRoundRect(Gold.copy(alpha = .22f), Offset(0f, bandTop), Size(size.width, bandHeight), CornerRadius(5f))
            drawRoundRect(Gold.copy(alpha = .8f), Offset(0f, bandTop), Size(size.width, bandHeight), CornerRadius(5f), style = Stroke(1.dp.toPx()))
            drawRect(Gold, Offset(0f, bandTop + bandHeight * .4f), Size(size.width, bandHeight * .2f))
            val y = (1f - state.power) * size.height
            if (state.power > 0f) {
                drawRoundRect(Brush.verticalGradient(listOf(Ice, Color(0xFF327DEF))), Offset(trackLeft, y), Size(trackWidth, size.height - y), CornerRadius(10f))
                drawLine(Ice.copy(alpha = .22f), Offset(0f, y), Offset(size.width, y), 12.dp.toPx(), StrokeCap.Round)
                drawLine(White, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                drawCircle(Ice, 4.dp.toPx(), Offset(size.width, y))
            }
        }
        Text(stringResource(R.string.zeus_power_value, (state.power * 100).toInt()), color = Ice, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ArenaEffects(state: ZeusState, ambient: Float, charge: Float, modifier: Modifier) {
    Canvas(modifier) {
        val target = Offset(43.dp.toPx(), size.height - 52.dp.toPx())
        drawCircle(Brush.radialGradient(listOf(Ice.copy(alpha = .13f + charge * .15f), Color.Transparent),
            Offset(size.width * .3f, size.height * .45f), size.width * .65f), size.width * .65f, Offset(size.width * .3f, size.height * .45f))
        repeat(22) { index ->
            val progress = (ambient + index * .071f) % 1f
            val x = ((index * .137f) % .75f) * size.width + sin(progress * PI.toFloat() * 2 + index) * 10f
            val alpha = sin(progress * PI.toFloat()) * .65f
            drawCircle(if (index % 3 == 0) Gold.copy(alpha = alpha) else Ice.copy(alpha = alpha * .5f),
                (index % 3 + 1) * .6.dp.toPx(), Offset(x, size.height * (1f - progress)))
        }
        drawOval(Gold.copy(alpha = .35f), Offset(target.x - 45.dp.toPx(), size.height - 42.dp.toPx()),
            Size(90.dp.toPx(), 13.dp.toPx()), style = Stroke(1.dp.toPx()))
        if (state.phase != ZeusPhase.STRIKE) return@Canvas
        val strike = state.lastStrike ?: return@Canvas
        val progress = (state.feedbackSeconds / ZeusEngine.STRIKE_SECONDS).coerceIn(0f, 1f)
        val fade = 1f - progress
        val color = if (strike.isPerfect) Gold else if (strike.isHit) Ice else Miss
        if (strike.isHit) {
            if (strike.isPerfect && progress < .22f) drawRect(Gold.copy(alpha = .13f * (1f - progress / .22f)))
            repeat(if (strike.isPerfect) 2 else 1) { bolt ->
                val path = Path()
                val startX = size.width * (.25f + bolt * .25f)
                path.moveTo(startX, -10f)
                repeat(9) { step ->
                    val fraction = (step + 1) / 9f
                    val zigzag = if (step == 8) 0f else sin(step * 3.3f + state.strikes.size) * size.width * .085f
                    path.lineTo(startX + (target.x - startX) * fraction + zigzag, target.y * fraction)
                }
                drawPath(path, color.copy(alpha = fade * .18f), style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, color.copy(alpha = fade), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, White.copy(alpha = fade), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(color.copy(alpha = fade * .7f), 10.dp.toPx() + progress * 90.dp.toPx(), target, style = Stroke(2.dp.toPx()))
        } else {
            drawOval(Miss.copy(alpha = fade * .5f), Offset(target.x - 22.dp.toPx(), target.y),
                Size(44.dp.toPx(), 8.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
        repeat(if (strike.isHit) 24 else 9) { index ->
            val angle = index * 2.39996f
            val radius = progress * (if (strike.isHit) 115 else 45).dp.toPx()
            val point = target + Offset(cos(angle) * radius, sin(angle) * radius + progress * progress * 40.dp.toPx())
            drawCircle(color.copy(alpha = fade), (1f + index % 3) * fade.dp.toPx(), point)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(4.dp).background(color, CircleShape))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}
