package mahjong.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class GameConfig(val eastOnly: Boolean = true, val startScore: Int = 25000)

@Serializable
enum class Phase { DISCARD, RESPOND, HAND_END, GAME_END }

@Serializable
sealed class Action {
    @Serializable data class Discard(val tile: Int) : Action()
    @Serializable data class Riichi(val tile: Int) : Action()
    @Serializable data object Tsumo : Action()
    @Serializable data class ClosedKan(val tile: Int) : Action()
    @Serializable data class AddedKan(val tile: Int) : Action()
    @Serializable data object Ron : Action()
    @Serializable data object Pon : Action()
    @Serializable data object OpenKan : Action()
    /** [low] is the lowest tile of the resulting sequence. */
    @Serializable data class Chi(val low: Int) : Action()
    @Serializable data object Pass : Action()
}

@Serializable
sealed class GameEvent {
    @Serializable data object HandStarted : GameEvent()
    @Serializable data class Drew(val p: Int, val rinshan: Boolean) : GameEvent()
    @Serializable data class Discarded(val p: Int, val tile: Int, val tsumogiri: Boolean, val riichi: Boolean) : GameEvent()
    @Serializable data class Called(val p: Int, val from: Int, val meld: MeldType, val tile: Int) : GameEvent()
    @Serializable data class DoraRevealed(val indicator: Int) : GameEvent()
    @Serializable data class RiichiAccepted(val p: Int) : GameEvent()
}

@Serializable
data class DiscardInfo(val tile: Int, val tsumogiri: Boolean, val riichi: Boolean, var calledBy: Int = -1)

@Serializable
class PlayerState {
    var score: Int = 25000
    /** All concealed tiles, including the tile just drawn. */
    val hand: MutableList<Int> = mutableListOf()
    /** Kind of the tile just drawn this turn, or -1. */
    var drawn: Int = -1
    val melds: MutableList<Meld> = mutableListOf()
    val discards: MutableList<DiscardInfo> = mutableListOf()
    var riichi: Boolean = false
    var doubleRiichi: Boolean = false
    var ippatsu: Boolean = false
    var riichiPending: Boolean = false
    var tempFuriten: Boolean = false
    var riichiFuriten: Boolean = false
    /** Tiles other players discarded after this player's riichi without being ronned. */
    val safeTiles: MutableSet<Int> = mutableSetOf()

    fun counts(): IntArray = Tiles.counts(hand)
    val isClosed: Boolean get() = melds.all { it.type == MeldType.CLOSED_KAN }
}

@Serializable
data class WinInfo(
    val winner: Int,
    val loser: Int,
    val winTile: Int,
    /** Concealed tiles without the winning tile. */
    val hand: List<Int>,
    val melds: List<Meld>,
    val score: Score,
    val deltas: List<Int>,
    val riichiSticksWon: Int,
    val honba: Int,
    val isDealer: Boolean,
)

@Serializable
data class HandResult(
    val wins: List<WinInfo>,
    val isDraw: Boolean,
    val tenpai: List<Boolean>,
    val deltas: List<Int>,
    val scoresBefore: List<Int>,
    val dealerContinues: Boolean,
    val uraIndicators: List<Int>,
    val hands: List<List<Int>>,
)

@Serializable
class GameState(val config: GameConfig = GameConfig()) {
    val players: List<PlayerState> = List(4) { PlayerState() }
    var roundWind: Int = 0
    var handNumber: Int = 0
    var dealer: Int = 0
    var initialDealer: Int = 0
    var honba: Int = 0
    var riichiSticks: Int = 0
    val wall: MutableList<Int> = mutableListOf()
    val rinshan: MutableList<Int> = mutableListOf()
    val doraIndicators: MutableList<Int> = mutableListOf()
    val uraIndicators: MutableList<Int> = mutableListOf()
    var doraRevealed: Int = 1
    var phase: Phase = Phase.DISCARD
    var turn: Int = 0
    var lastDiscard: Int = -1
    var lastDiscarder: Int = -1
    var chankanPending: Boolean = false
    var afterKan: Boolean = false
    var mustDiscard: Boolean = false
    val kuikae: MutableList<Int> = mutableListOf()
    var callsMade: Boolean = false
    val pending: MutableList<Int> = mutableListOf()
    val responses: MutableMap<Int, Action> = mutableMapOf()
    var result: HandResult? = null
    val events: MutableList<GameEvent> = mutableListOf()

