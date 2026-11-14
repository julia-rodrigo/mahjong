package mahjong.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mahjong.engine.Action
import mahjong.engine.Advice
import mahjong.engine.Game
import mahjong.engine.GameState
import mahjong.engine.Glossary
import mahjong.engine.Meld
import mahjong.engine.MeldType
import mahjong.engine.Phase
import mahjong.engine.Tiles
import mahjong.engine.Tutor
import mahjong.engine.Verdict

fun fmt(n: Int): String = "%,d".format(n)
private val WIND_LETTER = arrayOf("E", "S", "W", "N")

@Composable
fun GameScreen(vm: GameViewModel) {
    val v = vm.version
    val g = vm.game ?: return
    val st = g.state
    var menu by remember { mutableStateOf(false) }
    val advice = remember(v) { vm.advice() }
    val shown = if (vm.tutorLevel == TutorLevel.FULL || (vm.tutorLevel == TutorLevel.HINTS && vm.hintShown)) advice else null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val small = (w - 12.dp) / 19f
        Column(Modifier.fillMaxSize()) {
            // The engine state is mutable, so everything that draws it is rebuilt whenever the version changes.
            key(v) {
                Header(st, v, vm.labels, small) { menu = true }
                Table(st, v, small, vm.labels)
            }
            TutorPanel(vm, g, v, advice, shown, small, Modifier.weight(1f))
            key(v) {
                ActionBar(vm, g, v, advice, shown)
                MyHand(vm, g, v, w, small, shown)
            }
        }
        key(v) {
            if (st.phase == Phase.HAND_END) HandResultOverlay(vm, g, w)
            if (st.phase == Phase.GAME_END) GameOverOverlay(vm, g)
        }
    }
    if (menu) GameMenu(vm) { menu = false }
}

@Composable
private fun Header(st: GameState, v: Int, labels: Boolean, small: Dp, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.FeltDark).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${st.handName}  ·  ${st.wall.size} tiles left", color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Repeats ${st.honba}  ·  Riichi bets ${st.riichiSticks}", color = Palette.InkDim, fontSize = 12.sp)
        }
        Text("Dora", color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
        for (ind in st.revealedDora) TileView(Tiles.doraFor(ind), small * 1.15f, labels = labels)
        Text("☰", color = Palette.Ink, fontSize = 24.sp, modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onMenu).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun Badge(st: GameState, seat: Int, v: Int, small: Dp, labels: Boolean, modifier: Modifier = Modifier) {
    val pl = st.players[seat]
    val active = (st.phase == Phase.DISCARD && st.turn == seat)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(if (active) Palette.FeltLight else Color.Transparent)
                .border(1.5.dp, if (active) Palette.Gold else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val wind = st.seatWind(seat) - Tiles.EAST
            Text(
                WIND_LETTER[wind], fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (wind == 0) Color(0xFF2A2000) else Palette.Ink,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (wind == 0) Palette.Gold else Palette.FeltDark).padding(horizontal = 5.dp),
            )
            Text(" ${SHORT_NAMES[seat]} ", color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(fmt(pl.score), color = Palette.InkDim, fontSize = 13.sp)
            if (pl.riichi) Text(" RIICHI", color = Palette.Bad, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (pl.melds.isNotEmpty()) MeldRow(pl.melds, small * .82f, labels)
    }
}

@Composable
fun MeldRow(melds: List<Meld>, tile: Dp, labels: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (m in melds) Row {
            m.tiles.forEachIndexed { i, t ->
                TileView(t, tile, labels = labels, faceDown = m.type == MeldType.CLOSED_KAN && (i == 0 || i == 3))
            }
        }
    }
}

@Composable
private fun Pond(st: GameState, seat: Int, v: Int, small: Dp, labels: Boolean, minRows: Int) {
    val pl = st.players[seat]
    val rows = pl.discards.chunked(6)
    val isLast = st.lastDiscarder == seat && st.phase != Phase.HAND_END
    Column(Modifier.width(small * 6).heightIn(min = small * 1.36f * minRows)) {
        rows.forEachIndexed { r, row ->
            Row {
                row.forEachIndexed { i, d ->
                    val last = isLast && r * 6 + i == pl.discards.size - 1 && d.calledBy < 0
                    TileView(d.tile, small, labels = labels, dim = d.calledBy >= 0,
                        outline = if (last) Palette.Gold else null, tint = if (d.riichi) Color(0xFFFFD9A8) else null)
                }
            }
        }
    }
}

@Composable
private fun Table(st: GameState, v: Int, small: Dp, labels: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Badge(st, 2, v, small, labels)
        Pond(st, 2, v, small, labels, 3)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Badge(st, 3, v, small, labels)
                Pond(st, 3, v, small, labels, 4)
            }
            Column(Modifier.weight(1f).padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Turn order", color = Palette.InkDim, fontSize = 11.sp)
                Text("You → Aki → Ren → Sora", color = Palette.InkDim, fontSize = 10.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Round wind", color = Palette.InkDim, fontSize = 11.sp)
                Text(Tiles.WIND_NAMES[st.roundWind.coerceAtMost(3)], color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Badge(st, 1, v, small, labels)
                Pond(st, 1, v, small, labels, 4)
            }
        }
        Pond(st, 0, v, small, labels, 3)
    }
}

