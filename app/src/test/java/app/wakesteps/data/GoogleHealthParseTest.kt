package app.wakesteps.data

import app.wakesteps.core.SleepSpan
import app.wakesteps.core.SleepStage
import app.wakesteps.core.StageSpan
import app.wakesteps.widget.RenderTestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Google Health responses, shaped like the examples in Google's API docs. */
@RunWith(RobolectricTestRunner::class) // for Android's org.json
@Config(sdk = [35], application = RenderTestApp::class)
class GoogleHealthParseTest {

    @Test
    fun rollUpStepsNewestFirst() {
        val (points, next) = GoogleHealth.parseRollup(
            """
            {"rollupDataPoints": [
              {"startTime": "2026-07-29T09:00:00Z", "endTime": "2026-07-29T10:00:00Z", "steps": {"countSum": "3450"}},
              {"startTime": "2026-07-29T08:00:00Z", "endTime": "2026-07-29T09:00:00Z", "steps": {"countSum": "1200"}}
            ]}
            """,
        )
        assertNull(next)
        assertEquals(listOf(3450.0, 1200.0), points.map { GoogleHealth.steps(it) })
        assertEquals(Instant.parse("2026-07-29T08:00:00Z"), GoogleHealth.start(points[1]))
        assertEquals(Instant.parse("2026-07-29T10:00:00Z"), GoogleHealth.end(points[0]))
    }

    @Test
    fun rollUpDistanceAndCalories() {
        val (distance, _) = GoogleHealth.parseRollup(
            """{"rollupDataPoints": [{"startTime": "2026-07-29T08:00:00Z", "endTime": "2026-07-29T09:00:00Z",
               "distance": {"millimetersSum": "1534000"}}], "nextPageToken": "abc"}""",
        )
        assertEquals(1534.0, GoogleHealth.meters(distance.single())!!, 0.001)
        val (calories, next) = GoogleHealth.parseRollup(
            """{"rollupDataPoints": [{"startTime": "2026-07-29T08:00:00Z", "endTime": "2026-07-29T09:00:00Z",
               "totalCalories": {"kcalSum": 87.25}}], "nextPageToken": "abc"}""",
        )
        assertEquals(87.25, GoogleHealth.kcal(calories.single())!!, 0.001)
        assertEquals("abc", next)
    }

    @Test
    fun emptyRollUp() {
        val (points, next) = GoogleHealth.parseRollup("{}")
        assertEquals(0, points.size)
        assertNull(next)
    }

    @Test
    fun sleepSessions() {
        val (spans, next) = GoogleHealth.parseSleeps(
            """
            {"dataPoints": [{
              "name": "users/1/dataTypes/sleep/dataPoints/2",
              "sleep": {
                "interval": {"startTime": "2026-03-03T20:57:30Z", "startUtcOffset": "0s",
                             "endTime": "2026-03-04T04:41:30Z", "endUtcOffset": "0s"},
                "type": "STAGES",
                "metadata": {"main": true}
              }
            }], "nextPageToken": ""}
            """,
        )
        assertEquals(listOf(SleepSpan(Instant.parse("2026-03-03T20:57:30Z"), Instant.parse("2026-03-04T04:41:30Z"))), spans)
        assertNull(next)
    }

    @Test
    fun sleepWithStagesAndBriefWakeUps() {
        val (sessions, next) = GoogleHealth.parseSleepSessions(
            """
            {"dataPoints": [{
              "name": "users/1/dataTypes/sleep/dataPoints/9",
              "sleep": {
                "interval": {"startTime": "2026-10-18T23:00:00Z", "startUtcOffset": "-14400s",
                             "endTime": "2026-10-19T07:00:00Z", "endUtcOffset": "-14400s"},
                "type": "STAGES",
                "stages": [
                  {"startTime": "2026-10-19T01:00:00Z", "endTime": "2026-10-19T07:00:00Z", "type": "LIGHT"},
                  {"startTime": "2026-10-18T23:00:00Z", "endTime": "2026-10-18T23:12:00Z", "type": "AWAKE"},
                  {"startTime": "2026-10-18T23:12:00Z", "endTime": "2026-10-19T01:00:00Z", "type": "DEEP"},
                  {"startTime": "2026-10-19T01:00:00Z", "endTime": "2026-10-19T01:00:00Z", "type": "SLEEP_STAGE_TYPE_UNSPECIFIED"}
                ],
                "shortAwakenings": [{"startTime": "2026-10-19T03:10:00Z", "startUtcOffset": "-14400s",
                                     "endTime": "2026-10-19T03:11:00Z", "endUtcOffset": "-14400s"}],
                "metadata": {"stagesStatus": "SUCCEEDED", "processed": true, "mainSleep": true},
                "summary": {"minutesInSleepPeriod": "480", "minutesToFallAsleep": "12", "minutesAsleep": "468"}
              }
            }]}
            """,
        )
        assertNull(next)
        val s = sessions.single()
        assertEquals(Instant.parse("2026-10-18T23:00:00Z"), s.start)
        assertEquals(Instant.parse("2026-10-19T07:00:00Z"), s.end)
        // In time order, with the unknown stage left out.
        assertEquals(listOf(SleepStage.AWAKE, SleepStage.DEEP, SleepStage.LIGHT), s.stages.map { it.stage })
        // Brief wake-ups come without a type.
        assertEquals(StageSpan(Instant.parse("2026-10-19T03:10:00Z"), Instant.parse("2026-10-19T03:11:00Z"), SleepStage.AWAKE), s.shortAwakenings.single())
        assertEquals(12L, s.minutesToFallAsleep)
    }

    @Test
    fun sleepIsFilteredByWhenItEnded() {
        // Google refuses sleep.interval.start_time (INVALID_DATA_POINT_FILTER_DATA_TYPE_MEMBER).
        assertEquals(
            "sleep.interval.end_time >= \"2026-10-18T12:00:00Z\" AND sleep.interval.end_time < \"2026-10-19T12:00:00Z\"",
            GoogleHealth.sleepFilter(Instant.parse("2026-10-18T12:00:00.123456Z"), Instant.parse("2026-10-19T12:00:00Z")),
        )
    }

    @Test
    fun readableErrors() {
        val notLinked = """{"error": {"code": 400, "message": "The account is not linked to Google Health.",
            "status": "FAILED_PRECONDITION", "details": [{"@type": "type.googleapis.com/google.rpc.ErrorInfo",
            "reason": "ACCOUNT_NOT_LINKED", "domain": "health.googleapis.com"}]}}"""
        assertEquals("Sign in to the Google Health app with this Google account first.", GoogleHealth.parseError(400, notLinked))
        assertEquals("Something specific", GoogleHealth.parseError(500, """{"error": {"message": "Something specific"}}"""))
        assertEquals("Google Health error 503", GoogleHealth.parseError(503, null))
    }
}
