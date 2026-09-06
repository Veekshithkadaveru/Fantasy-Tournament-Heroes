package app.krafted.fantasyheroestournament.trial.zeus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.krafted.fantasyheroestournament.R

@Composable
internal fun ChargeControl(state: ZeusState, onStart: () -> Unit, onHold: () -> Unit, onRelease: () -> Unit, onCancel: () -> Unit) {
    val charging = state.phase == ZeusPhase.CHARGING
    val ready = state.phase == ZeusPhase.READY
    val enabled = !state.paused && (state.canCharge || charging)
    val scale by animateFloatAsState(if (charging) .975f else 1f, tween(160), label = "charge press")
    val currentHold by rememberUpdatedState(onHold)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentCancel by rememberUpdatedState(onCancel)
    val currentState by rememberUpdatedState(state)
    val description = stringResource(R.string.zeus_charge_accessibility)
    val actionLabel = stringResource(if (charging) R.string.zeus_accessibility_release else R.string.zeus_accessibility_charge)
    val shape = RoundedCornerShape(18.dp)
    val input = if (ready) Modifier.clickable(role = Role.Button, onClick = onStart) else Modifier
        // Stable keys prevent meter recomposition from cancelling the active gesture.
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                if (currentState.canCharge) {
                    currentHold()
                    try { if (tryAwaitRelease()) currentRelease() else currentCancel() }
                    finally { currentCancel() }
                }
            })
        }
        .semantics {
            role = Role.Button
            contentDescription = description
            if (!enabled) disabled()
            onClick(actionLabel) {
                if (enabled) { if (charging) currentRelease() else currentHold(); true } else false
            }
        }
        .onKeyEvent { event ->
            if (event.key == Key.Spacebar || event.key == Key.Enter || event.key == Key.DirectionCenter) {
                if (event.type == KeyEventType.KeyDown && currentState.canCharge) currentHold()
                if (event.type == KeyEventType.KeyUp) currentRelease()
                true
            } else false
        }.focusable(enabled)
    Box(Modifier.fillMaxWidth().heightIn(min = 66.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape).background(Brush.verticalGradient(if (charging) listOf(Color(0xFFB3F4FF), Color(0xFF56BDD9))
            else if (ready || enabled) listOf(Color(0xFFF6DDA0), Color(0xFFD9AA58)) else listOf(Panel, Panel)))
        .border(1.dp, if (ready || enabled) Gold else Muted.copy(alpha = .2f), shape)
        .then(input).testTag("charge"), contentAlignment = Alignment.Center) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BoltIcon(if (ready || enabled) Night else Muted, Modifier.size(24.dp))
            Text(stringResource(when { ready -> R.string.zeus_begin; charging -> R.string.zeus_release;
                state.phase == ZeusPhase.STRIKE -> R.string.zeus_recovering; else -> R.string.zeus_hold }),
                color = if (ready || enabled) Night else Muted, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun RoundSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val isSelected = index == selected
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Gold.copy(alpha = .1f) else Color.Transparent)
                .border(1.dp, if (isSelected) Gold.copy(alpha = .35f) else Muted.copy(alpha = .15f), RoundedCornerShape(12.dp))
                .clickable(role = Role.Tab) { onSelect(index) }.semantics { this.selected = isSelected }, contentAlignment = Alignment.Center) {
                Text(roundName(index), color = if (isSelected) Gold else Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
internal fun TrialPauseDialog(onResume: () -> Unit, onRestart: () -> Unit) {
    GameDialog(onDismiss = onResume) {
        BoltIcon(Gold, Modifier.size(42.dp))
        Eyebrow(stringResource(R.string.zeus_paused), Gold)
        Text(stringResource(R.string.zeus_pause_hint), color = White, textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 24.sp)
        GoldButton(stringResource(R.string.zeus_resume), onResume)
        Text(stringResource(R.string.zeus_restart), color = Muted, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onRestart).padding(16.dp))
    }
}

@Composable
internal fun TrialCompleteDialog(state: ZeusState, onRestart: () -> Unit, onSelectRound: (Int) -> Unit) {
    GameDialog(onDismiss = onRestart) {
        Box(Modifier.size(78.dp).background(Gold.copy(alpha = .08f), CircleShape).border(1.dp, Gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
            BoltIcon(Gold, Modifier.size(42.dp))
        }
        Eyebrow(stringResource(if (state.timedOut) R.string.zeus_time_up else R.string.zeus_complete), Gold)
        Text(stringResource(when { state.score >= 800 -> R.string.zeus_result_great; state.score >= 390 -> R.string.zeus_result_good; else -> R.string.zeus_result_try }),
            color = White, fontSize = 26.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.zeus_points_value, state.score), color = Gold, fontSize = 64.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("final_score"))
            Text(stringResource(R.string.zeus_score_max), color = Muted, fontSize = 14.sp)
        }
        Text(stringResource(R.string.zeus_result_detail, state.perfectCount,
            state.strikes.count { it.isHit && !it.isPerfect }, state.strikes.count { !it.isHit }), color = White, fontSize = 13.sp, textAlign = TextAlign.Center)
        val unused = state.totalThrows - state.strikes.size
        if (unused > 0) Text(pluralStringResource(R.plurals.zeus_unthrown, unused, unused), color = Muted, fontSize = 12.sp)
        GoldButton(stringResource(R.string.zeus_replay), onRestart)
        Eyebrow(stringResource(R.string.zeus_difficulty))
        RoundSelector(state.roundIndex, onSelectRound)
    }
}

@Composable
private fun GameDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(max = 430.dp).fillMaxWidth(), color = Night,
            shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .4f))) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
        }
    }
}

@Composable
private fun GoldButton(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFFF6DDA0), Color(0xFFD9AA58))))
        .clickable(role = Role.Button, onClick = onClick).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Night, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}
