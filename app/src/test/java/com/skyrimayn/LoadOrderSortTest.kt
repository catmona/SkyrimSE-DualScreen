package com.skyrimayn

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** AUTO SORT keeps .esl files among the masters, where the game loads them, even without the master flag. */
@RunWith(RobolectricTestRunner::class)
class LoadOrderSortTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var game: File

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "skyrimayn").deleteRecursively()
        game = tmp.newFolder("game")
        File(game, "Data").mkdirs()
    }

    /** A plugin file with just a TES4 header: [flags] 0x1 is the master flag, 0x200 the light flag. */
    private fun plugin(name: String, flags: Int = 0) {
        val head = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        head.put("TES4".toByteArray()).putInt(0).putInt(flags)
        File(game, "Data/$name").writeBytes(head.array())
    }

    private fun order() = LoadOrder.plugins.value.filter { !it.locked }.map { it.name }

    @Test fun eslWithoutTheMasterFlagStaysWithTheMasters() = runBlocking {
        plugin("Skyrim.esm", 0x1)
        plugin("Unofficial Skyrim Special Edition Patch.esp", 0x1)   // ESM-flagged .esp
        plugin("TrueHUD.esl")                                         // .esl, no flags in the header (like TrueHUD's)
        plugin("FlaggedLight.esl", 0x201)
        plugin("Plain.esp")
        File(ctx.filesDir, "skyrimayn").mkdirs()
        File(ctx.filesDir, "skyrimayn/plugins.txt").writeText(
            listOf("*Unofficial Skyrim Special Edition Patch.esp", "*TrueHUD.esl", "*FlaggedLight.esl", "*Plain.esp").joinToString("\r\n"),
        )
        LoadOrder.load(ctx, game.path)
        LoadOrder.autoSort(game.path)
        assertEquals(listOf("Unofficial Skyrim Special Edition Patch.esp", "TrueHUD.esl", "FlaggedLight.esl", "Plain.esp"), order())
    }

    @Test fun newEslJoinsTheNewMastersFirst() = runBlocking {
        plugin("Skyrim.esm", 0x1)
        plugin("B.esp")
        plugin("A.esl")
        plugin("C.esm", 0x1)
        LoadOrder.load(ctx, game.path)
        assertEquals(listOf("A.esl", "C.esm", "B.esp"), order())
    }
}
