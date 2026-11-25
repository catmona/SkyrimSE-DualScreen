package com.skyrimayn

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** An imported mod list's load order and active plugins reach plugins.txt, and later changes are the player's. */
@RunWith(RobolectricTestRunner::class)
class LoadOrderImportTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var game: File
    private val importedFile get() = File(ctx.filesDir, "skyrimayn/imported-plugins.txt")

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "skyrimayn").deleteRecursively()
        game = tmp.newFolder("game")
        File(game, "Data").mkdirs()
    }

    /** A plugin file with just a TES4 header. */
    private fun plugin(name: String) {
        val head = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        head.put("TES4".toByteArray()).putInt(0).putInt(0)
        File(game, "Data/$name").writeBytes(head.array())
    }

    /** What a mod's deploy does: its plugins appear, then the load order is loaded, sorted and saved. */
    private suspend fun deploy(vararg names: String) {
        names.forEach(::plugin)
        LoadOrder.load(ctx, game.path)
        LoadOrder.autoSort(game.path)
        LoadOrder.save(ctx)
    }

    private fun ours() = File(ctx.filesDir, "skyrimayn/plugins.txt").readLines().filter { !it.startsWith("#") }

    @Test fun importedOrderAndActiveFlagsReachPlugins() = runBlocking {
        // What the deploys leave behind: the order the mods arrived in, everything active.
        deploy("Skyrim.esm", "Update.esm", "Alpha.esp", "Beta.esp", "Gamma.esp", "Zulu.esp")
        assertEquals(listOf("*Alpha.esp", "*Beta.esp", "*Gamma.esp", "*Zulu.esp"), ours())

        LoadOrder.applyImported(ctx, game.path, listOf("Zulu.esp" to true, "Gamma.esp" to false, "Alpha.esp" to true, "Beta.esp" to true), modsToCome = false)

        assertEquals(listOf("*Zulu.esp", "Gamma.esp", "*Alpha.esp", "*Beta.esp"), ours())
        assertFalse(importedFile.exists())
        // And it stays that way on the next load (the Mods screen, or Play).
        LoadOrder.load(ctx, game.path)
        assertEquals(listOf("Zulu.esp", "Gamma.esp", "Alpha.esp", "Beta.esp"), LoadOrder.plugins.value.filter { !it.locked }.map { it.name })
    }

    @Test fun pluginsTheListDoesntNameKeepTheirPlace() = runBlocking {
        deploy("Skyrim.esm", "A.esp", "B.esp", "C.esp", "D.esp")
        LoadOrder.applyImported(ctx, game.path, listOf("D.esp" to true, "B.esp" to false), modsToCome = false)
        assertEquals(listOf("*A.esp", "*D.esp", "*C.esp", "B.esp"), ours())
    }

    /** A Nexus list's missing mods download after its order is applied. */
    @Test fun pluginsStillToArriveTakeTheirPlaceFromTheList() = runBlocking {
        deploy("Skyrim.esm", "Alpha.esp", "Delta.esp")
        LoadOrder.applyImported(ctx, game.path, listOf("Delta.esp" to true, "Beta.esp" to false, "Alpha.esp" to true, "Gamma.esp" to true), modsToCome = true)
        assertEquals(listOf("*Delta.esp", "*Alpha.esp"), ours())
        assertTrue(importedFile.exists())

        deploy("Beta.esp")
        assertEquals(listOf("*Delta.esp", "Beta.esp", "*Alpha.esp"), ours())
        deploy("Gamma.esp", "Extra.esp")
        assertEquals(listOf("*Delta.esp", "Beta.esp", "*Alpha.esp", "*Gamma.esp", "*Extra.esp"), ours())
        assertFalse(importedFile.exists())
    }

    /** An MO2 list names plugins that never reach the device (Creation Club files, mods left out); a folder import keeps nothing for them. */
    @Test fun aFolderImportKeepsNoListForPluginsThatNeverArrive() = runBlocking {
        deploy("Skyrim.esm", "Alpha.esp", "Beta.esp")
        LoadOrder.applyImported(ctx, game.path, listOf("ccBGSSSE001-Fish.esm" to true, "Beta.esp" to true, "Alpha.esp" to false), modsToCome = false)
        assertEquals(listOf("*Beta.esp", "Alpha.esp"), ours())
        assertFalse(importedFile.exists())

        deploy("ccBGSSSE001-Fish.esm")
        assertEquals(listOf("*ccBGSSSE001-Fish.esm", "*Beta.esp", "Alpha.esp"), ours())
    }

    /** Masters keep the list's order among themselves; auto sort only keeps them ahead of plugins. */
    @Test fun mastersKeepTheListsOrder() = runBlocking {
        deploy("Skyrim.esm", "Alpha.esm", "Beta.esm", "Zulu.esp")
        LoadOrder.applyImported(ctx, game.path, listOf("Beta.esm" to true, "Zulu.esp" to true, "Alpha.esm" to true), modsToCome = false)
        assertEquals(listOf("*Beta.esm", "*Alpha.esm", "*Zulu.esp"), ours())
    }

    @Test fun thePlayersChangesStandWhileTheListWaitsForMods() = runBlocking {
        deploy("Skyrim.esm", "Alpha.esp", "Beta.esp")
        LoadOrder.applyImported(ctx, game.path, listOf("Alpha.esp" to true, "Beta.esp" to true, "Later.esp" to true), modsToCome = true)
        LoadOrder.move("Beta.esp", -1)
        LoadOrder.toggle("Alpha.esp")
        LoadOrder.save(ctx)
        assertTrue(importedFile.exists())

        LoadOrder.load(ctx, game.path)
        assertEquals(listOf("*Beta.esp", "Alpha.esp"), ours())
        assertEquals(listOf("Beta.esp" to true, "Alpha.esp" to false), LoadOrder.plugins.value.filter { !it.locked }.map { it.name to it.active })
    }
}
