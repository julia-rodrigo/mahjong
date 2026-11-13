package mahjong.engine

import kotlin.random.Random

enum class Difficulty { EASY, NORMAL }

/** One possible discard and how good it is for the hand. */
class DiscardOption(
    val tile: Int,
    /** Tiles away from tenpai after this discard (taking the need for a yaku into account). */
    val shanten: Int,
    /** Number of unseen tiles that would bring the hand closer. */
    val ukeire: Int,
    /** Kinds of tiles that would bring the hand closer. */
    val improving: List<Int>,
    /** 0 (safe) to 12 (very dangerous) against players in riichi. */
    val danger: Int,
)

enum class YakuPath { CLOSED, VALUE_TILES, ALL_SIMPLES, NONE }

/** Hand analysis shared by the computer players and the tutor. */
object Analysis {
    /** How many copies of each kind the viewer cannot see. */
    fun unseen(st: GameState, viewer: Int): IntArray {
        val u = IntArray(Tiles.KINDS) { 4 }
        for (t in st.players[viewer].hand) u[t]--
        for (pl in st.players) {
            for (d in pl.discards) if (d.calledBy < 0) u[d.tile]--
            for (m in pl.melds) for (t in m.tiles) u[t]--
        }
        for (t in st.revealedDora) u[t]--
        for (i in u.indices) if (u[i] < 0) u[i] = 0
        return u
    }

    fun isValueTile(st: GameState, p: Int, k: Int): Boolean =
        Tiles.isDragon(k) || k == st.seatWind(p) || k == st.roundWindTile

    fun threats(st: GameState, viewer: Int): List<Int> = (0 until 4).filter { it != viewer && st.players[it].riichi }

    /** Which yaku the hand can realistically rely on. An open hand cannot riichi, so it needs one. */
    fun yakuPath(st: GameState, p: Int, counts: IntArray, melds: List<Meld>, unseen: IntArray): YakuPath {
        if (melds.all { it.type == MeldType.CLOSED_KAN }) return YakuPath.CLOSED
        if (melds.any { it.type != MeldType.CHI && isValueTile(st, p, it.kind) }) return YakuPath.VALUE_TILES
        for (k in 27 until 34) {
            if (!isValueTile(st, p, k)) continue
            if (counts[k] >= 3 || (counts[k] == 2 && unseen[k] > 0)) return YakuPath.VALUE_TILES
        }
        if (melds.all { m -> m.tiles.all { Tiles.isSimple(it) } }) return YakuPath.ALL_SIMPLES
        return YakuPath.NONE
    }

    /** Shanten that also respects the yaku the hand is going for. */
    fun effectiveShanten(counts: IntArray, meldCount: Int, path: YakuPath): Int = when (path) {
        YakuPath.CLOSED, YakuPath.VALUE_TILES -> Shanten.shanten(counts, meldCount)
        YakuPath.NONE -> Shanten.shanten(counts, meldCount) + 2
        YakuPath.ALL_SIMPLES -> {
            val c = counts.copyOf()
            for (k in 0 until Tiles.KINDS) if (Tiles.isYaochuu(k)) c[k] = 0
            Shanten.regular(c, meldCount)
        }
    }

    /** Danger of discarding [tile]: 0 = cannot deal in, 12 = very risky. Only riichi players count. */
    fun danger(st: GameState, viewer: Int, tile: Int, unseen: IntArray): Int {
        var worst = 0
        for (r in threats(st, viewer)) {
            val pl = st.players[r]
            fun safe(k: Int) = k in pl.safeTiles || pl.discards.any { it.tile == k }
            val d = when {
                safe(tile) -> 0
                Tiles.isHonor(tile) -> when (unseen[tile]) { 0 -> 1; 1 -> 2; 2 -> 4; else -> 6 }
                else -> {
                    val n = Tiles.num(tile)
                    val lo = n > 3 && safe(tile - 3)
                    val hi = n < 7 && safe(tile + 3)
                    when (n) {
                        1, 9 -> if (lo || hi) 2 else 6
                        2, 8 -> if (lo || hi) 3 else 8
                        3, 7 -> if (lo || hi) 4 else 10
                        else -> if (lo && hi) 3 else if (lo || hi) 9 else 12
                    }
                }
            }
            worst = maxOf(worst, d)
        }
        return worst
    }

