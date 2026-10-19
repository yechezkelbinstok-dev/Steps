package app.wakesteps.data

import androidx.health.connect.client.HealthConnectClient
import app.wakesteps.WakeStepsApp
import app.wakesteps.core.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A plain-text snapshot of the app's state plus a live read from each source, side by side, so a
 * freshness problem can be pinned down: if Google Health's newest step is far behind Health
 * Connect's (or behind now), the delay is on Google's upload side, not in the app.
 *
 * It's meant to be copied out of Settings and shared. It reads only this app's own health data;
 * there's nothing here that isn't already on the Today screen or in the sync log.
 */
object Diagnostics {

    /** How long one read may take before the report gives up on it and moves on. */
    private const val PROBE_MS = 10_000L

    suspend fun collect(app: WakeStepsApp): String {
        val now = Instant.now()
        val settings = probe { app.prefs.currentSettings() }.getOrDefault(AppSettings())
        return buildString {
            appendLine("== Awake Steps diagnostics ==")
            appendLine(Format.day(now) + ", " + Format.time(now) + "  (" + ZoneId.systemDefault() + ")")

            appInfo(app)
            settingsInfo(settings)
            val window = healthConnect(app, settings, now)
            googleHealth(app, settings, window, now)
            syncLog(app)
        }
    }

    /** The day window the live reads use: the current awake day, or the last 18 h as a fallback. */
    private data class Window(val start: Instant, val end: Instant)

