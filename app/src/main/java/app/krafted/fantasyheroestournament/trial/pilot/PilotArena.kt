package app.krafted.fantasyheroestournament.trial.pilot

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.krafted.fantasyheroestournament.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
internal fun PilotArena(state: PilotState, onSteer: (Float) -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "arena")
    val ambient by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "speed lines")
    val spin by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "coin spin")
    val idle by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2900, easing = LinearEasing)), label = "idle bob")
    val boost by animateFloatAsState(state.speedStep / 3f, tween(420), label = "speed glow")
    val currentSteer by rememberUpdatedState(onSteer)
    val currentState by rememberUpdatedState(state)
    val description = stringResource(R.string.pilot_arena_description,
        (state.planeX * 100).toInt(), (state.corridorLeft * 100).toInt(), (state.corridorRight * 100).toInt())
    val planeSprite = painterResource(R.drawable.pilot_plane)
    val cloudSprite = painterResource(R.drawable.pilot_cloud)
    val coinSprite = painterResource(R.drawable.elem_7)
    val shape = RoundedCornerShape(24.dp)

    BoxWithConstraints(modifier.clip(shape).background(Night).border(1.dp, Gold.copy(alpha = .26f), shape)
        .pointerInput(Unit) {
            // Stable key: the corridor recomposes every frame and must not cancel an active drag.
            detectDragGestures { change, drag ->
                if (currentState.canSteer) {
                    change.consume()
                    currentSteer(drag.x / size.width.coerceAtLeast(1))
                }
            }
        }
        .semantics { contentDescription = description }.testTag("arena")) {
        val fieldWidthDp = (maxHeight * PilotEngine.ARENA_ASPECT).coerceAtMost(maxWidth)
        val originXDp = (maxWidth - fieldWidthDp) / 2

        Canvas(Modifier.matchParentSize()) {
            val fieldWidth = (size.height * PilotEngine.ARENA_ASPECT).coerceAtMost(size.width)
            val originX = (size.width - fieldWidth) / 2f
            val drift = if (state.paused) 0f else ambient
            val breath = if (state.paused) 0f else idle
            drawOuterSky(state, boost, drift)
            // The whole corridor kicks on impact; the far sky behind it stays put.
            val shake = crashShake(state)
            translate(shake.x, shake.y) {
                translate(originX, 0f) {
                    drawSky(state, fieldWidth, size.height, drift, boost)
                    drawCoins(state, fieldWidth, size.height, spin, coinSprite)
                    drawClouds(state, fieldWidth, size.height, cloudSprite)
                }
                // Walls cut off storms hanging over the edge; the plane and its crash stay on top
                // of both, so clipping a wall is something you watch happen.
                drawWalls(state, originX, fieldWidth, size.height, drift)
                translate(originX, 0f) {
                    drawPlane(state, fieldWidth, size.height, drift, breath, boost, planeSprite)
                    drawPickupBurst(state, fieldWidth, size.height)
                    drawCrash(state, fieldWidth, size.height)
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Night.copy(alpha = .72f), Color.Transparent, Color.Transparent, Night.copy(alpha = .4f)))))
        Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Eyebrow(stringResource(R.string.pilot_corridor), Gold)
            Text(stringResource(R.string.pilot_domain), color = White.copy(alpha = .6f), fontSize = 7.sp, letterSpacing = 1.sp)
        }
        SpeedGauge(state, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).width(54.dp).fillMaxHeight(.52f))
        Row(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(Gold, stringResource(R.string.pilot_legend_coin))
            LegendDot(Ember, stringResource(R.string.pilot_legend_storm))
        }
        state.lastPickup?.let { pickup ->
            val life = (state.pickupSeconds / PilotEngine.PICKUP_SECONDS).coerceIn(0f, 1f)
            // Score lands where the coin was taken, not in the middle of the screen.
            if (life < 1f) Text(
                stringResource(if (pickup.isStreakBonus) R.string.pilot_streak_popup else R.string.pilot_pickup, pickup.points),
                fontSize = if (pickup.isStreakBonus) 28.sp else 22.sp, fontWeight = FontWeight.Black,
                color = if (pickup.isStreakBonus) Gold else Sky,
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(x = originXDp + fieldWidthDp * pickup.x, y = maxHeight * PilotEngine.PLANE_Y)
                    .graphicsLayer {
                        alpha = 1f - life * life
                        val pop = 1f + .35f * (1f - (1f - life).let { it * it })
                        scaleX = pop; scaleY = pop
                        translationX = -size.width / 2f
                        translationY = -size.height / 2f - life * 74.dp.toPx()
                    })
        }
    }
}

