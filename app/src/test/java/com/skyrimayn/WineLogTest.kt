package com.skyrimayn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Wine's log: written when wanted, the previous game's kept, and only the end read into a report. */
class WineLogTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun debugBuildsAlwaysLog_releaseOnlyWhenTurnedOn() {
        assertTrue(WineLog.wanted(debugBuild = true, setting = "Off"))
        assertTrue(WineLog.wanted(debugBuild = false, setting = "On"))
        assertFalse(WineLog.wanted(debugBuild = false, setting = "Off"))
    }

    @Test fun theLastGamesLogBecomesThePreviousOne() {
        val dir = tmp.newFolder("wine_logs")
        val log = File(dir, WineLog.NAME).apply { writeText("this game") }
        File(dir, WineLog.PREVIOUS).writeText("two games ago")
        WineLog.keepPrevious(log)
        assertFalse(log.exists())
        assertEquals("this game", File(dir, WineLog.PREVIOUS).readText())
    }

    @Test fun noLogYet_leavesThePreviousOneAlone() {
        val dir = tmp.newFolder("wine_logs")
        File(dir, WineLog.PREVIOUS).writeText("kept")
        WineLog.keepPrevious(File(dir, WineLog.NAME))
        assertEquals("kept", File(dir, WineLog.PREVIOUS).readText())
    }

    @Test fun aReportTakesThisGamesLogFirst_andSkipsEmptyOnes() {
        val dir = tmp.newFolder("wine_logs")
        assertTrue(WineLog.forReport(dir).isEmpty())
        File(dir, WineLog.PREVIOUS).writeText("before")
        File(dir, WineLog.NAME).writeText("")
        assertEquals(listOf(WineLog.PREVIOUS), WineLog.forReport(dir).map { it.name })
        File(dir, WineLog.NAME).writeText("now")
        assertEquals(listOf(WineLog.NAME, WineLog.PREVIOUS), WineLog.forReport(dir).map { it.name })
    }

    @Test fun tailReadsOnlyTheEnd() {
        val f = tmp.newFile("big.log").apply { writeText("a".repeat(1000) + "THE END") }
        assertEquals("THE END", WineLog.tail(f, 7))
        val small = tmp.newFile("small.log").apply { writeText("short") }
        assertEquals("short", WineLog.tail(small, 1000))
    }
}