    /** How little a tile is worth keeping when everything else is equal (higher = throw first). */
    fun junkValue(st: GameState, p: Int, tile: Int, counts: IntArray): Int {
        var v = when {
            Tiles.isHonor(tile) -> if (isValueTile(st, p, tile)) 4 else 8
            Tiles.isTerminal(tile) -> 6
            Tiles.num(tile) == 2 || Tiles.num(tile) == 8 -> 3
            else -> 1
        }
        if (counts[tile] >= 2) v -= 3
        if (st.revealedDora.any { Tiles.doraFor(it) == tile }) v -= 6
        return v
    }

    /** All discards the seat may make now, best for the hand first. */
    fun discardOptions(game: Game, p: Int): List<DiscardOption> {
        val st = game.state
        val pl = st.players[p]
        val tiles = game.legalActions(p).filterIsInstance<Action.Discard>().map { it.tile }
        val unseen = unseen(st, p)
        val c = pl.counts()
        val meldCount = pl.melds.size
        val out = ArrayList<DiscardOption>()
        for (t in tiles) {
            c[t]--
            val path = yakuPath(st, p, c, pl.melds, unseen)
            val s = effectiveShanten(c, meldCount, path)
            var ukeire = 0
            val improving = ArrayList<Int>()
            for (k in 0 until Tiles.KINDS) {
                if (unseen[k] == 0 || c[k] >= 4) continue
                if (path == YakuPath.ALL_SIMPLES && Tiles.isYaochuu(k)) continue
                c[k]++
                if (effectiveShanten(c, meldCount, path) < s) { ukeire += unseen[k]; improving.add(k) }
                c[k]--
            }
            c[t]++
            out.add(DiscardOption(t, s, ukeire, improving, danger(st, p, t, unseen)))
        }
        val full = pl.counts()
        return out.sortedWith(
            compareBy<DiscardOption>({ it.shanten }, { -it.ukeire }, { -junkValue(st, p, it.tile, full) }, { it.tile })
        )
    }

    /** True when the seat should stop attacking and discard safely. */
    fun shouldFold(st: GameState, p: Int, bestShanten: Int): Boolean {
        if (st.players[p].riichi || threats(st, p).isEmpty()) return false
        return bestShanten >= 2 || (bestShanten == 1 && st.wall.size < 24)
    }

    class CallEval(val action: Action, val shantenBefore: Int, val shantenAfter: Int, val pathAfter: YakuPath, val good: Boolean)

    /** Evaluates a chi/pon offered to seat [p] on the last discard. */
    fun evaluateCall(game: Game, p: Int, a: Action): CallEval {
        val st = game.state
        val pl = st.players[p]
        val tile = st.lastDiscard
        val unseen = unseen(st, p)
        val c = pl.counts()
        val before = effectiveShanten(c, pl.melds.size, yakuPath(st, p, c, pl.melds, unseen))
        val meld = when (a) {
            Action.Pon -> { c[tile] -= 2; Meld(MeldType.PON, listOf(tile, tile, tile), tile, st.lastDiscarder) }
            is Action.Chi -> {
                for (t in a.low..a.low + 2) if (t != tile) c[t]--
                Meld(MeldType.CHI, listOf(a.low, a.low + 1, a.low + 2), tile, st.lastDiscarder)
            }
            Action.OpenKan -> { c[tile] -= 3; Meld(MeldType.OPEN_KAN, List(4) { tile }, tile, st.lastDiscarder) }
            else -> error("not a call")
        }
        val melds = pl.melds + meld
        var after = 99
        var pathAfter = YakuPath.NONE
        if (a == Action.OpenKan) {
            pathAfter = yakuPath(st, p, c, melds, unseen)
            after = effectiveShanten(c, melds.size, pathAfter)
        } else {
            for (k in 0 until Tiles.KINDS) {
                if (c[k] == 0) continue
                c[k]--
                val path = yakuPath(st, p, c, melds, unseen)
                val s = effectiveShanten(c, melds.size, path)
                if (s < after) { after = s; pathAfter = path }
                c[k]++
            }
        }
        val sureYaku = melds.any { it.type != MeldType.CHI && isValueTile(st, p, it.kind) } ||
            (27 until 34).any { isValueTile(st, p, it) && c[it] >= 3 } ||
            (pathAfter == YakuPath.ALL_SIMPLES && (!pl.isClosed || after <= 1))
        val closedTenpai = pl.isClosed && before == 0
        val good = a != Action.OpenKan && after < before && sureYaku && !closedTenpai && !shouldFold(st, p, after)
        return CallEval(a, before, after, pathAfter, good)
    }
}

