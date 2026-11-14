package mahjong.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import mahjong.engine.Action
import mahjong.engine.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Plays a whole game through the real UI state and saves pictures of the main screens to build/shots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun playThrough() {
        val vm = ViewModelProvider(rule.activity)[GameViewModel::class.java]
        vm.aiDelay = 0
        shot("01-home")
        rule.runOnUiThread { vm.screen = Screen.LESSON }
        shot("02-lesson")
        rule.runOnUiThread { vm.openGlossary() }
        shot("03-glossary")
        rule.runOnUiThread { vm.finishLesson(); vm.newGame() }
        val taken = HashSet<String>()
        fun once(name: String) { if (taken.add(name)) shot(name) }
        var steps = 0
        while (steps++ < 20000) {
            rule.waitForIdle()
            val g = vm.game!!
            val st = g.state
            if (st.phase == Phase.GAME_END) break
            if (st.phase == Phase.HAND_END) {
                once(if (st.result!!.isDraw) "08-draw" else "07-win")
                rule.runOnUiThread { vm.nextHand() }
                continue
            }
            val adv = vm.advice()
            if (adv == null) {
                // a computer player is "thinking": let the clock run
                rule.mainClock.advanceTimeBy(100)
                org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                continue
            }
            val rec = adv.recommended!!
            when {
                st.phase == Phase.RESPOND -> once("05-call")
                rec is Action.Riichi -> once("06-riichi")
                st.players[1].riichi || st.players[2].riichi || st.players[3].riichi -> once("09-defence")
                st.players[HUMAN].discards.size == 3 -> once("04-turn")
            }
            if (rec is Action.Discard && taken.add("select")) {
                val other = g.legalActions(HUMAN).filterIsInstance<Action.Discard>().last { it.tile != rec.tile }
                rule.runOnUiThread { vm.tapTile(other.tile) }
                shot("04b-selected")
                rule.runOnUiThread { vm.selectedTile = -1 }
            }
            rule.runOnUiThread { vm.humanAct(rec) }
        }
        val g = vm.game!!
        assertEquals("steps=$steps turn=${g.state.turn} actors=${g.actors()} acts=${g.legalActions(HUMAN)} rec=${vm.advice()?.recommended} hand=${g.state.handName} wall=${g.state.wall.size}", Phase.GAME_END, g.state.phase)
        shot("10-gameover")
        assertTrue(taken.contains("04-turn"))
        // traditional tile faces
        rule.runOnUiThread { vm.setLabelsPref(false); vm.newGame() }
        shot("11-traditional")
        rule.runOnUiThread { vm.setLabelsPref(true) }
    }
}