    fun seatWind(p: Int): Int = Tiles.EAST + (p - dealer + 4) % 4
    val roundWindTile: Int get() = Tiles.EAST + roundWind
    val revealedDora: List<Int> get() = doraIndicators.take(doraRevealed)
    val totalKans: Int get() = players.sumOf { pl -> pl.melds.count { it.isKan } }
    /** e.g. "East 2". */
    val handName: String get() = "${Tiles.WIND_NAMES[roundWind.coerceAtMost(3)]} ${handNumber + 1}"
}

/** The rules engine. All state lives in [state] so a game can be saved and resumed. */
class Game(val state: GameState, private val rng: Random = Random.Default) {
    private val st get() = state

    companion object {
        fun create(config: GameConfig = GameConfig(), rng: Random = Random.Default): Game {
            val s = GameState(config)
            for (p in s.players) p.score = config.startScore
            s.dealer = rng.nextInt(4)
            s.initialDealer = s.dealer
            return Game(s, rng).also { it.startHand() }
        }
    }

    // ------------------------------------------------------------------ queries

    /** Seats that have to make a decision right now. */
    fun actors(): List<Int> = when (st.phase) {
        Phase.DISCARD -> listOf(st.turn)
        Phase.RESPOND -> st.pending.filter { it !in st.responses }
        else -> emptyList()
    }

    fun legalActions(p: Int): List<Action> = when (st.phase) {
        Phase.DISCARD -> if (p == st.turn) discardPhaseActions(p) else emptyList()
        Phase.RESPOND -> if (p in st.pending && p !in st.responses) responseOptions(p) else emptyList()
        else -> emptyList()
    }

    /** Winning tiles for a seat holding 13 tiles (minus melds). Empty when not tenpai. */
    fun waits(p: Int): List<Int> {
        val pl = st.players[p]
        if ((pl.hand.size + pl.melds.size * 3) != 13) return emptyList()
        return Shanten.waits(pl.counts(), pl.melds.size)
    }

    fun isFuriten(p: Int, waits: List<Int> = waits(p)): Boolean {
        val pl = st.players[p]
        if (pl.tempFuriten || pl.riichiFuriten) return true
        return waits.any { w -> pl.discards.any { it.tile == w } }
    }

    fun context(p: Int, tile: Int, tsumo: Boolean): WinContext {
        val pl = st.players[p]
        val first = pl.discards.isEmpty() && !st.callsMade && pl.melds.isEmpty()
        return WinContext(
            winTile = tile,
            tsumo = tsumo,
            seatWind = st.seatWind(p),
            roundWind = st.roundWindTile,
            riichi = pl.riichi,
            doubleRiichi = pl.doubleRiichi,
            ippatsu = pl.ippatsu,
            haitei = tsumo && st.wall.isEmpty() && !st.afterKan,
            houtei = !tsumo && !st.chankanPending && st.wall.isEmpty(),
            rinshan = tsumo && st.afterKan,
            chankan = !tsumo && st.chankanPending,
            tenhou = tsumo && first && p == st.dealer && !st.afterKan,
            chiihou = tsumo && first && p != st.dealer && !st.afterKan,
            doraIndicators = st.revealedDora,
            uraIndicators = if (pl.riichi) st.uraIndicators.take(st.doraRevealed) else emptyList(),
        )
    }

