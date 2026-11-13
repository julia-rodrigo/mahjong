package mahjong.engine

class Term(val id: String, val word: String, val definition: String)

object Glossary {
    val terms: List<Term> = listOf(
        Term("sequence", "Sequence (chi / shuntsu)", "Three consecutive numbers in the same suit, like 3-4-5 of bamboo. Winds and dragons cannot form sequences."),
        Term("triplet", "Triplet (pon / koutsu)", "Three identical tiles."),
        Term("pair", "Pair", "Two identical tiles. Every standard winning hand has exactly one."),
        Term("tenpai", "Tenpai (ready)", "Your hand is one tile away from winning."),
        Term("shanten", "Tiles away", "How many useful tiles you still need before your hand is ready (tenpai)."),
        Term("wait", "Wait", "The tile or tiles that would complete your ready hand."),
        Term("yaku", "Yaku", "A scoring pattern. You need at least one yaku to win a hand, even if your tiles form four sets and a pair."),
        Term("riichi", "Riichi", "A bet of 1,000 points you may declare when your closed hand is ready. It counts as a yaku, but you must then discard every tile you draw unless it wins."),
        Term("tsumo", "Tsumo", "Winning with a tile you drew yourself. All three opponents pay."),
        Term("ron", "Ron", "Winning with a tile another player just discarded. Only that player pays."),
        Term("chi", "Chi", "Calling the discard of the player on your left to complete a sequence. It opens your hand."),
        Term("pon", "Pon", "Calling anyone's discard to complete a triplet. It opens your hand."),
        Term("kan", "Kan", "A set of four identical tiles. You draw a replacement tile and a new dora indicator is revealed."),
        Term("closed", "Closed hand", "A hand that has not called chi, pon or an open kan. Only closed hands can declare riichi."),
        Term("open", "Open hand", "A hand that has called a discard. It cannot riichi, and several yaku are worth less or nothing."),
        Term("furiten", "Furiten", "If any tile that would complete your hand is among your own discards, you may not win by ron (self-draw is still fine). Passing up a winning discard also makes you furiten until your next turn, or for the rest of the hand after riichi."),
        Term("dora", "Dora", "Bonus tiles. The dora indicator shows the tile just before the dora: if it shows 3-pin, every 4-pin is worth one extra han. Dora are not a yaku."),
        Term("uradora", "Ura dora", "Extra dora indicators under the normal ones, revealed only for a player who wins after riichi."),
        Term("han", "Han", "The doubling units of a hand's value. Each yaku and each dora adds han; more han means many more points."),
        Term("fu", "Fu", "Minipoints for the shape of the hand (triplets, the wait, winning by self-draw...). Together with han they set the score."),
        Term("mangan", "Mangan", "A score limit: 5 han (or 4 han with high fu) is worth 8,000 points, 12,000 for the dealer."),
        Term("dealer", "Dealer (East)", "The East seat. The dealer wins and pays 50% more, and stays dealer after winning or being ready at a draw."),
        Term("seatwind", "Seat wind", "Each player is East, South, West or North for the hand. A triplet of your own seat wind is a yaku."),
        Term("roundwind", "Round wind", "The wind of the current round (East first). A triplet of it is a yaku for everyone."),
        Term("value", "Value tiles (yakuhai)", "Dragons, your seat wind and the round wind. A triplet of any of them is a yaku by itself."),
        Term("terminal", "Terminals", "The 1s and 9s of each suit."),
        Term("honor", "Honors", "Wind tiles (East, South, West, North) and dragon tiles (White, Green, Red)."),
        Term("simples", "Simples", "Number tiles from 2 to 8."),
        Term("wall", "Wall", "The face-down tiles everyone draws from. When it runs out the hand ends in a draw."),
        Term("draw", "Exhaustive draw", "Nobody won before the wall ran out. Players who are not ready pay a total of 3,000 points to those who are."),
        Term("honba", "Honba (repeat counter)", "Counts dealer repeats and draws. Each one adds 300 points to the next win."),
        Term("ippatsu", "Ippatsu (one shot)", "A bonus yaku for winning within one turn of declaring riichi."),
        Term("safe", "Safe tile (genbutsu)", "A tile a riichi player has already discarded, or that was discarded after their riichi. They cannot win on it."),
        Term("dealin", "Dealing in", "Discarding the tile another player wins on. You alone pay the whole hand."),
    )
    private val byId = terms.associateBy { it.id }
    operator fun get(id: String): Term = byId.getValue(id)
}

