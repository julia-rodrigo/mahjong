package mahjong.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mahjong.engine.Difficulty
import mahjong.engine.Phase

@Composable
fun App(vm: GameViewModel) {
    AppTheme {
        Surface(Modifier.fillMaxSize(), color = Palette.Felt) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (vm.screen) {
                    Screen.HOME -> HomeScreen(vm)
                    Screen.LESSON -> LessonScreen(vm)
                    Screen.GAME -> GameScreen(vm)
                    Screen.GLOSSARY -> GlossaryScreen(vm)
                }
            }
        }
    }
    BackHandler(vm.screen != Screen.HOME) {
        when (vm.screen) {
            Screen.GAME -> vm.quitToHome()
            Screen.GLOSSARY -> vm.screen = vm.glossaryFrom
            else -> vm.screen = Screen.HOME
        }
    }
}

/** A row of mutually exclusive choices. */
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.FeltDark).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (on) Palette.Gold else Color.Transparent)
                    .clickable { onSelect(value) }.padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color(0xFF2A2000) else Palette.Ink, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Palette.InkDim, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
fun SettingsBlock(vm: GameViewModel) {
    SectionLabel("TUTOR")
    Choice(TutorLevel.entries.map { it to it.title }, vm.tutorLevel, vm::setTutorPref)
    Text(vm.tutorLevel.blurb, color = Palette.InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Numbers and letters on tiles", color = Palette.Ink, fontSize = 15.sp)
            Text("So you do not have to read the Chinese characters.", color = Palette.InkDim, fontSize = 13.sp)
        }
        Switch(vm.labels, vm::setLabelsPref)
    }
}

@Composable
fun HomeScreen(vm: GameViewModel) {
    val inProgress = vm.game?.let { it.state.phase != Phase.GAME_END } == true || vm.hasSave
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Riichi Tutor", color = Palette.Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Japanese mahjong against three computer players, with a coach who explains every move.",
            color = Palette.InkDim, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(Modifier.padding(bottom = 16.dp)) {
            for (k in listOf(0, 13, 26, 27, 31, 32, 33)) TileView(k, 40.dp, labels = vm.labels)
        }
        if (inProgress) {
            Button(onClick = vm::resume, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Continue game", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
        val primaryNew = !inProgress
        val startNew = { if (vm.lessonDone) vm.newGame() else vm.screen = Screen.LESSON }
        if (primaryNew) {
            Button(onClick = startNew, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp)) {
                Text(if (vm.lessonDone) "New game" else "Start: learn the basics, then play", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(onClick = startNew, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Text("New game", fontSize = 16.sp, color = Palette.Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.screen = Screen.LESSON }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Lesson", fontSize = 15.sp, color = Palette.Ink)
            }
            OutlinedButton(onClick = vm::openGlossary, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Words & yaku", fontSize = 15.sp, color = Palette.Ink)
            }
        }
        SectionLabel("OPPONENTS (NEXT NEW GAME)")
        Choice(listOf(Difficulty.EASY to "Easy", Difficulty.NORMAL to "Normal"), vm.difficulty, vm::setDifficultyPref)
        SectionLabel("GAME LENGTH (NEXT NEW GAME)")
        Choice(listOf(true to "East only (short)", false to "East + South"), vm.eastOnly, vm::setEastOnlyPref)
        SettingsBlock(vm)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.Gold, textColor: Color = Color(0xFF2A2000), starred: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp).then(if (starred) Modifier.border(3.dp, Palette.Good, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
