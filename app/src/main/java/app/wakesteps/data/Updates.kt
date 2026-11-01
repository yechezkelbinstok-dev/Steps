package app.wakesteps.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A version of the app published on GitHub, with its APK and what's new in it. */
data class AppRelease(val versionName: String, val versionCode: Long, val apkUrl: String, val size: Long, val notes: String)

/**
 * Updates from inside the app: each version is published as a release of the app's repository
 * (by its GitHub Actions build), so the app finds the newest, downloads its APK and hands it to
 * Android to install over itself. The first time, Android asks to let the app install apps.
 */
object Updates {

    private const val LATEST = "https://api.github.com/repos/yechezkelbinstok-dev/Steps/releases/latest"

    /** What [install] tells the app when Android has an answer. */
    const val ACTION_INSTALL_STATUS = "app.wakesteps.INSTALL_STATUS"

    /** 1.17 → 11700, 1.15.1 → 11501: the app's versionCode for a version name. */
    fun versionCode(name: String): Long? {
        val parts = name.split('.').map { it.toLongOrNull() ?: return null }
        if (parts.size !in 2..3) return null
        return parts[0] * 10_000 + parts[1] * 100 + (parts.getOrNull(2) ?: 0)
    }

    fun installedCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** The newest version published, if it can be read. */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** A GitHub release ("v1.17", with an .apk attached), or null if it isn't one of the app's. */
    internal fun parseRelease(json: String): AppRelease? {
        val release = JSONObject(json)
        val name = release.optString("tag_name").removePrefix("v")
        val code = versionCode(name) ?: return null
        val assets = release.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
            ?: return null
        return AppRelease(name, code, apk.getString("browser_download_url"), apk.optLong("size"), release.optString("body").trim())
    }

    /** Downloads [release]'s APK into the cache, reporting how far along it is (0 to 1). */
    suspend fun download(context: Context, release: AppRelease, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "awake-steps-${release.versionName}.apk")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            check(conn.responseCode == 200) { "The download didn't start (${conn.responseCode})" }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = -1
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        // Once a percent, not for every read.
                        val percent = if (total > 0) (done * 100 / total).toInt().coerceAtMost(100) else -1
                        if (percent != shown) {
                            shown = percent
                            progress(maxOf(percent, 0) / 100f)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        file
    }

    /** Whether Android lets the app install apps (its own updates); asked once, in Settings. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system page for letting the app install its updates. */
    fun allowInstallsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /**
     * Hands [apk] to Android to install over the app. Android answers through the main activity
     * ([ACTION_INSTALL_STATUS]): asking the user to confirm (always the first time), or the result.
     * Once the app has installed itself, Android 12 and later let it update without asking.
     */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("awake-steps.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
                .setClassName(context.packageName, "app.wakesteps.ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, id, status, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }
}