@Composable
private fun TutorPanel(vm: GameViewModel, g: Game, v: Int, advice: Advice?, shown: Advice?, small: Dp, modifier: Modifier) {
    val st = g.state
    val scroll = rememberScrollState()
    val events = remember(v, vm.eventMark) { st.events.drop(vm.eventMark.coerceAtMost(st.events.size)) }
    LaunchedEffect(v, vm.selectedTile, vm.hintShown) { scroll.scrollTo(0) }
    val lines = Tutor.narrate(events, HUMAN, PLAYER_NAMES, st).takeLast(4)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(Palette.Panel)
            .verticalScroll(scroll).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        for (l in lines) Text(l, color = Palette.InkDim, fontSize = 13.sp, lineHeight = 17.sp)
        if (advice == null) {
            if (st.phase == Phase.DISCARD || st.phase == Phase.RESPOND) {
                val who = if (st.turn == HUMAN) "Discarding your drawn tile (you are in riichi)…" else "${PLAYER_NAMES[st.turn]} is thinking…"
                Text(who, color = Palette.Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            return@Column
        }
        if (shown == null) {
            Text(
                if (vm.tutorLevel == TutorLevel.HINTS) "Your move. Tap Hint if you would like advice." else "Your move.",
                color = Palette.Ink, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp),
            )
            return@Column
        }
        for (m in Tutor.missedWins(g, HUMAN, events, PLAYER_NAMES)) Note(m, Palette.Warn)
        val riichiPick = vm.riichiMode
        Text(
            if (riichiPick) "Riichi: tap the tile to discard, then tap it again to confirm." else shown.headline,
            color = Palette.Good, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp),
        )
        Text(shown.reason, color = Palette.Ink, fontSize = 14.sp, lineHeight = 19.sp)
        if (vm.selectedTile >= 0 && !riichiPick) {
            val note = Tutor.discardNote(g, HUMAN, vm.selectedTile, PLAYER_NAMES)
            if (note != null) Note("You picked ${Tiles.name(vm.selectedTile)}. $note Tap it again to discard it anyway.", Palette.Warn)
            else Note("Tap ${Tiles.name(vm.selectedTile)} again to discard it.", Palette.Good)
        }
        if (shown.status.isNotEmpty()) Text(shown.status, color = Palette.InkDim, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
        if (shown.improving.isNotEmpty() && shown.improving.size <= 12) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tiles you want: ", color = Palette.InkDim, fontSize = 13.sp)
                for (k in shown.improving) TileView(k, small * .9f, labels = vm.labels)
            }
        }
        for (wn in shown.warnings) Note(wn, Palette.Warn)
        for (o in shown.options) {
            val c = when (o.verdict) { Verdict.RECOMMENDED -> Palette.Good; Verdict.FINE -> Palette.Blue; Verdict.AVOID -> Palette.Bad }
            val tag = when (o.verdict) { Verdict.RECOMMENDED -> "recommended"; Verdict.FINE -> "possible"; Verdict.AVOID -> "not now" }
            Column(Modifier.padding(top = 6.dp)) {
                Row {
                    Text(o.label, color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("  $tag", color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("${o.what} ${o.why}", color = Palette.InkDim, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        for (id in shown.terms.filter { vm.isNewTerm(it) }.take(3)) {
            val t = Glossary[id]
            Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.FeltDark).padding(8.dp)) {
                Text("New word: ${t.word}", color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(t.definition, color = Palette.Ink, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ActionBar(vm: GameViewModel, g: Game, v: Int, advice: Advice?, shown: Advice?) {
    val acts = if (HUMAN in g.actors()) g.legalActions(HUMAN) else emptyList()
    val buttons = ArrayList<Triple<String, Action, Color>>()
    val red = Color(0xFFE5484D)
    val blue = Color(0xFF4F8FE8)
    for (a in acts) {
        when (a) {
            Action.Tsumo -> buttons.add(Triple("Tsumo", a, red))
            Action.Ron -> buttons.add(Triple("Ron", a, red))
            Action.Pon -> buttons.add(Triple("Pon", a, blue))
            Action.OpenKan -> buttons.add(Triple("Kan", a, blue))
            is Action.Chi -> buttons.add(Triple("Chi ${Tiles.num(a.low)}${Tiles.num(a.low) + 1}${Tiles.num(a.low) + 2}", a, blue))
            is Action.ClosedKan -> buttons.add(Triple("Kan ${Tiles.short(a.tile)}", a, blue))
            is Action.AddedKan -> buttons.add(Triple("Kan ${Tiles.short(a.tile)}", a, blue))
            Action.Pass -> buttons.add(Triple("Pass", a, Color(0xFF6B7C73)))
            else -> {}
        }
    }
    val canRiichi = acts.any { it is Action.Riichi }
    val needHint = vm.tutorLevel == TutorLevel.HINTS && advice != null && !vm.hintShown
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (buttons.isEmpty() && !canRiichi && !needHint) {
            val text = when {
                acts.isNotEmpty() -> "Tap a tile to select it, tap again to discard"
                g.state.phase == Phase.HAND_END || g.state.phase == Phase.GAME_END -> ""
                else -> "Waiting for the other players…"
            }
            Text(text, color = Palette.InkDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (canRiichi) {
            BigButton(if (vm.riichiMode) "Cancel" else "Riichi", { vm.riichiMode = !vm.riichiMode; vm.selectedTile = -1 },
                Modifier.weight(1f), color = Color(0xFFFF9F1C), starred = shown?.recommended is Action.Riichi && !vm.riichiMode)
        }
        for ((label, a, color) in buttons) {
            BigButton(label, { vm.humanAct(a) }, Modifier.weight(1f), color = color, textColor = Color.White, starred = shown?.recommended == a)
        }
        if (needHint) BigButton("Hint", { vm.hintShown = true }, Modifier.weight(1f), color = Palette.Good)
    }
}

@Composable
private fun MyHand(vm: GameViewModel, g: Game, v: Int, w: Dp, small: Dp, shown: Advice?) {
    val st = g.state
    val pl = st.players[HUMAN]
    val acts = if (st.phase == Phase.DISCARD && st.turn == HUMAN) g.legalActions(HUMAN) else emptyList()
    val discardable = acts.filterIsInstance<Action.Discard>().map { it.tile }.toSet()
    val riichiable = acts.filterIsInstance<Action.Riichi>().map { it.tile }.toSet()
    val allowed = if (vm.riichiMode) riichiable else discardable
    val recTile = when (val r = shown?.recommended) {
        is Action.Discard -> if (vm.riichiMode) -1 else r.tile
        is Action.Riichi -> if (vm.riichiMode) r.tile else -1
        else -> -1
    }
    val gap = 8.dp
    val tileW = minOf((w - 8.dp - gap) / 14f, 46.dp)
    val hand = pl.hand
    val hasDrawn = pl.drawn >= 0 && hand.isNotEmpty()
    val recIndex = hand.lastIndexOf(recTile)
    Column(Modifier.fillMaxWidth().background(Palette.FeltDark).padding(top = 4.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val wind = st.seatWind(HUMAN) - Tiles.EAST
            Text(
                "You: ${Tiles.WIND_NAMES[wind]}${if (wind == 0) " (dealer)" else ""}  ·  ${fmt(pl.score)}",
                color = if (st.phase == Phase.DISCARD && st.turn == HUMAN) Palette.Gold else Palette.Ink,
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            if (pl.riichi) Text("  RIICHI", color = Palette.Bad, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (pl.melds.isNotEmpty()) MeldRow(pl.melds, small * 1.05f, vm.labels)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            hand.forEachIndexed { i, t ->
                if (hasDrawn && i == hand.size - 1) Spacer(Modifier.width(gap))
                val selected = vm.selectedTile == t && allowed.isNotEmpty()
                val canTap = t in allowed
                TileView(
                    t, tileW,
                    modifier = Modifier.offset(y = if (selected) (-9).dp else 0.dp).clickable(enabled = canTap) { vm.tapTile(t) },
                    labels = vm.labels,
                    dim = acts.isNotEmpty() && !canTap,
                    outline = when { selected -> Palette.Gold; i == recIndex -> Palette.Good; else -> null },
                )
            }
        }
    }
}

@Composable
private fun GameMenu(vm: GameViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClose) { Text("Back to the game") } },
        dismissButton = { TextButton({ onClose(); vm.quitToHome() }) { Text("Save and quit") } },
        title = { Text("Options") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsBlock(vm)
                Spacer(Modifier.height(12.dp))
                TextButton({ onClose(); vm.openGlossary() }) { Text("Open the word list and yaku list") }
            }
        },
    )
}
