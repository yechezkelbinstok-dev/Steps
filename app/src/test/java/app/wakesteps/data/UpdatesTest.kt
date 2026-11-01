package app.wakesteps.data

import app.wakesteps.ui.noteBullets
import app.wakesteps.widget.RenderTestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) // for Android's org.json
@Config(sdk = [35], application = RenderTestApp::class)
class UpdatesTest {

    @Test
    fun versionNamesToCodes_asTheAppNumbersThem() {
        assertEquals(11700L, Updates.versionCode("1.17"))
        assertEquals(11501L, Updates.versionCode("1.15.1"))
        assertEquals(20000L, Updates.versionCode("2.0"))
        assertNull(Updates.versionCode("latest"))
        assertNull(Updates.versionCode("1"))
    }

    @Test
    fun aReleaseWithItsApk() {
        val json = """
            {"tag_name": "v1.17", "body": "- Updates from inside the app.\n  No more file manager.\n- Naps open with their stages.",
             "assets": [{"name": "awake-steps.apk", "size": 32500000,
                         "browser_download_url": "https://github.com/yechezkelbinstok-dev/Steps/releases/download/v1.17/awake-steps.apk"}]}
        """.trimIndent()
        val release = Updates.parseRelease(json)!!
        assertEquals("1.17", release.versionName)
        assertEquals(11700L, release.versionCode)
        assertEquals(32500000L, release.size)
        assertEquals("https://github.com/yechezkelbinstok-dev/Steps/releases/download/v1.17/awake-steps.apk", release.apkUrl)
        assertEquals(
            listOf("Updates from inside the app. No more file manager.", "Naps open with their stages."),
            noteBullets(release.notes),
        )
    }

    @Test
    fun notOneOfTheAppsReleases() {
        assertNull(Updates.parseRelease("""{"tag_name": "nightly", "assets": []}"""))
        assertNull(Updates.parseRelease("""{"tag_name": "v1.17", "assets": [{"name": "notes.txt", "browser_download_url": "x"}]}"""))
    }
}