/** Distant sky either side of the corridor, scrolling slower so the walls read as close. */
private fun DrawScope.drawOuterSky(state: PilotState, boost: Float, ambient: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B0710), Color(0xFF14060C), Color(0xFF0C0407))))
    val span = size.height * 1.6f
    repeat(9) { index ->
        val parallax = .22f + (index % 3) * .12f
        // Drifts down the screen like the corridor does, only far slower.
        val y = ((state.scrollDistance * parallax * 22f + index * 137f) % span) - span * .3f
        val x = if (index % 2 == 0) size.width * (.04f + (index % 4) * .035f)
        else size.width * (.96f - (index % 4) * .035f)
        val radius = size.width * (.1f + (index % 3) * .045f)
        drawCircle(Brush.radialGradient(
            listOf(Color(0xFF5A1B2C).copy(alpha = .3f + boost * .12f), Color.Transparent),
            Offset(x, y), radius), radius, Offset(x, y))
    }
    // A far horizon the corridor appears to run toward.
    drawRect(Brush.verticalGradient(
        listOf(Ember.copy(alpha = .16f + boost * .1f + .03f * sin(ambient * 2f * PI.toFloat())), Color.Transparent),
        endY = size.height * .38f))
}

private fun DrawScope.drawSky(state: PilotState, width: Float, height: Float, ambient: Float, boost: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3D0E1B), Color(0xFF6B1526), Color(0xFF95253A))),
        Offset(0f, 0f), Size(width, height))
    drawCircle(Brush.radialGradient(listOf(Ember.copy(alpha = .12f + boost * .14f), Color.Transparent),
        Offset(width / 2f, height * .22f), width), width, Offset(width / 2f, height * .22f))
    // Streaks thin and shorten toward the top, so the corridor reads as receding.
    repeat(30) { index ->
        val progress = (ambient * (1f + boost * .8f) + index * .0333f) % 1f
        val depth = .35f + .65f * progress
        val x = ((index * .173f) % 1f) * width
        val length = height * (.05f + .13f * boost) * depth
        val head = progress * (height + length)
        drawLine(White.copy(alpha = (.04f + .1f * sin(progress * PI.toFloat())) * depth),
            Offset(x, head - length), Offset(x, head), (.7f + (index % 2) * .8f).dp.toPx() * depth, StrokeCap.Round)
    }
    // Rails give the scroll a readable speed even where the corridor is empty. They start behind
    // the plane so the strip below it is not left bare.
    var rail = floor((state.scrollDistance - BEHIND_UNITS) / RAIL_SPACING) * RAIL_SPACING
    repeat(9) {
        rail += RAIL_SPACING
        val y = screenY(rail, state, height)
        if (y in 0f..height) {
            drawLine(White.copy(alpha = .02f + .06f * (y / height)),
                Offset(PilotEngine.corridorLeft(rail, state.roundIndex) * width, y),
                Offset(PilotEngine.corridorRight(rail, state.roundIndex) * width, y), 1.dp.toPx())
        }
    }
}