enum class Verdict { RECOMMENDED, FINE, AVOID }

class OptionAdvice(val action: Action, val label: String, val what: String, val verdict: Verdict, val why: String)

class Advice(
    /** Short instruction, e.g. "Discard 9-man". */
    val headline: String,
    /** Why, in plain English. */
    val reason: String,
    /** Where the hand stands. */
    val status: String,
    val recommended: Action?,
    /** Advice for each call/win button that is available. */
    val options: List<OptionAdvice>,
    val warnings: List<String>,
    /** Tiles that would bring the hand closer after the recommended play. */
    val improving: List<Int>,
    /** Glossary ids of terms used. */
    val terms: List<String>,
)

/** Turns the engine's analysis into beginner-friendly explanations. */
object Tutor {
    private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"

    private fun yakuList(s: Score): String {
        val parts = s.yaku.map { "${it.yaku.english} (${it.han} han)" }.toMutableList()
        if (s.dora > 0) parts.add("Dora (${s.dora} han)")
        if (s.uraDora > 0) parts.add("Ura dora (${s.uraDora} han)")
        return parts.joinToString(", ")
    }

    private fun awayText(s: Int): String = when {
        s <= 0 -> "ready (tenpai)"
        else -> "${plural(s, "tile")} away from ready"
    }

    /** For a 13-tile hand: which waits could be won by ron right now, and which could not (no yaku). */
    private fun ronYaku(game: Game, p: Int, counts: IntArray, melds: List<Meld>, withRiichi: Boolean): Pair<List<Int>, List<Int>> {
        val ok = ArrayList<Int>()
        val bad = ArrayList<Int>()
        for (w in Shanten.waits(counts, melds.size)) {
            counts[w]++
            val ctx = game.context(p, w, false).let { if (withRiichi) it.copy(riichi = true) else it }
            if (Scorer.evaluate(counts, melds, ctx) != null) ok.add(w) else bad.add(w)
            counts[w]--
        }
        return ok to bad
    }

    private fun safetyReason(st: GameState, p: Int, tile: Int, danger: Int, names: List<String>): String {
        val who = Analysis.threats(st, p).joinToString(" and ") { names[it] }
        return when {
            danger == 0 -> "${Tiles.name(tile)} is completely safe: it has already been discarded against $who's riichi, so they cannot win on it."
            danger <= 4 -> "${Tiles.name(tile)} is one of your safer tiles against $who."
            else -> "You have no truly safe tile, and ${Tiles.name(tile)} is the least risky one you hold."
        }
    }

    private fun whyDiscard(st: GameState, p: Int, tile: Int, counts: IntArray): String {
        val n = counts[tile]
        if (Tiles.isHonor(tile)) {
            val value = Analysis.isValueTile(st, p, tile)
            return if (n == 1) {
                if (value) "It is a single ${Tiles.name(tile)}. Winds and dragons cannot be part of a sequence, so a lone one is rarely useful, even though a triplet of it would be a yaku."
                else "It is a lone ${Tiles.name(tile)} tile. Winds and dragons cannot be part of a sequence, and this one is not a value tile for you."
            } else "Your hand works better without it."
        }
        val num = Tiles.num(tile)
        val near = (-2..2).any { d -> d != 0 && num + d in 1..9 && counts[tile + d] > 0 }
        if (n == 1 && !near) {
            return if (Tiles.isTerminal(tile)) "It is an isolated ${if (num == 1) "1" else "9"}: nothing near it in your hand, and end tiles fit into fewer sequences."
            else "It is isolated: you hold no tiles next to it that could grow into a sequence."
        }
        return "Of all your tiles it is the one your hand needs least: the others already work together as pairs or sequence parts."
    }

