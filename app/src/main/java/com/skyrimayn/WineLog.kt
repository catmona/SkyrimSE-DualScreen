package com.skyrimayn

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * Wine's own log, `wine_logs/wine_debug.log` in the app's external files. Debug builds always write it. Release
 * builds write it only when the player turns on Launcher › Advanced › WINE LOG, so a bug report can carry it.
 */
object WineLog {
    const val NAME = "wine_debug.log"
    /** The game before this one: a crash's log survives the restart that follows it. */
    const val PREVIOUS = "wine_debug.prev.log"

    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "wine_logs")

    /** Whether the engine writes Wine's log for this launch. */
    fun wanted(debugBuild: Boolean, setting: String): Boolean = debugBuild || setting == "On"

    /** At launch, before [log] is written again: it becomes [PREVIOUS], replacing the one before. */
    fun keepPrevious(log: File) {
        if (!log.exists()) return
        val previous = File(log.parentFile, PREVIOUS)
        previous.delete()
        if (!log.renameTo(previous)) log.delete()
    }

    /** WINE LOG is off: the logs from games played with it on go, so a report never passes them off as this game's. */
    fun clear(dir: File) {
        File(dir, NAME).delete()
        File(dir, PREVIOUS).delete()
    }

    /** The logs a bug report includes: this game's, then the one before. */
    fun forReport(dir: File): List<File> = listOf(File(dir, NAME), File(dir, PREVIOUS)).filter { it.isFile && it.length() > 0 }

    /** The last [max] bytes of [file], without reading the rest (a long game's log can be large). */
    fun tail(file: File, max: Int): String = RandomAccessFile(file, "r").use { f ->
        val n = minOf(f.length(), max.toLong()).toInt()
        val bytes = ByteArray(n)
        f.seek(f.length() - n)
        f.readFully(bytes)
        bytes.decodeToString()
    }
}