private fun DrawScope.drawWalls(state: PilotState, originX: Float, width: Float, height: Float, ambient: Float) {
    val steps = 44
    val left = Path()
    val right = Path()
    for (step in 0..steps) {
        val y = height * step / steps
        val distance = state.scrollDistance + (PilotEngine.PLANE_Y - step.toFloat() / steps) * PilotEngine.VIEW_UNITS
        val leftX = originX + PilotEngine.corridorLeft(distance, state.roundIndex) * width
        val rightX = originX + PilotEngine.corridorRight(distance, state.roundIndex) * width
        if (step == 0) { left.moveTo(leftX, y); right.moveTo(rightX, y) }
        else { left.lineTo(leftX, y); right.lineTo(rightX, y) }
    }
    val bleed = 40.dp.toPx()
    Path().apply { addPath(left); lineTo(-bleed, height); lineTo(-bleed, 0f); close() }
        .let { drawPath(it, Brush.horizontalGradient(listOf(Night, Night.copy(alpha = .7f)))) }
    Path().apply { addPath(right); lineTo(size.width + bleed, height); lineTo(size.width + bleed, 0f); close() }
        .let { drawPath(it, Brush.horizontalGradient(listOf(Night.copy(alpha = .7f), Night))) }

    // Each wall lights up as the plane crowds it: the warning arrives before the crash does.
    // Measured from the wingtip, so the glow peaks exactly where the collision starts.
    val leftGap = state.planeX - state.corridorLeft - PilotEngine.PLANE_RADIUS
    val rightGap = state.corridorRight - state.planeX - PilotEngine.PLANE_RADIUS
    val nearLeft = (1f - leftGap / DANGER_RANGE).coerceIn(0f, 1f)
    val nearRight = (1f - rightGap / DANGER_RANGE).coerceIn(0f, 1f)
    val flying = state.phase == PilotPhase.FLYING
    listOf(left to nearLeft, right to nearRight).forEach { (path, danger) ->
        val heat = if (flying) danger else 0f
        drawPath(path, Ember.copy(alpha = .14f + heat * .4f), style = Stroke((9f + heat * 14f).dp.toPx()))
        drawPath(path, Ember.copy(alpha = .7f + heat * .3f), style = Stroke((1.6f + heat * 1.4f).dp.toPx()))
        if (heat > 0f) drawPath(path, White.copy(alpha = heat * .5f), style = Stroke(.8.dp.toPx()))
    }
    drawWallChevrons(state, originX, width, height, ambient)
}

