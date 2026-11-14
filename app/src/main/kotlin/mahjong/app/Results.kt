package mahjong.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mahjong.engine.Game
import mahjong.engine.Tiles
import mahjong.engine.Tutor
import mahjong.engine.WinInfo

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp),
    ) { content() }
}

@Composable
private fun WinCard(vm: GameViewModel, g: Game, w: WinInfo, tile: Dp) {
    val s = w.score
    Card {
        Text(
            if (w.winner == HUMAN) "You won!" else "${PLAYER_NAMES[w.winner]} won",
            color = if (w.winner == HUMAN) Palette.Good else Palette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            if (w.loser < 0) "by tsumo (drew the winning tile)" else "by ron on ${if (w.loser == HUMAN) "your" else PLAYER_NAMES[w.loser] + "'s"} discard",
            color = if (w.loser == HUMAN) Palette.Bad else Palette.InkDim, fontSize = 14.sp,
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            for (t in w.hand) TileView(t, tile, labels = vm.labels)
            Spacer(Modifier.width(6.dp))
            TileView(w.winTile, tile, labels = vm.labels, outline = Palette.Gold)
        }
        if (w.melds.isNotEmpty()) MeldRow(w.melds, tile, vm.labels, Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        for (y in s.yaku) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("${y.yaku.english}  (${y.yaku.japanese})", color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(y.yaku.desc, color = Palette.InkDim, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Text(if (y.yaku.yakuman) "yakuman" else "${y.han} han", color = Palette.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (s.dora > 0) Row(Modifier.fillMaxWidth()) {
            Text("Dora (bonus tiles)", color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${s.dora} han", color = Palette.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (s.uraDora > 0) Row(Modifier.fillMaxWidth()) {
            Text("Ura dora (riichi bonus)", color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${s.uraDora} han", color = Palette.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        val head = if (s.yakumanCount > 0) "Yakuman" else "${s.han} han, ${s.fu} fu" + (if (s.limit.isNotEmpty()) "  ·  ${s.limit}" else "")
        Text("$head  =  ${fmt(w.deltas[w.winner])} points", color = Palette.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("How the score was worked out", color = Palette.InkDim, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        for (line in Tutor.explainWin(w, PLAYER_NAMES, g.state.dealer).drop(1)) {
            Text("•  $line", color = Palette.Ink, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun HandResultOverlay(vm: GameViewModel, g: Game, w: Dp) {
    val st = g.state
    val r = st.result ?: return
    val tile = (w - 56.dp) / 15f
    Column(Modifier.fillMaxSize().background(Palette.FeltDark).clickable(enabled = false) {}) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            Text(st.handName + if (r.isDraw) ": draw" else ": hand over", color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            for (win in r.wins) WinCard(vm, g, win, tile)
            if (r.isDraw) Card {
                Text("Nobody won before the wall ran out.", color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val ready = (0 until 4).filter { r.tenpai[it] }
                Text(
                    when (ready.size) {
                        0 -> "No one was ready (tenpai), so no points change hands."
                        4 -> "Everyone was ready (tenpai), so no points change hands."
                        else -> "Ready (tenpai): ${ready.joinToString(", ") { PLAYER_NAMES[it] }}. The players who were not ready pay them 3,000 points in total."
                    },
                    color = Palette.InkDim, fontSize = 14.sp, lineHeight = 19.sp,
                )
                for (p in ready) {
                    Text(PLAYER_NAMES[p], color = Palette.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    Row { for (t in r.hands[p]) TileView(t, tile, labels = vm.labels) }
                    if (st.players[p].melds.isNotEmpty()) MeldRow(st.players[p].melds, tile, vm.labels)
                }
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dora  ", color = Palette.InkDim, fontSize = 13.sp)
                    for (i in st.revealedDora) TileView(Tiles.doraFor(i), tile, labels = vm.labels)
                    if (r.uraIndicators.isNotEmpty()) {
                        Text("   Ura dora  ", color = Palette.InkDim, fontSize = 13.sp)
                        for (i in r.uraIndicators) TileView(Tiles.doraFor(i), tile, labels = vm.labels)
                    }
                }
                Spacer(Modifier.height(8.dp))
                for (p in 0 until 4) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(PLAYER_NAMES[p], color = Palette.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        val d = r.deltas[p]
                        Text(
                            if (d == 0) "" else (if (d > 0) "+" else "") + fmt(d),
                            color = if (d > 0) Palette.Good else Palette.Bad, fontSize = 15.sp, modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(fmt(st.players[p].score), color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    if (r.dealerContinues) "${PLAYER_NAMES[st.dealer]} ${if (st.dealer == HUMAN) "stay" else "stays"} dealer and the repeat counter goes up."
                    else "The deal passes to ${PLAYER_NAMES[(st.dealer + 1) % 4]}.",
                    color = Palette.InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        BigButton("Continue", vm::nextHand, Modifier.fillMaxWidth().padding(10.dp))
    }
}

@Composable
fun GameOverOverlay(vm: GameViewModel, g: Game) {
    val rank = g.ranking()
    val place = rank.indexOf(HUMAN) + 1
    Column(Modifier.fillMaxSize().background(Palette.FeltDark).clickable(enabled = false) {}.padding(20.dp), verticalArrangement = Arrangement.Center) {
        Text("Game over", color = Palette.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "You finished in ${listOf("1st", "2nd", "3rd", "4th")[place - 1]} place." + if (place == 1) " Well played!" else "",
            color = if (place == 1) Palette.Good else Palette.InkDim, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        rank.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (p == HUMAN) Palette.FeltLight else Palette.Panel).padding(14.dp),
            ) {
                Text("${i + 1}.  ${PLAYER_NAMES[p]}", color = Palette.Ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(fmt(g.state.players[p].score), color = Palette.Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        BigButton("New game", vm::newGame, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        BigButton("Main menu", vm::quitToHome, Modifier.fillMaxWidth(), color = Palette.FeltLight, textColor = Palette.Ink)
    }
}
