package mahjong.engine

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameTest {
    /** Puts seat 0 (dealer) on the move with the given 14 tiles; everyone else gets junk. */
    private fun setup(hand: String, drawn: String, others: String = "147m258p369s1234z"): Game {
        val g = Game.create(GameConfig(), Random(1))
        val st = g.state
        st.dealer = 0; st.turn = 0; st.phase = Phase.DISCARD
        for ((i, pl) in st.players.withIndex()) {
            pl.hand.clear()
            pl.hand.addAll(Tiles.parse(if (i == 0) hand else others))
            pl.drawn = -1
        }
        val d = Tiles.parse(drawn)[0]
        st.players[0].hand.add(d); st.players[0].drawn = d
        // keep the wall harmless: only 9-sou... not needed, tests act immediately
        return g
    }

    @Test fun dealIsCorrect() {
        val g = Game.create(GameConfig(), Random(7))
        val st = g.state
        assertEquals(14, st.players[st.dealer].hand.size)
        for (p in 0 until 4) if (p != st.dealer) assertEquals(13, st.players[p].hand.size)
        assertEquals(69, st.wall.size)
        assertEquals(listOf(st.dealer), g.actors())
    }

    @Test fun tsumoWinPaysAndDealerRepeats() {
        val g = setup("234m456p678s34s99p", "2s")
        assertTrue(Action.Tsumo in g.legalActions(0))
        g.act(0, Action.Tsumo)
        assertEquals(Phase.HAND_END, g.state.phase)
        val r = g.state.result!!
        // dealer tsumo, menzen tsumo + pinfu (first draw would be tenhou: discards empty) -> yakuman
        assertTrue(r.wins[0].score.yakumanCount == 1)
        assertTrue(r.dealerContinues)
        assertEquals(100000, g.state.players.sumOf { it.score })
    }

    @Test fun ronRequiresYakuAndRespectsFuriten() {
        // seat 1 waits on 8m with riichi-less no-yaku hand -> cannot ron
        val g = setup("123m456p789s1122z", "8m", others = "123m456p789s67m11z")
        g.state.callsMade = true
        g.act(0, Action.Discard(Tiles.parse("8m")[0]))
        assertFalse(Action.Ron in g.legalActions(1))
        g.act(1, Action.Pass) // seat 1 may chi, but not ron
        assertFalse(g.state.phase == Phase.HAND_END)
        assertTrue(g.state.players[2].tempFuriten)

        // with riichi the same hand can ron
        val h = setup("123m456p789s1122z", "8m", others = "123m456p789s67m11z")
        h.state.callsMade = true
        h.state.players[1].riichi = true
        h.act(0, Action.Discard(Tiles.parse("8m")[0]))
        assertEquals(listOf(1), h.actors())
        // seat 1 has discarded 5m before: 5m-8m wait -> furiten, no ron offered
        val f = setup("123m456p789s1122z", "8m", others = "123m456p789s67m11z")
        f.state.callsMade = true
        for (q in 1..3) { f.state.players[q].riichi = true; f.state.players[q].discards.add(DiscardInfo(Tiles.parse("5m")[0], false, false)) }
        f.act(0, Action.Discard(Tiles.parse("8m")[0]))
        assertEquals(Phase.DISCARD, f.state.phase)
        assertTrue(f.isFuriten(1))
    }

    @Test fun multipleRon() {
        val h = setup("123m456p789s1122z", "8m", others = "123m456p789s67m11z")
        h.state.callsMade = true
        h.state.honba = 1; h.state.riichiSticks = 2
        for (q in 1..3) h.state.players[q].riichi = true
        h.act(0, Action.Discard(Tiles.parse("8m")[0]))
        h.act(1, Action.Ron); h.act(2, Action.Pass); h.act(3, Action.Ron)
        val r = h.state.result!!
        assertEquals(2, r.wins.size)
        assertEquals(2, r.wins[0].riichiSticksWon); assertEquals(0, r.wins[1].riichiSticksWon)
        assertEquals(102000, h.state.players.sumOf { it.score })
        assertFalse(r.dealerContinues)
        assertTrue(h.state.players[2].riichiFuriten)
        h.nextHand()
        assertEquals(1, h.state.dealer); assertEquals(0, h.state.honba)
    }

    @Test fun riichiFlow() {
        val g = setup("123m456p789s1122z", "9m", others = "147m258p369s5567z")
        g.state.callsMade = true
        val nine = Tiles.parse("9m")[0]
        assertTrue(Action.Riichi(nine) in g.legalActions(0))
        assertFalse(Action.Riichi(Tiles.parse("1m")[0]) in g.legalActions(0))
        g.act(0, Action.Riichi(nine))
        assertTrue(g.state.players[0].riichi)
        assertEquals(24000, g.state.players[0].score)
        assertEquals(1, g.state.riichiSticks)
        // go around until it is seat 0's turn again: may only discard the drawn tile (or win)
        while (g.state.phase != Phase.HAND_END && !(g.state.phase == Phase.DISCARD && g.state.turn == 0)) {
            val p = g.actors()[0]
            val acts = g.legalActions(p)
            g.act(p, if (Action.Pass in acts) Action.Pass else acts.filterIsInstance<Action.Discard>().first { it.tile !in listOf(27, 28) })
        }
        if (g.state.phase == Phase.DISCARD) {
            val acts = g.legalActions(0).filter { it !is Action.Tsumo && it !is Action.ClosedKan }
            assertEquals(listOf<Action>(Action.Discard(g.state.players[0].drawn)), acts)
        }
    }

    @Test fun callsAndKuikae() {
        // seat 1 holds 45m; seat 0 discards 3m -> chi offered, afterwards 3m and 6m may not be discarded
        val g = setup("123m456p789s1122z", "3m", others = "45m6m258p369s1234z")
        g.act(0, Action.Discard(2))
        assertTrue(Action.Chi(2) in g.legalActions(1))
        assertTrue(g.legalActions(2).isEmpty()) // only the next player may chi
        g.act(1, Action.Chi(2))
        val discards = g.legalActions(1).filterIsInstance<Action.Discard>().map { it.tile }
        assertFalse(5 in discards)
        assertTrue(discards.isNotEmpty())
        assertEquals(1, g.state.players[1].melds.size)
        assertEquals(11, g.state.players[1].hand.size)
    }

    @Test fun ponBeatsChi() {
        val g = setup("123m456p789s1122z", "3m", others = "45m6m258p369s1234z")
        g.state.players[2].hand.clear(); g.state.players[2].hand.addAll(Tiles.parse("33m258p369s12345z"))
        g.act(0, Action.Discard(2))
        g.act(1, Action.Chi(2)); g.act(2, Action.Pon)
        assertEquals(2, g.state.turn)
        assertEquals(MeldType.PON, g.state.players[2].melds[0].type)
    }

    @Test fun closedKanDrawsReplacementAndRevealsDora() {
        val g = setup("1111m456p789s112z", "2z")
        val wall = g.state.wall.size
        g.act(0, Action.ClosedKan(0))
        assertEquals(2, g.state.doraRevealed)
        assertEquals(wall - 1, g.state.wall.size)
        assertEquals(11, g.state.players[0].hand.size)
        assertTrue(g.state.afterKan)
    }

    @Test fun exhaustiveDrawPaysTenpai() {
        val g = setup("123m456p789s1122z", "9m", others = "147m258p369s5567z")
        g.state.callsMade = true
        g.state.wall.clear()
        g.act(0, Action.Discard(Tiles.parse("9m")[0]))
        assertEquals(Phase.HAND_END, g.state.phase)
        val r = g.state.result!!
        assertTrue(r.isDraw)
        assertEquals(listOf(3000, -1000, -1000, -1000), r.deltas)
        assertTrue(r.dealerContinues)
        g.nextHand()
        assertEquals(0, g.state.dealer); assertEquals(1, g.state.honba)
    }

    private fun checkInvariants(g: Game) {
        val st = g.state
        assertEquals(100000, st.players.sumOf { it.score } + st.riichiSticks * 1000)
        if (st.phase == Phase.HAND_END || st.phase == Phase.GAME_END) return
        var n = st.wall.size + st.rinshan.size + 10
        val c = IntArray(34)
        for (t in st.wall + st.rinshan + st.doraIndicators + st.uraIndicators) c[t]++
        for (pl in st.players) {
            n += pl.hand.size + pl.melds.sumOf { it.tiles.size } + pl.discards.count { it.calledBy < 0 }
            for (t in pl.hand) c[t]++
            for (m in pl.melds) for (t in m.tiles) c[t]++
            for (d in pl.discards) if (d.calledBy < 0) c[d.tile]++
            val expect = 13 - 3 * pl.melds.size
            assertTrue(pl.hand.size == expect || pl.hand.size == expect + 1, "hand size ${pl.hand.size} with ${pl.melds.size} melds")
        }
        assertEquals(136 - st.totalKans, n)
        assertTrue(c.all { it <= 4 })
    }

    private fun playGame(seed: Int, eastOnly: Boolean, chooser: (Game, Int, Random) -> Action): Game {
        val rng = Random(seed)
        val g = Game.create(GameConfig(eastOnly = eastOnly), rng)
        var steps = 0
        while (g.state.phase != Phase.GAME_END) {
            if (g.state.phase == Phase.HAND_END) { g.nextHand(); continue }
            val p = g.actors().first()
            g.act(p, chooser(g, p, rng))
            checkInvariants(g)
            check(++steps < 200_000) { "game does not terminate" }
        }
        assertEquals(100000, g.state.players.sumOf { it.score })
        return g
    }

    @Test fun randomPlayFuzz() {
        // Random legal actions exercise calls, kans, chankan and odd states far more than sensible play does.
        repeat(300) { seed ->
            playGame(seed, eastOnly = seed % 2 == 0) { g, p, rng ->
                val acts = g.legalActions(p)
                acts.firstOrNull { it == Action.Ron || it == Action.Tsumo } ?: acts[rng.nextInt(acts.size)]
            }
        }
    }

    @Test fun aiGamesFinishAndProduceWins() {
        var wins = 0; var draws = 0; var hands = 0; var riichi = 0; var open = 0
        repeat(120) { seed ->
            val rng = Random(seed + 1000)
            val g = Game.create(GameConfig(eastOnly = true), rng)
            var steps = 0
            while (g.state.phase != Phase.GAME_END) {
                if (g.state.phase == Phase.HAND_END) {
                    hands++
                    val r = g.state.result!!
                    if (r.isDraw) draws++ else {
                        wins++
                        if (r.wins.any { w -> w.score.yaku.any { it.yaku == Yaku.RIICHI } }) riichi++
                        if (r.wins.any { w -> w.melds.any { it.isOpen } }) open++
                    }
                    g.nextHand(); continue
                }
                val p = g.actors().first()
                g.act(p, Ai.decide(g, p, if (p % 2 == 0) Difficulty.NORMAL else Difficulty.EASY, rng))
                checkInvariants(g)
                check(++steps < 100_000)
            }
        }
        println("AI sim: hands=$hands wins=$wins draws=$draws riichiWins=$riichi openWins=$open")
        assertTrue(wins > hands / 2, "AIs should win most hands (wins=$wins of $hands)")
        assertTrue(riichi > 0 && open > 0)
    }

    @Test fun saveAndResume() {
        val rng = Random(5)
        val g = Game.create(GameConfig(), rng)
        repeat(60) {
            if (g.state.phase == Phase.HAND_END) g.nextHand()
            val p = g.actors().first()
            g.act(p, Ai.decide(g, p, Difficulty.NORMAL, rng))
        }
        val json = Json.encodeToString(GameState.serializer(), g.state)
        val back = Json.decodeFromString(GameState.serializer(), json)
        assertEquals(json, Json.encodeToString(GameState.serializer(), back))
        val g2 = Game(back)
        assertEquals(g.actors(), g2.actors())
        assertEquals(g.legalActions(g.actors().first()), g2.legalActions(g2.actors().first()))
    }
}

