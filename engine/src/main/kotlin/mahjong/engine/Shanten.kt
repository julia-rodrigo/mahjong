package mahjong.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Shanten = how many tile swaps a hand is away from "tenpai" (ready, one tile from winning).
 * 0 means tenpai, -1 means the hand is complete.
 */
object Shanten {
    private val suitCache = ConcurrentHashMap<Int, ByteArray>()

    /** Lowest shanten over the standard shape, seven pairs and thirteen orphans. */
    fun shanten(counts: IntArray, meldCount: Int): Int {
        var best = regular(counts, meldCount)
        if (meldCount == 0) {
            best = minOf(best, chiitoi(counts), kokushi(counts))
        }
        return best
    }

    fun chiitoi(counts: IntArray): Int {
        var pairs = 0
        var kinds = 0
        for (c in counts) {
            if (c > 0) kinds++
            if (c >= 2) pairs++
        }
        return 6 - pairs + maxOf(0, 7 - kinds)
    }

    fun kokushi(counts: IntArray): Int {
        var kinds = 0
        var pair = 0
        for (k in 0 until Tiles.KINDS) {
            if (!Tiles.isYaochuu(k)) continue
            if (counts[k] > 0) kinds++
            if (counts[k] >= 2) pair = 1
        }
        return 13 - kinds - pair
    }

    /** Standard "4 sets + 1 pair" shanten. */
    fun regular(counts: IntArray, meldCount: Int): Int {
        // cur[p*5+m] = max partial sets using exactly m complete sets and p pairs as the head
        var cur = IntArray(10) { -1 }
        cur[0] = 0
        for (s in 0 until 3) {
            var key = 0
            for (i in 0 until 9) key = key * 5 + counts[s * 9 + i]
            cur = combine(cur, suitCache.getOrPut(key) { analyzeSuit(counts, s * 9) })
        }
        cur = combine(cur, analyzeHonors(counts))
        var best = 8
        for (p in 0..1) for (m in 0..4) {
            val t0 = cur[p * 5 + m]
            if (t0 < 0) continue
            val mm = m + meldCount
            if (mm > 4) continue
            val t = minOf(t0, 4 - mm)
            best = minOf(best, 8 - 2 * mm - t - p)
        }
        return best
    }

    private fun combine(a: IntArray, b: ByteArray): IntArray {
        val out = IntArray(10) { -1 }
        for (p1 in 0..1) for (m1 in 0..4) {
            val t1 = a[p1 * 5 + m1]
            if (t1 < 0) continue
            for (p2 in 0..(1 - p1)) for (m2 in 0..(4 - m1)) {
                val t2 = b[p2 * 5 + m2].toInt()
                if (t2 < 0) continue
                val idx = (p1 + p2) * 5 + m1 + m2
                if (t1 + t2 > out[idx]) out[idx] = t1 + t2
            }
        }
        return out
    }

    private fun analyzeHonors(counts: IntArray): ByteArray {
        var sets = 0
        var pairs = 0
        for (k in 27 until 34) {
            if (counts[k] >= 3) sets++ else if (counts[k] == 2) pairs++
        }
        val res = ByteArray(10) { -1 }
        if (sets <= 4) {
            res[sets] = pairs.toByte()
            if (pairs > 0) res[5 + sets] = (pairs - 1).toByte()
        }
        return res
    }

    private fun analyzeSuit(counts: IntArray, offset: Int): ByteArray {
        val c = IntArray(9) { counts[offset + it] }
        val res = ByteArray(10) { -1 }
        fun dfs(start: Int, m: Int, t: Int, p: Int) {
            var i = start
            while (i < 9 && c[i] == 0) i++
            if (i == 9) {
                if (m <= 4) {
                    val idx = p * 5 + m
                    if (t > res[idx]) res[idx] = t.toByte()
                }
                return
            }
            if (c[i] >= 3) { c[i] -= 3; dfs(i, m + 1, t, p); c[i] += 3 }
            if (i <= 6 && c[i + 1] > 0 && c[i + 2] > 0) {
                c[i]--; c[i + 1]--; c[i + 2]--
                dfs(i, m + 1, t, p)
                c[i]++; c[i + 1]++; c[i + 2]++
            }
            if (c[i] >= 2) {
                c[i] -= 2
                if (p == 0) dfs(i, m, t, 1)
                dfs(i, m, t + 1, p)
                c[i] += 2
            }
            if (i <= 7 && c[i + 1] > 0) {
                c[i]--; c[i + 1]--
                dfs(i, m, t + 1, p)
                c[i]++; c[i + 1]++
            }
            if (i <= 6 && c[i + 2] > 0) {
                c[i]--; c[i + 2]--
                dfs(i, m, t + 1, p)
                c[i]++; c[i + 2]++
            }
            c[i]--
            dfs(i, m, t, p)
            c[i]++
        }
        dfs(0, 0, 0, 0)
        return res
    }

    /** True if the tiles (14 minus 3 per meld) form a complete hand. */
    fun isComplete(counts: IntArray, meldCount: Int): Boolean = shanten(counts, meldCount) == -1

    /** Tiles that would complete a 13-tile (minus melds) hand. Empty if not tenpai. */
    fun waits(counts: IntArray, meldCount: Int): List<Int> {
        if (shanten(counts, meldCount) != 0) return emptyList()
        val out = ArrayList<Int>()
        for (k in 0 until Tiles.KINDS) {
            if (counts[k] >= 4) continue
            counts[k]++
            if (shanten(counts, meldCount) == -1) out.add(k)
            counts[k]--
        }
        return out
    }
}

data class Group(val seq: Boolean, val kind: Int)
data class Division(val pair: Int, val groups: List<Group>)

/** Splits a complete concealed hand into every possible "sets + pair" arrangement. */
object HandDivider {
    fun divide(counts: IntArray): List<Division> {
        val c = counts.copyOf()
        val out = ArrayList<Division>()
        val groups = ArrayList<Group>()
        fun rec(start: Int, pair: Int) {
            var i = start
            while (i < Tiles.KINDS && c[i] == 0) i++
            if (i == Tiles.KINDS) {
                out.add(Division(pair, groups.toList()))
                return
            }
            if (c[i] >= 3) {
                c[i] -= 3; groups.add(Group(false, i))
                rec(i, pair)
                groups.removeAt(groups.size - 1); c[i] += 3
            }
            if (i < 27 && i % 9 <= 6 && c[i + 1] > 0 && c[i + 2] > 0) {
                c[i]--; c[i + 1]--; c[i + 2]--; groups.add(Group(true, i))
                rec(i, pair)
                groups.removeAt(groups.size - 1); c[i]++; c[i + 1]++; c[i + 2]++
            }
        }
        for (p in 0 until Tiles.KINDS) {
            if (c[p] >= 2) {
                c[p] -= 2
                rec(0, p)
                c[p] += 2
            }
        }
        return out
    }
}