    fun advise(game: Game, p: Int, names: List<String>): Advice? {
        val acts = game.legalActions(p)
        if (acts.isEmpty()) return null
        return if (game.state.phase == Phase.RESPOND) adviseResponse(game, p, acts, names) else adviseTurn(game, p, acts, names)
    }

    private fun adviseTurn(game: Game, p: Int, acts: List<Action>, names: List<String>): Advice {
        val st = game.state
        val pl = st.players[p]
        val terms = LinkedHashSet<String>()
        val warnings = ArrayList<String>()
        val options = ArrayList<OptionAdvice>()
        val choice = Ai.decide(game, p, Difficulty.NORMAL)
        val discards = Analysis.discardOptions(game, p)
        val unseen = Analysis.unseen(st, p)
        val threats = Analysis.threats(st, p)

        if (Action.Tsumo in acts) {
            val s = game.winScore(p, pl.drawn, true)!!
            terms.add("tsumo"); terms.add("yaku")
            options.add(OptionAdvice(Action.Tsumo, "Tsumo", "Win with the tile you just drew.", Verdict.RECOMMENDED, "It completes your hand."))
            return Advice(
                "You drew your winning tile. Press TSUMO!",
                "The ${Tiles.name(pl.drawn)} completes your hand. Your yaku: ${yakuList(s)}.",
                "Your hand is complete.", Action.Tsumo, options, warnings, emptyList(), terms.toList(),
            )
        }
        for (a in acts) {
            when (a) {
                is Action.ClosedKan -> {
                    terms.add("kan")
                    val rec = choice == a
                    options.add(OptionAdvice(a, "Kan", "Set aside your four ${Tiles.name(a.tile)} as a concealed kan. You draw a replacement tile and a new dora indicator is revealed. Your hand stays closed.",
                        if (rec) Verdict.RECOMMENDED else Verdict.AVOID,
                        if (rec) "It costs you nothing here and gives an extra draw."
                        else if (threats.isNotEmpty()) "New dora would mostly help the player in riichi."
                        else "You need some of those tiles for other sets."))
                }
                is Action.AddedKan -> {
                    terms.add("kan")
                    val rec = choice == a
                    options.add(OptionAdvice(a, "Kan", "Add your fourth ${Tiles.name(a.tile)} to your pon to make a kan. You draw a replacement tile and a new dora indicator is revealed.",
                        if (rec) Verdict.RECOMMENDED else Verdict.AVOID,
                        if (rec) "The tile is of no other use to you and you get an extra draw."
                        else "It adds dora for everyone, which is risky right now."))
                }
                else -> {}
            }
        }
        val riichiPossible = acts.any { it is Action.Riichi }
        if (riichiPossible) {
            terms.add("riichi")
            options.add(OptionAdvice(acts.first { it is Action.Riichi }, "Riichi",
                "Bet 1,000 points and announce that your hand is ready. Riichi is a yaku, so you can then win on any of your winning tiles. In return your hand is locked: you must discard every tile you draw unless it wins.",
                if (choice is Action.Riichi) Verdict.RECOMMENDED else Verdict.FINE,
                if (choice is Action.Riichi) "For a beginner, declaring riichi whenever you can is the simplest strong strategy." else "Possible, but your winning tiles are nearly all gone."))
        }

        if (threats.isNotEmpty()) {
            terms.add("safe"); terms.add("dealin")
            warnings.add("${threats.joinToString(" and ") { names[it] }} declared riichi and can win at any moment. Tiles they have discarded are safe against them.")
        }

        val headline: String
        val reason: String
        var status: String
        var improving: List<Int> = emptyList()

        when (choice) {
            is Action.ClosedKan, is Action.AddedKan -> {
                val k = if (choice is Action.ClosedKan) choice.tile else (choice as Action.AddedKan).tile
                headline = "Declare KAN with your ${Tiles.name(k)}."
                reason = "A kan gives you an extra draw and reveals another dora indicator, and here it does not slow your hand down."
                status = "You are ${awayText(discards.firstOrNull()?.shanten ?: 0)}."
            }
            is Action.Riichi -> {
                val c = pl.counts(); c[choice.tile]--
                val waits = Shanten.waits(c, pl.melds.size)
                val left = waits.sumOf { unseen[it] }
                terms.add("tenpai"); terms.add("wait")
                headline = "Declare RIICHI and discard ${Tiles.name(choice.tile)}."
                reason = "After that discard your hand is ready: you win on ${Tiles.names(waits)} (${plural(left, "tile")} still unseen). " +
                    "Riichi gives you a yaku, so any of them wins, by your own draw or from a discard."
                status = "You are ready (tenpai)."
                improving = waits
                if (waits.any { w -> w == choice.tile || pl.discards.any { it.tile == w } }) {
                    terms.add("furiten")
                    warnings.add("Furiten: one of your winning tiles is among your own discards, so you could only win by drawing the tile yourself.")
                }
            }
            is Action.Discard -> {
                val opt = discards.firstOrNull { it.tile == choice.tile } ?: discards.first()
                val folding = Analysis.shouldFold(st, p, discards.first().shanten)
                if (pl.riichi) {
                    headline = "Discard the tile you drew (${Tiles.name(choice.tile)})."
                    reason = "You are in riichi, so your hand is locked: every tile you draw that does not win must be discarded."
                } else if (folding) {
                    headline = "Play safe: discard ${Tiles.name(choice.tile)}."
                    reason = "Your hand is still ${awayText(discards.first().shanten)}, so you are unlikely to win this hand before the riichi player does. " +
                        "The goal now is not to deal in. " + safetyReason(st, p, choice.tile, opt.danger, names)
                } else {
                    headline = "Discard ${Tiles.name(choice.tile)}."
                    reason = whyDiscard(st, p, choice.tile, pl.counts())
                }
                improving = opt.improving
                terms.add("tenpai")
                val c = pl.counts(); c[choice.tile]--
                val realShanten = Shanten.shanten(c, pl.melds.size)
                if (realShanten == 0) {
                    val waits = Shanten.waits(c, pl.melds.size)
                    val left = waits.sumOf { unseen[it] }
                    terms.add("wait")
                    status = "After this you are ready (tenpai), waiting on ${Tiles.names(waits)} (${plural(left, "tile")} unseen)."
                    improving = waits
                    val (ok, bad) = ronYaku(game, p, c, pl.melds, pl.riichi)
                    if (waits.any { w -> w == choice.tile || pl.discards.any { it.tile == w } } || pl.riichiFuriten) {
                        terms.add("furiten")
                        warnings.add("Furiten: one of your winning tiles is among your own discards, so you cannot win by ron. You can still win by drawing the tile yourself.")
                    }
                    if (bad.isNotEmpty() && !pl.riichi) {
                        terms.add("yaku")
                        if (ok.isEmpty()) {
                            warnings.add(
                                if (pl.isClosed) "No yaku yet: your tiles form a ready hand, but without riichi you could only win by drawing the tile yourself."
                                else "No yaku: your hand is open and has no scoring pattern, so you cannot win on a discard. An open hand needs a yaku such as a triplet of dragons or your wind, or all tiles from 2 to 8."
                            )
                        } else {
                            warnings.add("You can only win by ron on ${Tiles.names(ok)}. ${Tiles.names(bad)} would complete the shape but leave you without a yaku.")
                        }
                    }
                } else {
                    terms.add("shanten")
                    status = "After this you are ${awayText(opt.shanten)}." +
                        if (!folding && opt.improving.isNotEmpty()) " ${plural(opt.improving.size, "kind")} of tile (${opt.ukeire} unseen) would bring you closer." else ""
                    if (!pl.isClosed && Analysis.yakuPath(st, p, c, pl.melds, unseen) == YakuPath.NONE) {
                        terms.add("yaku"); terms.add("open")
                        warnings.add("Your open hand has no yaku in sight. Without one you cannot win, so aim for a triplet of dragons or of your seat or round wind.")
                    }
                }
            }
            else -> { headline = ""; reason = ""; status = "" }
        }
        return Advice(headline, reason, status, choice, options, warnings, improving, terms.toList())
    }

