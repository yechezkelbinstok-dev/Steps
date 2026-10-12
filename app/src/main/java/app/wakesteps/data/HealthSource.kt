package app.wakesteps.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import app.wakesteps.core.SleepSpan
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

/** Thin wrapper around Health Connect for the handful of reads the app needs. */
class HealthSource(private val context: Context) {

    companion object {
        val CORE_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )
        val TOTAL_CALORIES = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val ACTIVE_CALORIES = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val DISTANCE = HealthPermission.getReadPermission(DistanceRecord::class)

        /** Nice to have: the app still works on steps alone if these are declined. */
        val EXTRA_PERMISSIONS = setOf(TOTAL_CALORIES, ACTIVE_CALORIES, DISTANCE)
        const val BACKGROUND_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)
    val isAvailable: Boolean get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun hasCorePermissions(): Boolean = grantedPermissions().containsAll(CORE_PERMISSIONS)

    /** Whether this device's Health Connect supports reading while the app isn't open. */
    fun backgroundReadSupported(): Boolean =
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun hasBackgroundPermission(): Boolean = BACKGROUND_PERMISSION in grantedPermissions()

    private fun origins(sourcePackage: String?): Set<DataOrigin> =
        sourcePackage?.let { setOf(DataOrigin(it)) } ?: emptySet()

    suspend fun sleeps(from: Instant, to: Instant, sourcePackage: String?): List<SleepSpan> {
        val out = mutableListOf<SleepSpan>()
        var token: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    dataOriginFilter = origins(sourcePackage),
                    pageToken = token,
                )
            )
            resp.records.mapTo(out) { SleepSpan(it.startTime, it.endTime) }
            token = resp.pageToken
        } while (token != null)
        return out
    }

    /**
     * Steps plus whichever of calories/distance we're allowed to read, in one aggregate call.
     * Pass the result of [grantedPermissions] so metrics without access are skipped instead
     * of failing the whole read.
     */
    suspend fun totals(
        from: Instant,
        to: Instant,
        sourcePackage: String?,
        granted: Set<String>,
        calorieMode: CalorieMode,
    ): DayTotals {
        val m = Metrics(granted, calorieMode)
        if (!to.isAfter(from)) return m.empty()
        val result = client.aggregate(
            AggregateRequest(
                metrics = m.all,
                timeRangeFilter = TimeRangeFilter.between(from, to),
                dataOriginFilter = origins(sourcePackage),
            )
        )
        return m.read(result)
    }

    /**
     * Midnight-to-midnight totals for each calendar day from [firstDay] through today, the way
     * Fitbit / Google count them. One Health Connect call for the whole range.
     */
    suspend fun calendarDays(
        firstDay: LocalDate,
        now: LocalDateTime,
        sourcePackage: String?,
        granted: Set<String>,
        calorieMode: CalorieMode,
    ): List<Pair<LocalDate, DayTotals>> {
        val m = Metrics(granted, calorieMode)
        val byDay = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = m.all,
                timeRangeFilter = TimeRangeFilter.between(firstDay.atStartOfDay(), now),
                timeRangeSlicer = Period.ofDays(1),
                dataOriginFilter = origins(sourcePackage),
            )
        ).associate { it.startTime.toLocalDate() to m.read(it.result) }
        // Days with no data at all come back missing; show them as zero rather than skipping.
        return generateSequence(now.toLocalDate()) { it.minusDays(1) }
            .takeWhile { !it.isBefore(firstDay) }
            .map { it to (byDay[it] ?: m.empty()) }
            .toList()
    }

    /** Which metrics to aggregate given the user's grants and calorie preference. */
    private class Metrics(granted: Set<String>, calorieMode: CalorieMode) {
        val calories: AggregateMetric<Energy>? = when {
            calorieMode == CalorieMode.TOTAL && TOTAL_CALORIES in granted -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
            calorieMode == CalorieMode.ACTIVE && ACTIVE_CALORIES in granted -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
            else -> null
        }
        val distance: AggregateMetric<Length>? = if (DISTANCE in granted) DistanceRecord.DISTANCE_TOTAL else null
        val all: Set<AggregateMetric<*>> = setOfNotNull(StepsRecord.COUNT_TOTAL, calories, distance)

        fun empty() = DayTotals(0, calories?.let { 0.0 }, distance?.let { 0.0 })

        fun read(r: AggregationResult) = DayTotals(
            steps = r[StepsRecord.COUNT_TOTAL] ?: 0,
            calories = calories?.let { r[it]?.inKilocalories ?: 0.0 },
            distanceMeters = distance?.let { r[it]?.inMeters ?: 0.0 },
        )
    }

    /** Steps bucketed by [bucket], starting at [from]. */
    suspend fun stepsBuckets(
        from: Instant,
        to: Instant,
        bucket: Duration,
        sourcePackage: String?,
    ): List<Pair<Instant, Long>> {
        if (!to.isAfter(from)) return emptyList()
        return client.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from, to),
                timeRangeSlicer = bucket,
                dataOriginFilter = origins(sourcePackage),
            )
        ).map { it.startTime to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
    }

    /** A bookmark in Health Connect's change log for steps and sleep. */
    suspend fun changesToken(sourcePackage: String?): String = client.getChangesToken(
        ChangesTokenRequest(
            recordTypes = setOf(StepsRecord::class, SleepSessionRecord::class),
            dataOriginFilters = origins(sourcePackage),
        )
    )

    /**
     * Whether anything was written since [token], plus the bookmark for next time (null if the
     * old one expired). The cheap call Health Connect recommends for frequent checks.
     */
    suspend fun pollChanges(token: String): Pair<Boolean, String?> {
        var next = token
        var changed = false
        do {
            val response = client.getChanges(next)
            if (response.changesTokenExpired) return true to null
            changed = changed || response.changes.isNotEmpty()
            next = response.nextChangesToken
        } while (response.hasMore)
        return changed to next
    }

    /** End time of the most recent step record, to show how fresh the tracker's sync is. */
    suspend fun latestStepTime(since: Instant, now: Instant, sourcePackage: String?): Instant? {
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, now),
                dataOriginFilter = origins(sourcePackage),
                ascendingOrder = false,
                pageSize = 1,
            )
        )
        return resp.records.firstOrNull()?.endTime
    }

    /** Apps that wrote steps recently, so the user can pin one source (e.g. Fitbit). */
    suspend fun recentStepSources(since: Instant, now: Instant): List<String> {
        val packages = linkedSetOf<String>()
        var token: String? = null
        var pages = 0
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(since, now),
                    ascendingOrder = false,
                    pageSize = 1000,
                    pageToken = token,
                )
            )
            resp.records.mapTo(packages) { it.metadata.dataOrigin.packageName }
            token = resp.pageToken
        } while (token != null && ++pages < 5)
        return packages.toList()
    }
}
