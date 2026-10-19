package app.wakesteps.data

import android.content.Context
import android.content.pm.PackageManager
import app.wakesteps.core.SleepSession
import app.wakesteps.core.SleepSpan
import app.wakesteps.core.SleepStage
import app.wakesteps.core.StageSpan
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads steps, calories, distance and sleep straight from Google Health (the Google Health API,
 * successor of the Fitbit Web API), which can be fresher than what the Fitbit / Google Health
 * app has written to Health Connect so far.
 *
 * Sign-in goes through Google Play services: Google recognises the app by its package name and
 * signing certificate, registered once as an Android OAuth client in the user's own Google Cloud
 * project (see the setup sheet in Settings), so no keys live in the app.
 */
class GoogleHealth(private val context: Context) {

    companion object {
        const val SCOPE_ACTIVITY = "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly"
        const val SCOPE_SLEEP = "https://www.googleapis.com/auth/googlehealth.sleep.readonly"
        /** Heart rate (for sound sleep): asked for on its own, so steps and sleep never wait on it. */
        const val SCOPE_HEALTH = "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly"
        private const val BASE = "https://health.googleapis.com/v4/users/me/dataTypes"

        /** Google Play services' code for an app it doesn't recognise (no matching OAuth client). */
        private const val DEVELOPER_ERROR = 10

        // ------------------------------------------------ response parsing (unit-tested)

        /** One page of `rollupDataPoints` (newest first, as the API sends them) and the next page token. */
        fun parseRollup(json: String): Pair<List<JSONObject>, String?> {
            val root = JSONObject(json)
            val points = root.optJSONArray("rollupDataPoints") ?: JSONArray()
            return (0 until points.length()).map { points.getJSONObject(it) } to
                root.optString("nextPageToken").takeIf { it.isNotEmpty() }
        }

        fun start(point: JSONObject): Instant? = point.optString("startTime").takeIf { it.isNotEmpty() }?.let(Instant::parse)
        fun end(point: JSONObject): Instant? = point.optString("endTime").takeIf { it.isNotEmpty() }?.let(Instant::parse)

        val steps: (JSONObject) -> Double? = { it.optJSONObject("steps")?.optString("countSum")?.toDoubleOrNull() }
        val kcal: (JSONObject) -> Double? = { it.optJSONObject("totalCalories")?.optDouble("kcalSum")?.takeUnless(Double::isNaN) }
        /** A heart-rate window's average, in beats a minute. */
        val bpm: (JSONObject) -> Double? = { it.optJSONObject("heartRate")?.optDouble("beatsPerMinuteAvg")?.takeUnless(Double::isNaN) }

        /** Resting heart rate by day, from a `daily-resting-heart-rate` list. */
        fun parseRestingHeartRates(json: String): Map<LocalDate, Double> {
            val points = JSONObject(json).optJSONArray("dataPoints") ?: JSONArray()
            return (0 until points.length()).mapNotNull { i ->
                val p = points.getJSONObject(i).optJSONObject("dailyRestingHeartRate") ?: return@mapNotNull null
                val date = when (val d = p.opt("date")) {
                    is JSONObject -> runCatching { LocalDate.of(d.getInt("year"), d.getInt("month"), d.getInt("day")) }.getOrNull()
                    is String -> runCatching { LocalDate.parse(d) }.getOrNull()
                    else -> null
                } ?: return@mapNotNull null
                number(p, "beatsPerMinute")?.let { date to it.toDouble() }
            }.toMap()
        }

        val meters: (JSONObject) -> Double? = {
            it.optJSONObject("distance")?.optString("millimetersSum")?.toDoubleOrNull()?.div(1000.0)
        }

        /**
         * Sleep sessions from a `dataPoints` list, with their stages, brief wake-ups and time to
         * fall asleep, plus the next page token.
         */
        fun parseSleepSessions(json: String): Pair<List<SleepSession>, String?> {
            val root = JSONObject(json)
            val points = root.optJSONArray("dataPoints") ?: JSONArray()
            val sessions = (0 until points.length()).mapNotNull { i ->
                val sleep = points.getJSONObject(i).optJSONObject("sleep") ?: return@mapNotNull null
                val interval = sleep.optJSONObject("interval") ?: return@mapNotNull null
                SleepSession(
                    start = time(interval, "startTime") ?: return@mapNotNull null,
                    end = time(interval, "endTime") ?: return@mapNotNull null,
                    stages = stageSpans(sleep.optJSONArray("stages")),
                    // Brief wake-ups are just a start and an end, without a stage type.
                    shortAwakenings = stageSpans(sleep.optJSONArray("shortAwakenings"), SleepStage.AWAKE),
                    minutesToFallAsleep = sleep.optJSONObject("summary")?.let { number(it, "minutesToFallAsleep") },
                )
            }
            return sessions to root.optString("nextPageToken").takeIf { it.isNotEmpty() }
        }

        /**
         * The filter for sleep that ended from [from] up to [to]. Sleep is the one type Google only
         * filters by when it ended (asking by its start is refused as an invalid member).
         */
        fun sleepFilter(from: Instant, to: Instant): String {
            val a = from.truncatedTo(ChronoUnit.SECONDS)
            val b = to.truncatedTo(ChronoUnit.SECONDS)
            return "sleep.interval.end_time >= \"$a\" AND sleep.interval.end_time < \"$b\""
        }

        /** Just when each sleep started and ended (what the day logic needs), plus the next page token. */
        fun parseSleeps(json: String): Pair<List<SleepSpan>, String?> =
            parseSleepSessions(json).let { (sessions, next) -> sessions.map { SleepSpan(it.start, it.end) } to next }

        private fun time(o: JSONObject, key: String): Instant? = o.optString(key).takeIf { it.isNotEmpty() }?.let(Instant::parse)

        /** Whole-number fields arrive as strings ("462"); plain numbers are taken too. */
        private fun number(o: JSONObject, key: String): Long? = when (val v = o.opt(key)) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        }