/** Hazard marks sliding down the wall, the clearest cue that the corridor is speeding up. */
private fun DrawScope.drawWallChevrons(state: PilotState, originX: Float, width: Float, height: Float, ambient: Float) {
    val arm = 7.dp.toPx()
    var mark = floor((state.scrollDistance - BEHIND_UNITS) / CHEVRON_SPACING) * CHEVRON_SPACING
    repeat(8) {
        mark += CHEVRON_SPACING
        val y = screenY(mark, state, height)
        if (y < -arm || y > height + arm) return@repeat
        val fade = (.1f + .5f * (y / height)) * (.7f + .3f * sin((ambient + mark) * 2f * PI.toFloat()))
        val leftX = originX + PilotEngine.corridorLeft(mark, state.roundIndex) * width
        val rightX = originX + PilotEngine.corridorRight(mark, state.roundIndex) * width
        drawLine(Gold.copy(alpha = fade), Offset(leftX - arm, y - arm), Offset(leftX, y), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(Gold.copy(alpha = fade), Offset(leftX - arm, y + arm), Offset(leftX, y), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(Gold.copy(alpha = fade), Offset(rightX + arm, y - arm), Offset(rightX, y), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(Gold.copy(alpha = fade), Offset(rightX + arm, y + arm), Offset(rightX, y), 1.6.dp.toPx(), StrokeCap.Round)
    }
}

private fun DrawScope.drawCoins(state: PilotState, width: Float, height: Float, spin: Float, sprite: Painter) {
    val radius = PilotEngine.COIN_RADIUS_UNITS / PilotEngine.UNITS_PER_WIDTH * width
    state.coins.forEach { coin ->
        val y = screenY(coin.distance, state, height)
        if (y < -radius * 2f || y > height + radius * 2f) return@forEach
        val phase = (spin + coin.id * .17f) * 2f * PI.toFloat()
        val turn = .78f + .22f * sin(phase)
        val x = coin.x * width
        val depth = (.45f + .55f * (y / height)).coerceIn(.3f, 1f)
        drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = .34f * depth), Color.Transparent),
            Offset(x, y), radius * 1.7f), radius * 1.7f, Offset(x, y))
        translate(x - radius * turn, y - radius) {
            with(sprite) { draw(Size(radius * 2f * turn, radius * 2f), alpha = depth) }
        }
        // A glint that sweeps the face as the coin turns.
        val glint = (sin(phase) * .5f + .5f) * depth
        if (glint > .55f) {
            val arm = radius * .95f * glint
            drawLine(White.copy(alpha = (glint - .55f) * 1.8f), Offset(x - arm, y), Offset(x + arm, y), 1.2.dp.toPx(), StrokeCap.Round)
            drawLine(White.copy(alpha = (glint - .55f) * 1.4f), Offset(x, y - arm), Offset(x, y + arm), 1.2.dp.toPx(), StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawClouds(state: PilotState, width: Float, height: Float, sprite: Painter) {
    state.clouds.forEach { cloud ->
        val radius = cloud.radius / PilotEngine.UNITS_PER_WIDTH * width
        val y = screenY(cloud.distance, state, height)
        if (y < -radius * 2f || y > height + radius * 2f) return@forEach
        val x = cloud.centerX(state.elapsedSeconds) * width
        val depth = (.5f + .5f * (y / height)).coerceIn(.35f, 1f)
        // Each storm carries its own charge cycle, so the corridor never flashes in unison.
        val charge = ((state.elapsedSeconds * .55f + cloud.sway) % 1f)
        val strike = if (charge < .14f) 1f - charge / .14f else 0f
        val swell = 1f + .04f * sin(state.elapsedSeconds * 1.7f + cloud.sway) + strike * .05f
        drawCircle(Brush.radialGradient(
            listOf(Color(0xFF9B6BFF).copy(alpha = (.14f + .3f * strike) * depth), Color.Transparent),
            Offset(x, y), radius * 1.5f), radius * 1.5f, Offset(x, y))
        // Drawn only a little wider than the hitbox: a storm must look as dangerous as it is.
        translate(x - radius * 1.1f * swell, y - radius * .85f * swell) {
            with(sprite) { draw(Size(radius * 2.2f * swell, radius * 1.7f * swell), alpha = depth) }
        }
        if (strike > 0f) drawBolt(x, y, radius, cloud.id, strike * depth)
    }
}

private fun DrawScope.drawBolt(x: Float, y: Float, radius: Float, seed: Int, intensity: Float) {
    val path = Path()
    val top = y - radius * .5f
    path.moveTo(x - radius * .22f, top)
    repeat(4) { step ->
        val fraction = (step + 1) / 4f
        val jitter = sin(seed * 12.9898f + step * 4.1414f) * radius * .3f
        path.lineTo(x - radius * .22f + radius * .44f * fraction + jitter, top + radius * .95f * fraction)
    }
    drawPath(path, Color(0xFFC9A6FF).copy(alpha = intensity * .55f), style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, White.copy(alpha = intensity), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawPlane(
    state: PilotState, width: Float, height: Float,
    ambient: Float, breath: Float, boost: Float, sprite: Painter
) {
    if (state.phase == PilotPhase.COMPLETE && state.isDead) return
    val x = state.planeX * width
    val bobY = if (state.phase == PilotPhase.FLYING) sin(breath * 2f * PI.toFloat()) * height * .006f else 0f
    val y = height * PilotEngine.PLANE_Y + bobY
    // Kept close to the hitbox so a near miss reads as a near miss.
    val span = PilotEngine.PLANE_RADIUS * width * 2.4f
    val bank = ((state.targetX - state.planeX) * 260f).coerceIn(-26f, 26f)
    val roll = abs(bank) / 26f

    // Exhaust: a tapered plume that lengthens and brightens with the speed tier.
    val plume = span * (.85f + boost * .75f)
    drawPath(Path().apply {
        moveTo(x - span * .17f, y + span * .28f)
        lineTo(x + span * .17f, y + span * .28f)
        lineTo(x + span * .04f, y + span * .28f + plume)
        lineTo(x - span * .04f, y + span * .28f + plume)
        close()
    }, Brush.verticalGradient(
        listOf(Ember.copy(alpha = .45f + boost * .2f), Ember.copy(alpha = .1f), Color.Transparent),
        startY = y, endY = y + span * .28f + plume))
    repeat(6) { index ->
        val trail = (ambient * 3.4f + index * .167f) % 1f
        drawCircle(Gold.copy(alpha = (1f - trail) * (.3f + boost * .15f)),
            span * .11f * (1f - trail * .72f), Offset(x - bank * trail * .35f, y + span * (.3f + trail * .95f)))
    }
    drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = .2f), Color.Transparent), Offset(x, y), span * .95f),
        span * .95f, Offset(x, y))
    // A hard bank shows its wingtip vortices.
    if (roll > .35f) {
        val side = if (bank > 0f) 1f else -1f
        repeat(3) { index ->
            val tail = span * (.3f + index * .22f)
            drawLine(White.copy(alpha = (roll - .35f) * .5f * (1f - index * .3f)),
                Offset(x + side * span * .46f, y + tail * .3f),
                Offset(x + side * span * .46f - side * span * .1f, y + tail), 1.dp.toPx(), StrokeCap.Round)
        }
    }
    // The sprite is drawn nose-right, so the climb costs a quarter turn before the bank is applied.
    // Squashing across the roll axis turns a flat rotation into a banking turn.
    rotate(PLANE_NOSE_UP + bank, Offset(x, y)) {
        translate(x - span / 2f, y - span / 2f) {
            scale(1f, 1f - roll * .18f, Offset(span / 2f, span / 2f)) {
                with(sprite) { draw(Size(span, span)) }
            }
        }
    }
}

/** Gold spray where a coin was taken, using the pickup clock the engine already keeps. */
private fun DrawScope.drawPickupBurst(state: PilotState, width: Float, height: Float) {
    val pickup = state.lastPickup ?: return
    val life = (state.pickupSeconds / PilotEngine.PICKUP_SECONDS).coerceIn(0f, 1f)
    if (life >= 1f) return
    val fade = 1f - life
    val point = Offset(pickup.x * width, height * PilotEngine.PLANE_Y)
    val reach = if (pickup.isStreakBonus) 70.dp.toPx() else 34.dp.toPx()
    drawCircle(Gold.copy(alpha = fade * .5f), 6.dp.toPx() + life * reach, point, style = Stroke(fade * 2.dp.toPx()))
    repeat(if (pickup.isStreakBonus) 16 else 8) { index ->
        val angle = index * 2.39996f
        val radius = life * reach * (.6f + (index % 3) * .2f)
        drawCircle(if (pickup.isStreakBonus) Gold.copy(alpha = fade) else Sky.copy(alpha = fade),
            (1f + index % 2) * fade.dp.toPx(), point + Offset(cos(angle) * radius, sin(angle) * radius))
    }
}

private fun DrawScope.drawCrash(state: PilotState, width: Float, height: Float) {
    if (state.impact == PilotImpact.NONE) return
    val progress = (state.crashSeconds / PilotEngine.CRASH_SECONDS).coerceIn(0f, 1f)
    val fade = 1f - progress
    val point = Offset(state.planeX * width, height * PilotEngine.PLANE_Y)
    if (progress < .25f) drawRect(Ember.copy(alpha = .26f * (1f - progress / .25f)))
    // Shockwave: a thick ring that expands fast and thins as it goes.
    drawCircle(Ember.copy(alpha = fade * fade * .55f), 10.dp.toPx() + progress * 150.dp.toPx(), point,
        style = Stroke((10f * fade + 1f).dp.toPx()))
    drawCircle(Ember.copy(alpha = fade * .8f), 12.dp.toPx() + progress * 120.dp.toPx(), point, style = Stroke(2.5.dp.toPx()))
    drawCircle(Gold.copy(alpha = fade * .5f), 6.dp.toPx() + progress * 70.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
    drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = fade * .5f), Color.Transparent), point, 60.dp.toPx()),
        60.dp.toPx(), point)
    repeat(30) { index ->
        val angle = index * 2.39996f
        val speed = .5f + (index % 5) * .16f
        val radius = progress * 150.dp.toPx() * speed
        // Debris arcs: thrown out, then pulled down.
        val debris = point + Offset(cos(angle) * radius, sin(angle) * radius + progress * progress * 80.dp.toPx())
        drawCircle(if (index % 3 == 0) Gold.copy(alpha = fade) else Ember.copy(alpha = fade),
            (1f + index % 3) * fade.dp.toPx(), debris)
    }
}