    /** Feedback on a tile the player has selected but not yet discarded. Null means "that is the recommended tile". */
    fun discardNote(game: Game, p: Int, tile: Int, names: List<String>): String? {
        val st = game.state
        if (st.phase != Phase.DISCARD || st.turn != p) return null
        val options = Analysis.discardOptions(game, p)
        val pick = options.firstOrNull { it.tile == tile } ?: return "You cannot discard that tile right now."
        val best = options.first()
        val choice = Ai.decide(game, p, Difficulty.NORMAL)
        val recTile = when (choice) { is Action.Discard -> choice.tile; is Action.Riichi -> choice.tile; else -> best.tile }
        if (tile == recTile) return null
        val notes = ArrayList<String>()
        val recDanger = options.firstOrNull { it.tile == recTile }?.danger ?: 0
        if (pick.danger >= 8 && pick.danger > recDanger) {
            notes.add("Dangerous: ${Tiles.name(tile)} could easily be the tile ${Analysis.threats(st, p).joinToString(" or ") { names[it] }} is waiting for. If they win on it, you pay the whole hand.")
        } else if (pick.danger > recDanger + 2) {
            notes.add("${Tiles.name(tile)} is riskier against the riichi than ${Tiles.name(recTile)}.")
        }
        if (pick.shanten > best.shanten) {
            notes.add("It breaks up part of your hand: you would be ${awayText(pick.shanten)} instead of ${awayText(best.shanten)}.")
        } else if (pick.ukeire < best.ukeire && !Analysis.shouldFold(st, p, best.shanten)) {
            notes.add("It works, but leaves fewer helpful tiles: ${pick.ukeire} unseen tiles would improve your hand, against ${best.ukeire} if you discard ${Tiles.name(best.tile)}.")
        }
        if (notes.isEmpty()) notes.add("That is about as good as the suggested discard.")
        return notes.joinToString(" ")
    }

