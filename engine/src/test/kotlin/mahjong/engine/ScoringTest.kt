package mahjong.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoringTest {
    private fun score(
        hand: String, win: String, tsumo: Boolean, melds: List<Meld> = emptyList(),
        seat: Int = Tiles.SOUTH, round: Int = Tiles.EAST, riichi: Boolean = false,
        dora: List<Int> = emptyList(), ura: List<Int> = emptyList(), ippatsu: Boolean = false,
        haitei: Boolean = false, rinshan: Boolean = false,
    ): Score? {
        val c = Tiles.counts(Tiles.parse(hand))
        val w = Tiles.parse(win)[0]
        c[w]++
        return Scorer.evaluate(c, melds, WinContext(w, tsumo, seat, round, riichi = riichi, ippatsu = ippatsu,
            haitei = haitei, rinshan = rinshan, doraIndicators = dora, uraIndicators = ura))
    }

    private fun yaku(s: Score?) = s!!.yaku.map { it.yaku }.toSet()
    private fun pon(t: String) = Tiles.parse(t)[0].let { Meld(MeldType.PON, listOf(it, it, it), it, 1) }
    private fun chi(t: String) = Tiles.parse(t).let { Meld(MeldType.CHI, it, it[0], 3) }
    private fun ckan(t: String) = Tiles.parse(t)[0].let { Meld(MeldType.CLOSED_KAN, List(4) { _ -> it }) }

    @Test fun shantenBasics() {
        assertEquals(0, Shanten.shanten(Tiles.counts(Tiles.parse("123m456p789s1122z")), 0))
        assertEquals(listOf(27, 28), Shanten.waits(Tiles.counts(Tiles.parse("123m456p789s1122z")), 0))
        assertEquals(-1, Shanten.shanten(Tiles.counts(Tiles.parse("123m456p789s11222z")), 0))
        assertEquals(0, Shanten.shanten(Tiles.counts(Tiles.parse("19m19p19s1234567z")), 0))
        assertEquals(13, Shanten.waits(Tiles.counts(Tiles.parse("19m19p19s1234567z")), 0).size)
        assertEquals(0, Shanten.shanten(Tiles.counts(Tiles.parse("1133m5577p2244s6z")), 0))
        assertEquals(1, Shanten.shanten(Tiles.counts(Tiles.parse("123m456p78s11225z")), 0))
        assertEquals(8, Shanten.regular(Tiles.counts(Tiles.parse("159m159p159s1234z")), 0))
        // nine-sided wait
        assertEquals(9, Shanten.waits(Tiles.counts(Tiles.parse("1112345678999m")), 0).size)
        // with one meld
        assertEquals(0, Shanten.shanten(Tiles.counts(Tiles.parse("456p789s1122z")), 1))
    }

    @Test fun riichiTsumoPinfu() {
        val s = score("234m456p678s34s99p", "2s", tsumo = true, riichi = true)
        assertEquals(setOf(Yaku.RIICHI, Yaku.MENZEN_TSUMO, Yaku.PINFU), yaku(s))
        assertEquals(3, s!!.han); assertEquals(20, s.fu); assertEquals(640, s.base)
        assertEquals(listOf(-700, 2700, -1300, -700), Scorer.payments(s.base, 1, 2, -1, 0).toList())
    }

    @Test fun riichiOnlyRon() {
        val s = score("123m456p789s67m11z", "8m", tsumo = false, riichi = true)
        assertEquals(setOf(Yaku.RIICHI), yaku(s))
        assertEquals(40, s!!.fu)
        assertEquals(1300, Scorer.payments(s.base, 1, 0, 2, 0)[1])
        assertEquals(2000 + 300, Scorer.payments(s.base, 0, 0, 2, 1)[0])
    }

    @Test fun noYakuIsNotAWin() {
        assertNull(score("123m456p789s67m11z", "8m", tsumo = false))
        assertNotNull(score("123m456p789s67m11z", "8m", tsumo = true)) // self-draw gives a yaku
        // open hand, no yaku
        assertNull(score("456p789s67m11z", "8m", tsumo = true, melds = listOf(chi("123m"))))
        // dora alone is not a yaku
        assertNull(score("123m456p789s67m11z", "8m", tsumo = false, dora = listOf(4)))
    }

    @Test fun chiitoi() {
        val s = score("1133m5577p2244s6z", "6z", tsumo = false, riichi = true)
        assertEquals(setOf(Yaku.RIICHI, Yaku.CHIITOITSU), yaku(s))
        assertEquals(25, s!!.fu); assertEquals(800, s.base)
        assertEquals(3200, Scorer.payments(s.base, 1, 0, 2, 0)[1])
    }

    @Test fun tanyaoOpen() {
        val s = score("234m456p66s78s", "6s", tsumo = false, melds = listOf(pon("3p")))
        assertEquals(setOf(Yaku.TANYAO), yaku(s))
        assertEquals(30, s!!.fu)
        assertEquals(1000, Scorer.payments(s.base, 1, 0, 2, 0)[1])
    }

    @Test fun yakuhaiAndFu() {
        // closed triplet of red dragon, kanchan wait, ron: 20 + 10 + 8 + 2 = 40 fu, 1 han
        val s = score("777z123m456p79s22m", "8s", tsumo = false)
        assertEquals(setOf(Yaku.YAKUHAI_RED), yaku(s))
        assertEquals(40, s!!.fu)
        // double east for the dealer in the east round
        val d = score("111z123m456p79s22m", "8s", tsumo = false, seat = Tiles.EAST)
        assertEquals(setOf(Yaku.SEAT_WIND, Yaku.ROUND_WIND), yaku(d))
    }

    @Test fun shanponRonTripletIsOpen() {
        // 111m 999p shanpon ron on 9p: the 9p triplet counts as open -> only two concealed triplets
        val s = score("111m99p222s456s77z", "7z", tsumo = false)
        assertEquals(setOf(Yaku.YAKUHAI_RED), yaku(s))
        val t = score("111m999p222s77z45s", "6s", tsumo = false, riichi = true)
        assertTrue(Yaku.SANANKOU in yaku(t))
        val u = score("111m999p222s456s7z", "7z", tsumo = false, riichi = true)
        assertTrue(Yaku.SANANKOU in yaku(u)) // tanki wait keeps all three concealed
        val v = score("111m999p22s444s77z", "2s", tsumo = false, riichi = true)
        assertTrue(Yaku.SANANKOU in yaku(v))
        assertTrue(Yaku.SUUANKOU !in yaku(v))
        val w = score("111m999p22s444s77z", "2s", tsumo = true)
        assertEquals(setOf(Yaku.SUUANKOU), yaku(w))
        assertEquals(8000, w!!.base)
    }

    @Test fun flushesAndStraights() {
        val honitsu = score("123m456m789m11z22m", "1z", tsumo = false)
        assertEquals(setOf(Yaku.HONITSU, Yaku.ITTSU, Yaku.ROUND_WIND), yaku(honitsu))
        val chinitsu = score("123m456m789m2234m", "2m", tsumo = false)
        assertTrue(Yaku.CHINITSU in yaku(chinitsu))
        assertEquals(3000, chinitsu!!.base.coerceAtMost(3000))
        val openChin = score("456m789m2234m", "2m", tsumo = false, melds = listOf(chi("123m")))
        assertEquals(5, openChin!!.yaku.first { it.yaku == Yaku.CHINITSU }.han)
        val sanshoku = score("234m234p234s678m5z", "5z", tsumo = false)
        assertEquals(setOf(Yaku.SANSHOKU), yaku(sanshoku))
        assertEquals(2, sanshoku!!.han)
    }

    @Test fun iipeikouRyanpeikouPinfuChoice() {
        val s = score("223344m678p99s45s", "6s", tsumo = false)
        assertEquals(setOf(Yaku.IIPEIKOU, Yaku.PINFU), yaku(s))
        val r = score("223344m667788p5s", "5s", tsumo = false)
        assertEquals(setOf(Yaku.RYANPEIKOU, Yaku.TANYAO), yaku(r))
    }

    @Test fun outsideHands() {
        val chanta = score("123m789p111z99s12s", "3s", tsumo = false)
        assertEquals(setOf(Yaku.CHANTA, Yaku.ROUND_WIND), yaku(chanta))
        val junchan = score("123m789p111s99s12p", "3p", tsumo = false)
        assertEquals(setOf(Yaku.JUNCHAN), yaku(junchan))
        assertEquals(3, junchan!!.han)
        val honro = score("111m99p11z", "1z", tsumo = false, melds = listOf(pon("9s"), pon("1s")))
        assertEquals(setOf(Yaku.HONROUTOU, Yaku.TOITOI, Yaku.ROUND_WIND), yaku(honro))
    }

    @Test fun yakuman() {
        assertEquals(setOf(Yaku.DAISANGEN), yaku(score("555z666z77z123m55p", "7z", false)))
        assertEquals(setOf(Yaku.KOKUSHI), yaku(score("19m19p19s1234567z", "1m", false)))
        assertEquals(setOf(Yaku.CHUUREN), yaku(score("1112345678999m", "5m", false)))
        assertEquals(setOf(Yaku.TSUUIISOU, Yaku.SHOUSUUSHII), yaku(score("111z222z333z44z55z", "5z", false)))
        assertEquals(16000, score("111z222z333z44z55z", "5z", false)!!.base)
        assertEquals(setOf(Yaku.RYUUIISOU), yaku(score("234s234s666s88s66z", "6z", false)))
        assertEquals(setOf(Yaku.CHINROUTOU), yaku(score("111m11p99s", "9s", false, melds = listOf(pon("1s"), pon("9p")))))
    }

    @Test fun kansAndFu() {
        // closed kan of terminals = 32 fu; 20 + 32 + 2 (tsumo) + 2 (tanki) = 56 -> 60
        val s = score("234m456p678s5s", "5s", tsumo = true, melds = listOf(ckan("1m")))
        assertEquals(setOf(Yaku.MENZEN_TSUMO), yaku(s))
        assertEquals(60, s!!.fu)
        val r = score("234m456p678s5s", "5s", tsumo = true, melds = listOf(ckan("1m")), rinshan = true)
        assertTrue(Yaku.RINSHAN in yaku(r))
    }

    @Test fun doraAndLimits() {
        // riichi + tanyao + pinfu + 2 dora = 5 han -> mangan
        val s = score("234m345p678s34s66p", "5s", tsumo = false, riichi = true, dora = listOf(Tiles.parse("5p")[0]))
        assertEquals(5, s!!.han); assertEquals("Mangan", s.limit)
        assertEquals(8000, Scorer.payments(s.base, 1, 0, 2, 0)[1])
        assertEquals(12000, Scorer.payments(s.base, 0, 0, 2, 0)[0])
        assertEquals(listOf(12000, -4000, -4000, -4000), Scorer.payments(s.base, 0, 0, -1, 0).toList())
        // 4 han 40 fu = 2560 base -> mangan cap
        assertEquals(1920, 30 * 64)
        // dora wraps: 9 -> 1, North -> East, Red -> White
        assertEquals(0, Tiles.doraFor(8)); assertEquals(Tiles.EAST, Tiles.doraFor(Tiles.NORTH)); assertEquals(Tiles.WHITE, Tiles.doraFor(Tiles.RED))
        // ura dora only add han
        val u = score("234m345p678s34s66p", "5s", tsumo = false, riichi = true, ura = listOf(Tiles.parse("5p")[0]))
        assertEquals(2, u!!.uraDora)
    }

    @Test fun tenpaiPaymentsRounding() {
        // 1 han 30 fu non-dealer tsumo: 300/500
        assertEquals(listOf(-500, 1100, -300, -300), Scorer.payments(240, 1, 0, -1, 0).toList())
        // 2 han 30 fu with 2 honba ron: 2000 + 600
        assertEquals(2600, Scorer.payments(480, 1, 0, 2, 2)[1])
    }
}
