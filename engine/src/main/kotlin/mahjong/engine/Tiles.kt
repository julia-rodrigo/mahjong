package mahjong.engine

/**
 * Tiles are represented by their "kind", an Int from 0 to 33:
 *  0..8  = 1-9 man (characters), 9..17 = 1-9 pin (circles), 18..26 = 1-9 sou (bamboo),
 *  27..30 = East, South, West, North, 31..33 = White, Green, Red dragon.
 * There are four copies of every kind (136 tiles, no red fives).
 */
object Tiles {
    const val KINDS = 34
    const val EAST = 27
    const val SOUTH = 28
    const val WEST = 29
    const val NORTH = 30
    const val WHITE = 31
    const val GREEN = 32
    const val RED = 33

    fun suit(k: Int): Int = if (k < 27) k / 9 else 3
    fun num(k: Int): Int = k % 9 + 1
    fun isHonor(k: Int): Boolean = k >= 27
    fun isWind(k: Int): Boolean = k in 27..30
    fun isDragon(k: Int): Boolean = k >= 31
    fun isTerminal(k: Int): Boolean = k < 27 && (k % 9 == 0 || k % 9 == 8)
    fun isYaochuu(k: Int): Boolean = isHonor(k) || isTerminal(k)
    fun isSimple(k: Int): Boolean = !isYaochuu(k)

    /** The dora tile pointed at by a dora indicator. */
    fun doraFor(indicator: Int): Int = when {
        indicator < 27 -> (indicator / 9) * 9 + (indicator % 9 + 1) % 9
        indicator < 31 -> 27 + (indicator - 27 + 1) % 4
        else -> 31 + (indicator - 31 + 1) % 3
    }

    private val SUIT_NAMES = arrayOf("man", "pin", "sou")
    private val HONOR_NAMES = arrayOf("East", "South", "West", "North", "White dragon", "Green dragon", "Red dragon")
    private val HONOR_SHORT = arrayOf("E", "S", "W", "N", "Wh", "G", "R")
    val WIND_NAMES = arrayOf("East", "South", "West", "North")

    /** Friendly name, e.g. "3-man", "East", "Red dragon". */
    fun name(k: Int): String = if (k < 27) "${num(k)}-${SUIT_NAMES[suit(k)]}" else HONOR_NAMES[k - 27]

    /** Compact name, e.g. "3m", "E", "Wh". */
    fun short(k: Int): String = if (k < 27) "${num(k)}${"mps"[suit(k)]}" else HONOR_SHORT[k - 27]

    fun names(ks: Collection<Int>): String = ks.joinToString(", ") { name(it) }

    /** Parses notation like "123m456p789s1122z" (z: 1-4 winds ESWN, 5-7 dragons white/green/red). */
    fun parse(s: String): List<Int> {
        val out = ArrayList<Int>()
        val pending = ArrayList<Int>()
        for (ch in s) {
            when (ch) {
                in '1'..'9' -> pending.add(ch - '1')
                'm' -> { pending.forEach { out.add(it) }; pending.clear() }
                'p' -> { pending.forEach { out.add(9 + it) }; pending.clear() }
                's' -> { pending.forEach { out.add(18 + it) }; pending.clear() }
                'z' -> { pending.forEach { out.add(27 + it) }; pending.clear() }
                ' ' -> {}
                else -> throw IllegalArgumentException("bad tile string: $s")
            }
        }
        require(pending.isEmpty()) { "bad tile string: $s" }
        return out
    }

    fun counts(tiles: Collection<Int>): IntArray {
        val c = IntArray(KINDS)
        for (t in tiles) c[t]++
        return c
    }
}