class TutorTest {
    private val names = listOf("You", "Right", "Across", "Left")

    @Test fun adviceIsAlwaysAvailableAndLegal() {
        repeat(40) { seed ->
            val rng = Random(seed + 500)
            val g = Game.create(GameConfig(), rng)
            var steps = 0
            while (g.state.phase != Phase.GAME_END && steps++ < 4000) {
                if (g.state.phase == Phase.HAND_END) {
                    for (w in g.state.result!!.wins) assertTrue(Tutor.explainWin(w, names, g.state.dealer).size >= 4)
                    g.nextHand(); continue
                }
                val p = g.actors().first()
                val acts = g.legalActions(p)
                val adv = Tutor.advise(g, p, names)!!
                assertTrue(adv.recommended in acts, "recommended ${adv.recommended} not legal in $acts")
                assertTrue(adv.headline.isNotBlank() && adv.reason.isNotBlank())
                for (o in adv.options) assertTrue(o.action in acts)
                for (t in adv.terms) Glossary[t]
                if (g.state.phase == Phase.DISCARD) {
                    for (a in acts.filterIsInstance<Action.Discard>()) Tutor.discardNote(g, p, a.tile, names)
                } else Tutor.missedWinNote(g, 0, names)
                Tutor.narrate(g.state.events.takeLast(6), 0, names, g.state)
                // mix of sensible and odd play so the tutor sees open hands without yaku too
                g.act(p, if (rng.nextInt(4) == 0) acts[rng.nextInt(acts.size)] else Ai.decide(g, p, Difficulty.NORMAL, rng))
            }
        }
    }

    @Test fun tutorRecommendsRiichiAndExplainsTsumo() {
        val g = Game.create(GameConfig(), Random(1))
        val st = g.state
        st.dealer = 0; st.turn = 0; st.phase = Phase.DISCARD; st.callsMade = true
        st.players[0].hand.clear(); st.players[0].hand.addAll(Tiles.parse("123m456p789s1122z9m")); st.players[0].drawn = 8
        val adv = Tutor.advise(g, 0, names)!!
        assertEquals(Action.Riichi(8), adv.recommended)
        assertTrue("riichi" in adv.terms)
        st.players[0].hand.clear(); st.players[0].hand.addAll(Tiles.parse("123m456p789s11222z")); st.players[0].drawn = 28
        val win = Tutor.advise(g, 0, names)!!
        assertEquals(Action.Tsumo, win.recommended)
    }
}
