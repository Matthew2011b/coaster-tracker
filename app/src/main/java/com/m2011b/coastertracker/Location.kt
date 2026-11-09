package com.m2011b.coastertracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat

typealias LatLon = Pair<Double, Double>

/** Gets a rough position (city-level is plenty for "nearest park"). Tests swap in a fixed one. */
interface Locator {
    fun hasPermission(): Boolean
    fun locate(onResult: (LatLon?) -> Unit)
}

class AndroidLocator(private val context: Context) : Locator {
    override fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun locate(onResult: (LatLon?) -> Unit) {
        if (!hasPermission()) return onResult(null)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 30 * 60 * 1000) return onResult(last.latitude to last.longitude)
        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return onResult(last?.let { it.latitude to it.longitude })
        lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { loc ->
            onResult((loc ?: last)?.let { it.latitude to it.longitude })
        }
    }
}

class FixedLocator(private val at: LatLon?) : Locator {
    override fun hasPermission() = true
    override fun locate(onResult: (LatLon?) -> Unit) = onResult(at)
}

val LocalHere = staticCompositionLocalOf<LatLon?> { null }