        /** Stage segments, each of its own type, or all of [only] when given. */
        private fun stageSpans(array: JSONArray?, only: SleepStage? = null): List<StageSpan> = (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val s = array!!.getJSONObject(i)
            val stage = only ?: SleepStage.entries.firstOrNull { it.name == s.optString("type") } ?: return@mapNotNull null
            StageSpan(time(s, "startTime") ?: return@mapNotNull null, time(s, "endTime") ?: return@mapNotNull null, stage)
        }.sortedBy { it.start }

        /** A readable reason from an API error body: Google's `error.message`, or the status. */
        fun parseError(code: Int, body: String?): String {
            val error = runCatching { JSONObject(body ?: "").optJSONObject("error") }.getOrNull()
            val reason = error?.optJSONArray("details")?.let { d ->
                (0 until d.length()).firstNotNullOfOrNull { d.getJSONObject(it).optString("reason").takeIf(String::isNotEmpty) }
            }
            return when {
                reason == "ACCOUNT_NOT_LINKED" -> "Sign in to the Google Health app with this Google account first."
                reason == "SERVICE_DISABLED" || reason == "API_DISABLED" -> "The Google Health API isn't turned on in your project."
                code == 401 -> "Google sign-in expired. Connect again."
                code == 429 -> "Google Health is busy. Trying again shortly."
                else -> error?.optString("message")?.takeIf { it.isNotEmpty() } ?: "Google Health error $code"
            }
        }
    }

    class ApiError(val code: Int, message: String) : IOException(message)

    private val authorization by lazy { Identity.getAuthorizationClient(context) }

    private fun request(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_ACTIVITY), Scope(SCOPE_SLEEP)))
        .build()

    @Volatile private var cached: Pair<String, Instant>? = null

    private fun heartRequest(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_HEALTH)))
        .build()

    @Volatile private var heartCached: Pair<String, Instant>? = null

    /**
     * Asks Google for access. If the user still has to agree (first time, or after it lapsed) the
     * result carries a screen to show; from the app, launch it. Throws with a readable message when
     * Google doesn't recognise the app yet.
     */
    suspend fun authorize(): AuthorizationResult = try {
        authorization.authorize(request()).await()
    } catch (e: ApiException) {
        throw IOException(
            if (e.statusCode == DEVELOPER_ERROR) "Google doesn't recognise the app yet: check the Android client (package name and SHA-1)."
            else "Google sign-in failed (${e.statusCode}).",
            e,
        )
    }

    /** Reads the outcome of the consent screen launched from [authorize]. */
    fun resultFrom(intent: android.content.Intent?): AuthorizationResult =
        authorization.getAuthorizationResultFromIntent(intent)

    /** An access token without showing anything, or null when the user needs to (re)connect. */
    suspend fun token(): String? {
        cached?.let { (token, at) -> if (Duration.between(at, Instant.now()) < Duration.ofMinutes(40)) return token }
        val result = runCatching { authorize() }.getOrNull() ?: return null
        if (result.hasResolution()) return null
        return result.accessToken?.also { cached = it to Instant.now() }
    }

    fun forgetToken() {
        cached = null
        heartCached = null
    }

    /** Asks for heart rate. As with [authorize], the result may carry Google's consent screen to show. */
    suspend fun authorizeHeart(): AuthorizationResult = try {
        authorization.authorize(heartRequest()).await()
    } catch (e: ApiException) {
        throw IOException("Google sign-in failed (${e.statusCode}).", e)
    }

    /** A token for heart rate without showing anything, or null until the user allows it. */
    suspend fun heartToken(): String? {
        heartCached?.let { (token, at) -> if (Duration.between(at, Instant.now()) < Duration.ofMinutes(40)) return token }
        val result = runCatching { authorizeHeart() }.getOrNull() ?: return null
        if (result.hasResolution()) return null
        return result.accessToken?.also { heartCached = it to Instant.now() }
    }

    /** Keeps the token from the consent screen, so heart rate can be read straight away. */
    fun keepHeartToken(result: AuthorizationResult) {
        result.accessToken?.let { heartCached = it to Instant.now() }
    }

    // ------------------------------------------------ reads

    /** Throws a readable error unless this Google account is linked to Google Health. */
    suspend fun checkLinked(token: String) {
        call(token, "GET", "https://health.googleapis.com/v4/users/me/identity", null)
    }

    /** Steps, calories and distance between [from] and [to], in parallel. */
    suspend fun totals(token: String, from: Instant, to: Instant): DayTotals = coroutineScope {
        if (!to.isAfter(from)) return@coroutineScope DayTotals(0, 0.0, 0.0)
        val window = "${Duration.between(from, to).seconds.coerceAtLeast(1)}s"
        // Calories can only be read 14 days at a time.
        val calStart = maxOf(from, to.minus(Duration.ofDays(14)))
        val s = async { rollUp(token, "steps", from, to, window).sumOf { steps(it) ?: 0.0 } }
        val c = async { runCatching { rollUp(token, "total-calories", calStart, to, window).sumOf { kcal(it) ?: 0.0 } }.getOrNull() }
        val d = async { runCatching { rollUp(token, "distance", from, to, window).sumOf { meters(it) ?: 0.0 } }.getOrNull() }
        DayTotals(s.await().toLong(), c.await(), d.await())
    }

    /** Steps per [bucket] from [from] to [to], oldest first, every bucket present. */
    suspend fun stepsBuckets(token: String, from: Instant, to: Instant, bucket: Duration): List<Pair<Instant, Long>> {
        if (!to.isAfter(from)) return emptyList()
        val starts = generateSequence(from) { it.plus(bucket) }.takeWhile { it.isBefore(to) }.toList()
        val counts = LongArray(starts.size)
        // Windows run forward from [from]; place each by its offset rather than an exact match.
        for (p in rollUp(token, "steps", from, to, "${bucket.seconds}s")) {
            val at = start(p) ?: continue
            val i = Math.round(Duration.between(from, at).toMillis().toDouble() / bucket.toMillis()).toInt()
            if (i in counts.indices) counts[i] += (steps(p) ?: 0.0).toLong()
        }
        return starts.mapIndexed { i, t -> t to counts[i] }
    }

    /** End of the newest minute with steps, looking back from [now] to [since]. */
    suspend fun latestStepTime(token: String, since: Instant, now: Instant): Instant? =
        rollUp(token, "steps", since, now, "60s")
            .filter { (steps(it) ?: 0.0) > 0 }
            .mapNotNull { end(it) }
            .maxOrNull()

    /** Sleep sessions that ended between [from] and [to]. */
    suspend fun sleeps(token: String, from: Instant, to: Instant): List<SleepSpan> =
        sleepSessions(token, from, to).map { SleepSpan(it.start, it.end) }.distinct()

    /** Sleep sessions that ended between [from] and [to], with their stages. */
    suspend fun sleepSessions(token: String, from: Instant, to: Instant): List<SleepSession> {
        val out = mutableListOf<SleepSession>()
        var page: String? = null
        do {
            val query = buildString {
                append("filter=").append(URLEncoder.encode(sleepFilter(from, to), "UTF-8"))
                page?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val (sessions, next) = parseSleepSessions(call(token, "GET", "$BASE/sleep/dataPoints:reconcile?$query", null))
            out += sessions
            page = next
        } while (page != null)
        return out.distinctBy { it.start to it.end }
    }

    /** Each minute's average heart rate from [from] to [to], oldest first; minutes without any are left out. */
    suspend fun heartRate(token: String, from: Instant, to: Instant): List<Pair<Instant, Double>> =
        rollUp(token, "heart-rate", from, to, "60s")
            .mapNotNull { p -> start(p)?.let { at -> bpm(p)?.let { at to it } } }
            .sortedBy { it.first }

    /** The resting heart rate Google worked out for each of the last couple of months. */
    suspend fun restingHeartRates(token: String): Map<LocalDate, Double> =
        parseRestingHeartRates(call(token, "GET", "$BASE/daily-resting-heart-rate/dataPoints?pageSize=60", null))

    /** Every rolled-up window of [type] between [from] and [to], all pages. */
    private suspend fun rollUp(token: String, type: String, from: Instant, to: Instant, window: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var page: String? = null
        do {
            val body = JSONObject()
                .put("range", JSONObject().put("startTime", from.toString()).put("endTime", to.toString()))
                .put("windowSize", window)
                .put("pageSize", 10_000)
            page?.let { body.put("pageToken", it) }
            val (points, next) = parseRollup(call(token, "POST", "$BASE/$type/dataPoints:rollUp", body.toString()))
            out += points
            page = next
        } while (page != null)
        return out
    }

    private suspend fun call(token: String, method: String, url: String, body: String?): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.let { conn.outputStream.use { o -> o.write(it.toByteArray()) } }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                if (code == 401) forgetToken()
                throw ApiError(code, parseError(code, err))
            }
        } finally {
            conn.disconnect()
        }
    }

    /** The app's signing certificate fingerprint, as Google Cloud asks for it (AA:BB:…). */
    fun signingSha1(): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val cert = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert).joinToString(":") { "%02X".format(it) }
    }.getOrNull()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