    private fun adviseResponse(game: Game, p: Int, acts: List<Action>, names: List<String>): Advice {
        val st = game.state
        val pl = st.players[p]
        val tile = st.lastDiscard
        val from = names[st.lastDiscarder]
        val terms = LinkedHashSet<String>()
        val warnings = ArrayList<String>()
        val options = ArrayList<OptionAdvice>()
        val choice = Ai.decide(game, p, Difficulty.NORMAL)

        if (Action.Ron in acts) {
            val s = game.winScore(p, tile, false)!!
            terms.add("ron"); terms.add("yaku")
            options.add(OptionAdvice(Action.Ron, "Ron", "Win on the tile $from just discarded. Only they pay.", Verdict.RECOMMENDED, "It completes your hand."))
            options.add(OptionAdvice(Action.Pass, "Pass", "Let the tile go.", Verdict.AVOID, "Passing a winning tile also makes you furiten."))
            return Advice(
                "That is your winning tile. Press RON!",
                "$from discarded ${Tiles.name(tile)}, which completes your hand. Your yaku: ${yakuList(s)}.",
                "Your hand is complete.", Action.Ron, options, warnings, emptyList(), terms.toList(),
            )
        }
        var recommendText = ""
        for (a in acts) {
            if (a == Action.Pass) continue
            val ev = Analysis.evaluateCall(game, p, a)
            val rec = a == choice
            val why = when {
                rec && Analysis.isValueTile(st, p, tile) && a == Action.Pon ->
                    "${Tiles.name(tile)} is a value tile for you: the triplet is a yaku by itself, so you can win with an open hand, and it brings you closer."
                rec -> "Your hand already has a yaku that works when open, and this brings you to ${awayText(ev.shantenAfter)}."
                a == Action.OpenKan -> "An open kan shows your tiles, stops you from declaring riichi and adds dora for everyone."
                pl.isClosed && ev.shantenBefore == 0 -> "You are already ready with a closed hand. Calling would throw away riichi."
                Analysis.shouldFold(st, p, ev.shantenAfter) -> "Someone is in riichi and you are far from ready. Calling leaves you fewer safe tiles to discard."
                ev.shantenAfter >= ev.shantenBefore -> "It does not bring your hand any closer to ready."
                pl.isClosed -> "Calling opens your hand: you could no longer declare riichi, and you would have no yaku to win with."
                else -> "It would leave your open hand without a yaku."
            }
            val (label, what) = when (a) {
                Action.Pon -> { terms.add("pon"); "Pon" to "Take $from's ${Tiles.name(tile)} to complete a triplet with your two. The set is laid face up, then you discard." }
                is Action.Chi -> { terms.add("chi"); "Chi ${Tiles.num(a.low)}-${Tiles.num(a.low) + 1}-${Tiles.num(a.low) + 2}" to "Take $from's ${Tiles.name(tile)} to complete the sequence ${Tiles.num(a.low)}-${Tiles.num(a.low) + 1}-${Tiles.num(a.low) + 2}. The set is laid face up, then you discard." }
                else -> { terms.add("kan"); "Kan" to "Take $from's ${Tiles.name(tile)} to make four of a kind, then draw a replacement tile." }
            }
            if (rec) recommendText = why
            options.add(OptionAdvice(a, label, what, if (rec) Verdict.RECOMMENDED else Verdict.AVOID, why))
        }
        terms.add("open")
        val passWhy = if (choice == Action.Pass) "Keeping your hand closed keeps riichi available, the easiest yaku for a beginner." else "You would miss a useful tile."
        options.add(OptionAdvice(Action.Pass, "Pass", "Do nothing and let play continue.", if (choice == Action.Pass) Verdict.RECOMMENDED else Verdict.FINE, passWhy))

        val headline: String
        val reason: String
        if (choice == Action.Pass) {
            headline = "You could call $from's ${Tiles.name(tile)}, but it is better to PASS."
            reason = options.first { it.action != Action.Pass }.why
        } else {
            val o = options.first { it.action == choice }
            headline = "Call ${o.label.uppercase()} on $from's ${Tiles.name(tile)}."
            reason = recommendText
        }
        val c = pl.counts()
        val s = Analysis.effectiveShanten(c, pl.melds.size, Analysis.yakuPath(st, p, c, pl.melds, Analysis.unseen(st, p)))
        return Advice(headline, reason, "You are ${awayText(s)}.", choice, options, warnings, emptyList(), terms.toList())
    }

