package mahjong.engine

import kotlinx.serialization.Serializable

@Serializable
enum class MeldType { CHI, PON, OPEN_KAN, ADDED_KAN, CLOSED_KAN }

/** A called set (or a concealed kan). [tiles] is sorted; [from] is the seat the tile was taken from. */
@Serializable
data class Meld(val type: MeldType, val tiles: List<Int>, val called: Int = -1, val from: Int = -1) {
    val isKan: Boolean get() = type == MeldType.OPEN_KAN || type == MeldType.ADDED_KAN || type == MeldType.CLOSED_KAN
    val isOpen: Boolean get() = type != MeldType.CLOSED_KAN
    val kind: Int get() = tiles[0]
}

/**
 * Every standard yaku. [han] is the value with a closed hand, [openHan] the value with an open hand
 * (-1 = not allowed when open).
 */
@Serializable
enum class Yaku(
    val english: String,
    val japanese: String,
    val han: Int,
    val openHan: Int,
    val desc: String,
    val yakuman: Boolean = false,
) {
    RIICHI("Riichi", "Riichi", 1, -1, "Declare riichi when your closed hand is one tile from winning. Costs a 1,000-point bet and locks your hand."),
    DOUBLE_RIICHI("Double Riichi", "Daburu Riichi", 2, -1, "Declare riichi on your very first discard."),
    IPPATSU("One Shot", "Ippatsu", 1, -1, "Win within one turn after declaring riichi, with no calls in between."),
    MENZEN_TSUMO("Self-Draw", "Menzen Tsumo", 1, -1, "Draw your winning tile yourself with a closed hand."),
    PINFU("All Sequences", "Pinfu", 1, -1, "Four sequences, a pair that is not a dragon or your own/round wind, and a two-sided wait."),
    TANYAO("All Simples", "Tanyao", 1, 1, "No 1s, 9s, winds or dragons: only tiles 2 through 8."),
    IIPEIKOU("Twin Sequences", "Iipeikou", 1, -1, "Two identical sequences in the same suit, e.g. 2-3-4 and 2-3-4 of bamboo."),
    YAKUHAI_WHITE("White Dragon Triplet", "Yakuhai (Haku)", 1, 1, "Three white dragons."),
    YAKUHAI_GREEN("Green Dragon Triplet", "Yakuhai (Hatsu)", 1, 1, "Three green dragons."),
    YAKUHAI_RED("Red Dragon Triplet", "Yakuhai (Chun)", 1, 1, "Three red dragons."),
    SEAT_WIND("Seat Wind Triplet", "Yakuhai (Jikaze)", 1, 1, "Three of the wind that matches your seat this hand."),
    ROUND_WIND("Round Wind Triplet", "Yakuhai (Bakaze)", 1, 1, "Three of the wind of the current round (East in the East round)."),
    HAITEI("Last Tile Draw", "Haitei", 1, 1, "Win by drawing the very last tile of the wall."),
    HOUTEI("Last Tile Discard", "Houtei", 1, 1, "Win on the very last discard of the hand."),
    RINSHAN("After a Kan", "Rinshan Kaihou", 1, 1, "Win on the replacement tile drawn after declaring a kan."),
    CHANKAN("Robbing a Kan", "Chankan", 1, 1, "Win on the tile another player adds to a pon to make a kan."),
    CHIITOITSU("Seven Pairs", "Chiitoitsu", 2, -1, "Seven different pairs instead of four sets and a pair."),
    SANSHOKU("Three-Colour Straight", "Sanshoku Doujun", 2, 1, "The same sequence in all three suits, e.g. 3-4-5 in man, pin and sou."),
    ITTSU("Full Straight", "Ittsu", 2, 1, "1-2-3, 4-5-6 and 7-8-9 all in one suit."),
    CHANTA("Outside Hand", "Chanta", 2, 1, "Every set and the pair contains a 1, 9, wind or dragon."),
    TOITOI("All Triplets", "Toitoi", 2, 2, "Four triplets (or kans) and a pair, no sequences."),
    SANANKOU("Three Concealed Triplets", "Sanankou", 2, 2, "Three triplets made without calling them."),
    SANSHOKU_DOUKOU("Three-Colour Triplets", "Sanshoku Doukou", 2, 2, "The same number as a triplet in all three suits."),
    SANKANTSU("Three Kans", "Sankantsu", 2, 2, "Three kans (sets of four)."),
    SHOUSANGEN("Little Three Dragons", "Shousangen", 2, 2, "Two dragon triplets and a pair of the third dragon."),
    HONROUTOU("All Terminals and Honors", "Honroutou", 2, 2, "Every tile is a 1, 9, wind or dragon."),
    HONITSU("Half Flush", "Honitsu", 3, 2, "One suit only, plus winds and dragons."),
    JUNCHAN("Pure Outside Hand", "Junchan", 3, 2, "Every set and the pair contains a 1 or 9, with no winds or dragons."),
    RYANPEIKOU("Double Twin Sequences", "Ryanpeikou", 3, -1, "Two separate pairs of identical sequences."),
    CHINITSU("Full Flush", "Chinitsu", 6, 5, "Every tile from a single suit, no winds or dragons."),
    KOKUSHI("Thirteen Orphans", "Kokushi Musou", 13, -1, "One of each 1, 9, wind and dragon, plus one duplicate.", true),
    SUUANKOU("Four Concealed Triplets", "Suuankou", 13, -1, "Four triplets all made without calling.", true),
    DAISANGEN("Big Three Dragons", "Daisangen", 13, 13, "Triplets of all three dragons.", true),
    SHOUSUUSHII("Little Four Winds", "Shousuushii", 13, 13, "Three wind triplets and a pair of the fourth wind.", true),
    DAISUUSHII("Big Four Winds", "Daisuushii", 13, 13, "Triplets of all four winds.", true),
    TSUUIISOU("All Honors", "Tsuuiisou", 13, 13, "Every tile is a wind or dragon.", true),
    CHINROUTOU("All Terminals", "Chinroutou", 13, 13, "Every tile is a 1 or a 9.", true),
    RYUUIISOU("All Green", "Ryuuiisou", 13, 13, "Only the all-green tiles: 2, 3, 4, 6, 8 of bamboo and green dragons.", true),
    CHUUREN("Nine Gates", "Chuuren Poutou", 13, -1, "1-1-1-2-3-4-5-6-7-8-9-9-9 in one suit plus any one more tile of that suit, closed.", true),
    SUUKANTSU("Four Kans", "Suukantsu", 13, 13, "Four kans.", true),
    TENHOU("Blessing of Heaven", "Tenhou", 13, -1, "The dealer wins on their very first draw.", true),
    CHIIHOU("Blessing of Earth", "Chiihou", 13, -1, "A non-dealer wins on their very first draw, before any call.", true),
}

