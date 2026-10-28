package pl.wolter.ostoja.desktop

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hasło w schowku ma formaty, po których Windows nie zapisuje go w historii (Win+V) ani w chmurze.
 * Zmienia prawdziwy schowek, więc działa tylko z OSTOJA_CLIP_TEST=1.
 */
class ClipboardTest {
    @Test fun secretIsExcludedFromHistoryAndCloud() {
        assumeTrue(System.getenv("OSTOJA_CLIP_TEST") == "1")
        Clipboard.copy("ostoja-test", 30)
        val ps = "Add-Type -AssemblyName System.Windows.Forms; [Windows.Forms.Clipboard]::GetDataObject().GetFormats() -join ','; [Windows.Forms.Clipboard]::GetText()"
        val out = ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", ps).redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        Clipboard.copy("", 0)
        println(out)
        listOf("ExcludeClipboardContentFromMonitorProcessing", "CanIncludeInClipboardHistory", "CanUploadToCloudClipboard", "ostoja-test")
            .forEach { assertTrue(it, out.contains(it)) }
    }
}