    /**
     * Explains why seat [p] was not offered a win on the last discard although it completes the hand shape.
     * Null if the discard does not complete the hand.
     */
    fun missedWinNote(game: Game, p: Int, names: List<String>): String? {
        val st = game.state
        if (st.lastDiscard < 0 || st.lastDiscarder == p) return null
        val pl = st.players[p]
        if (pl.hand.size + pl.melds.size * 3 != 13) return null
        val waits = Shanten.waits(pl.counts(), pl.melds.size)
        if (st.lastDiscard !in waits) return null
        if (game.winScore(p, st.lastDiscard, false) == null) {
            return "${names[st.lastDiscarder]}'s ${Tiles.name(st.lastDiscard)} would have completed your hand, but you have no yaku, so you could not call ron. " +
                (if (pl.isClosed) "Declaring riichi when you are ready avoids this." else "An open hand needs a yaku such as a value-tile triplet or all simples.")
        }
        return "${names[st.lastDiscarder]}'s ${Tiles.name(st.lastDiscard)} would have completed your hand, but you are furiten (one of your winning tiles was already discarded by you or passed up), so you could not call ron."
    }

    /** Plain-English description of recent events. */
    fun narrate(events: List<GameEvent>, viewer: Int, names: List<String>, st: GameState): List<String> {
        val out = ArrayList<String>()
        for (e in events) {
            when (e) {
                is GameEvent.HandStarted -> out.add("New hand: ${st.handName}. ${names[st.dealer]} ${if (st.dealer == viewer) "are" else "is"} the dealer (East).")
                is GameEvent.Drew -> if (e.p == viewer) {
                    val t = st.players[viewer].drawn
                    if (t >= 0 && e === events.last { it is GameEvent.Drew }) out.add(if (e.rinshan) "You drew a replacement tile: ${Tiles.name(t)}." else "You drew ${Tiles.name(t)}.")
                }
                is GameEvent.Discarded -> out.add(
                    "${names[e.p]} ${if (e.riichi) "declared RIICHI and discarded" else "discarded"} ${Tiles.name(e.tile)}."
                )
                is GameEvent.Called -> out.add(
                    when (e.type) {
                        MeldType.CHI -> "${names[e.p]} called chi on ${names[e.from]}'s ${Tiles.name(e.tile)} (a sequence)."
                        MeldType.PON -> "${names[e.p]} called pon on ${names[e.from]}'s ${Tiles.name(e.tile)} (a triplet)."
                        MeldType.OPEN_KAN -> "${names[e.p]} called kan on ${names[e.from]}'s ${Tiles.name(e.tile)} (four of a kind)."
                        MeldType.ADDED_KAN -> "${names[e.p]} added a fourth ${Tiles.name(e.tile)} to a pon, making a kan."
                        MeldType.CLOSED_KAN -> "${names[e.p]} declared a concealed kan of ${Tiles.name(e.tile)}."
                    }
                )
                is GameEvent.DoraRevealed -> out.add("New dora indicator: ${Tiles.name(e.indicator)}, so ${Tiles.name(Tiles.doraFor(e.indicator))} is now dora too.")
                is GameEvent.RiichiAccepted -> {}
            }
        }
        return out
    }

