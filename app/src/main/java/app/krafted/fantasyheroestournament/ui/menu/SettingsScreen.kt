package app.krafted.fantasyheroestournament.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.krafted.fantasyheroestournament.R
import app.krafted.fantasyheroestournament.data.UserRecords
import app.krafted.fantasyheroestournament.ui.theme.*

/**
 * Sound, haptics, and the one destructive action in the app. Reset asks first
 * and says exactly what it clears, because there is no undo behind it.
 */
@Composable
fun SettingsScreen(
    records: UserRecords,
    onSoundChange: (Boolean) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    PageScaffold(
        title = stringResource(R.string.settings_title),
        eyebrow = stringResource(R.string.settings_eyebrow),
        onBack = onBack,
        backLabel = stringResource(R.string.settings_back)
    ) {
        AnimatedVisibility(entered, enter = fadeIn(tween(550)) + slideInVertically(tween(600)) { 22 }) {
            Column {
                SectionHeader(stringResource(R.string.settings_feel))
                Spacer(Modifier.height(Space.lg))
                Column(Modifier.fillMaxWidth().panel(RoundedCornerShape(Radius.card), elevation = 12.dp)) {
                    ToggleRow(
                        title = stringResource(R.string.settings_sound),
                        body = stringResource(R.string.settings_sound_body),
                        checked = records.soundOn,
                        onCheckedChange = onSoundChange
                    ) { tint -> SpeakerGlyph(tint, records.soundOn) }
                    HairLine(Modifier.padding(horizontal = Space.xl))
                    ToggleRow(
                        title = stringResource(R.string.settings_vibrate),
                        body = stringResource(R.string.settings_vibrate_body),
                        checked = records.vibrateOn,
                        onCheckedChange = onVibrateChange
                    ) { tint -> PulseGlyph(tint, records.vibrateOn) }
                }
            }
        }

        Spacer(Modifier.height(Space.section))
        AnimatedVisibility(entered, enter = fadeIn(tween(500, 140)) + slideInVertically(tween(560, 140)) { 26 }) {
            Column {
                SectionHeader(stringResource(R.string.settings_records), color = Rose)
                Spacer(Modifier.height(Space.lg))
                Column(Modifier.fillMaxWidth().panel(RoundedCornerShape(Radius.card),
                    border = Rose.copy(alpha = .22f), elevation = 12.dp).padding(Space.xl)) {
                    Text(stringResource(R.string.settings_reset_title), color = Parchment,
                        fontSize = TextSize.body, lineHeight = LineHeight.body, fontWeight = FontWeight.Bold)
                    Text(pluralStringResource(R.plurals.settings_reset_body, records.runsCompleted,
                        records.runsCompleted, points(records.bestGrandTotal)),
                        color = Muted, fontSize = TextSize.label, lineHeight = LineHeight.body,
                        modifier = Modifier.padding(top = Space.xs))
                    Spacer(Modifier.height(Space.lg))
                    SecondaryButton(stringResource(R.string.settings_reset_action),
                        { confirmReset = true }, accent = Rose)
                }
            }
        }

        Spacer(Modifier.height(Space.section))
    }

    if (confirmReset) TournamentDialog(onDismiss = { confirmReset = false }) {
        CrossEmblem(Modifier.size(44.dp))
        DialogTitle(stringResource(R.string.settings_reset_confirm_title))
        Text(stringResource(R.string.settings_reset_confirm_body), color = Muted,
            fontSize = TextSize.body, lineHeight = LineHeight.body, textAlign = TextAlign.Center)
        TournamentButton(stringResource(R.string.settings_reset_keep), { confirmReset = false })
        GhostButton(stringResource(R.string.settings_reset_action), {
            confirmReset = false
            onReset()
        }, color = Rose)
    }
}

/**
 * A settings row whose whole surface is the switch. The knob and the track are
 * drawn rather than themed, so the control matches the rest of the tournament.
 */
@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    glyph: @Composable (Color) -> Unit
) {
    val tint = if (checked) Gold else Faint
    Row(Modifier.fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics(mergeDescendants = true) { contentDescription = title }
        .padding(horizontal = Space.xl, vertical = Space.lg),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(Radius.chip))
            .background(tint.copy(alpha = if (checked) .14f else .06f)),
            contentAlignment = Alignment.Center) { glyph(tint) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Parchment, fontSize = TextSize.body, lineHeight = LineHeight.body,
                fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = TextSize.label, lineHeight = LineHeight.label,
                modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked)
    }
}

@Composable
private fun Switch(checked: Boolean) {
    val slide by animateFloatAsState(if (checked) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switch knob")
    Canvas(Modifier.size(52.dp, 30.dp)) {
        val trackHeight = size.height * .78f
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(if (checked) GoldDeep.copy(alpha = .5f) else Well,
            Offset(0f, top), Size(size.width, trackHeight), radius)
        drawRoundRect(if (checked) Gold.copy(alpha = .7f) else Color.White.copy(alpha = .12f),
            Offset(0f, top), Size(size.width, trackHeight), radius, style = Stroke(1.dp.toPx()))
        val knobRadius = trackHeight * .38f
        val travel = size.width - knobRadius * 2f - 4.dp.toPx()
        val cx = knobRadius + 2.dp.toPx() + travel * slide
        drawCircle(if (checked) Gold else Faint, knobRadius, Offset(cx, size.height / 2f))
        drawCircle(Ink.copy(alpha = .35f), knobRadius * .3f, Offset(cx, size.height / 2f))
    }
}

@Composable
private fun SpeakerGlyph(tint: Color, on: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        val body = Path().apply {
            moveTo(size.width * .1f, size.height * .35f)
            lineTo(size.width * .3f, size.height * .35f)
            lineTo(size.width * .55f, size.height * .12f)
            lineTo(size.width * .55f, size.height * .88f)
            lineTo(size.width * .3f, size.height * .65f)
            close()
        }
        drawPath(body, tint)
        if (on) {
            repeat(2) { arc ->
                drawArc(tint, -55f, 110f, false,
                    Offset(size.width * (.45f + arc * .12f), size.height * (.24f - arc * .12f)),
                    Size(size.width * (.35f + arc * .25f), size.height * (.52f + arc * .24f)),
                    style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }
        } else {
            drawLine(tint, Offset(size.width * .68f, size.height * .34f),
                Offset(size.width * .95f, size.height * .66f), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(tint, Offset(size.width * .95f, size.height * .34f),
                Offset(size.width * .68f, size.height * .66f), 1.6.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun PulseGlyph(tint: Color, on: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        drawRoundRect(tint, Offset(size.width * .34f, size.height * .12f),
            Size(size.width * .32f, size.height * .76f),
            CornerRadius(2.5.dp.toPx()), style = Stroke(1.6.dp.toPx()))
        if (on) {
            listOf(-1f, 1f).forEach { side ->
                repeat(2) { wave ->
                    val x = size.width * .5f + side * (size.width * (.42f + wave * .16f))
                    drawLine(tint.copy(alpha = if (wave == 0) .9f else .45f),
                        Offset(x, size.height * (.3f - wave * .06f)),
                        Offset(x, size.height * (.7f + wave * .06f)), 1.6.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}
