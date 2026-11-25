package com.skyrimayn

/**
 * Once Skyrim's window is up, SkyrimSE.exe can end on its own while Wine's desktop keeps running: the screen
 * freezes and the engine reports nothing, because the game isn't a process the app can wait on. [SkyrimEngine]
 * checks /proc every [CHECK_MS]; this decides, from what it saw, when the game has really gone.
 */
object GameExitWatch {
    const val CHECK_MS = 2_000L
    /** How long the game must stay gone: room for the plugin's quit message, or the app's own close, to arrive. */
    const val GRACE_MS = 3_000L

    /** What one check found. [goneSince]: when the game was first seen gone (0 while it runs). */
    data class Step(val goneSince: Long, val died: Boolean)

    /**
     * [running]: SkyrimSE.exe is in /proc. [closing]: the app or the game's own Quit is closing it, so its end
     * is expected.
     */
    fun step(goneSince: Long, running: Boolean, closing: Boolean, now: Long): Step = when {
        running -> Step(0L, false)
        goneSince == 0L -> Step(now, false)
        else -> Step(goneSince, !closing && now - goneSince >= GRACE_MS)
    }
}
