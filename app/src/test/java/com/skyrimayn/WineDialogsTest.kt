package com.skyrimayn

import android.view.KeyEvent
import com.skyrimayn.WineDialogs.Win
import com.winlator.xserver.XKeycode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pad closes a Windows message box from the game, and only then: Skyrim's own window never counts. */
class WineDialogsTest {
    @After fun clear() = WineDialogs.reset()

    private fun win(cls: String, w: Int, h: Int, mapped: Boolean = true, popup: Boolean = false, kids: List<Win> = emptyList()) =
        Win(cls, mapped, popup, w, h, kids)

    /** Wine's virtual desktop: explorer.exe's window under the root, the programs' windows under it (bottom first). */
    private fun desktop(vararg windows: Win, w: Int = 1280, h: Int = 720) =
        win("", w, h, kids = listOf(win("explorer.exe", w, h, kids = windows.toList())))

    private val skyrim = win("skyrimse.exe", 1280, 720, kids = listOf(win("", 1280, 720)))   // with its Vulkan child
    private val box = win("skyrimse.exe", 262, 86)   // apprentice.dll's "failed to open address library file" (boot 2)

    @Test fun findsTheBoxBeforeSkyrimsWindow() {
        assertEquals(box, WineDialogs.find(desktop(box), 1280, 720))
    }

    @Test fun findsTheBoxOverSkyrimsWindow() {
        assertEquals(box, WineDialogs.find(desktop(skyrim, box), 1280, 720))
        assertEquals(box, WineDialogs.find(win("", 1280, 720, kids = listOf(skyrim, box)), 1280, 720))   // no desktop
    }

    @Test fun skyrimsWindowIsNeverABox() {
        assertNull(WineDialogs.find(desktop(skyrim), 1280, 720))
        assertNull(WineDialogs.find(desktop(box, skyrim), 1280, 720))   // the box is under the game
        val low = win("skyrimse.exe", 640, 360)                        // 360p: the screen is that size too
        assertNull(WineDialogs.find(desktop(low, w = 640, h = 360), 640, 360))
        assertEquals(box, WineDialogs.find(desktop(low, box, w = 640, h = 360), 640, 360))
    }

    @Test fun otherWindowsDontCount() {
        assertNull(WineDialogs.find(desktop(skyrim, win("skyrimse.exe", 262, 86, mapped = false)), 1280, 720))
        assertNull(WineDialogs.find(desktop(skyrim, win("skyrimse.exe", 200, 300, popup = true)), 1280, 720))
        assertNull(WineDialogs.find(desktop(skyrim, win("skyrimse.exe", 1, 1)), 1280, 720))
        assertNull(WineDialogs.find(desktop(win("conhost.exe", 652, 343)), 1280, 720))   // skse64_loader's console
        assertNull(WineDialogs.find(desktop(skyrim, win("steam.exe", 400, 200)), 1280, 720))
        assertNull(WineDialogs.find(desktop(), 1280, 720))
        // Loader errors and Wine's crash dialog do count.
        assertTrue(WineDialogs.find(desktop(win("SKSE64_LOADER.EXE", 300, 120)), 1280, 720) != null)
        assertTrue(WineDialogs.find(desktop(skyrim, win("winedbg.exe", 500, 180)), 1280, 720) != null)
    }

    @Test fun padAAndBPressEnterAndEscapeWhileABoxIsUp() {
        val keys = mutableListOf<String>()
        fun pad(code: Int, down: Boolean, repeat: Int = 0) =
            WineDialogs.onPadKey(code, down, repeat, { keys += "+$it" }, { keys += "-$it" })

        assertFalse(pad(KeyEvent.KEYCODE_BUTTON_A, true))          // no box: the game gets A
        assertFalse(pad(KeyEvent.KEYCODE_BUTTON_A, false))
        assertTrue(keys.isEmpty())

        WineDialogs.setUpForTest(true)
        assertTrue(pad(KeyEvent.KEYCODE_BUTTON_A, true))
        assertTrue(pad(KeyEvent.KEYCODE_BUTTON_A, true, repeat = 1)) // held: no second Enter
        WineDialogs.setUpForTest(false)                             // Enter closed the box
        assertTrue(pad(KeyEvent.KEYCODE_BUTTON_A, false))           // ...and the release still lets go of Enter
        assertEquals(listOf("+${XKeycode.KEY_ENTER}", "-${XKeycode.KEY_ENTER}"), keys)

        keys.clear()
        WineDialogs.setUpForTest(true)
        assertTrue(pad(KeyEvent.KEYCODE_BUTTON_B, true))
        assertTrue(pad(KeyEvent.KEYCODE_BUTTON_B, false))
        assertFalse(pad(KeyEvent.KEYCODE_BUTTON_X, true))           // other buttons go to the game
        assertFalse(pad(KeyEvent.KEYCODE_BUTTON_A, false))          // a release we never pressed goes to the game
        assertEquals(listOf("+${XKeycode.KEY_ESC}", "-${XKeycode.KEY_ESC}"), keys)
    }
}
