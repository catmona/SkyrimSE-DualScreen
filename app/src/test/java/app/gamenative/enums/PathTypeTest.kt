package app.gamenative.enums

import com.winlator.container.Container
import com.winlator.xenvironment.ImageFs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PathTypeTest {

    // Production builds these with java.nio Paths, so the separator follows the OS; compare with "/" everywhere.
    private val containerRoot = File("/data/test/container").absolutePath.replace(File.separatorChar, '/')
    private val container = Container("test-id").apply { setRootDir(File(containerRoot)) }
    private val appId = 220
    private val accountId = 76561198025127569L

    private fun PathType.absPath(container: Container = this@PathTypeTest.container) =
        toAbsPath(container, appId, accountId).replace(File.separatorChar, '/')

    @Test
    fun `SteamUserData resolves to Steam userdata remote directory`() {
        assertEquals(
            "$containerRoot/.wine/drive_c/Program Files (x86)/Steam/userdata/$accountId/$appId/remote/",
            PathType.SteamUserData.absPath(),
        )
    }

    @Test
    fun `WinMyDocuments resolves to Documents folder in wine prefix`() {
        assertEquals(
            "$containerRoot/.wine/drive_c/users/${ImageFs.USER}/Documents/",
            PathType.WinMyDocuments.absPath(),
        )
    }

    @Test
    fun `WinAppDataLocal resolves to AppData Local in wine prefix`() {
        assertEquals(
            "$containerRoot/.wine/drive_c/users/${ImageFs.USER}/AppData/Local/",
            PathType.WinAppDataLocal.absPath(),
        )
    }

    @Test
    fun `WinAppDataLocalLow resolves to AppData LocalLow in wine prefix`() {
        assertEquals(
            "$containerRoot/.wine/drive_c/users/${ImageFs.USER}/AppData/LocalLow/",
            PathType.WinAppDataLocalLow.absPath(),
        )
    }

    @Test
    fun `WinAppDataRoaming resolves to AppData Roaming in wine prefix`() {
        assertEquals(
            "$containerRoot/.wine/drive_c/users/${ImageFs.USER}/AppData/Roaming/",
            PathType.WinAppDataRoaming.absPath(),
        )
    }

    @Test
    fun `WinSavedGames resolves to Saved Games folder in wine prefix`() {
        assertEquals(
            "$containerRoot/.wine/drive_c/users/${ImageFs.USER}/Saved Games/",
            PathType.WinSavedGames.absPath(),
        )
    }

    @Test
    fun `WinProgramData resolves to ProgramData in wine prefix`() {
        assertEquals(
            "$containerRoot/.wine/drive_c/ProgramData/",
            PathType.WinProgramData.absPath(),
        )
    }

    @Test
    fun `Root resolves to user home in wine prefix`() {
        assertEquals(
            "$containerRoot/.wine/drive_c/users/${ImageFs.USER}/",
            PathType.Root.absPath(),
        )
    }

    @Test
    fun `paths are rooted in the container not a global shared directory`() {
        val otherRoot = File("/data/test/other-container").absolutePath.replace(File.separatorChar, '/')
        val otherContainer = Container("other-id").apply { setRootDir(File(otherRoot)) }
        val path1 = PathType.WinAppDataRoaming.absPath()
        val path2 = PathType.WinAppDataRoaming.absPath(otherContainer)
        assertTrue(path1.startsWith(containerRoot))
        assertTrue(path2.startsWith(otherRoot))
        assertNotEquals(path1, path2)
    }

    @Test
    fun `isWindows is true for Windows path types`() {
        listOf(
            PathType.GameInstall,
            PathType.SteamUserData,
            PathType.WinMyDocuments,
            PathType.WinAppDataLocal,
            PathType.WinAppDataLocalLow,
            PathType.WinAppDataRoaming,
            PathType.WinSavedGames,
            PathType.WinProgramData,
            PathType.Root,
        ).forEach { assertTrue("${it.name} should be isWindows", it.isWindows) }
    }

    @Test
    fun `isWindows is false for non-Windows path types`() {
        listOf(
            PathType.LinuxHome,
            PathType.LinuxXdgDataHome,
            PathType.LinuxXdgConfigHome,
            PathType.MacHome,
            PathType.MacAppSupport,
            PathType.None,
        ).forEach { assertFalse("${it.name} should not be isWindows", it.isWindows) }
    }
}
