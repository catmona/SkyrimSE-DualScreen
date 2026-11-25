package app.gamenative.testutil

import org.junit.Assume.assumeFalse

/**
 * Skips the calling test when the JVM runs on Windows. The app targets Android (Linux), and some tests rely on things a Windows
 * file system can't do: ':' in file names (Wine's dosdevices/c:), the "unix" attribute view, symlinks, or renaming over a file.
 */
fun assumePosixFileSystem(reason: String) {
    assumeFalse("Needs a POSIX file system ($reason)", System.getProperty("os.name").startsWith("Windows"))
}
