package mahjong.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mahjong.engine.Action
import mahjong.engine.Advice
import mahjong.engine.Ai
import mahjong.engine.Difficulty
import mahjong.engine.Game
import mahjong.engine.GameConfig
import mahjong.engine.GameState
import mahjong.engine.Phase
import mahjong.engine.Tutor
import java.io.File

enum class TutorLevel(val title: String, val blurb: String) {
    FULL("Full guidance", "Explains every turn and highlights the suggested move."),
    HINTS("Hints on request", "Stays quiet until you tap Hint."),
    OFF("Off", "No advice, only a log of what happens."),
}

enum class Screen { HOME, LESSON, GAME, GLOSSARY }

@Serializable
class SaveData(val state: GameState, val difficulty: String)

const val HUMAN = 0
val PLAYER_NAMES = listOf("You", "Aki (right)", "Ren (across)", "Sora (left)")
val SHORT_NAMES = listOf("You", "Aki", "Ren", "Sora")

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", 0)
    private val saveFile = File(app.filesDir, "save.json")
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    var screen by mutableStateOf(Screen.HOME)
    /** Where the glossary's back button returns to. */
    var glossaryFrom by mutableStateOf(Screen.HOME)

    var difficulty by mutableStateOf(Difficulty.valueOf(prefs.getString("difficulty", "EASY")!!))
    var eastOnly by mutableStateOf(prefs.getBoolean("eastOnly", true))
    var tutorLevel by mutableStateOf(TutorLevel.valueOf(prefs.getString("tutor", "FULL")!!))
    var labels by mutableStateOf(prefs.getBoolean("labels", true))
    var lessonDone by mutableStateOf(prefs.getBoolean("lessonDone", false))
    private var seenTerms: Set<String> = prefs.getStringSet("seenTerms", emptySet())!!.toSet()

    var game: Game? = null
        private set
    /** Bumped after every change to the (mutable) game state so the UI redraws. */
    var version by mutableIntStateOf(0)
        private set
    var hasSave by mutableStateOf(saveFile.exists())
        private set

    var selectedTile by mutableIntStateOf(-1)
    var riichiMode by mutableStateOf(false)
    var hintShown by mutableStateOf(false)
    /** Index into the hand's event log of the first event the player has not been told about. */
    var eventMark by mutableIntStateOf(0)
        private set
    private var loop: Job? = null
    /** Delay between computer moves, in milliseconds. Tests set it to 0. */
    var aiDelay = 750L

    fun setDifficultyPref(d: Difficulty) { difficulty = d; prefs.edit().putString("difficulty", d.name).apply() }
    fun setEastOnlyPref(v: Boolean) { eastOnly = v; prefs.edit().putBoolean("eastOnly", v).apply() }
    fun setTutorPref(t: TutorLevel) { tutorLevel = t; hintShown = false; prefs.edit().putString("tutor", t.name).apply() }
    fun setLabelsPref(v: Boolean) { labels = v; prefs.edit().putBoolean("labels", v).apply() }
    fun finishLesson() { lessonDone = true; prefs.edit().putBoolean("lessonDone", true).apply() }

    fun isNewTerm(id: String) = id !in seenTerms
    private fun markTermsSeen(ids: Collection<String>) {
        if (ids.all { it in seenTerms }) return
        seenTerms = seenTerms + ids
        prefs.edit().putStringSet("seenTerms", seenTerms).apply()
    }

    fun openGlossary() { glossaryFrom = screen; screen = Screen.GLOSSARY }

    fun newGame() {
        loop?.cancel()
        game = Game.create(GameConfig(eastOnly = eastOnly))
        resetTurnUi()
        eventMark = 0
        screen = Screen.GAME
        changed()
        runAi()
    }

    fun resume() {
        if (game == null) {
            val data = runCatching { json.decodeFromString(SaveData.serializer(), saveFile.readText()) }.getOrNull()
            if (data == null) { saveFile.delete(); hasSave = false; return }
            difficulty = runCatching { Difficulty.valueOf(data.difficulty) }.getOrDefault(difficulty)
            game = Game(data.state)
            eventMark = maxOf(0, data.state.events.size - 4)
        }
        resetTurnUi()
        screen = Screen.GAME
        version++
        runAi()
    }

    fun quitToHome() {
        loop?.cancel()
        saveNow()
        screen = Screen.HOME
    }

    private fun resetTurnUi() { selectedTile = -1; riichiMode = false; hintShown = false }

    private fun changed() {
        version++
        saveNow()
    }

    fun saveNow() {
        val g = game ?: return
        if (g.state.phase == Phase.GAME_END) {
            hasSave = false
            viewModelScope.launch(io) { saveFile.delete() }
            return
        }
        // A failed save must never interrupt the game.
        val text = runCatching { json.encodeToString(SaveData.serializer(), SaveData(g.state, difficulty.name)) }.getOrNull() ?: return
        hasSave = true
        viewModelScope.launch(io) {
            val tmp = File(saveFile.path + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(saveFile)
        }
    }

    /** Advice for the human's current decision, or null when it is not their move. */
    fun advice(): Advice? {
        val g = game ?: return null
        if (HUMAN !in g.actors()) return null
        return Tutor.advise(g, HUMAN, PLAYER_NAMES)
    }

    fun humanAct(a: Action) {
        val g = game ?: return
        if (a !in g.legalActions(HUMAN)) return
        advice()?.let { markTermsSeen(it.terms) }
        eventMark = g.state.events.size
        g.act(HUMAN, a)
        resetTurnUi()
        changed()
        runAi()
    }

    /** Tap on a tile in the player's hand: first tap selects, second tap discards. */
    fun tapTile(tile: Int) {
        val g = game ?: return
        val acts = g.legalActions(HUMAN)
        val wanted: Action = if (riichiMode) Action.Riichi(tile) else Action.Discard(tile)
        if (wanted !in acts) return
        if (selectedTile == tile) humanAct(wanted) else selectedTile = tile
    }

    fun nextHand() {
        val g = game ?: return
        if (g.state.phase != Phase.HAND_END) return
        g.nextHand()
        eventMark = 0
        resetTurnUi()
        changed()
        runAi()
    }

    /** Plays the computer seats (and the forced discards of a human in riichi) until the human must decide. */
    private fun runAi() {
        loop?.cancel()
        loop = viewModelScope.launch {
            while (true) {
                val g = game ?: break
                val st = g.state
                if (st.phase == Phase.HAND_END || st.phase == Phase.GAME_END) break
                val actors = g.actors()
                if (st.phase == Phase.RESPOND) {
                    // Computer players answer at once; their answers stay hidden until everyone has decided.
                    val bots = actors.filter { it != HUMAN }
                    if (bots.isEmpty()) break
                    for (p in bots) g.act(p, Ai.decide(g, p, difficulty))
                    changed()
                    continue
                }
                val p = actors.first()
                if (p == HUMAN) {
                    val acts = g.legalActions(HUMAN)
                    val forced = st.players[HUMAN].riichi && acts.size == 1
                    if (!forced) break
                    delay(aiDelay)
                    eventMark = st.events.size
                    g.act(HUMAN, acts[0])
                } else {
                    delay(aiDelay)
                    g.act(p, Ai.decide(g, p, difficulty))
                }
                changed()
            }
        }
    }
}