    /** Score if seat [p] won on [tile] right now, or null if it is not a legal win (incomplete or no yaku). */
    fun winScore(p: Int, tile: Int, tsumo: Boolean): Score? {
        val pl = st.players[p]
        val c = pl.counts()
        if (!tsumo) c[tile]++
        if (!Shanten.isComplete(c, pl.melds.size)) return null
        return Scorer.evaluate(c, pl.melds, context(p, tile, tsumo))
    }

    private fun canTsumo(p: Int): Boolean {
        val pl = st.players[p]
        return pl.drawn >= 0 && !st.mustDiscard && winScore(p, pl.drawn, true) != null
    }

    private fun canRon(p: Int): Boolean = winScore(p, st.lastDiscard, false) != null && !isFuriten(p)

    private fun discardPhaseActions(p: Int): List<Action> {
        val pl = st.players[p]
        val acts = ArrayList<Action>()
        val kinds = pl.hand.distinct().sorted()
        if (st.mustDiscard) {
            for (t in kinds) if (t !in st.kuikae) acts.add(Action.Discard(t))
            return acts
        }
        if (canTsumo(p)) acts.add(Action.Tsumo)
        val c = pl.counts()
        val kanOk = st.wall.isNotEmpty() && st.totalKans < 4
        if (pl.riichi) {
            acts.add(Action.Discard(pl.drawn))
            if (kanOk && c[pl.drawn] == 4) {
                val k = pl.drawn
                c[k]--
                val before = Shanten.waits(c, pl.melds.size)
                c[k] -= 3
                val after = Shanten.waits(c, pl.melds.size + 1)
                if (before.isNotEmpty() && before == after) acts.add(Action.ClosedKan(k))
            }
            return acts
        }
        for (t in kinds) acts.add(Action.Discard(t))
        if (pl.isClosed && pl.score >= 1000 && st.wall.size >= 4) {
            for (t in kinds) {
                c[t]--
                if (Shanten.waits(c, pl.melds.size).isNotEmpty()) acts.add(Action.Riichi(t))
                c[t]++
            }
        }
        if (kanOk) {
            for (t in kinds) if (c[t] == 4) acts.add(Action.ClosedKan(t))
            for (m in pl.melds) if (m.type == MeldType.PON && c[m.kind] >= 1) acts.add(Action.AddedKan(m.kind))
        }
        return acts
    }

    /** Tiles that may not be discarded right after a chi (swap-calling is not allowed). */
    private fun chiForbidden(tile: Int, low: Int): List<Int> {
        val out = arrayListOf(tile)
        if (tile == low && Tiles.num(low) <= 6) out.add(low + 3)
        if (tile == low + 2 && Tiles.num(low) >= 2) out.add(low - 1)
        return out
    }

    private fun responseOptions(q: Int): List<Action> {
        val tile = st.lastDiscard
        val d = st.lastDiscarder
        val pl = st.players[q]
        val opts = ArrayList<Action>()
        if (canRon(q)) opts.add(Action.Ron)
        if (!st.chankanPending && st.wall.isNotEmpty() && !pl.riichi) {
            val c = pl.counts()
            if (c[tile] >= 2 && pl.hand.any { it != tile }) opts.add(Action.Pon)
            if (c[tile] == 3 && st.totalKans < 4) opts.add(Action.OpenKan)
            if (q == (d + 1) % 4 && tile < 27) {
                for (low in tile - 2..tile) {
                    if (low < 0 || low / 9 != tile / 9 || (low + 2) / 9 != tile / 9) continue
                    val need = (low..low + 2).filter { it != tile }
                    if (need.any { c[it] == 0 }) continue
                    val forbidden = chiForbidden(tile, low)
                    val rest = pl.hand.toMutableList()
                    need.forEach { rest.remove(it) }
                    if (rest.any { it !in forbidden }) opts.add(Action.Chi(low))
                }
            }
        }
        if (opts.isNotEmpty()) opts.add(Action.Pass)
        return opts
    }

    // ------------------------------------------------------------------ flow

