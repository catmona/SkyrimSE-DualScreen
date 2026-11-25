package com.skyrimayn

import android.view.KeyEvent
import com.winlator.xserver.Window
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * A Windows message box from Skyrim, SKSE or a mod (CommonLibSSE's "failed to open address library file", for
 * one) waits for Enter, Escape or a click. The pad reaches Wine only as XInput state, which a message box never
 * reads, so nothing on the Thor could close it and the launch sat there. While one is on screen, pad A presses
 * Enter (the box's default button) and B presses Escape, and our loading screen steps aside so it can be read.
 */
object WineDialogs {
    /** What the check needs from an X window, kept apart from [Window] so it can be tested without an X server. */
    data class Win(
        val className: String,
        val mapped: Boolean,
        val overrideRedirect: Boolean,
        val width: Int,
        val height: Int,
        val children: List<Win> = emptyList(),
    )

    /** The programs whose message boxes the pad answers: the game, its loader, and Wine's crash dialog. */
    private val programs = setOf("skyrimse.exe", "skse64_loader.exe", "winedbg.exe")

    /**
     * The message box on top, or null. Wine gives every top-level window its program's WM_CLASS (in a virtual
     * desktop they sit under explorer.exe's window), and Skyrim's own window always fills the X screen
     * (borderless at the launcher resolution, which is also the screen size). So a message box is the topmost
     * Wine window when it belongs to one of [programs] and falls short of the screen by more than a tenth.
     * Its window type doesn't help: Wine marks only owned popups as dialogs, and a plugin's
     * MessageBox(NULL, ...) has no owner.
     */
    fun find(root: Win, screenW: Int, screenH: Int): Win? {
        val top = topmost(root) ?: return null
        if (top.className.lowercase() !in programs) return null
        return top.takeIf { it.width * 10 < screenW * 9 || it.height * 10 < screenH * 9 }
    }

    /** The topmost mapped Wine top-level window. 1-pixel windows don't count, nor popups (override-redirect) unless [popups]. */
    private fun topmost(w: Win, popups: Boolean = false): Win? {
        for (c in w.children.asReversed()) {
            if (!c.mapped || (c.overrideRedirect && !popups) || c.width <= 1 || c.height <= 1) continue
            if (c.className.isEmpty() || c.className.equals("explorer.exe", true)) {
                topmost(c, popups)?.let { return it }
                continue
            }
            return c
        }
        return null
    }

    private fun snapshot(w: Window): Win = Win(
        w.className, w.attributes.isMapped, w.attributes.isOverrideRedirect, w.width.toInt(), w.height.toInt(),
        w.children.map(::snapshot),
    )

    private val _up = MutableStateFlow(false)
    /** True while a message box from the game is the top window. */
    val up: StateFlow<Boolean> = _up

    /** Looks again after a window is mapped, unmapped, moved, resized or restacked (on the X server's thread). */
    fun refresh(xs: XServer) {
        val root = runCatching { snapshot(xs.windowManager.rootWindow) }
            .onFailure { Timber.w(it, "SkyrimAYN: couldn't look for a message box") }.getOrNull() ?: return
        // The top window as Wine left it, popups too, when it changes: a Thor test shows what this check saw.
        val top = topmost(root, popups = true)?.let { "${it.className} ${it.width}x${it.height}" + if (it.overrideRedirect) " popup" else "" }
        if (top != lastTop) { lastTop = top; Timber.i("SkyrimAYN: top window ${top ?: "none"}") }
        val box = find(root, xs.screenInfo.width.toInt(), xs.screenInfo.height.toInt())
        if ((box != null) == _up.value) return
        if (box != null) Timber.i("SkyrimAYN: message box from ${box.className} (${box.width}x${box.height}): pad A = Enter, B = Escape")
        else Timber.i("SkyrimAYN: message box closed")
        _up.value = box != null
    }

    private var lastTop: String? = null

    /** A new X server, or the game ended: nothing from the last one is on screen. */
    fun reset() {
        _up.value = false
        lastTop = null
        held.clear()
    }

    /** Pad buttons that pressed a key here, so their release lets go of the same key even after the box closed. */
    private val held = ConcurrentHashMap<Int, XKeycode>()

    /**
     * Pad A / B while a message box is up: presses Enter / Escape instead of reaching the game. True when the
     * event was used (the game doesn't see it).
     */
    fun onPadKey(keyCode: Int, down: Boolean, repeat: Int, press: (XKeycode) -> Unit, release: (XKeycode) -> Unit): Boolean {
        val key = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> XKeycode.KEY_ENTER
            KeyEvent.KEYCODE_BUTTON_B -> XKeycode.KEY_ESC
            else -> return false
        }
        if (!down) {
            release(held.remove(keyCode) ?: return false)
            return true
        }
        if (!_up.value && !held.containsKey(keyCode)) return false
        if (repeat == 0 && held.putIfAbsent(keyCode, key) == null) press(key)
        return true
    }

    internal fun setUpForTest(up: Boolean) { _up.value = up }
}