    private fun StringBuilder.appInfo(app: WakeStepsApp) {
        section("App")
        val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        line("Version", info?.let { "${it.versionName} (${it.longVersionCode})" })
        line("Android", "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        line("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        line("Signing SHA-1", runCatching { app.googleHealth.signingSha1() }.getOrNull())
    }

    private fun StringBuilder.settingsInfo(s: AppSettings) {
        section("Settings")
        line("Goal", Format.number(s.goal.toLong()))
        line("Data source", s.sourcePackage ?: "all sources (Health Connect priority)")
        line("Google Health", if (s.googleHealth) "on" else "off")
        line("Calories", s.calorieMode.name.lowercase())
        line("Distance unit", s.distanceUnit.name.lowercase())
        line("Live mode", if (s.liveMode) "on" else "off")
    }

    private suspend fun StringBuilder.healthConnect(app: WakeStepsApp, s: AppSettings, now: Instant): Window {
        section("Health Connect")
        val health = app.health
        val status = runCatching { health.sdkStatus() }.getOrNull()
        line(
            "Status",
            when (status) {
                HealthConnectClient.SDK_AVAILABLE -> "available"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update required"
                else -> "not available"
            },
        )
        val granted = probe { health.grantedPermissions() }.getOrDefault(emptySet())
        val canRead = status == HealthConnectClient.SDK_AVAILABLE && granted.containsAll(HealthSource.CORE_PERMISSIONS)
        line("Steps + sleep", yesNo(granted.containsAll(HealthSource.CORE_PERMISSIONS)))
        line("Total calories", yesNo(HealthSource.TOTAL_CALORIES in granted))
        line("Active calories", yesNo(HealthSource.ACTIVE_CALORIES in granted))
        line("Distance", yesNo(HealthSource.DISTANCE in granted))
        val bgSupported = runCatching { health.backgroundReadSupported() }.getOrDefault(false)
        line("Background read", if (bgSupported) yesNo(HealthSource.BACKGROUND_PERMISSION in granted) + " (supported)" else "not supported")

        // The awake-day window the app is using right now (the same computation as Today).
        val today = if (canRead) probe { app.engine.today(s, includeHourly = false) } else null
        today?.exceptionOrNull()?.let { line("Day computation", "failed — ${reason(it)}") }
        val day = today?.getOrNull()?.window
        val window = day?.let { Window(it.start, it.endOr(now)) } ?: Window(now.minus(Duration.ofHours(18)), now)
        line(
            "Day window",
            if (day == null) "last 18 h (couldn't work out the awake day)"
            else "${Format.time(window.start)} → ${if (day.isOngoing) "now" else Format.time(window.end)}",
        )

        val snapshot = probe { app.prefs.currentSnapshot() }.getOrNull()
        line("Widget shows", snapshot?.let { Format.number(it.totals.steps) })
        line("Snapshot updated", snapshot?.updatedAt?.let { Format.ago(it, now) })
        line("Tracker data up to", snapshot?.latestDataAt?.let { at(it, now) } ?: "unknown")
        snapshot?.error?.let { line("Last refresh error", it) }

        if (!canRead) {
            appendLine("  live read: skipped (no access to Health Connect)")
            return window
        }
        // A fresh read straight from Health Connect for the same window.
        val totals = probe { health.totals(window.start, window.end, s.sourcePackage, granted, s.calorieMode) }
        line("  live read", totals.fold({ describe(it, s) }, { "failed — ${reason(it)}" }))
        val latest = probe { health.latestStepTime(now.minus(Duration.ofDays(2)), now, s.sourcePackage) }
        line("  newest step", latest.fold({ it?.let { t -> at(t, now) } ?: "none in 2 days" }, { "failed — ${reason(it)}" }))
        return window
    }

    private suspend fun StringBuilder.googleHealth(app: WakeStepsApp, s: AppSettings, window: Window, now: Instant) {
        section("Google Health")
        if (!s.googleHealth) {
            appendLine("Off — the app is reading Health Connect only.")
            return
        }
        val google = app.googleHealth
        val token = probe { google.token() }.getOrNull()
        line("Signed in", if (token != null) "yes" else "no")
        if (token == null) {
            appendLine("Not signed in right now — reconnect in Settings → Google Health.")
            line("Last error", probe { app.prefs.googleHealthErrorNow() }.getOrNull())
            return
        }
        line("Account linked", probe { google.checkLinked(token) }.fold({ "yes" }, { reason(it) }))
        val totals = probe { google.totals(token, window.start, window.end) }
        line("  live read", totals.fold({ describe(it, s) }, { "failed — ${reason(it)}" }))
        val latest = probe { google.latestStepTime(token, now.minus(Duration.ofHours(6)), now) }
        line("  newest step", latest.fold({ it?.let { t -> at(t, now) } ?: "none in the last 6 h" }, { "failed — ${reason(it)}" }))
        line("Last error", probe { app.prefs.googleHealthErrorNow() }.getOrNull())
    }

    private suspend fun StringBuilder.syncLog(app: WakeStepsApp) {
        section("Sync log (newest first)")
        val events = probe { app.prefs.syncLogNow() }.getOrDefault(emptyList())
        if (events.isEmpty()) {
            appendLine("No new tracker data has been seen yet.")
            return
        }
        for (e in events.take(8)) {
            appendLine(
                "  ${Format.day(e.seenAt)} ${Format.time(e.seenAt)}  — up to ${Format.time(e.dataUpTo)} " +
                    "(${behind(e.dataUpTo, e.seenAt)}), +${Format.number(e.stepsDelta)}",
            )
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Runs one read, giving up after [PROBE_MS] so a source that never answers can't hold up the
     * whole report. Failures (and time-outs) come back as failures to print, not exceptions.
     */
    private suspend fun <T> probe(block: suspend () -> T): Result<T> = try {
        Result.success(withTimeout(PROBE_MS) { block() })
    } catch (e: TimeoutCancellationException) {
        Result.failure(IOException("no answer within ${PROBE_MS / 1000} s"))
    } catch (e: CancellationException) {
        throw e // the sheet was closed: stop, rather than report every read as failed
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("== $title ==")
    }

    private fun StringBuilder.line(label: String, value: String?) {
        appendLine("$label: ${value ?: "–"}")
    }

    private fun yesNo(value: Boolean) = if (value) "granted" else "no"

    private fun reason(e: Throwable) = e.message ?: e.javaClass.simpleName

    private fun describe(t: DayTotals, s: AppSettings) =
        "${Format.number(t.steps)} steps, ${Format.calories(t.calories)}, ${Format.distance(t.distanceMeters, s.distanceUnit)}"

    /** "2:41 PM (3m behind)". */
    private fun at(time: Instant, now: Instant) = "${Format.time(time)}  (${behind(time, now)})"

    /** How far [time] is behind [now], e.g. "3m behind", or "ahead" if it's in the future. */
    private fun behind(time: Instant, now: Instant): String {
        val d = Duration.between(time, now)
        return if (d.isNegative) "ahead" else Format.duration(d) + " behind"
    }
}