@Serializable
data class YakuHit(val yaku: Yaku, val han: Int)

@Serializable
data class FuItem(val label: String, val fu: Int)

@Serializable
data class Score(
    val yaku: List<YakuHit>,
    val dora: Int,
    val uraDora: Int,
    val han: Int,
    val fu: Int,
    val fuItems: List<FuItem>,
    val yakumanCount: Int,
    /** Base points before the x4/x6 (ron) or x1/x2 (tsumo) payment multipliers. */
    val base: Int,
    /** "", "Mangan", "Haneman", "Baiman", "Sanbaiman", "Yakuman". */
    val limit: String,
)

data class WinContext(
    val winTile: Int,
    val tsumo: Boolean,
    val seatWind: Int,
    val roundWind: Int,
    val riichi: Boolean = false,
    val doubleRiichi: Boolean = false,
    val ippatsu: Boolean = false,
    val haitei: Boolean = false,
    val houtei: Boolean = false,
    val rinshan: Boolean = false,
    val chankan: Boolean = false,
    val tenhou: Boolean = false,
    val chiihou: Boolean = false,
    val doraIndicators: List<Int> = emptyList(),
    val uraIndicators: List<Int> = emptyList(),
)

object Scorer {
    private class SetInfo(val seq: Boolean, val kind: Int, val open: Boolean, val kan: Boolean)

    /**
     * Scores a complete hand. [counts] are the concealed tiles including the winning tile.
     * Returns null if the hand is not complete or has no yaku.
     */
    fun evaluate(counts: IntArray, melds: List<Meld>, ctx: WinContext): Score? {
        val all = counts.copyOf()
        for (m in melds) for (t in m.tiles) all[t]++
        var best: Score? = null
        fun consider(s: Score?) {
            if (s == null) return
            val b = best
            if (b == null || s.base > b.base || (s.base == b.base && (s.han > b.han || (s.han == b.han && s.fu > b.fu)))) best = s
        }
        if (melds.isEmpty() && Shanten.kokushi(counts) == -1) {
            val list = arrayListOf(Yaku.KOKUSHI)
            if (ctx.tenhou) list.add(Yaku.TENHOU)
            if (ctx.chiihou) list.add(Yaku.CHIIHOU)
            consider(yakumanScore(list))
        }
        if (melds.isEmpty() && Shanten.chiitoi(counts) == -1) consider(evalChiitoi(counts, all, ctx))
        for (div in HandDivider.divide(counts)) {
            if (div.groups.size + melds.size != 4) continue
            if (div.pair == ctx.winTile) consider(evalStandard(div, -1, melds, all, ctx))
            val seen = HashSet<Group>()
            for ((i, g) in div.groups.withIndex()) {
                val contains = if (g.seq) ctx.winTile in g.kind..g.kind + 2 else g.kind == ctx.winTile
                if (contains && seen.add(g)) consider(evalStandard(div, i, melds, all, ctx))
            }
        }
        return best
    }

