package app.krafted.fantasyheroestournament.trial.joker

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.krafted.fantasyheroestournament.R
import kotlin.math.ceil

/**
 * Begin button before the deal, standing order once it starts: one slot, like Zeus's charge control
 * and the Pilot's flight stick. The grid itself is the Joker's input, so this slot says what to do
 * with it and how long the order stands.
 */
@Composable
internal fun RuleControl(state: JokerState, onStart: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    if (state.phase == JokerPhase.READY) {
        // The waiting button breathes and its cap tips side to side: the court is ready to deal.
        val idle = rememberInfiniteTransition(label = "deal")
        val breathe by idle.animateFloat(1f, 1.018f,
            infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
        val tilt by idle.animateFloat(-5f, 5f,
            infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
        Box(Modifier.fillMaxWidth().heightIn(min = 66.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }
            .clip(shape).background(Brush.verticalGradient(listOf(Color(0xFFF6DDA0), Color(0xFFD9AA58))))
            .border(1.dp, Gold, shape).clickable(role = Role.Button, onClick = onStart).testTag("launch"),
            contentAlignment = Alignment.Center) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                JesterIcon(Night, Modifier.size(24.dp).graphicsLayer { rotationZ = tilt })
                Text(stringResource(R.string.joker_begin), color = Night, fontSize = 13.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }
    val playing = state.phase == JokerPhase.PLAYING
    val accent = if (state.isFreeze) Mint else Candy
    Column(Modifier.fillMaxWidth().heightIn(min = 66.dp).clip(shape).background(Panel.copy(alpha = .9f))
        .border(1.dp, if (playing) accent.copy(alpha = .5f) else Muted.copy(alpha = .2f), shape)
        .padding(vertical = 14.dp, horizontal = 16.dp).testTag("prompt"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(stringResource(when {
            !playing -> R.string.joker_over
            state.isFreeze -> R.string.joker_hold_still
            else -> R.string.joker_tap_prompt
        }), color = if (playing) accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        if (playing) Text(stringResource(R.string.joker_next_rule, ceil(state.ruleRemaining).toInt()),
            color = Muted, fontSize = 10.sp)
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
        JesterIcon(Gold, Modifier.size(42.dp))
        Eyebrow(stringResource(R.string.joker_paused), Gold)
        Text(stringResource(R.string.joker_pause_hint), color = White, textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 24.sp)
        GoldButton(stringResource(R.string.joker_resume), onResume)
        Text(stringResource(R.string.joker_restart), color = Muted, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onRestart).padding(16.dp))
    }
}

@Composable
internal fun TrialCompleteDialog(state: JokerState, onRestart: () -> Unit, onSelectRound: (Int) -> Unit) {
    GameDialog(onDismiss = onRestart) {
        Box(Modifier.size(78.dp).background(Gold.copy(alpha = .08f), CircleShape).border(1.dp, Gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
            JesterIcon(Gold, Modifier.size(42.dp))
        }
        Eyebrow(stringResource(if (state.timedOut) R.string.joker_time_up else R.string.joker_complete), Gold)
        Text(stringResource(when { state.score >= 800 -> R.string.joker_result_great
            state.score >= 390 -> R.string.joker_result_good; else -> R.string.joker_result_try }),
            color = White, fontSize = 26.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.joker_points_value, state.score), color = Gold, fontSize = 64.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.testTag("final_score"))
            Text(stringResource(R.string.joker_score_max), color = Muted, fontSize = 14.sp)
        }
        Text(stringResource(R.string.joker_result_detail, state.correctTaps,
            state.correctTaps * RuleEngine.CORRECT_POINTS, state.wrongTaps, state.wrongTaps * state.penalty),
            color = White, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (state.freezeBonuses > 0) Text(pluralStringResource(R.plurals.joker_freeze_claimed,
            state.freezeBonuses, state.freezeBonuses, state.freezeBonuses * RuleEngine.FREEZE_BONUS),
            color = Mint, fontSize = 12.sp)
        GoldButton(stringResource(R.string.joker_replay), onRestart)
        Eyebrow(stringResource(R.string.joker_difficulty))
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