/** A short, damped kick on impact. Returns pixels, already zero once the crash settles. */
private fun DrawScope.crashShake(state: PilotState): Offset {
    if (state.impact == PilotImpact.NONE) return Offset.Zero
    val progress = (state.crashSeconds / PilotEngine.CRASH_SECONDS).coerceIn(0f, 1f)
    val decay = (1f - progress).let { it * it }
    val amount = 7.dp.toPx() * decay
    return Offset(sin(progress * 46f) * amount, cos(progress * 38f) * amount * .5f)
}

private fun screenY(distance: Float, state: PilotState, height: Float): Float =
    height * (PilotEngine.PLANE_Y - (distance - state.scrollDistance) / PilotEngine.VIEW_UNITS)

private const val RAIL_SPACING = 2f
private const val CHEVRON_SPACING = 3f
/** World units of corridor visible below the plane, so decoration starts behind it. */
private const val BEHIND_UNITS = PilotEngine.VIEW_UNITS * (1f - PilotEngine.PLANE_Y)
private const val PLANE_NOSE_UP = -90f
/** How close to a wall, past the plane's own width, before it starts glowing a warning. */
private const val DANGER_RANGE = .075f
/** A 25 second final steps up three times, so four bars cover every tier the trial can reach. */
private const val SPEED_TIERS = 4