    private fun yakumanScore(list: List<Yaku>): Score =
        Score(list.map { YakuHit(it, 13) }, 0, 0, 13 * list.size, 0, emptyList(), list.size, 8000 * list.size, "Yakuman")

    private fun situational(ctx: WinContext, closed: Boolean, hits: MutableList<YakuHit>) {
        if (ctx.doubleRiichi) hits.add(YakuHit(Yaku.DOUBLE_RIICHI, 2)) else if (ctx.riichi) hits.add(YakuHit(Yaku.RIICHI, 1))
        if (ctx.riichi && ctx.ippatsu) hits.add(YakuHit(Yaku.IPPATSU, 1))
        if (closed && ctx.tsumo) hits.add(YakuHit(Yaku.MENZEN_TSUMO, 1))
        if (ctx.haitei) hits.add(YakuHit(Yaku.HAITEI, 1))
        if (ctx.houtei) hits.add(YakuHit(Yaku.HOUTEI, 1))
        if (ctx.rinshan) hits.add(YakuHit(Yaku.RINSHAN, 1))
        if (ctx.chankan) hits.add(YakuHit(Yaku.CHANKAN, 1))
    }

    private fun flush(all: IntArray, closed: Boolean, hits: MutableList<YakuHit>) {
        val suits = (0 until 27).filter { all[it] > 0 }.map { it / 9 }.toSet()
        val honors = (27 until 34).any { all[it] > 0 }
        if (suits.size == 1) {
            if (honors) hits.add(YakuHit(Yaku.HONITSU, if (closed) 3 else 2))
            else hits.add(YakuHit(Yaku.CHINITSU, if (closed) 6 else 5))
        }
    }

    private fun countDora(all: IntArray, indicators: List<Int>): Int = indicators.sumOf { all[Tiles.doraFor(it)] }

    private fun finish(hits: List<YakuHit>, fu: Int, fuItems: List<FuItem>, all: IntArray, ctx: WinContext): Score? {
        val yakuHan = hits.sumOf { it.han }
        if (yakuHan == 0) return null
        val dora = countDora(all, ctx.doraIndicators)
        val ura = countDora(all, ctx.uraIndicators)
        val han = yakuHan + dora + ura
        var limit = ""
        val base = when {
            han >= 13 -> { limit = "Yakuman"; 8000 }
            han >= 11 -> { limit = "Sanbaiman"; 6000 }
            han >= 8 -> { limit = "Baiman"; 4000 }
            han >= 6 -> { limit = "Haneman"; 3000 }
            han == 5 -> { limit = "Mangan"; 2000 }
            else -> {
                val b = fu * (1 shl (han + 2))
                if (b >= 2000) { limit = "Mangan"; 2000 } else b
            }
        }
        return Score(hits, dora, ura, han, fu, fuItems, 0, base, limit)
    }

    private fun evalChiitoi(counts: IntArray, all: IntArray, ctx: WinContext): Score? {
        if ((0 until 27).none { all[it] > 0 }) {
            val list = arrayListOf(Yaku.TSUUIISOU)
            if (ctx.tenhou) list.add(Yaku.TENHOU)
            if (ctx.chiihou) list.add(Yaku.CHIIHOU)
            return yakumanScore(list)
        }
        if (ctx.tenhou) return yakumanScore(listOf(Yaku.TENHOU))
        if (ctx.chiihou) return yakumanScore(listOf(Yaku.CHIIHOU))
        val hits = ArrayList<YakuHit>()
        situational(ctx, true, hits)
        hits.add(YakuHit(Yaku.CHIITOITSU, 2))
        val kinds = (0 until 34).filter { all[it] > 0 }
        if (kinds.all { Tiles.isSimple(it) }) hits.add(YakuHit(Yaku.TANYAO, 1))
        if (kinds.all { Tiles.isYaochuu(it) }) hits.add(YakuHit(Yaku.HONROUTOU, 2))
        flush(all, true, hits)
        return finish(hits, 25, listOf(FuItem("Seven pairs is always 25 fu", 25)), all, ctx)
    }

