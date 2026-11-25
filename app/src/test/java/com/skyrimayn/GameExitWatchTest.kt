package com.skyrimayn

import com.skyrimayn.GameExitWatch.GRACE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When SkyrimSE.exe is judged to have ended on its own while the game was on screen. */
class GameExitWatchTest {
    @Test fun aRunningGameIsNeverJudgedDead() {
        val s = GameExitWatch.step(goneSince = 1_000L, running = true, closing = false, now = 99_000L)
        assertEquals(GameExitWatch.Step(0L, false), s)
    }

    @Test fun goneOnItsOwn_diesOnlyAfterTheGracePeriod() {
        val first = GameExitWatch.step(0L, running = false, closing = false, now = 10_000L)
        assertEquals(GameExitWatch.Step(10_000L, false), first)
        assertFalse(GameExitWatch.step(first.goneSince, false, false, 10_000L + GRACE_MS - 1).died)
        assertTrue(GameExitWatch.step(first.goneSince, false, false, 10_000L + GRACE_MS).died)
    }

    @Test fun aQuitOrTheAppsOwnClose_isNotADeath() {
        assertFalse(GameExitWatch.step(10_000L, running = false, closing = true, now = 60_000L).died)
    }

    @Test fun backAfterABriefGap_startsTheGraceAgain() {
        val back = GameExitWatch.step(10_000L, running = true, closing = false, now = 12_000L)
        val goneAgain = GameExitWatch.step(back.goneSince, running = false, closing = false, now = 14_000L)
        assertEquals(GameExitWatch.Step(14_000L, false), goneAgain)
    }
}