@Composable
private fun SpeedGauge(state: PilotState, modifier: Modifier) {
    val lit = (state.speedStep + 1).coerceIn(1, SPEED_TIERS)
    val fill = lit.toFloat() / SPEED_TIERS
    // A newly lit tier flares once, so a speed step is felt and not just displayed.
    val surge = remember { Animatable(0f) }
    LaunchedEffect(lit) {
        surge.snapTo(1f)
        surge.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
    }
    val description = stringResource(R.string.pilot_speed_description, lit, state.scrollDistance.toInt())
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Night.copy(alpha = .78f))
        .border(1.dp, Sky.copy(alpha = .24f + surge.value * .5f), RoundedCornerShape(18.dp))
        .padding(vertical = 12.dp, horizontal = 8.dp)
        .semantics { contentDescription = description; progressBarRangeInfo = ProgressBarRangeInfo(fill, 0f..1f) },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(stringResource(R.string.pilot_speed), Sky)
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)) {
            val gap = 5.dp.toPx()
            val cell = (size.height - gap * (SPEED_TIERS - 1)) / SPEED_TIERS
            repeat(SPEED_TIERS) { index ->
                val top = size.height - (index + 1) * cell - index * gap
                val on = index < lit
                val newest = index == lit - 1
                val color = if (index >= SPEED_TIERS - 2) Ember else Sky
                drawRoundRect(if (on) color.copy(alpha = .85f) else Color(0xFF3A1C26),
                    Offset(0f, top), Size(size.width, cell), CornerRadius(4.dp.toPx()))
                if (on) {
                    val bloom = (if (newest) surge.value else 0f) * 5f
                    drawRoundRect(color.copy(alpha = .28f + (if (newest) surge.value * .5f else 0f)),
                        Offset(-(2f + bloom).dp.toPx(), top - (2f + bloom).dp.toPx()),
                        Size(size.width + (4f + bloom * 2f).dp.toPx(), cell + (4f + bloom * 2f).dp.toPx()),
                        CornerRadius(6.dp.toPx()))
                }
                if (newest && surge.value > 0f) {
                    drawRoundRect(White.copy(alpha = surge.value * .6f), Offset(0f, top),
                        Size(size.width, cell), CornerRadius(4.dp.toPx()))
                }
            }
        }
        Text(stringResource(R.string.pilot_speed_value, lit), color = Sky, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(4.dp).background(color, CircleShape))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}