    private fun fmt(n: Int): String = "%,d".format(n)

    /** Step-by-step explanation of how a winning hand was scored. */
    fun explainWin(w: WinInfo, names: List<String>, dealer: Int): List<String> {
        val s = w.score
        val out = ArrayList<String>()
        val who = names[w.winner]
        out.add(if (w.loser < 0) "$who won by tsumo (self-draw) on ${Tiles.name(w.winTile)}." else "$who won by ron on ${names[w.loser]}'s ${Tiles.name(w.winTile)}.")
        if (s.yakumanCount > 0) {
            out.add("Yakuman! ${s.yaku.joinToString(", ") { it.yaku.english }}: the highest hand value, a base of ${fmt(s.base)} points.")
        } else {
            out.add("Han: ${yakuList(s)} = ${s.han} han in total.")
            out.add("Fu: ${s.fuItems.joinToString(" + ") { "${it.label} ${it.fu}" }} = ${s.fu} fu.")
            if (s.limit.isEmpty()) {
                out.add("Base points = fu x 2^(han+2) = ${s.fu} x ${1 shl (s.han + 2)} = ${fmt(s.base)}.")
            } else {
                val raw = if (s.han <= 4) " (${s.fu} x ${1 shl (s.han + 2)} would be more than 2,000)" else ""
                out.add("${s.han} han is a limit hand: ${s.limit}, a fixed base of ${fmt(s.base)}$raw.")
            }
        }
        val honbaRon = if (w.honba > 0) " + ${300 * w.honba} for ${plural(w.honba, "repeat counter")}" else ""
        val honbaTsumo = if (w.honba > 0) " + ${100 * w.honba} each for ${plural(w.honba, "repeat counter")}" else ""
        if (w.loser >= 0) {
            val mult = if (w.isDealer) 6 else 4
            out.add("Ron: the discarder pays base x $mult (${if (w.isDealer) "the winner is dealer" else "non-dealer win"}), rounded up to the next 100$honbaRon = ${fmt(-w.deltas[w.loser])}.")
        } else if (w.isDealer) {
            val each = -w.deltas[(w.winner + 1) % 4]
            out.add("Dealer tsumo: every other player pays base x 2, rounded up to the next 100$honbaTsumo = ${fmt(each)} each.")
        } else {
            val fromDealer = -w.deltas[dealer]
            val other = (0 until 4).first { it != w.winner && it != dealer }
            out.add("Tsumo: the dealer pays base x 2 and the others base x 1, each rounded up to the next 100$honbaTsumo = ${fmt(fromDealer)} and ${fmt(-w.deltas[other])}.")
        }
        if (w.riichiSticksWon > 0) out.add("Plus ${fmt(w.riichiSticksWon * 1000)} from riichi bets on the table.")
        out.add("Total for $who: +${fmt(w.deltas[w.winner])}.")
        return out
    }
}