    fun startHand() {
        for (pl in st.players) {
            pl.hand.clear(); pl.drawn = -1; pl.melds.clear(); pl.discards.clear()
            pl.riichi = false; pl.doubleRiichi = false; pl.ippatsu = false; pl.riichiPending = false
            pl.tempFuriten = false; pl.riichiFuriten = false; pl.safeTiles.clear()
        }
        val tiles = (0 until 136).map { it / 4 }.shuffled(rng)
        st.rinshan.clear(); st.rinshan.addAll(tiles.subList(0, 4))
        st.doraIndicators.clear(); st.doraIndicators.addAll(tiles.subList(4, 9))
        st.uraIndicators.clear(); st.uraIndicators.addAll(tiles.subList(9, 14))
        st.wall.clear(); st.wall.addAll(tiles.subList(14, 136))
        for (i in 0 until 4) {
            val pl = st.players[(st.dealer + i) % 4]
            repeat(13) { pl.hand.add(st.wall.removeAt(st.wall.size - 1)) }
            pl.hand.sort()
        }
        st.doraRevealed = 1
        st.lastDiscard = -1; st.lastDiscarder = -1
        st.chankanPending = false; st.afterKan = false; st.mustDiscard = false
        st.kuikae.clear(); st.callsMade = false
        st.pending.clear(); st.responses.clear()
        st.result = null
        st.events.clear()
        st.events.add(GameEvent.HandStarted)
        draw(st.dealer, false)
    }

    private fun draw(p: Int, fromRinshan: Boolean) {
        if (!fromRinshan && st.wall.isEmpty()) { exhaustiveDraw(); return }
        val pl = st.players[p]
        val t = if (fromRinshan) {
            st.wall.removeAt(0) // the live wall shrinks to keep the dead wall at 14 tiles
            st.rinshan.removeAt(st.rinshan.size - 1)
        } else st.wall.removeAt(st.wall.size - 1)
        pl.hand.sort()
        pl.hand.add(t)
        pl.drawn = t
        pl.tempFuriten = false
        st.turn = p
        st.phase = Phase.DISCARD
        st.afterKan = fromRinshan
        st.mustDiscard = false
        st.kuikae.clear()
        st.events.add(GameEvent.Drew(p, fromRinshan))
    }

    fun act(p: Int, a: Action) {
        require(a in legalActions(p)) { "illegal action $a for seat $p" }
        when (a) {
            is Action.Discard -> discard(p, a.tile, false)
            is Action.Riichi -> discard(p, a.tile, true)
            Action.Tsumo -> finishWin(listOf(p), -1, st.players[p].drawn)
            is Action.ClosedKan -> closedKan(p, a.tile)
            is Action.AddedKan -> addedKan(p, a.tile)
            else -> {
                st.responses[p] = a
                if (st.pending.all { it in st.responses }) resolveResponses()
            }
        }
    }

    private fun discard(p: Int, tile: Int, declareRiichi: Boolean) {
        val pl = st.players[p]
        val tsumogiri = pl.drawn == tile
        pl.hand.remove(tile)
        if (pl.riichi) pl.ippatsu = false
        if (declareRiichi) {
            pl.riichi = true
            pl.riichiPending = true
            pl.ippatsu = true
            pl.doubleRiichi = pl.discards.isEmpty() && !st.callsMade
        }
        pl.drawn = -1
        pl.hand.sort()
        pl.discards.add(DiscardInfo(tile, tsumogiri, declareRiichi))
        st.lastDiscard = tile
        st.lastDiscarder = p
        st.afterKan = false
        st.mustDiscard = false
        st.kuikae.clear()
        st.events.add(GameEvent.Discarded(p, tile, tsumogiri, declareRiichi))
        beginResponses()
    }

    private fun beginResponses() {
        st.phase = Phase.RESPOND
        st.responses.clear()
        st.pending.clear()
        val d = st.lastDiscarder
        for (i in 1..3) {
            val q = (d + i) % 4
            if (responseOptions(q).isNotEmpty()) st.pending.add(q)
        }
        if (st.pending.isEmpty()) resolveResponses()
    }

