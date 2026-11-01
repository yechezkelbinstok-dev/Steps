package app.wakesteps.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import app.wakesteps.core.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Where the phone is, roughly (the network's idea of it is plenty for sunset), for Shabbos
 * times: with location allowed while the app is open, or all the time for the check before
 * Shabbos in the background.
 */
object Locator {

    fun allowed(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Allowed all the time, so the check before Shabbos can run without the app open. */
    fun allowedInBackground(context: Context): Boolean =
        allowed(context) && (Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Where the phone is now (or was last, if it can't tell now), named by town if it can be; null without permission. */
    suspend fun locate(context: Context): Place? {
        if (!allowed(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val location = current(context, manager) ?: lastKnown(manager) ?: return null
        return Place(location.latitude, location.longitude, ZoneId.systemDefault(), townOf(context, location))
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(context: Context, manager: LocationManager): Location? {
        if (Build.VERSION.SDK_INT < 30) return null
        val provider = when {
            Build.VERSION.SDK_INT >= 31 && manager.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { done ->
                val signal = CancellationSignal()
                done.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { done.resume(it) }
                } catch (e: Exception) {
                    done.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    /** The town, from the phone's geocoder; null if it doesn't know or can't be reached. */
    private suspend fun townOf(context: Context, location: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull()
    }
}
