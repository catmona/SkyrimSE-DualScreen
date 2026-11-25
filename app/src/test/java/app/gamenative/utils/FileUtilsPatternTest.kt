package app.gamenative.utils

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsPatternTest {

    private fun dirWith(vararg fileNames: String): Path {
        val dir = Files.createTempDirectory("fileutils-pattern")
        fileNames.forEach { Files.createFile(dir.resolve(it)) }
        return dir
    }

    private fun matches(dir: Path, pattern: String): List<String> =
        FileUtils.findFiles(dir, pattern).map { it.name }.toList().sorted()

    private fun matchesRecursive(dir: Path, pattern: String): List<String> =
        FileUtils.findFilesRecursive(dir, pattern, 5).map { it.name }.toList().sorted()

    @Test
    fun recursiveSearchLeavesOutSkippedFolders() {
        val dir = Files.createTempDirectory("fileutils-skip")
        Files.createDirectories(dir.resolve("_CommonRedist/DirectX"))
        Files.createFile(dir.resolve("_CommonRedist/DirectX/DXSETUP.exe"))
        Files.createDirectories(dir.resolve("Data/SomeMod"))
        Files.createFile(dir.resolve("Data/SomeMod/DXSETUP.exe"))

        val all = FileUtils.findFilesRecursive(dir, "DXSETUP.exe", 5).map { dir.relativize(it).toString().replace('\\', '/') }.toList().sorted()
        assertEquals(listOf("Data/SomeMod/DXSETUP.exe", "_CommonRedist/DirectX/DXSETUP.exe"), all)

        val skipped = FileUtils.findFilesRecursive(dir, "DXSETUP.exe", 5, skipDirectory = { it.parent == dir && it.name == "Data" })
            .map { dir.relativize(it).toString().replace('\\', '/') }.toList()
        assertEquals(listOf("_CommonRedist/DirectX/DXSETUP.exe"), skipped)
    }

    @Test
    fun questionMarkStandsForOneCharacter() {
        val dir = dirWith("FFXII_000", "FFXII_001", "FFXII_00", "GameSetting.ini")

        assertEquals(listOf("FFXII_000", "FFXII_001"), matches(dir, "FFXII_???"))
        assertEquals(listOf("FFXII_000", "FFXII_001"), matchesRecursive(dir, "FFXII_???"))
    }

    @Test
    fun matchingStaysLenientAboutTrailingCharacters() {
        val dir = dirWith("FFXII_0000", "save.sav.bak")

        assertEquals(listOf("FFXII_0000"), matches(dir, "FFXII_???"))
        assertEquals(listOf("save.sav.bak"), matches(dir, "*.sav"))
    }

    @Test
    fun starStillMatchesAnyRun() {
        val dir = dirWith("save.sav", "Profile1.sav", "Profile22.sav", "notes.txt")

        assertEquals(listOf("Profile1.sav", "Profile22.sav", "save.sav"), matches(dir, "*.sav"))
        assertEquals(listOf("Profile1.sav", "Profile22.sav"), matches(dir, "Profile*.sav"))
        assertEquals(listOf("Profile1.sav", "Profile22.sav", "notes.txt", "save.sav").sorted(), matches(dir, "*"))
    }

    @Test
    fun literalPartsAreNotTreatedAsRegex() {
        val dir = dirWith("save.sav", "saveXsav")

        assertEquals(listOf("save.sav"), matches(dir, "save.sav"))
    }

    @Test
    fun matchingIgnoresCase() {
        val dir = dirWith("FFXII_000")

        assertEquals(listOf("FFXII_000"), matches(dir, "ffxii_???"))
    }

    @Test
    fun recursiveMatchFindsNestedFiles() {
        val dir = dirWith("FFXII_000")
        val nested = Files.createDirectory(dir.resolve("backup"))
        Files.createFile(nested.resolve("FFXII_007"))

        assertEquals(listOf("FFXII_000", "FFXII_007"), matchesRecursive(dir, "FFXII_???"))
    }
}