    private fun resolveResponses() {
        val d = st.lastDiscarder
        val tile = st.lastDiscard
        val order = (1..3).map { (d + it) % 4 }
        val ronners = order.filter { st.responses[it] == Action.Ron }
        // Anyone whose hand this tile completed but who did not win on it becomes furiten.
        for (q in order) {
            if (q in ronners) continue
            val pl = st.players[q]
            if (tile in waits(q)) {
                if (pl.riichi) pl.riichiFuriten = true else pl.tempFuriten = true
            }
        }
        if (ronners.isNotEmpty()) { finishWin(ronners, d, tile); return }
        if (st.chankanPending) {
            st.chankanPending = false
            for (pl in st.players) pl.ippatsu = false
            revealDora()
            draw(d, true)
            return
        }
        val dp = st.players[d]
        if (dp.riichiPending) {
            dp.riichiPending = false
            dp.score -= 1000
            st.riichiSticks++
            st.events.add(GameEvent.RiichiAccepted(d))
        }
        for (q in order) if (st.players[q].riichi) st.players[q].safeTiles.add(tile)
        val caller = st.pending.firstOrNull { st.responses[it] == Action.Pon || st.responses[it] == Action.OpenKan }
            ?: st.pending.firstOrNull { st.responses[it] is Action.Chi }
        if (caller == null) {
            if (st.wall.isEmpty()) exhaustiveDraw() else draw((d + 1) % 4, false)
            return
        }
        applyCall(caller, st.responses[caller]!!)
    }

    private fun applyCall(q: Int, a: Action) {
        val d = st.lastDiscarder
        val tile = st.lastDiscard
        val pl = st.players[q]
        st.players[d].discards.last().calledBy = q
        for (x in st.players) x.ippatsu = false
        st.callsMade = true
        pl.tempFuriten = false
        pl.drawn = -1
        st.turn = q
        when (a) {
            Action.Pon -> {
                repeat(2) { pl.hand.remove(tile) }
                pl.melds.add(Meld(MeldType.PON, listOf(tile, tile, tile), tile, d))
                st.events.add(GameEvent.Called(q, d, MeldType.PON, tile))
                st.kuikae.clear(); st.kuikae.add(tile)
                st.mustDiscard = true
                st.phase = Phase.DISCARD
            }
            is Action.Chi -> {
                for (t in a.low..a.low + 2) if (t != tile) pl.hand.remove(t)
                pl.melds.add(Meld(MeldType.CHI, listOf(a.low, a.low + 1, a.low + 2), tile, d))
                st.events.add(GameEvent.Called(q, d, MeldType.CHI, tile))
                st.kuikae.clear(); st.kuikae.addAll(chiForbidden(tile, a.low))
                st.mustDiscard = true
                st.phase = Phase.DISCARD
            }
            Action.OpenKan -> {
                repeat(3) { pl.hand.remove(tile) }
                pl.melds.add(Meld(MeldType.OPEN_KAN, List(4) { tile }, tile, d))
                st.events.add(GameEvent.Called(q, d, MeldType.OPEN_KAN, tile))
                revealDora()
                draw(q, true)
            }
            else -> error("not a call: $a")
        }
    }

    private fun revealDora() {
        if (st.doraRevealed < 5) {
            st.doraRevealed++
            st.events.add(GameEvent.DoraRevealed(st.doraIndicators[st.doraRevealed - 1]))
        }
    }

    private fun closedKan(p: Int, tile: Int) {
        val pl = st.players[p]
        repeat(4) { pl.hand.remove(tile) }
        pl.melds.add(Meld(MeldType.CLOSED_KAN, List(4) { tile }))
        pl.drawn = -1
        for (x in st.players) x.ippatsu = false
        st.callsMade = true
        st.events.add(GameEvent.Called(p, p, MeldType.CLOSED_KAN, tile))
        revealDora()
        draw(p, true)
    }

