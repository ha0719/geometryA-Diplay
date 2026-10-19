package com.shilapi.xcertplay.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import com.shilapi.xcertplay.compat.systemService

/**
 * Optional Android 4.x helpers that automate the head unit's own radios around a CarPlay session:
 * turning the OEM hotspot on before connecting (head units without a settings screen reset it to
 * off at every boot), and pausing mobile data while projecting so the hotspot stays local-only
 * and the iPhone uses its own cellular link instead of the car's uplink. Everything is best-effort
 * reflection against hidden framework APIs of that era; failures return false and must never block
 * the connection itself.
 */
object CarNetworkAutomation {
    private const val PREFS = "diplay_car_network"
    private const val KEY_HOTSPOT_AUTO_ENABLE = "hotspot_auto_enable"
    private const val KEY_DATA_PAUSE = "data_pause_during_projection"
    private const val KEY_DATA_WAS_PAUSED_BY_US = "data_paused_by_diplay"
    private const val KEY_DATA_WAS_ENABLED_BEFORE_PAUSE = "data_enabled_before_pause"
    private const val HOTSPOT_ENABLE_TIMEOUT_MILLIS = 10_000L
    private const val HOTSPOT_POLL_MILLIS = 250L

    fun hotspotAutoEnableEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HOTSPOT_AUTO_ENABLE, true)

    fun saveHotspotAutoEnableEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_HOTSPOT_AUTO_ENABLE, value).apply()
    }

    fun dataPauseEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DATA_PAUSE, true)

    fun saveDataPauseEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DATA_PAUSE, value).apply()
    }

    /**
     * Reflection call to the hidden `WifiManager.setWifiApEnabled`. A null configuration keeps the
     * firmware's saved SSID/credentials; never pass a new one here, the phone already knows them.
     */
    fun requestHotspot(context: Context, enabled: Boolean): Boolean {
        val wifi = context.applicationContext.systemService(WifiManager::class.java, "wifi") ?: return false
        return runCatching {
            WifiManager::class.java
                .getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
                .invoke(wifi, null, enabled) as Boolean
        }.getOrDefault(false)
    }

    /** Blocks the calling (controller worker) thread until the AP reports enabled or gives up. */
    fun awaitHotspotEnabled(context: Context, timeoutMillis: Long = HOTSPOT_ENABLE_TIMEOUT_MILLIS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (CarHotspotStatus.isEnabled(context) == true) return true
            Thread.sleep(HOTSPOT_POLL_MILLIS)
        }
        return CarHotspotStatus.isEnabled(context) == true
    }

    private fun connectivity(context: Context): ConnectivityManager? =
        context.applicationContext.systemService(ConnectivityManager::class.java, "connectivity")

    /** Current mobile-data master switch, or null when the firmware hides the method. */
    fun mobileDataEnabled(context: Context): Boolean? = connectivity(context)?.let { manager ->
        runCatching {
            ConnectivityManager::class.java.getMethod("getMobileDataEnabled").invoke(manager) as Boolean
        }.getOrNull()
    }

    private fun setMobileData(context: Context, enabled: Boolean): Boolean {
        val manager = connectivity(context) ?: return false
        return runCatching {
            ConnectivityManager::class.java
                .getMethod("setMobileDataEnabled", Boolean::class.javaPrimitiveType)
                .invoke(manager, enabled)
            true
        }.getOrDefault(false)
    }

    /**
     * Pauses the car's mobile data while a CarPlay session runs. The prior state is remembered so
     * [restoreMobileData] only re-enables data this app actually turned off — data the user had
     * already switched off stays off.
     */
    fun pauseMobileDataForProjection(context: Context): Boolean {
        if (dataPausedByUs(context)) return true
        val before = mobileDataEnabled(context) ?: return false
        val turnedOff = !before || setMobileData(context, false)
        prefs(context).edit()
            .putBoolean(KEY_DATA_WAS_PAUSED_BY_US, turnedOff)
            .putBoolean(KEY_DATA_WAS_ENABLED_BEFORE_PAUSE, before)
            .apply()
        return turnedOff
    }

    /** Restores data paused by [pauseMobileDataForProjection]; idempotent and safe when paused elsewhere. */
    fun restoreMobileData(context: Context): Boolean {
        if (!dataPausedByUs(context)) return false
        val wasEnabledBefore = prefs(context).getBoolean(KEY_DATA_WAS_ENABLED_BEFORE_PAUSE, true)
        prefs(context).edit().putBoolean(KEY_DATA_WAS_PAUSED_BY_US, false).apply()
        return wasEnabledBefore && setMobileData(context, true)
    }

    fun dataPausedByUs(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DATA_WAS_PAUSED_BY_US, false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
