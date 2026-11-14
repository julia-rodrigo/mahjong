package mahjong.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mahjong.engine.Glossary
import mahjong.engine.Tiles
import mahjong.engine.Yaku

@Composable
private fun P(text: String) {
    Text(text, color = Palette.Ink, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Caption(text: String) {
    Text(text, color = Palette.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun Tiles(code: String, w: Dp, labels: Boolean) {
    Row { for (group in code.split(" ")) { TileRow(Tiles.parse(group), w, labels); Spacer(Modifier.width(w * .3f)) } }
}

/** A multiple-choice question. Each answer is a tile group plus the feedback shown when it is tapped. */
@Composable
private fun Quiz(question: String, answers: List<Triple<String, Boolean, String>>, w: Dp, labels: Boolean, textAnswers: Boolean = false) {
    var picked by remember { mutableIntStateOf(-1) }
    Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp)) {
        Text("Try it", color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(question, color = Palette.Ink, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
        answers.forEachIndexed { i, (content, right, _) ->
            val border = when { picked != i -> Color.Transparent; right -> Palette.Good; else -> Palette.Bad }
            Row(
                Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.FeltDark)
                    .border(2.dp, border, RoundedCornerShape(10.dp)).clickable { picked = i }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (textAnswers) Text(content, color = Palette.Ink, fontSize = 15.sp) else Tiles(content, w, labels)
            }
        }
        if (picked >= 0) {
            val (_, right, why) = answers[picked]
            Text((if (right) "Correct! " else "Not quite. ") + why, color = if (right) Palette.Good else Palette.Warn, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private val LESSON_TITLES = listOf("The tiles", "Sets", "A winning hand", "How a turn works", "You need a yaku", "Playing with the tutor")

@Composable
fun LessonScreen(vm: GameViewModel) {
    var page by remember { mutableIntStateOf(0) }
    val labels = vm.labels
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = (maxWidth - 60.dp) / 14f
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lesson ${page + 1} of ${LESSON_TITLES.size}", color = Palette.InkDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton({ vm.finishLesson(); vm.screen = Screen.HOME }) { Text("Skip the lesson") }
            }
            key(page) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text(LESSON_TITLES[page], color = Palette.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    when (page) {
                        0 -> {
                            P("Mahjong is played with 136 tiles: four copies of each of the 34 tiles below. There are three suits numbered 1 to 9, and seven honor tiles.")
                            Caption("Characters (man): the red 萬 symbol"); Tiles("123456789m", w * 1.4f, labels)
                            Caption("Circles (pin)"); Tiles("123456789p", w * 1.4f, labels)
                            Caption("Bamboo (sou)"); Tiles("123456789s", w * 1.4f, labels)
                            Caption("Winds: East, South, West, North"); Tiles("1234z", w * 1.4f, labels)
                            Caption("Dragons: White, Green, Red"); Tiles("567z", w * 1.4f, labels)
                            P("The 1s and 9s are called terminals. Winds and dragons together are called honors. Tiles 2 to 8 are simples.")
                            P(if (labels) "Each tile shows a number or letter on top and its suit underneath. You can switch to traditional tile faces in the options." else "You can turn on numbers and letters on the tiles in the options.")
                        }
                        1 -> {
                            P("Hands are built from sets of three tiles. There are two kinds of set.")
                            Caption("Sequence: three numbers in a row, same suit"); Tiles("345p 789s", w * 1.4f, labels)
                            Caption("Triplet: three identical tiles"); Tiles("222m 777z", w * 1.4f, labels)
                            P("Sequences cannot wrap around (8-9-1 is not a sequence) and cannot mix suits. Winds and dragons have no order, so they only form triplets.")
                            Quiz("Which of these is a valid set?", listOf(
                                Triple("891s", false, "Sequences do not wrap around from 9 to 1."),
                                Triple("456m", true, "4-5-6 in one suit is a sequence."),
                                Triple("123z", false, "Winds cannot form a sequence, only a triplet of the same wind."),
                            ), w * 1.4f, labels)
                        }
                        2 -> {
                            P("A winning hand has 14 tiles: four sets and one pair.")
                            Caption("Four sets + a pair"); Tiles("234m 567p 678s 555z 99s", w, labels)
                            P("You hold 13 tiles. On your turn you draw a 14th. If the 14 tiles make four sets and a pair, you can win. Otherwise you discard one tile and stay at 13.")
                            P("When you are one tile away from winning, your hand is called ready (tenpai).")
                            Caption("This hand is ready"); Tiles("234m 567p 678s 555z 9s", w, labels)
                            Quiz("Which tile completes the hand above?", listOf(
                                Triple("8s", false, "The lone 9 of bamboo needs a partner to become the pair."),
                                Triple("9s", true, "A second 9 of bamboo makes the pair, giving four sets and a pair."),
                                Triple("5z", false, "A fourth white dragon does not help: the 9 of bamboo would still be alone."),
                            ), w * 1.4f, labels)
                        }
                        3 -> {
                            P("Players take turns in a fixed order: you, then the player on your right, then across, then left. On your turn you draw one tile and discard one tile.")
                            P("In this app: tap a tile to select it, then tap it again to discard it.")
                            Caption("Taking other players' discards")
                            P("Pon: if you hold two identical tiles and anyone discards a third, you may take it to make a triplet.")
                            P("Chi: if the player on your left discards a tile that completes a sequence with two of yours, you may take it.")
                            P("Kan: a set of four identical tiles. It counts as a triplet and gives you an extra draw.")
                            P("Called sets are laid face up and your hand becomes open. An open hand is faster, but as you will see next, it is also harder to win with. As a beginner, calling rarely is a good habit.")
                            Caption("Winning")
                            P("Tsumo: you draw the winning tile yourself. Everyone pays you.")
                            P("Ron: another player discards your winning tile. Only that player pays.")
                            P("If nobody wins before the tiles run out, the hand is a draw.")
                        }
                        4 -> {
                            P("This is the rule that surprises every beginner: four sets and a pair are not enough. Your hand must also contain at least one yaku, a scoring pattern.")
                            P("There are about 40 yaku, but three are enough to start with:")
                            Caption("1. Riichi")
                            P("When your hand is ready and closed (you have not called chi or pon), you may declare riichi. It costs 1,000 points and counts as a yaku. After riichi your hand is locked: you discard whatever you draw until you win.")
                            Caption("2. All Simples (tanyao)")
                            P("A hand with only tiles from 2 to 8.")
                            Tiles("234m 567p 678s 333p 66s", w, labels)
                            Caption("3. Value tiles (yakuhai)")
                            P("A triplet of dragons, of your own seat wind, or of the round wind.")
                            Tiles("777z 555z", w * 1.4f, labels)
                            Quiz("Your closed hand is ready, but it has no other yaku. What should you do?", listOf(
                                Triple("Just wait for someone to discard my tile", false, "Without a yaku you could not call ron on it."),
                                Triple("Declare riichi", true, "Riichi is itself a yaku, so any winning tile now wins."),
                                Triple("Call pon to finish faster", false, "Calling opens your hand, and an open hand cannot riichi."),
                            ), w, labels, textAnswers = true)
                            P("Dora are bonus tiles shown at the top of the screen. Each one in a winning hand adds value, but dora are not a yaku.")
                        }
                        else -> {
                            P("While you play, the tutor panel in the middle of the screen tells you:")
                            P("•  what just happened,")
                            P("•  what it suggests you do, and why,")
                            P("•  how many tiles you are away from a ready hand, and which tiles would help,")
                            P("•  warnings before mistakes: no yaku, furiten, or a dangerous discard when someone has declared riichi.")
                            P("The suggested tile has a green outline, and the suggested button a green border. You are always free to do something else.")
                            P("New words are explained the first time they appear, and the full word list and yaku list are in the menu (☰).")
                            P("After every hand you will see exactly how the winning hand was scored.")
                            Caption("Tutor level")
                            SettingsBlock(vm)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page > 0) BigButton("Back", { page-- }, Modifier.weight(1f), color = Palette.FeltLight, textColor = Palette.Ink)
                if (page < LESSON_TITLES.size - 1) BigButton("Next", { page++ }, Modifier.weight(2f))
                else BigButton("Start playing", { vm.finishLesson(); vm.newGame() }, Modifier.weight(2f), color = Palette.Good)
            }
        }
    }
}

@Composable
fun GlossaryScreen(vm: GameViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Words & yaku", color = Palette.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton({ vm.screen = vm.glossaryFrom }) { Text("Close") }
        }
        Choice(listOf(0 to "Words", 1 to "Yaku (scoring patterns)"), tab, { tab = it }, Modifier.padding(vertical = 8.dp))
        if (tab == 0) {
            LazyColumn(Modifier.weight(1f)) {
                items(Glossary.terms) { t ->
                    Column(Modifier.padding(vertical = 7.dp)) {
                        Text(t.word, color = Palette.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(t.definition, color = Palette.Ink, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text("You need at least one of these to win. \"Closed only\" yaku do not count once you have called chi, pon or an open kan.",
                        color = Palette.InkDim, fontSize = 13.sp, lineHeight = 18.sp)
                }
                items(Yaku.entries.toList()) { y ->
                    Column(Modifier.padding(vertical = 7.dp)) {
                        Row {
                            Text(y.english, color = Palette.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                when {
                                    y.yakuman -> "yakuman"
                                    y.openHan < 0 -> "${y.han} han · closed only"
                                    y.openHan != y.han -> "${y.han} han · ${y.openHan} open"
                                    else -> "${y.han} han"
                                },
                                color = Palette.Ink, fontSize = 13.sp,
                            )
                        }
                        Text(y.japanese, color = Palette.InkDim, fontSize = 12.sp)
                        Text(y.desc, color = Palette.Ink, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}