    private fun addedKan(p: Int, tile: Int) {
        val pl = st.players[p]
        pl.hand.remove(tile)
        pl.drawn = -1
        val i = pl.melds.indexOfFirst { it.type == MeldType.PON && it.kind == tile }
        val old = pl.melds[i]
        pl.melds[i] = Meld(MeldType.ADDED_KAN, List(4) { tile }, old.called, old.from)
        st.callsMade = true
        st.events.add(GameEvent.Called(p, p, MeldType.ADDED_KAN, tile))
        // Other players may rob the kan.
        st.lastDiscard = tile
        st.lastDiscarder = p
        st.chankanPending = true
        beginResponses()
    }

    private fun finishWin(winners: List<Int>, loser: Int, tile: Int) {
        val before = st.players.map { it.score }
        val total = IntArray(4)
        val wins = ArrayList<WinInfo>()
        for ((i, w) in winners.withIndex()) {
            val pl = st.players[w]
            val score = winScore(w, tile, loser < 0) ?: error("win without a valid score")
            val honba = if (i == 0) st.honba else 0
            val d = Scorer.payments(score.base, w, st.dealer, loser, honba)
            val sticks = if (i == 0) st.riichiSticks else 0
            d[w] += sticks * 1000
            val concealed = pl.hand.toMutableList()
            if (loser < 0) concealed.remove(tile)
            concealed.sort()
            wins.add(WinInfo(w, loser, tile, concealed, pl.melds.toList(), score, d.toList(), sticks, honba, w == st.dealer))
            for (k in 0 until 4) total[k] += d[k]
        }
        st.riichiSticks = 0
        for (k in 0 until 4) st.players[k].score += total[k]
        val anyRiichi = winners.any { st.players[it].riichi }
        st.result = HandResult(
            wins = wins,
            isDraw = false,
            tenpai = List(4) { it in winners },
            deltas = total.toList(),
            scoresBefore = before,
            dealerContinues = st.dealer in winners,
            uraIndicators = if (anyRiichi) st.uraIndicators.take(st.doraRevealed) else emptyList(),
            hands = st.players.map { it.hand.sorted() },
        )
        st.chankanPending = false
        st.phase = Phase.HAND_END
    }

    private fun exhaustiveDraw() {
        val before = st.players.map { it.score }
        val tenpai = List(4) { waits(it).isNotEmpty() }
        val n = tenpai.count { it }
        val d = IntArray(4)
        if (n in 1..3) {
            for (p in 0 until 4) d[p] = if (tenpai[p]) 3000 / n else -3000 / (4 - n)
        }
        for (p in 0 until 4) st.players[p].score += d[p]
        st.result = HandResult(
            wins = emptyList(),
            isDraw = true,
            tenpai = tenpai,
            deltas = d.toList(),
            scoresBefore = before,
            dealerContinues = tenpai[st.dealer],
            uraIndicators = emptyList(),
            hands = st.players.map { it.hand.sorted() },
        )
        st.phase = Phase.HAND_END
    }

    /** Moves on from the hand-result screen: deals the next hand or ends the game. */
    fun nextHand() {
        check(st.phase == Phase.HAND_END)
        val r = st.result!!
        if (r.dealerContinues) {
            st.honba++
        } else {
            st.honba = if (r.isDraw) st.honba + 1 else 0
            st.dealer = (st.dealer + 1) % 4
            st.handNumber++
            if (st.handNumber == 4) { st.handNumber = 0; st.roundWind++ }
        }
        val lastRound = if (st.config.eastOnly) 0 else 1
        if (st.players.any { it.score < 0 } || st.roundWind > lastRound) {
            val top = ranking()[0]
            st.players[top].score += st.riichiSticks * 1000
            st.riichiSticks = 0
            st.phase = Phase.GAME_END
        } else {
            startHand()
        }
    }

    /** Seats from first place to last. Ties go to the seat closer to the first dealer. */
    fun ranking(): List<Int> =
        (0 until 4).sortedWith(compareBy({ -st.players[it].score }, { (it - st.initialDealer + 4) % 4 }))
}