    private fun evalStandard(div: Division, winIdx: Int, melds: List<Meld>, all: IntArray, ctx: WinContext): Score? {
        val closed = melds.all { it.type == MeldType.CLOSED_KAN }
        val sets = ArrayList<SetInfo>()
        for ((i, g) in div.groups.withIndex()) {
            sets.add(SetInfo(g.seq, g.kind, !g.seq && !ctx.tsumo && i == winIdx, false))
        }
        for (m in melds) {
            sets.add(SetInfo(m.type == MeldType.CHI, m.kind, m.isOpen, m.isKan))
        }
        val seqs = sets.filter { it.seq }
        val trips = sets.filter { !it.seq }
        val kinds = (0 until 34).filter { all[it] > 0 }

        // ----- yakuman -----
        val ym = ArrayList<Yaku>()
        if (ctx.tenhou) ym.add(Yaku.TENHOU)
        if (ctx.chiihou) ym.add(Yaku.CHIIHOU)
        if (closed && trips.size == 4 && trips.none { it.open }) ym.add(Yaku.SUUANKOU)
        val dragonTrips = trips.count { Tiles.isDragon(it.kind) }
        val windTrips = trips.count { Tiles.isWind(it.kind) }
        if (dragonTrips == 3) ym.add(Yaku.DAISANGEN)
        if (windTrips == 4) ym.add(Yaku.DAISUUSHII)
        else if (windTrips == 3 && Tiles.isWind(div.pair)) ym.add(Yaku.SHOUSUUSHII)
        if (kinds.all { Tiles.isHonor(it) }) ym.add(Yaku.TSUUIISOU)
        if (kinds.all { Tiles.isTerminal(it) }) ym.add(Yaku.CHINROUTOU)
        val greens = setOf(19, 20, 21, 23, 25, Tiles.GREEN)
        if (kinds.all { it in greens }) ym.add(Yaku.RYUUIISOU)
        if (trips.count { it.kan } == 4) ym.add(Yaku.SUUKANTSU)
        if (melds.isEmpty() && kinds.all { it < 27 } && kinds.map { it / 9 }.toSet().size == 1) {
            val o = kinds[0] / 9 * 9
            if (all[o] >= 3 && all[o + 8] >= 3 && (1..7).all { all[o + it] >= 1 }) ym.add(Yaku.CHUUREN)
        }
        if (ym.isNotEmpty()) return yakumanScore(ym)

        // ----- wait shape -----
        var waitFu = 0
        var waitLabel = ""
        var twoSided = false
        if (winIdx == -1) {
            waitFu = 2; waitLabel = "Pair wait (tanki)"
        } else {
            val g = div.groups[winIdx]
            if (g.seq) {
                val n = Tiles.num(g.kind)
                when {
                    ctx.winTile == g.kind + 1 -> { waitFu = 2; waitLabel = "Middle wait (kanchan)" }
                    (ctx.winTile == g.kind + 2 && n == 1) || (ctx.winTile == g.kind && n == 7) -> { waitFu = 2; waitLabel = "Edge wait (penchan)" }
                    else -> twoSided = true
                }
            }
        }

        // ----- yaku -----
        val hits = ArrayList<YakuHit>()
        situational(ctx, closed, hits)
        val pairIsValue = Tiles.isDragon(div.pair) || div.pair == ctx.seatWind || div.pair == ctx.roundWind
        val pinfu = closed && melds.isEmpty() && seqs.size == 4 && !pairIsValue && twoSided
        if (pinfu) hits.add(YakuHit(Yaku.PINFU, 1))
        if (kinds.all { Tiles.isSimple(it) }) hits.add(YakuHit(Yaku.TANYAO, 1))
        if (closed) {
            val dup = seqs.groupingBy { it.kind }.eachCount().values.sumOf { it / 2 }
            if (dup >= 2) hits.add(YakuHit(Yaku.RYANPEIKOU, 3)) else if (dup == 1) hits.add(YakuHit(Yaku.IIPEIKOU, 1))
        }
        for (t in trips) {
            when (t.kind) {
                Tiles.WHITE -> hits.add(YakuHit(Yaku.YAKUHAI_WHITE, 1))
                Tiles.GREEN -> hits.add(YakuHit(Yaku.YAKUHAI_GREEN, 1))
                Tiles.RED -> hits.add(YakuHit(Yaku.YAKUHAI_RED, 1))
            }
            if (t.kind == ctx.seatWind) hits.add(YakuHit(Yaku.SEAT_WIND, 1))
            if (t.kind == ctx.roundWind) hits.add(YakuHit(Yaku.ROUND_WIND, 1))
        }
        val seqKinds = seqs.map { it.kind }.toSet()
        if ((0..6).any { n -> n in seqKinds && n + 9 in seqKinds && n + 18 in seqKinds }) {
            hits.add(YakuHit(Yaku.SANSHOKU, if (closed) 2 else 1))
        }
        if ((0..2).any { s -> s * 9 in seqKinds && s * 9 + 3 in seqKinds && s * 9 + 6 in seqKinds }) {
            hits.add(YakuHit(Yaku.ITTSU, if (closed) 2 else 1))
        }
        val allYaochuuTiles = kinds.all { Tiles.isYaochuu(it) }
        if (allYaochuuTiles) {
            hits.add(YakuHit(Yaku.HONROUTOU, 2))
        } else {
            val outside = Tiles.isYaochuu(div.pair) && sets.all {
                if (it.seq) Tiles.num(it.kind) == 1 || Tiles.num(it.kind) == 7 else Tiles.isYaochuu(it.kind)
            }
            if (outside && seqs.isNotEmpty()) {
                if (kinds.any { Tiles.isHonor(it) }) hits.add(YakuHit(Yaku.CHANTA, if (closed) 2 else 1))
                else hits.add(YakuHit(Yaku.JUNCHAN, if (closed) 3 else 2))
            }
        }
        if (trips.size == 4) hits.add(YakuHit(Yaku.TOITOI, 2))
        if (trips.count { !it.open } == 3) hits.add(YakuHit(Yaku.SANANKOU, 2))
        val tripKinds = trips.map { it.kind }.toSet()
        if ((0..8).any { n -> n in tripKinds && n + 9 in tripKinds && n + 18 in tripKinds }) hits.add(YakuHit(Yaku.SANSHOKU_DOUKOU, 2))
        if (trips.count { it.kan } == 3) hits.add(YakuHit(Yaku.SANKANTSU, 2))
        if (dragonTrips == 2 && Tiles.isDragon(div.pair)) hits.add(YakuHit(Yaku.SHOUSANGEN, 2))
        flush(all, closed, hits)

        // ----- fu -----
        val items = ArrayList<FuItem>()
        var fu: Int
        if (pinfu) {
            if (ctx.tsumo) {
                items.add(FuItem("Pinfu won by self-draw is always 20 fu", 20)); fu = 20
            } else {
                items.add(FuItem("Base", 20)); items.add(FuItem("Closed hand won by ron", 10)); fu = 30
            }
        } else {
            items.add(FuItem("Base", 20))
            if (closed && !ctx.tsumo) items.add(FuItem("Closed hand won by ron", 10))
            if (ctx.tsumo) items.add(FuItem("Self-draw", 2))
            for (t in trips) {
                var v = if (t.open) 2 else 4
                if (Tiles.isYaochuu(t.kind)) v *= 2
                if (t.kan) v *= 4
                val what = (if (t.open) "Open " else "Concealed ") + (if (t.kan) "kan" else "triplet") + " of ${Tiles.name(t.kind)}"
                items.add(FuItem(what, v))
            }
            var pairFu = 0
            if (Tiles.isDragon(div.pair)) pairFu += 2
            if (div.pair == ctx.seatWind) pairFu += 2
            if (div.pair == ctx.roundWind) pairFu += 2
            if (pairFu > 0) items.add(FuItem("Pair of ${Tiles.name(div.pair)} (value tile)", pairFu))
            if (waitFu > 0) items.add(FuItem(waitLabel, waitFu))
            fu = items.sumOf { it.fu }
            if (fu == 20) { items.add(FuItem("Open hand minimum", 10)); fu = 30 }
            val rounded = (fu + 9) / 10 * 10
            if (rounded != fu) { items.add(FuItem("Round up to the next 10", rounded - fu)); fu = rounded }
        }
        return finish(hits, fu, items, all, ctx)
    }

    private fun roundUp100(x: Int): Int = (x + 99) / 100 * 100

    /** Point changes for each seat. [loser] is -1 for a self-draw win. Riichi sticks are not included. */
    fun payments(base: Int, winner: Int, dealer: Int, loser: Int, honba: Int): IntArray {
        val d = IntArray(4)
        if (loser >= 0) {
            val amt = roundUp100(base * (if (winner == dealer) 6 else 4)) + 300 * honba
            d[loser] -= amt
            d[winner] += amt
        } else {
            for (q in 0 until 4) {
                if (q == winner) continue
                val amt = roundUp100(base * (if (winner == dealer || q == dealer) 2 else 1)) + 100 * honba
                d[q] -= amt
                d[winner] += amt
            }
        }
        return d
    }
}