/** Computer players. */
object Ai {
    fun decide(game: Game, p: Int, level: Difficulty, rng: Random = Random.Default): Action {
        val acts = game.legalActions(p)
        require(acts.isNotEmpty()) { "seat $p has nothing to decide" }
        return if (game.state.phase == Phase.RESPOND) respond(game, p, acts, level) else turn(game, p, acts, level, rng)
    }

    private fun respond(game: Game, p: Int, acts: List<Action>, level: Difficulty): Action {
        if (Action.Ron in acts) return Action.Ron
        val st = game.state
        val calls = acts.filter { it == Action.Pon || it is Action.Chi }
        if (level == Difficulty.EASY) {
            if (Action.Pon in acts && Analysis.isValueTile(st, p, st.lastDiscard) && Analysis.threats(st, p).isEmpty()) return Action.Pon
            return Action.Pass
        }
        val best = calls.map { Analysis.evaluateCall(game, p, it) }.filter { it.good }.minByOrNull { it.shantenAfter }
        return best?.action ?: Action.Pass
    }

    private fun turn(game: Game, p: Int, acts: List<Action>, level: Difficulty, rng: Random): Action {
        if (Action.Tsumo in acts) return Action.Tsumo
        val st = game.state
        val pl = st.players[p]
        val options = Analysis.discardOptions(game, p)
        if (options.isEmpty()) return acts.first()
        val best = options[0]
        val folding = level == Difficulty.NORMAL && Analysis.shouldFold(st, p, best.shanten)

        // Kans: only when they do not slow the hand down and nobody is threatening.
        if (!folding && Analysis.threats(st, p).isEmpty()) {
            val unseen = Analysis.unseen(st, p)
            for (a in acts) {
                val k = when (a) { is Action.ClosedKan -> a.tile; is Action.AddedKan -> a.tile; else -> continue }
                val c = pl.counts()
                val meldsAfter: List<Meld>
                if (a is Action.ClosedKan) {
                    c[k] -= 4
                    meldsAfter = pl.melds + Meld(MeldType.CLOSED_KAN, List(4) { k })
                } else {
                    c[k] -= 1
                    meldsAfter = pl.melds
                }
                val s = Analysis.effectiveShanten(c, meldsAfter.size, Analysis.yakuPath(st, p, c, meldsAfter, unseen))
                if (s <= best.shanten) return a
            }
        }
        if (pl.riichi) return Action.Discard(pl.drawn)

        if (folding) {
            val safest = options.minWith(compareBy({ it.danger }, { options.indexOf(it) }))
            return Action.Discard(safest.tile)
        }

        // Riichi whenever possible, on the discard that leaves the most winning tiles.
        val riichis = acts.filterIsInstance<Action.Riichi>()
        if (riichis.isNotEmpty()) {
            val unseen = Analysis.unseen(st, p)
            var bestTile = -1
            var bestCount = 0
            for (r in riichis) {
                val c = pl.counts()
                c[r.tile]--
                val waits = Shanten.waits(c, pl.melds.size)
                var n = waits.sumOf { unseen[it] }
                if (waits.any { w -> pl.discards.any { it.tile == w } || w == r.tile }) n = n / 4 // furiten: self-draw only
                if (n > bestCount) { bestCount = n; bestTile = r.tile }
            }
            if (bestTile >= 0) return Action.Riichi(bestTile)
        }

        if (level == Difficulty.EASY) {
            // Beginners don't always find the most efficient discard.
            val pool = options.filter { it.shanten == best.shanten }.take(3)
            if (rng.nextInt(100) < 35) return Action.Discard(pool[rng.nextInt(pool.size)].tile)
            return Action.Discard(best.tile)
        }
        // Pushing against a riichi: among nearly-equal discards take the safer one.
        if (Analysis.threats(st, p).isNotEmpty()) {
            val near = options.filter { it.shanten == best.shanten && it.ukeire * 10 >= best.ukeire * 7 }
            return Action.Discard(near.minBy { it.danger }.tile)
        }
        return Action.Discard(best.tile)
    }
}
