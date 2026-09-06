package app.krafted.fantasyheroestournament

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.krafted.fantasyheroestournament.trial.joker.JokerTrialRoute
import app.krafted.fantasyheroestournament.trial.pilot.PilotTrialRoute
import app.krafted.fantasyheroestournament.trial.zeus.ZeusTrialRoute
import app.krafted.fantasyheroestournament.ui.theme.FantasyHeroesTournamentTheme
import androidx.compose.ui.graphics.Color as UiColor

private enum class Trial { ZEUS, PILOT, JOKER }

private val Night = UiColor(0xFF080F21)
private val Panel = UiColor(0xFF111C32)
private val Gold = UiColor(0xFFF3CD82)
private val Muted = UiColor(0xFF9DACC4)
private val White = UiColor(0xFFF6F4EE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            FantasyHeroesTournamentTheme(darkTheme = true, dynamicColor = false) {
                // Placeholder entry point until the Phase C bracket takes over navigation.
                var trial by rememberSaveable { mutableStateOf<Trial?>(null) }
                BackHandler(trial != null) { trial = null }
                when (trial) {
                    Trial.ZEUS -> ZeusTrialRoute(lifecycle)
                    Trial.PILOT -> PilotTrialRoute(lifecycle)
                    Trial.JOKER -> JokerTrialRoute(lifecycle)
                    null -> TrialPicker { trial = it }
                }
            }
        }
    }
}

@Composable
private fun TrialPicker(onPick: (Trial) -> Unit) {
    Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        Column(Modifier.safeDrawingPadding().widthIn(max = 420.dp).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.picker_eyebrow), color = Muted, fontSize = 9.sp,
                letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.picker_title), color = White, fontSize = 34.sp,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(6.dp))
            PickerCard(R.string.zeus_title, R.string.picker_zeus) { onPick(Trial.ZEUS) }
            PickerCard(R.string.pilot_title, R.string.picker_pilot) { onPick(Trial.PILOT) }
            PickerCard(R.string.joker_title, R.string.picker_joker) { onPick(Trial.JOKER) }
            Text(stringResource(R.string.picker_pending), color = Muted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PickerCard(title: Int, subtitle: Int, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().heightIn(min = 74.dp).clickable(role = Role.Button, onClick = onClick),
        color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .3f))) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(title), color = Gold, fontSize = 22.sp, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(stringResource(subtitle), color = Muted, fontSize = 11.sp)
        }
    }
}
